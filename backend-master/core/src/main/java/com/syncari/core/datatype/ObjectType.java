package com.syncari.core.datatype;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
@Slf4j
public class ObjectType extends AbstractDataType<Object> {
    public static final ObjectType VALUE = new ObjectType();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "object";
    }

    @Override
    public Class<Object> getJavaType() {
        return Object.class;
    }


    @Override
    public boolean canConvert(Datatype other) {
        return true;
    }

    @Override
    protected Map<Class<?>, Function<Object, Object>> getConverters() {
        return Map.of();
    }

    public Object convertFromJsonString(String json) {
        Object map = convertFromJsonString(json, Map.class);
        if (map == null) {
            return convertFromJsonString(json, List.class);
        }
        return map;
    }

    private Object convertFromJsonString(String json, Class expected) {
        try {
            return objectMapper.readValue(json, expected);
        } catch (IOException e) {
            log.error("Error converting to {} {}, failed with", expected.getName(), json, e.toString());
            return null;
        }
    }
}
