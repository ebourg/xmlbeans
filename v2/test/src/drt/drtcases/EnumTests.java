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

package drtcases;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.xmlbeans.XmlObject;
import org.openuri.enumtest.StatusreportDocument;
import org.openuri.enumtest.SalesreportDocument;
import org.openuri.enumtest.SalesreportDocument.Salesreport.Unit;
import org.openuri.enumtest.Quantity;
import org.openuri.enumtest.Data;

public class EnumTests extends TestCase
{
    public EnumTests(String name) { super(name); }
    public static Test suite() { return new TestSuite(EnumTests.class); }

    public static void testReport1() throws Exception
    {
        StatusreportDocument doc = (StatusreportDocument)
                    XmlObject.Factory.parse(TestEnv.xbeanCase("enumtest/report1.xml"));

        Quantity.Enum[] contents = new Quantity.Enum[]
        {
            Quantity.ALL,
            Quantity.ALL,
            Quantity.FEW,
            Quantity.ALL,
            Quantity.MOST,
            Quantity.NONE,
            Quantity.NONE,
            Quantity.NONE,
        };
        Data[] data = doc.getStatusreport().getStatusArray();
        int t = 0;
        for (int i = 0; i < data.length; i++)
        {
            Assert.assertEquals(contents[t++], data[i].enumValue());
            // System.out.println("Target: " + data[i].getTarget() + ", value: " + data[i].enumValue());
            Assert.assertEquals(contents[t++], data[i].getTarget());
        }
    }

    public static void testReport2() throws Exception
    {
        StatusreportDocument doc = StatusreportDocument.Factory.newInstance();
        StatusreportDocument.Statusreport report = doc.addNewStatusreport();

        Data d = report.addNewStatus();
        d.set(Quantity.ALL);
        d.setTarget(Quantity.ALL);

        d = report.addNewStatus();
        d.set(Quantity.FEW);
        d.setTarget(Quantity.ALL);

        d = report.addNewStatus();
        d.set(Quantity.MOST);
        d.setTarget(Quantity.NONE);

        d = report.addNewStatus();
        d.set(Quantity.NONE);
        d.setTarget(Quantity.NONE);

        Quantity.Enum[] contents = new Quantity.Enum[]
        {
            Quantity.ALL,
            Quantity.ALL,
            Quantity.FEW,
            Quantity.ALL,
            Quantity.MOST,
            Quantity.NONE,
            Quantity.NONE,
            Quantity.NONE,
        };
        Data[] data = doc.getStatusreport().getStatusArray();
        int t = 0;
        for (int i = 0; i < data.length; i++)
        {
            Assert.assertEquals(contents[t++], data[i].enumValue());
            // System.out.println("Target: " + data[i].getTarget() + ", value: " + data[i].enumValue());
            Assert.assertEquals(contents[t++], data[i].getTarget());
        }
    }

    public static void testReport3() throws Exception
    {
        SalesreportDocument doc = SalesreportDocument.Factory.newInstance();
        SalesreportDocument.Salesreport report = doc.addNewSalesreport();

        report.addUnit(Unit.ONE);
        report.addUnit(Unit.TWO);
        report.addUnit(Unit.NINETY_NINE);
        report.addUnit(Unit.ONE_HUNDRED);

        Unit.Enum[] contents = new Unit.Enum[]
        {
            Unit.ONE,
            Unit.TWO,
            Unit.NINETY_NINE,
            Unit.ONE_HUNDRED,
        };

        Unit[] xunits = report.xgetUnitArray();
        for (int i = 0; i < xunits.length; i++)
        {
            Assert.assertEquals(contents[i], xunits[i].enumValue());
        }

        Unit.Enum[] units = report.getUnitArray();
        for (int i = 0; i < units.length; i++)
        {
            Assert.assertEquals(contents[i], units[i]);
        }
    }


}