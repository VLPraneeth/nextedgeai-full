package com.syncari.core.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.TestConfig;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.pipeline.GraphContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@DirtiesContext
public class JsonFunctionsTest {
    @Autowired
    JsonFunctions functions;

    @Test
    public void parseJsonToArray() throws Exception {
        assertNull(functions.parseJsonToArray(nullList(), createCall(), getContext(null)));
        String json = "[{\"name\":\"sfdcid\", \"value\":\"003PX000007wWl4YAE\"}]";
        assertEquals(1, ((List)functions.parseJsonToArray(List.of(json), createCall("input", json), getContext("value"))).size());
    }

    @Test
    public void convertToJsonString() throws Exception {
        final GraphContext context = getContext("value");

        context.put("myvalue", Map.of("name", "sfdcid", "value", "003PX000007wWl4YAE"));
        Map result = (Map) functions.convertToJSONString(List.of(), createCall("input", "{{myvalue}}"), context);
        assertTrue((Boolean) result.get("success"));
        assertEquals(context.get("myvalue"), new ObjectMapper().reader().readValue(result.get("jsonString").toString(), Map.class));

        context.put("myvalue", "hello");
        result = (Map) functions.convertToJSONString(List.of(), createCall("input", "{{myvalue}}"), context);
        assertTrue((Boolean) result.get("success"));
        assertEquals(Map.of("value", "hello"), new ObjectMapper().reader().readValue(result.get("jsonString").toString(), Map.class));

    }

    @Test
    public void parseJsonToObject() throws Exception {
        assertNull(functions.parseJsonToArray(nullList(), createCall(), getContext(null)));
        String json = "{\"name\":\"sfdcid\", \"value\":\"003PX000007wWl4YAE\"}";
        assertEquals(2, ((Map)functions.parseJsonToObject(List.of(json), createCall("input", json), getContext("value"))).size());
        String json2 = "{\n" +
                "    \"itemEffCategory\": {\n" +
                "        \"CommerceCloudCategories\": [{}, {}]\n" +
                "    }\n" +
                "}";
        final Map map = (Map) functions.parseJsonToObject(List.of(json2), createCall("input", json2), getContext("value"));
        final Map itemEffCategory = (Map) map.get("itemEffCategory");
        assertEquals(2, List.class.cast(itemEffCategory.get("CommerceCloudCategories")).size());
    }

    @Test
    public void parseJsonToListWrapsSingleValue() throws Exception {
        assertNull(functions.parseJsonToArray(nullList(), createCall(), getContext(null)));
        String json = "{\"name\":\"sfdcid\", \"value\":\"003PX000007wWl4YAE\"}";
        assertEquals(1, ((List) functions.parseJsonToArray(List.of(json), createCall("input", json), getContext("value"))).size());
    }

	private List<Object> nullList() {
    	List<Object> nullList= new ArrayList<>();
    	nullList.add(null);
		return nullList;
	}

    private GraphContext getContext(String test) {
        return new GraphContext().set("param", test).setCurrentNode(new MappingNode().setName("My Custom Node"));
    }

    private FunctionCall createCall(Object... keyValues) {
        Map<String, Object> config = new HashMap<>();
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                config.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return new FunctionCall().setConfig(config).setParams(List.of(ParameterValue.string("param", "input")));
    }

}
