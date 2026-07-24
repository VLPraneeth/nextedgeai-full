package com.syncari.core.event.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.TestConfig;
import com.syncari.core.datatype.*;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.repositories.customer.AttributeDefinitionCache;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@Slf4j
public class AttributeDefinitionAwareDataTypeDeserializerTest extends AbstractSyncariTest {

    @Mock
    private AttributeDefinitionCache attributeDefinitionCache;

    @InjectMocks
    private AttributeDefinitionAwareDataTypeDeserializer deserializer;

    private ObjectMapper mapper;

    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        
        // Create mapper with custom deserializer
        mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Datatype.class, deserializer);
        mapper.registerModule(module);
    }

    @Test
    public void testDeserializeWithTextualNode() throws IOException {
        String json = "\"string\"";
        JsonNode node = mapper.readTree(json);
        
        Datatype result = deserializer.deserialize(mapper.getFactory().createParser(json), null);
        
        assertTrue(result instanceof StringType);
        assertEquals("string", result.getName());
    }

    @Test
    public void testDeserializeWithClassNode() throws IOException {
        String json = "{\"_class\":\"com.syncari.core.datatype.StringType\"}";
        
        Datatype result = mapper.readValue(json, Datatype.class);
        
        assertTrue(result instanceof StringType);
        assertEquals("string", result.getName());
    }

    @Test
    public void testDeserializeWithNameField() throws IOException {
        String json = "{\"name\":\"integer\"}";
        
        Datatype result = mapper.readValue(json, Datatype.class);
        
        assertTrue(result instanceof IntegerType);
        assertEquals("integer", result.getName());
    }

    @Test
    public void testDeserializeWithInvalidClassName() throws IOException {
        String json = "{\"_class\":\"com.invalid.ClassName\"}";
        
        Datatype result = mapper.readValue(json, Datatype.class);
        
        // Should fallback to string datatype
        assertTrue(result instanceof StringType);
        assertEquals("string", result.getName());
    }

    @Test
    public void testDeserializeWithInvalidName() throws IOException {
        String json = "{\"name\":\"invalidDataType\"}";
        
        Datatype result = mapper.readValue(json, Datatype.class);
        
        // Should fallback to string datatype
        assertTrue(result instanceof StringType);
        assertEquals("string", result.getName());
    }

    @Test
    public void testDeserializeWithNonExistentAttributeId() throws IOException {
        when(attributeDefinitionCache.findById(anyString())).thenReturn(null);
        
        String json = "{\"attributeId\":\"nonExistent\"}";
        
        Datatype result = mapper.readValue(json, Datatype.class);
        
        // Should fallback to string datatype
        assertTrue(result instanceof StringType);
        assertEquals("string", result.getName());
    }

    @Test
    public void testDeserializeWithEmptyObject() throws IOException {
        String json = "{}";
        
        Datatype result = mapper.readValue(json, Datatype.class);
        
        // Should fallback to string datatype
        assertTrue(result instanceof StringType);
        assertEquals("string", result.getName());
    }

    @Test
    public void testDeserializeComplexDataTypes() throws IOException {
        // Test various data types
        String[] dataTypes = {"date", "datetime", "double", "id", "list", "picklist", "reference", "textarea", "timestamp", "url", "externalId"};
        Class<?>[] expectedClasses = {DateType.class, DatetimeType.class, DoubleType.class, IdType.class, 
                                    ListType.class, PicklistType.class, ReferenceType.class, TextareaType.class, 
                                    TimestampType.class, UrlType.class, ExternalIdType.class};
        
        for (int i = 0; i < dataTypes.length; i++) {
            String json = "{\"name\":\"" + dataTypes[i] + "\"}";
            Datatype result = mapper.readValue(json, Datatype.class);
            
            assertTrue("Expected " + expectedClasses[i].getSimpleName() + " for " + dataTypes[i], 
                      expectedClasses[i].isInstance(result));
            assertEquals(dataTypes[i], result.getName());
        }
    }

}