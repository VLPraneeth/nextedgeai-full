package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@EqualsAndHashCode
public class StringType extends AbstractDataType<String> {
    public static final StringType VALUE = new StringType();
    public static final String NAME = "string";
    public static final Map<Class<?>, Function<Object, String>> CONVERTERS = Map.of(
            List.class, value -> String.join(",",((List<Object>)value).stream().map(l->l==null?"":l.toString()).collect(Collectors.toList())),
            Object.class, StringType::objectConverter
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEmpty(Object value) {
        return value == null || (value instanceof String && ((String) value).isEmpty());
    }

    @Override
    public Class<String> getJavaType() {
        return String.class;
    }

    @Override
    protected Map<Class<?>, Function<Object, String>> getConverters() {
        return CONVERTERS;
    }

    @Override
    public boolean canConvert(Datatype other) {
        return true;
    }

    private static String objectConverter(Object obj) {

        if (obj instanceof Double) {
            DecimalFormat df = new DecimalFormat("#");
            df.setMaximumFractionDigits(4); // Set some precision
            return df.format(obj);
        }
        return obj.toString();
    }

}
