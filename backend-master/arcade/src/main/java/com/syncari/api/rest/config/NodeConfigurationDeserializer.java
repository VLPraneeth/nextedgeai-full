package com.syncari.api.rest.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syncari.core.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class NodeConfigurationDeserializer extends JsonDeserializer<NodeConfiguration> {

    private static Map<String, Class<? extends NodeConfiguration>> nodeConfigMap = new HashMap<>();

    /**
     * Add an entry here for every new implementation of
     */
    static {
        nodeConfigMap.put(CoreAttributeNodeConfig.class.getSimpleName(), CoreAttributeNodeConfig.class);
        nodeConfigMap.put(CoreEntityNodeConfig.class.getSimpleName(), CoreEntityNodeConfig.class);
        nodeConfigMap.put(SimpleFunctionNodeConfig.class.getSimpleName(), SimpleFunctionNodeConfig.class);
        nodeConfigMap.put(AttributeSinkNodeConfig.class.getSimpleName(), AttributeSinkNodeConfig.class);
        nodeConfigMap.put(AttributeSourceNodeConfig.class.getSimpleName(), AttributeSourceNodeConfig.class);
        nodeConfigMap.put(EntitySourceNodeConfig.class.getSimpleName(), EntitySourceNodeConfig.class);
        nodeConfigMap.put(EntitySinkNodeConfig.class.getSimpleName(), EntitySinkNodeConfig.class);
        nodeConfigMap.put(SendEmailActionConfig.class.getSimpleName(), SendEmailActionConfig.class);
    }

    @Override
    public Class<?> handledType() {
        return NodeConfiguration.class;
    }

    @Override
    public NodeConfiguration deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectMapper mapper = (ObjectMapper) jp.getCodec();
        ObjectNode root = mapper.readTree(jp);

        Class<? extends NodeConfiguration> configType = nodeConfigMap.get(root.get("configType").asText());
        if(configType == null) {
            log.warn("Could not find mapping for class {0}. Add an entry in NodeConfigurationDeserializer.java",root.get("configType").asText());
            return null;
        }
        return mapper.readValue(root.toString(), configType);

    }
}
