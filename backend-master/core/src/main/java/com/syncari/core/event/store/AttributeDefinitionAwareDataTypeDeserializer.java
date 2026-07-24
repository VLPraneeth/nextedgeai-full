package com.syncari.core.event.store;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.repositories.customer.AttributeDefinitionCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

@Slf4j
@Component
public class AttributeDefinitionAwareDataTypeDeserializer extends JsonDeserializer<Datatype> {

    @Autowired
    private AttributeDefinitionCache attributeDefinitionCache;

    // Setter for testing purposes
    public void setAttributeDefinitionCache(AttributeDefinitionCache attributeDefinitionCache) {
        this.attributeDefinitionCache = attributeDefinitionCache;
    }

    @Override
    public Datatype deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isTextual()) {
            String dataType = node.asText();
            return DatatypeFactory.getDatatype(dataType);
        }

        if (node.has("_class")){
            String className = node.get("_class").asText();
            try {
                return (Datatype) Class.forName(className).getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException |
                    NoSuchMethodException | InvocationTargetException e) {
                log.error("Error while getting dataType {}", ExceptionUtils.getStackTrace(e));
            }
        }

        // Try to get the name from the JSON first
        if (node.has("name")) {
            String typeName = node.get("name").asText();
            try {
                return DatatypeFactory.getDatatype(typeName);
            } catch (Exception e) {
                log.debug("Could not create datatype from name: {}", typeName);
            }
        }
        // Last resort: return string datatype as default
        log.warn("Could not determine datatype from JSON node: {}, defaulting to string", node);
        return DatatypeFactory.getDatatype("string");
    }
}