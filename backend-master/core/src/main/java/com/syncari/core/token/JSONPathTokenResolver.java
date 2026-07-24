package com.syncari.core.token;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.syncari.connector.EntityData;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class JSONPathTokenResolver implements TokenResolver {
    private static final com.jayway.jsonpath.Configuration jsonPathConfig = Configuration.defaultConfiguration().jsonProvider(new GraphContextJsonJsonProvider());
    private static final ParseContext jsonParseContext = JsonPath.using(jsonPathConfig);
    private String jsonPathExpression;

    public JSONPathTokenResolver(String jsonPathExpression) {
        this.jsonPathExpression = jsonPathExpression;
    }

    @Override
    public TokenResolution resolveToken(Map<String, Object> context) {
        try {
            final DocumentContext docContext = jsonParseContext.parse(context);
            final Object result = docContext.read(jsonPathExpression);
            return new TokenResolution(result, true);
        } catch (Exception e) {
            return new TokenResolution(null, false, e.getMessage());
        }

    }
}

class GraphContextJsonJsonProvider extends JsonSmartJsonProvider {
    private Set<Class> whitelistedClasses = Set.of(EntityData.class);

    private boolean isMapLike(Object obj) {
        return whitelistedClasses.stream().anyMatch(clz -> clz.isInstance(obj));
    }

    @Override
    public boolean isMap(Object obj) {
        return super.isMap(obj) || isMapLike(obj);
    }

    @Override
    public Collection<String> getPropertyKeys(Object obj) {
        if (super.isMap(obj)) {
            return super.getPropertyKeys(obj);
        } else if (isMapLike(obj)) {
            return Arrays.stream(obj.getClass().getDeclaredFields()).map(f -> f.getName()).collect(Collectors.toList());
        } else {
            return List.of();
        }
    }

    @Override
    public Object getMapValue(Object obj, String key) {
        if (super.isMap(obj)) {
            return super.getMapValue(obj, key);
        } else {
            try {
                Field declaredField = obj.getClass().getDeclaredField(key);
                declaredField.setAccessible(true);
                return declaredField.get(obj);
            } catch (Exception e) {
                return null;
            }

        }
    }
}
