package com.syncari.connector.intacct;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ResultConverterTest {

    private static final String WELL_FORMED_ERROR_XML = 
        "<response>" +
            "<control><status>success</status></control>" +
            "<operation>" +
                "<errormessage>" +
                    "<error>" +
                        "<errorno>XL03000006</errorno>" +
                        "<description>Authentication Failed</description>" +
                        "<correction>Please check your credentials</correction>" +
                    "</error>" +
                "</errormessage>" +
            "</operation>" +
        "</response>";

    @Test
    public void testXMLParsingWithErrorResponse() {
        // Test parsing of error responses from Intacct
        try {
            IntacctResponse response = (IntacctResponse) IntacctClient.getResponseMarshaller().fromXML(WELL_FORMED_ERROR_XML);
            // Error responses should parse successfully
            assertNotNull(response);
            assertNotNull(response.getOperation());
            assertTrue("Response should have errors", response.hasErrors());
            assertNotNull("Error messages should be present", response.getErrorMessages());
            assertTrue("Should contain authentication error", 
                    response.getErrorMessage().contains("Authentication Failed"));
        } catch (Exception e) {
            fail("Error XML parsing should work fine: " + e.getMessage());
        }
    }

    @Test
    public void testXMLParsingWithEmptyDataElement() {
        // Test reproducing the IndexOutOfBoundsException when parsing XML with empty self-closing data element
        // This reproduces the production issue where Intacct returns empty results
        String xmlWithEmptyData = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<response>\n" +
                "    <control>\n" +
                "        <status>success</status>\n" +
                "        <senderid>SyncariMPP</senderid>\n" +
                "        <controlid>acc47e14-8d85-45f7-9082-e82262456ec4</controlid>\n" +
                "        <uniqueid>false</uniqueid>\n" +
                "        <dtdversion>3.0</dtdversion>\n" +
                "    </control>\n" +
                "    <operation>\n" +
                "        <authentication>\n" +
                "            <status>success</status>\n" +
                "            <userid>rok</userid>\n" +
                "            <companyid>totaralearning-imp</companyid>\n" +
                "            <locationid></locationid>\n" +
                "            <sessiontimestamp>2025-08-21T04:33:19+00:00</sessiontimestamp>\n" +
                "            <sessiontimeout>2025-08-21T16:33:19+00:00</sessiontimeout>\n" +
                "        </authentication>\n" +
                "        <result>\n" +
                "            <status>success</status>\n" +
                "            <function>query</function>\n" +
                "            <controlid>30c8a309-c4b2-4ee0-b54a-5d5da89763cf</controlid>\n" +
                "            <data listtype=\"SODOCUMENTPARAMS\" totalcount=\"0\" offset=\"0\" count=\"0\" numremaining=\"0\"/>\n" +
                "        </result>\n" +
                "    </operation>\n" +
                "</response>";

        try {
            IntacctResponse response = (IntacctResponse) IntacctClient.getResponseMarshaller().fromXML(xmlWithEmptyData);
            // If we reach here, the parsing succeeded - this means the bug is fixed
            assertNotNull(response);
            assertNotNull(response.getOperation());
            assertNotNull(response.getOperation().getResults());
        } catch (Exception e) {
            // Verify this is the expected IndexOutOfBoundsException from the production issue
            if (e.getCause() instanceof IndexOutOfBoundsException) {
                IndexOutOfBoundsException ioobe = (IndexOutOfBoundsException) e.getCause();
                assertTrue("Expected IndexOutOfBoundsException with message about END_TAG attributes", 
                        ioobe.getMessage().contains("only START_TAG can have attributes END_TAG"));
                // This test currently fails, which reproduces the production issue
                fail("XML parsing failed with IndexOutOfBoundsException when trying to access attributes on self-closing data element: " + ioobe.getMessage());
            } else {
                throw e;
            }
        }
    }

    @Test
    public void testXMLParsingWithDataElementWithChildren() {
        // Test XML parsing when data element has children (should work fine)
        String xmlWithDataChildren = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<response>\n" +
                "    <control>\n" +
                "        <status>success</status>\n" +
                "        <senderid>SyncariMPP</senderid>\n" +
                "        <controlid>acc47e14-8d85-45f7-9082-e82262456ec4</controlid>\n" +
                "        <uniqueid>false</uniqueid>\n" +
                "        <dtdversion>3.0</dtdversion>\n" +
                "    </control>\n" +
                "    <operation>\n" +
                "        <authentication>\n" +
                "            <status>success</status>\n" +
                "            <userid>rok</userid>\n" +
                "            <companyid>totaralearning-imp</companyid>\n" +
                "            <locationid></locationid>\n" +
                "            <sessiontimestamp>2025-08-21T04:33:19+00:00</sessiontimestamp>\n" +
                "            <sessiontimeout>2025-08-21T16:33:19+00:00</sessiontimeout>\n" +
                "        </authentication>\n" +
                "        <result>\n" +
                "            <status>success</status>\n" +
                "            <function>query</function>\n" +
                "            <controlid>30c8a309-c4b2-4ee0-b54a-5d5da89763cf</controlid>\n" +
                "            <data listtype=\"CUSTOMER\" totalcount=\"1\" offset=\"0\" count=\"1\" numremaining=\"0\">\n" +
                "                <CUSTOMER>\n" +
                "                    <RECORDNO>123</RECORDNO>\n" +
                "                    <CUSTOMERID>CUST001</CUSTOMERID>\n" +
                "                    <NAME>Test Customer</NAME>\n" +
                "                </CUSTOMER>\n" +
                "            </data>\n" +
                "        </result>\n" +
                "    </operation>\n" +
                "</response>";

        try {
            IntacctResponse response = (IntacctResponse) IntacctClient.getResponseMarshaller().fromXML(xmlWithDataChildren);
            // This should parse successfully
            assertNotNull(response);
            assertNotNull(response.getOperation());
            assertNotNull(response.getOperation().getResults());
        } catch (Exception e) {
            fail("XML parsing should work fine when data element has children: " + e.getMessage());
        }
    }

    @Test
    public void testXMLParsingWithSelfClosingDataElementAndErrorMessage() {
        // Test reproducing the specific IndexOutOfBoundsException from production
        // This reproduces the error with function='query' and a self-closing data element with errormessage
        String xmlWithErrorAndSelfClosingData = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<response>\n" +
                "    <control>\n" +
                "        <status>success</status>\n" +
                "        <senderid>SyncariMPP</senderid>\n" +
                "        <controlid>acc47e14-8d85-45f7-9082-e82262456ec4</controlid>\n" +
                "        <uniqueid>false</uniqueid>\n" +
                "        <dtdversion>3.0</dtdversion>\n" +
                "    </control>\n" +
                "    <operation>\n" +
                "        <result>\n" +
                "            <status>failure</status>\n" +
                "            <function>query</function>\n" +
                "            <controlid>30c8a309-c4b2-4ee0-b54a-5d5da89763cf</controlid>\n" +
                "            <errormessage>\n" +
                "                <error>\n" +
                "                    <errorno>XL03000009</errorno>\n" +
                "                    <description>There was an error processing the function</description>\n" +
                "                    <description2>Object definition SODOCUMENTPARAMS not found</description2>\n" +
                "                    <correction></correction>\n" +
                "                </error>\n" +
                "            </errormessage>\n" +
                "            <data listtype=\"SODOCUMENTPARAMS\" totalcount=\"0\" offset=\"0\" count=\"0\" numremaining=\"0\"/>\n" +
                "        </result>\n" +
                "    </operation>\n" +
                "</response>";

        try {
            IntacctResponse response = (IntacctResponse) IntacctClient.getResponseMarshaller().fromXML(xmlWithErrorAndSelfClosingData);
            // If we reach here, the parsing succeeded - verify the error is captured
            assertNotNull(response);
            assertNotNull(response.getOperation());
            assertTrue("Response should have errors", response.hasErrors());
        } catch (Exception e) {
            // Verify this is the expected IndexOutOfBoundsException from the production issue
            if (e.getCause() instanceof IndexOutOfBoundsException) {
                IndexOutOfBoundsException ioobe = (IndexOutOfBoundsException) e.getCause();
                assertTrue("Expected IndexOutOfBoundsException with message about END_TAG attributes", 
                        ioobe.getMessage().contains("only START_TAG can have attributes END_TAG"));
                // This test currently fails, reproducing the production issue with function=query and errormessage
                fail("XML parsing failed with IndexOutOfBoundsException when processing self-closing data element with errormessage: " + ioobe.getMessage());
            } else {
                throw e;
            }
        }
    }
}