/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.marshal;

import org.apache.xmlbeans.GDate;
import org.apache.xmlbeans.GDuration;
import org.apache.xmlbeans.XmlCalendar;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.binding.bts.BindingLoader;
import org.apache.xmlbeans.impl.binding.bts.BindingType;
import org.apache.xmlbeans.impl.binding.bts.BindingTypeName;
import org.apache.xmlbeans.impl.binding.bts.JavaTypeName;
import org.apache.xmlbeans.impl.binding.bts.SimpleDocumentBinding;
import org.apache.xmlbeans.impl.binding.bts.XmlTypeName;
import org.apache.xmlbeans.impl.common.InvalidLexicalValueException;
import org.apache.xmlbeans.impl.richParser.XMLStreamReaderExt;
import org.apache.xmlbeans.impl.richParser.XMLStreamReaderExtImpl;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;

/**
 * An UnmarshalResult holds the mutable state using by an Unmarshaller
 * during unmarshalling.  Example contents are an id -> object table
 * for href processing, and the position in the xml document.
 *
 * The UnmarshalResult is purposefullly unsynchronized.
 * Only one thread should ever be accessing this object, and a new one will be
 * required for each unmarshalling pass.
 */
final class UnmarshalResult
{
    //per binding context objects
    private final BindingLoader bindingLoader;
    private final RuntimeBindingTypeTable typeTable;

    //our state
    private XMLStreamReaderExt baseReader;
    private final Collection errors;
    private final XsiAttributeHolder xsiAttributeHolder =
        new XsiAttributeHolder();
    private boolean gotXsiAttributes;
    private BitSet defaultAttributeBits;
    private int currentAttributeIndex = INVALID;
    private int currentAttributeCount = INVALID;

    private static final int INVALID = -1;


    UnmarshalResult(BindingLoader bindingLoader,
                    RuntimeBindingTypeTable typeTable,
                    XmlOptions options)
    {
        this.bindingLoader = bindingLoader;
        this.typeTable = typeTable;
        this.errors = BindingContextImpl.extractErrorHandler(options);
    }


    void setXmlStream(XMLStreamReader reader)
    {
        assert reader != null;

        baseReader = createExtendedReader(reader);
        updateAttributeState();
    }


    private static XMLStreamReaderExt createExtendedReader(XMLStreamReader reader)
    {
        if (reader instanceof XMLStreamReaderExt) {
            return (XMLStreamReaderExt)reader;
        } else {
            return new XMLStreamReaderExtImpl(reader);
        }
    }

    //returns null and updates errors if there was a problem.
    TypeUnmarshaller getTypeUnmarshaller(QName xsi_type)
    {
        XmlTypeName xname = XmlTypeName.forTypeNamed(xsi_type);
        BindingType binding_type =
            bindingLoader.getBindingType(bindingLoader.lookupPojoFor(xname));
        if (binding_type == null) {
            addError("unknown type: " + xsi_type);
            return null;
        }
        TypeUnmarshaller um =
            typeTable.getTypeUnmarshaller(binding_type);
        if (um == null) {
            String msg = "unable to locate unmarshaller for " +
                binding_type.getName();
            addError(msg);
            return null;
        }
        return um;
    }

    private void addError(String msg)
    {
        addError(msg, baseReader.getLocation());
    }


    void addError(String msg, Location location)
    {
        MarshalStreamUtils.addError(errors, msg,
                                    location,
                                    "<unknown>");
    }


    Object unmarshal(XMLStreamReader reader)
        throws XmlException
    {
        setXmlStream(reader);
        advanceToFirstItemOfInterest();
        BindingType bindingType = determineRootType();
        return unmarshalBindingType(bindingType);
    }


    private Object unmarshalBindingType(BindingType bindingType)
        throws XmlException
    {
        TypeUnmarshaller um =
            typeTable.getOrCreateTypeUnmarshaller(bindingType, bindingLoader);

        this.updateAttributeState();

        return um.unmarshal(this);
    }

    Object unmarshalType(XMLStreamReader reader,
                         QName schemaType,
                         String javaType)
        throws XmlException
    {
        setXmlStream(reader);

        final QName xsi_type = getXsiType();

        BindingType btype = null;

        if (xsi_type != null) {
            btype = getPojoTypeFromXsiType(xsi_type);
        }

        if (btype == null) {
            btype = determineBindingType(schemaType, javaType);
        }

        if (btype == null) {
            final String msg = "unable to find binding type for " +
                schemaType + " : " + javaType;
            throw new XmlException(msg);
        }
        return unmarshalBindingType(btype);
    }

    private BindingType determineBindingType(QName schemaType, String javaType)
    {
        XmlTypeName xname = XmlTypeName.forTypeNamed(schemaType);
        JavaTypeName jname = JavaTypeName.forString(javaType);
        BindingTypeName btname = BindingTypeName.forPair(jname, xname);
        return bindingLoader.getBindingType(btname);
    }

    private BindingType determineRootType()
        throws XmlException
    {
        QName xsi_type = this.getXsiType();

        BindingType retval = null;
        if (xsi_type != null) {
            retval = getPojoTypeFromXsiType(xsi_type);
        }

        if (retval == null) {
            QName root_elem_qname = new QName(this.getNamespaceURI(),
                                              this.getLocalName());
            final XmlTypeName type_name =
                XmlTypeName.forGlobalName(XmlTypeName.ELEMENT, root_elem_qname);
            BindingType doc_binding_type = getPojoBindingType(type_name, true);
            SimpleDocumentBinding sd = (SimpleDocumentBinding)doc_binding_type;
            retval = getPojoBindingType(sd.getTypeOfElement(), true);
        }

        return retval;
    }

    //will return null on error and log errors
    private BindingType getPojoTypeFromXsiType(QName xsi_type)
        throws XmlException
    {
        final XmlTypeName type_name = XmlTypeName.forTypeNamed(xsi_type);
        final BindingType pojoBindingType = getPojoBindingType(type_name, false);
        assert !(pojoBindingType instanceof SimpleDocumentBinding);
        return pojoBindingType;
    }


    private BindingType getPojoBindingType(final XmlTypeName type_name,
                                           boolean fail_fast)
        throws XmlException
    {
        final BindingTypeName btName = bindingLoader.lookupPojoFor(type_name);
        if (btName == null) {
            final String msg = "failed to load java type corresponding " +
                "to " + type_name;
            if (fail_fast) {
                throw new XmlException(msg);
            } else {
                addError(msg);
                return null;
            }

        }

        BindingType bt = bindingLoader.getBindingType(btName);

        if (bt == null) {
            final String msg = "failed to load BindingType for " + btName;
            if (fail_fast) {
                throw new XmlException(msg);
            } else {
                addError(msg);
                return null;
            }
        }

        return bt;
    }


    // ======================= xml access methods =======================

    String getStringValue() throws XmlException
    {
        try {
            return baseReader.getStringValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    String getStringValue(int ws) throws XmlException
    {
        try {
            return baseReader.getStringValue(ws);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    boolean getBooleanValue() throws XmlException
    {
        try {
            return baseReader.getBooleanValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    byte getByteValue() throws XmlException
    {
        try {
            return baseReader.getByteValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    short getShortValue() throws XmlException
    {
        try {
            return baseReader.getShortValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    int getIntValue() throws XmlException
    {
        try {
            return baseReader.getIntValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    long getLongValue() throws XmlException
    {
        try {
            return baseReader.getLongValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    BigInteger getBigIntegerValue() throws XmlException
    {
        try {
            return baseReader.getBigIntegerValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    BigDecimal getBigDecimalValue() throws XmlException
    {
        try {
            return baseReader.getBigDecimalValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    float getFloatValue() throws XmlException
    {
        try {
            return baseReader.getFloatValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    double getDoubleValue() throws XmlException
    {
        try {
            return baseReader.getDoubleValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    InputStream getHexBinaryValue() throws XmlException
    {
        try {
            return baseReader.getHexBinaryValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    InputStream getBase64Value() throws XmlException
    {
        try {
            return baseReader.getBase64Value();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    XmlCalendar getCalendarValue() throws XmlException
    {
        try {
            return baseReader.getCalendarValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    String getAnyUriValue() throws XmlException
    {
        try {
            return baseReader.getStringValue(XMLStreamReaderExt.WS_COLLAPSE);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    Date getDateValue() throws XmlException
    {
        try {
            final GDate val = baseReader.getGDateValue();
            return val == null ? null : val.getDate();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    GDate getGDateValue() throws XmlException
    {
        try {
            return baseReader.getGDateValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    GDuration getGDurationValue() throws XmlException
    {
        try {
            return baseReader.getGDurationValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    QName getQNameValue() throws XmlException
    {
        try {
            return baseReader.getQNameValue();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    String getAttributeStringValue() throws XmlException
    {
        try {
            return baseReader.getAttributeStringValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    boolean getAttributeBooleanValue() throws XmlException
    {
        try {
            return baseReader.getAttributeBooleanValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    byte getAttributeByteValue() throws XmlException
    {
        try {
            return baseReader.getAttributeByteValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    short getAttributeShortValue() throws XmlException
    {
        try {
            return baseReader.getAttributeShortValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    int getAttributeIntValue() throws XmlException
    {
        try {
            return baseReader.getAttributeIntValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    long getAttributeLongValue() throws XmlException
    {
        try {
            return baseReader.getAttributeLongValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    BigInteger getAttributeBigIntegerValue() throws XmlException
    {
        try {
            return baseReader.getAttributeBigIntegerValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    BigDecimal getAttributeBigDecimalValue() throws XmlException
    {
        try {
            return baseReader.getAttributeBigDecimalValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    float getAttributeFloatValue() throws XmlException
    {
        try {
            return baseReader.getAttributeFloatValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    double getAttributeDoubleValue() throws XmlException
    {
        try {
            return baseReader.getAttributeDoubleValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    String getAttributeAnyUriValue() throws XmlException
    {
        try {
            return baseReader.getAttributeStringValue(currentAttributeIndex,
                                                      XMLStreamReaderExt.WS_COLLAPSE);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    InputStream getAttributeHexBinaryValue() throws XmlException
    {
        try {
            return baseReader.getAttributeHexBinaryValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    InputStream getAttributeBase64Value() throws XmlException
    {
        try {
            return baseReader.getAttributeBase64Value(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    XmlCalendar getAttributeCalendarValue() throws XmlException
    {
        try {
            return baseReader.getAttributeCalendarValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    Date getAttributeDateValue() throws XmlException
    {
        try {
            GDate val = baseReader.getAttributeGDateValue(currentAttributeIndex);
            return val == null ? null : val.getDate();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    GDate getAttributeGDateValue() throws XmlException
    {
        try {
            return baseReader.getAttributeGDateValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    GDuration getAttributeGDurationValue() throws XmlException
    {
        try {
            return baseReader.getAttributeGDurationValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    QName getAttributeQNameValue() throws XmlException
    {
        try {
            return baseReader.getAttributeQNameValue(currentAttributeIndex);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }


    /**
     * return the QName value found for xsi:type
     * or null if neither one was found
     */
    QName getXsiType() throws XmlException
    {
        if (!gotXsiAttributes) {
            getXsiAttributes();
        }
        assert gotXsiAttributes;
        return xsiAttributeHolder.xsiType;
    }

    boolean hasXsiNil() throws XmlException
    {
        if (!gotXsiAttributes) {
            getXsiAttributes();
        }
        assert gotXsiAttributes;
        return xsiAttributeHolder.hasXsiNil;
    }

    private void getXsiAttributes() throws XmlException
    {
        try {
            MarshalStreamUtils.getXsiAttributes(xsiAttributeHolder,
                                                baseReader, errors);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
        gotXsiAttributes = true;
    }

    /**
     *
     * @return  false if we hit an end element (any end element at all)
     */
    boolean advanceToNextStartElement()
        throws XmlException
    {
        final boolean ret;
        ret = MarshalStreamUtils.advanceToNextStartElement(baseReader);
        updateAttributeState();
        return ret;
    }


    void advanceToFirstItemOfInterest()
        throws XmlException
    {
        assert baseReader != null;
        MarshalStreamUtils.advanceToFirstItemOfInterest(baseReader);
    }

    int next() throws XmlException
    {
        try {
            final int new_state = baseReader.next();
            updateAttributeState();
            return new_state;
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    boolean hasNext() throws XmlException
    {
        try {
            return baseReader.hasNext();
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }


    private void updateAttributeState()
    {
        xsiAttributeHolder.reset();
        gotXsiAttributes = false;
        if (defaultAttributeBits != null) {
            defaultAttributeBits.clear();
        }
        if (baseReader.isStartElement()) {
            currentAttributeCount = baseReader.getAttributeCount();
            currentAttributeIndex = 0;
        } else {
            currentAttributeIndex = INVALID;
            currentAttributeCount = INVALID;
        }
    }


    boolean isStartElement()
    {
        return baseReader.isStartElement();
    }

    boolean isEndElement()
    {
        return baseReader.isEndElement();
    }

    int getAttributeCount()
    {
        assert baseReader.isStartElement();

        return baseReader.getAttributeCount();
    }

    String getLocalName()
    {
        return baseReader.getLocalName();
    }

    String getNamespaceURI()
    {
        return baseReader.getNamespaceURI();
    }

    void skipElement()
        throws XmlException
    {
        MarshalStreamUtils.skipElement(baseReader);
        updateAttributeState();
    }


    void advanceAttribute()
    {
        assert hasMoreAttributes();
        assert currentAttributeCount != INVALID;
        assert currentAttributeIndex != INVALID;

        currentAttributeIndex++;

        assert currentAttributeIndex <= currentAttributeCount;
    }

    boolean hasMoreAttributes()
    {
        assert baseReader.isStartElement();

        assert currentAttributeCount != INVALID;
        assert currentAttributeIndex != INVALID;

        return (currentAttributeIndex < currentAttributeCount);
    }

    String getCurrentAttributeNamespaceURI()
    {
        assert currentAttributeCount != INVALID;
        assert currentAttributeIndex != INVALID;

        return baseReader.getAttributeNamespace(currentAttributeIndex);
    }

    String getCurrentAttributeLocalName()
    {
        assert currentAttributeCount != INVALID;
        assert currentAttributeIndex != INVALID;

        return baseReader.getAttributeLocalName(currentAttributeIndex);
    }

    void attributePresent(int att_idx)
    {
        if (defaultAttributeBits == null) {
            int bits_size = getAttributeCount();
            defaultAttributeBits = new BitSet(bits_size);
        }

        defaultAttributeBits.set(att_idx);
    }

    boolean isAttributePresent(int att_idx)
    {
        if (defaultAttributeBits == null)
            return false;

        return defaultAttributeBits.get(att_idx);
    }

    void setNextElementDefault(String lexical_default)
        throws XmlException
    {
        try {
            baseReader.setDefaultValue(lexical_default);
        }
        catch (XMLStreamException e) {
            throw new XmlException(e);
        }
    }

    static void fillElementProp(RuntimeBindingProperty prop,
                                UnmarshalResult context,
                                Object inter)
        throws XmlException
    {
        final TypeUnmarshaller um = prop.getTypeUnmarshaller(context);
        assert um != null;

        try {
            final String lexical_default = prop.getLexicalDefault();
            if (lexical_default != null) {
                context.setNextElementDefault(lexical_default);
            }
            final Object prop_val = um.unmarshal(context);
            prop.fill(inter, prop_val);
        }
        catch (InvalidLexicalValueException ilve) {
            //unlike attributes, the error has been added to the context
            //already via BaseSimpleTypeConveter...
        }
    }

    static void fillAttributeProp(RuntimeBindingProperty prop,
                                  UnmarshalResult context,
                                  Object inter)
        throws XmlException
    {
        final TypeUnmarshaller um = prop.getTypeUnmarshaller(context);
        assert um != null;

        try {
            final Object prop_val = um.unmarshalAttribute(context);
            prop.fill(inter, prop_val);
        }
        catch (InvalidLexicalValueException ilve) {
            //TODO: review error messages
            String msg = "invalid value for " + prop.getName() +
                ": " + ilve.getMessage();
            context.addError(msg, ilve.getLocation());
        }
    }

    /**
     * Do the supplied localname, uri pair match the given qname?
     *
     * @param qn          name of element
     * @param localname   candidate localname
     * @param uri         candidtate uri
     * @return
     */
    static boolean doesElementMatch(QName qn, String localname, String uri)
    {
        if (qn.getLocalPart().equals(localname)) {
            //QNames always uses "" for no namespace, but the incoming uri
            //might use null or "".
            return qn.getNamespaceURI().equals(uri == null ? "" : uri);
        }
        return false;
    }

    TypeUnmarshaller determineTypeUnmarshaller(TypeUnmarshaller base)
        throws XmlException
    {
        final QName xsi_type = getXsiType();

        if (xsi_type != null) {
            TypeUnmarshaller typed_um = getTypeUnmarshaller(xsi_type);
            if (typed_um != null)
                return typed_um;
            //reaching here means some problem with extracting the
            //marshaller for the xsi type, so just use the expected one
        }

        if (hasXsiNil())
            return NullUnmarshaller.getInstance();

        return base;
    }

}
