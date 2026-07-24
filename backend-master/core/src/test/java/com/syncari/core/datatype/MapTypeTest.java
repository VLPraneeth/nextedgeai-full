package com.syncari.core.datatype;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.minidev.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MapTypeTest {

    @Test
    public void canConvertTypes(){
        // supported conversion
        assertTrue(MapType.VALUE.canConvert(MapType.VALUE));
        assertTrue(MapType.VALUE.canConvert(StringType.VALUE));

        // unsupported conversion
        assertFalse(MapType.VALUE.canConvert(ObjectType.VALUE));
    }

    @Test
    public void convertMap(){
        Map map = new HashMap();
        map.put("string", "stringValue");
        map.put("complex", Map.of("key1", "value1", "key2", "value2"));
        map.put("list", List.of("listValue1", "listValue2", "listValue3"));
        map.put("int", 100);
        map.put("bool", true);

        Map converted = new MapType().convert(map);
        assertNotNull(converted);
        assertEquals("stringValue", converted.get("string"));
        assertEquals(100, converted.get("int"));
        assertEquals(true, converted.get("bool"));
        assertEquals(Map.of("key1", "value1", "key2", "value2"), converted.get("complex"));
        assertEquals(List.of("listValue1", "listValue2", "listValue3"), converted.get("list"));
    }

    @Test
    public void convertJsonString() throws JsonProcessingException {
        Map map = new HashMap();
        map.put("string", "stringValue");
        map.put("complex", Map.of("key1", "value1", "key2", "value2"));
        map.put("list", List.of("listValue1", "listValue2", "listValue3"));
        map.put("int", 100);
        map.put("bool", true);

        String jsonString = new JSONObject(map).toJSONString();

        Map converted = new MapType().convert(jsonString);
        assertNotNull(converted);
        assertEquals("stringValue", converted.get("string"));
        assertEquals(100, converted.get("int"));
        assertEquals(true, converted.get("bool"));
        assertEquals(Map.of("key1", "value1", "key2", "value2"), converted.get("complex"));
        assertEquals(List.of("listValue1", "listValue2", "listValue3"), converted.get("list"));
    }
}
