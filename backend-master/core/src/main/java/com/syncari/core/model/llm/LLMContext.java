package com.syncari.core.model.llm;

import com.google.common.collect.ImmutableMap;
import com.syncari.core.exceptions.SyncariValidationException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LLMContext implements Serializable {
    private Map<String, Object> context = new HashMap<>();

    public LLMContext(Object... kvp) {
        if (kvp == null || kvp.length % 2 == 1) {
            throw new
                    SyncariValidationException("Odd number of arguments found. Requires an even number");
        }
        for (int i = 0; i < kvp.length; i += 2) {
            context.put(kvp[i].toString(), kvp[i + 1]);
        }
    }

    public LLMContext() {
    }
    public LLMContext add(String key, Object value) {
        context.put(key, value);
        return this;
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(context.get(key));
    }

    /**
     * Use carefully, this is expensive because it returns an immutable copy of the context
     *
     * @return
     */
    public Map<String, Object> toMap() {
        return ImmutableMap.copyOf(context);
    }
}
