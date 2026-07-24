package com.syncari.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EntityDataTest {

    @Test
    public void caseInsensitiveGets() {
        EntityData entityData = new EntityData();
        entityData.addValue("name", "n1");
        entityData.addValue("Email", "e1");
        assertEquals("n1", entityData.getValue("name"));
        assertEquals("n1", entityData.getValue("Name"));
        assertEquals("n1", entityData.getValue("nAme"));
        assertEquals("e1", entityData.getValue("Email"));
        assertEquals("e1", entityData.getValue("email"));
        assertEquals("e1", entityData.getValue("eMail"));

    }

    @Test
    public void lowerCaseKeysNotRegistered() {
        EntityData entityData = new EntityData();
        entityData.addValue("name", "n1");
        entityData.addValue("email", "e1");
        entityData.addValue("fname", "f1");
        assertTrue(entityData.getCaseInsensitiveKeys().isEmpty());
    }


    @Test
    public void withValues() {
        EntityData entityData = new EntityData();
        entityData.addValue("name", "n1");
        entityData.addValue("Email", "e1");
        final EntityData entityData1 = entityData.withValues(new HashMap<>(Map.of("k2", "v2")));
        assertEquals("v2", entityData1.getValue("K2"));
    }

    @Test
    public void changeTest() throws JsonProcessingException {
        EntityData entityData = new EntityData();
        List<Map<String, Object>> in = List.of(Map.of("key1", "v1"));
        ObjectMapper mapper = new ObjectMapper();
        entityData.addValue("name", in);
        final String s = mapper.writeValueAsString(entityData);
        EntityData read = mapper.readValue(s, EntityData.class);
        assertFalse(entityData.hasChanges("name", read.getValue("name")));
    }
}