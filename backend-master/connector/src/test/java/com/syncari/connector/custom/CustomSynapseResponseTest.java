package com.syncari.connector.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.SynapseInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CustomSynapseResponseTest {

    ObjectMapper mapper =  JsonMapper.builder().build();

    private Map<String, Object> getSynapseRequestMap(){
        Map<String, Object> synapseInfoMap = new HashMap<>();
        synapseInfoMap.put("name", "synapseName");
        synapseInfoMap.put("category", "CRM");
        synapseInfoMap.put("metadata", Map.ofEntries(Map.entry("displayName", "Test Synapse"), Map.entry("iconPath", "pathToIcon"), Map.entry("backgroundColor", "Red")));
        return synapseInfoMap;
    }

    @Test
    public void synapseInfoWithHelpSummary(){
        CustomSynapseResponse customSynapseResponse = new CustomSynapseResponse();
        Map<String, Object> reqMap = getSynapseRequestMap();
        Map<String, String> authFieldMap = Map.ofEntries(
                Map.entry("name", "endpoint"),
                Map.entry("dataType", "String"),
                Map.entry("label", "Endpoint URL"),
                Map.entry("helpSummary", "This is Help")
        );
        reqMap.put("configuredFields", List.of(authFieldMap));

        try{
            customSynapseResponse.setResponse(mapper.writeValueAsString(reqMap));
        } catch (JsonProcessingException jpe){
            Assert.fail("Exception occured");
        }
        customSynapseResponse.setType(RequestType.SYNAPSE_INFO);

        SynapseInfo synapseInfo = (SynapseInfo) customSynapseResponse.unpack();

        assertEquals("synapseName", synapseInfo.getName());
        assertEquals("CRM", synapseInfo.getCategory());

        assertNotNull(synapseInfo.getConfiguredFields());
        assertEquals(1, synapseInfo.getConfiguredFields().size());

        AuthField authField = synapseInfo.getConfiguredFields().get(0);
        assertEquals("endpoint", authField.getName());
        assertEquals("String", authField.getDataType());
        assertEquals("Endpoint URL", authField.getLabel());
        assertEquals("This is Help", authField.getHelpSummary());
        assertTrue(authField.toString().contains("helpSummary"));
        assertFalse(authField.toString().contains("description"));
    }

    @Test
    public void synapseInfoWithDescription(){
        CustomSynapseResponse customSynapseResponse = new CustomSynapseResponse();
        Map<String, Object> reqMap = getSynapseRequestMap();
        Map<String, String> authFieldMap = Map.ofEntries(
                Map.entry("name", "endpoint"),
                Map.entry("dataType", "String"),
                Map.entry("label", "Endpoint URL"),
                Map.entry("description", "This is Help")
        );
        reqMap.put("configuredFields", List.of(authFieldMap));

        try{
            customSynapseResponse.setResponse(mapper.writeValueAsString(reqMap));
        } catch (JsonProcessingException jpe){
            Assert.fail("Exception occured");
        }
        customSynapseResponse.setType(RequestType.SYNAPSE_INFO);

        SynapseInfo synapseInfo = (SynapseInfo) customSynapseResponse.unpack();

        assertEquals("synapseName", synapseInfo.getName());
        assertEquals("CRM", synapseInfo.getCategory());

        assertNotNull(synapseInfo.getConfiguredFields());
        assertEquals(1, synapseInfo.getConfiguredFields().size());

        AuthField authField = synapseInfo.getConfiguredFields().get(0);
        assertEquals("endpoint", authField.getName());
        assertEquals("String", authField.getDataType());
        assertEquals("Endpoint URL", authField.getLabel());
        assertEquals("This is Help", authField.getHelpSummary());
        assertTrue(authField.toString().contains("helpSummary"));
        assertFalse(authField.toString().contains("description"));
    }

    @Test
    public void synapseInfoWithHelpSummaryAndDescription(){
        CustomSynapseResponse customSynapseResponse = new CustomSynapseResponse();
        Map<String, Object> reqMap = getSynapseRequestMap();
        Map<String, String> authFieldMap = Map.ofEntries(
                Map.entry("name", "endpoint"),
                Map.entry("dataType", "String"),
                Map.entry("label", "Endpoint URL"),
                Map.entry("helpSummary", "This is Help"),
                Map.entry("description", "This is Description")
        );
        reqMap.put("configuredFields", List.of(authFieldMap));

        try{
            customSynapseResponse.setResponse(mapper.writeValueAsString(reqMap));
        } catch (JsonProcessingException jpe){
            Assert.fail("Exception occured");
        }
        customSynapseResponse.setType(RequestType.SYNAPSE_INFO);

        SynapseInfo synapseInfo = (SynapseInfo) customSynapseResponse.unpack();

        assertEquals("synapseName", synapseInfo.getName());
        assertEquals("CRM", synapseInfo.getCategory());

        assertNotNull(synapseInfo.getConfiguredFields());
        assertEquals(1, synapseInfo.getConfiguredFields().size());

        AuthField authField = synapseInfo.getConfiguredFields().get(0);
        assertEquals("endpoint", authField.getName());
        assertEquals("String", authField.getDataType());
        assertEquals("Endpoint URL", authField.getLabel());
        assertEquals("This is Help", authField.getHelpSummary());
        assertTrue(authField.toString().contains("helpSummary"));
        assertFalse(authField.toString().contains("description"));
    }

    @Test
    public void synapseInfoWithNothing(){
        CustomSynapseResponse customSynapseResponse = new CustomSynapseResponse();
        Map<String, Object> reqMap = getSynapseRequestMap();
        Map<String, String> authFieldMap = Map.ofEntries(
                Map.entry("name", "endpoint"),
                Map.entry("dataType", "String"),
                Map.entry("label", "Endpoint URL")
        );
        reqMap.put("configuredFields", List.of(authFieldMap));

        try{
            customSynapseResponse.setResponse(mapper.writeValueAsString(reqMap));
        } catch (JsonProcessingException jpe){
            Assert.fail("Exception occured");
        }
        customSynapseResponse.setType(RequestType.SYNAPSE_INFO);

        SynapseInfo synapseInfo = (SynapseInfo) customSynapseResponse.unpack();

        assertEquals("synapseName", synapseInfo.getName());
        assertEquals("CRM", synapseInfo.getCategory());

        assertNotNull(synapseInfo.getConfiguredFields());
        assertEquals(1, synapseInfo.getConfiguredFields().size());

        AuthField authField = synapseInfo.getConfiguredFields().get(0);
        assertEquals("endpoint", authField.getName());
        assertEquals("String", authField.getDataType());
        assertEquals("Endpoint URL", authField.getLabel());
        assertNull(authField.getHelpSummary());
        assertTrue(authField.toString().contains("helpSummary"));
        assertFalse(authField.toString().contains("description"));
    }
}
