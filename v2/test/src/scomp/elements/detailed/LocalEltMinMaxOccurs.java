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
package scomp.elements.detailed;

import scomp.common.BaseCase;
import xbean.scomp.element.localEltMinMaxOccurs.MinMaxOccursDocDocument;

/**
 * @owner: ykadiysk
 * Date: Jul 14, 2004
 * Time: 4:52:07 PM
 */
public class LocalEltMinMaxOccurs extends BaseCase {

    public void testMinOccursZero() throws Throwable {
        MinMaxOccursDocDocument testDoc = MinMaxOccursDocDocument
                .Factory.parse("<MinMaxOccursDoc" +
                " xmlns=\"http://xbean/scomp/element/LocalEltMinMaxOccurs\">" +
                "<minOccursOne>1</minOccursOne>" +
                "<maxOccursOne>1</maxOccursOne>" +
                "<twoToFour>1</twoToFour>" +
                "<twoToFour>1</twoToFour>" +
                "</MinMaxOccursDoc>");
        try {
            assertTrue(testDoc.validate());
        }
        catch (Throwable t) {
                 showErrors();
            throw t;
        }
    }


    public void testMinGTMaxOccurs() {
        //compile time error raised correctly. Same for neg values
    }

    // twoToFour occurs only once
    public void testInstanceLTMinOccurs() throws Exception {
        MinMaxOccursDocDocument testDoc = MinMaxOccursDocDocument
                .Factory.parse("<MinMaxOccursDoc" +
                " xmlns=\"http://xbean/scomp/element/LocalEltMinMaxOccurs\">" +
                "<minOccursOne>1</minOccursOne>" +
                "<maxOccursOne>1</maxOccursOne>" +
                "<twoToFour>1</twoToFour>" +
                "</MinMaxOccursDoc>");
        assertTrue(!testDoc.validate(validateOptions));
        assertEquals(1,errorList.size());
        showErrors();
    }

    // maxOccursOne occurs 2ce
    public void testInstanceGTMaxOccurs() throws Exception {
        MinMaxOccursDocDocument testDoc = MinMaxOccursDocDocument
                .Factory.parse("<MinMaxOccursDoc" +
                " xmlns=\"http://xbean/scomp/element/LocalEltMinMaxOccurs\">" +
                "<minOccursOne>1</minOccursOne>" +
                "<maxOccursOne>1</maxOccursOne>" +
                "<maxOccursOne>1</maxOccursOne>" +
                "<twoToFour>1</twoToFour>" +
                "<twoToFour>1</twoToFour>" +
                "</MinMaxOccursDoc>");
        assertEquals(0,errorList.size());
          assertTrue(!testDoc.validate(validateOptions));
          assertEquals(1,errorList.size());
         fail("Error is incorrect: the dev infers the cause... incorrectly");
                showErrors();
    }

}