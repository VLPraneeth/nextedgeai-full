package com.syncari.core.datatype;

import com.syncari.core.exceptions.SyncariValidationException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractDataType<T> implements Datatype<T> {
    protected abstract Map<Class<?>, Function<Object, T>> getConverters();

    protected T nullEquivalent() {
        return null;
    }

    public boolean isEmpty(Object value) {
        return value == null || (value instanceof String && ((String) value).isEmpty());
    }

    public T convert(Object value) {
        if (isEmpty(value)) return nullEquivalent();
        if (getJavaType().isAssignableFrom(value.getClass())) return getJavaType().cast(value);
        for (Map.Entry<Class<?>, Function<Object, T>> converter : getConverters().entrySet()) {
            if (converter.getKey().isAssignableFrom(value.getClass())) {
                return converter.getValue().apply(value);
            }
            if (List.class.isAssignableFrom(value.getClass())) {
                List valueList = (List) value;
                if(valueList.isEmpty()) return (T) List.of();
                if(!valueList.isEmpty() && converter.getKey().isAssignableFrom(valueList.get(0).getClass())) {
                    return (T) valueList.stream().map(val -> converter.getValue().apply(val)).collect(Collectors.toList());
                }
            }
        }
        log.debug("No converters found for value {} to {}:{}. Forcing string based conversion", value, getJavaType(), getClass());
        Function<Object, T> stringConverter = getConverters().get(String.class);
        if (stringConverter == null) {
            throw new SyncariValidationException("No converters found to convert value %s to type %s, and java type %s", value, getName(), getJavaType());
        }
        return stringConverter.apply(value.toString());
    }

    public Object convertMultiValuedInput(Object value, Boolean isLiteral) {
        Object converted;
        if (value != null && List.class.isAssignableFrom(value.getClass())) {
            converted = List.class.cast(value).stream()
                    .map(v -> convert(v))
                    .collect(Collectors.toList());
        } else {
            converted = value == null ? List.of() : asList(value, isLiteral);
        }
        return converted;
    }

    private List<Object> asList(Object value) {
        List<Object> convertedList = new ArrayList<>();
        try {
            JSONArray inputList = new JSONArray((String) value);
            if (inputList != null) {
                for (Object item : inputList) {
                    Object itemData = convert(item);
                    if (itemData == null)
                        return null;
                    convertedList.add(convert(item));
                }
            }
        } catch (Exception e){
            return null;
        }
        return convertedList;
    }

    private List<Object> asList(Object value, Boolean isLiteral) {
        List<Object> result = asList(value);
        if (!isLiteral && result == null) {
            Object converted = convert(value);
            if (converted == null) {
                return List.of();
            }
            return List.of(converted);
        }
        return result;
    }
}
