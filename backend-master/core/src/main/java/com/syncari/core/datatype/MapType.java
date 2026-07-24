package com.syncari.core.datatype;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@EqualsAndHashCode
@Slf4j
public class MapType extends AbstractDataType<Map> {

    public static final MapType VALUE= new MapType();
    public static final String NAME="complex";
    public static Set<Datatype> COMPATIBLE_TYPES = Set.of(StringType.VALUE, MapType.VALUE);
    public static final Map<Class<?>, Function<Object, Map>> CONVERTERS = Map.of(
            Map.class, value -> (Map) value,
            String.class, value -> convert(value.toString()) // JSON String
    );

    private static Map convert(String value){
        try{
            return new ObjectMapper().readValue(value, Map.class);
        } catch (Exception e){
            log.error(String.format("Unable to convert JSON String %s to Map", value), e);
        }
        return null;
    }

    @Override
    protected Map<Class<?>, Function<Object, Map>> getConverters() {
        return CONVERTERS;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<Map> getJavaType() {
        return Map.class;
    }

    @Override
    protected Map nullEquivalent() {
        return Collections.emptyMap();
    }

    @Override
    public boolean canConvert(Datatype other) {
        return COMPATIBLE_TYPES.contains(other);
    }
}
