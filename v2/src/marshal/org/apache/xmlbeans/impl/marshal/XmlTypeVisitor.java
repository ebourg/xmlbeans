/*
* The Apache Software License, Version 1.1
*
*
* Copyright (c) 2003 The Apache Software Foundation.  All rights
* reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
* 1. Redistributions of source code must retain the above copyright
*    notice, this list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright
*    notice, this list of conditions and the following disclaimer in
*    the documentation and/or other materials provided with the
*    distribution.
*
* 3. The end-user documentation included with the redistribution,
*    if any, must include the following acknowledgment:
*       "This product includes software developed by the
*        Apache Software Foundation (http://www.apache.org/)."
*    Alternately, this acknowledgment may appear in the software itself,
*    if and wherever such third-party acknowledgments normally appear.
*
* 4. The names "Apache" and "Apache Software Foundation" must
*    not be used to endorse or promote products derived from this
*    software without prior written permission. For written
*    permission, please contact apache@apache.org.
*
* 5. Products derived from this software may not be called "Apache
*    XMLBeans", nor may "Apache" appear in their name, without prior
*    written permission of the Apache Software Foundation.
*
* THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
* OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
* ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
* USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
* OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
* SUCH DAMAGE.
* ====================================================================
*
* This software consists of voluntary contributions made by many
* individuals on behalf of the Apache Software Foundation and was
* originally based on software copyright (c) 2000-2003 BEA Systems
* Inc., <http://www.bea.com/>. For more information on the Apache Software
* Foundation, please see <http://www.apache.org/>.
*/

package org.apache.xmlbeans.impl.marshal;

import org.apache.xmlbeans.XmlException;

import javax.xml.namespace.QName;

abstract class XmlTypeVisitor
{
    private final Object parentObject;
    private final RuntimeBindingProperty bindingProperty;
    protected final MarshalResult marshalResult;

    XmlTypeVisitor(Object parentObject,
                   RuntimeBindingProperty property,
                   MarshalResult result)
    {
        this.parentObject = parentObject;
        this.bindingProperty = property;
        marshalResult = result;
    }


    protected Object getParentObject()
    {
        return parentObject;
    }

    protected RuntimeBindingProperty getBindingProperty()
    {
        return bindingProperty;
    }


    static final int START = 1;
    static final int CONTENT = 2;
    static final int CHARS = 3;
    static final int END = 4;

    protected abstract int getState();

    /**
     *
     * @return  next state
     */
    protected abstract int advance()
        throws XmlException;

    public abstract XmlTypeVisitor getCurrentChild()
        throws XmlException;


    protected abstract QName getName();

    //guaranteed to be called before any getAttribute* or getNamespace* method
    protected void initAttributes()
        throws XmlException
    {
    }

    protected abstract int getAttributeCount() 
        throws XmlException;

    protected abstract String getAttributeValue(int idx);

    protected abstract QName getAttributeName(int idx);

    protected abstract CharSequence getCharData();

    public String toString()
    {
        return this.getClass().getName() +
            " prop=" + bindingProperty.getName() +
            " type=" + bindingProperty.getType().getName();
    }

    protected QName fillPrefix(final QName pname)
    {
        final String uri = pname.getNamespaceURI();

        assert uri != null;  //QName's should use "" for no namespace

        if (uri.length() == 0) {
            return new QName(pname.getLocalPart());
        } else {
            String prefix = marshalResult.ensurePrefix(uri);
            return new QName(uri, pname.getLocalPart(), prefix);
        }
    }


}