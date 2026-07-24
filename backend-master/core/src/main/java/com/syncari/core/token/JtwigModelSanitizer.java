package com.syncari.core.token;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.syncari.connector.EntityData;

import org.apache.commons.lang3.StringUtils;
import org.jtwig.value.convert.Converter;

public class JtwigModelSanitizer {
    public static JtwigModelSanitizer newModel(Map<String, Object> values) {
        JtwigModelSanitizer model = newModel();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            model.with(entry.getKey(), convertedValue(entry.getValue()));
        }
        return model;
    }

    /**
     * JTwig only understands bigdecimals. need to convert all numbers to BD
     * @param value
     * @return
     */
    protected static Object convertedValue(Object value) {
        if (value instanceof BigDecimal) return value;
        //Converting from double to string before converting to avoid precision issue. Refer BigDecimal(double val) javadoc
        if(value instanceof Double) {
			if ((double) value % 1 == 0) {
				return new BigDecimal(((Number) value).doubleValue());
			}
			return new BigDecimal(value.toString());
        	
        }
        if (value instanceof Number) return new BigDecimal(((Number) value).doubleValue());
        return value;
    }

    public static JtwigModelSanitizer newModel() {
        return new JtwigModelSanitizer();
    }

    private final Map<String, Object> values;

    public JtwigModelSanitizer() {
        this.values = new HashMap<>();
    }

    public JtwigModelSanitizer with(String name, Object value) {
        name = sanitizeToken(name);
        if (value instanceof Map<?, ?> && !"context".equalsIgnoreCase(name)) {
            values.put(name, JtwigModelSanitizer.newModel((Map<String, Object>) value).getValues());
        } else if (value instanceof EntityData) {
            EntityData ed = (EntityData) value;
            values.put(name, ed.withValues(JtwigModelSanitizer.newModel(ed.getValues()).getValues()));
        } else {
            values.put(name, value);
        }
        return this;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public static String sanitizeToken(String name) {
        if (StringUtils.isEmpty(name)) return name;
        name = name.replaceAll("[^a-zA-Z0-9{}_.\\[\\]]+", "_");
        Character c = name.charAt(0);
        if (!Character.isJavaIdentifierStart(c) && (c != '{')) {
            return "_" + name;
        }
        return name;
    }
}