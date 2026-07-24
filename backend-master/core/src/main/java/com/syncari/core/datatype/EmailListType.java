package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;

import java.util.*;
import java.util.function.Function;

@EqualsAndHashCode
public class EmailListType extends AbstractDataType<List<String>> {
    public static final EmailListType VALUE = new EmailListType();
    public static final String NAME ="emailList";
    public static final Class<List<String>> JAVA_TYPE = (Class<List<String>>) Collections.<String>emptyList().getClass();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<List< String>> getJavaType() {
        return JAVA_TYPE;

    }

    private static List<String> convert(String value) {
        return value==null? null: new ArrayList<>(Arrays.asList(value.split(",")));
    }

    @Override
    public boolean canConvert(Datatype other) {
        return true;
    }

    @Override
    protected Map<Class<?>, Function<Object, List<String>>> getConverters() {
        return Map.of(
                String.class, value -> convert(value.toString())
        );
    }
}
