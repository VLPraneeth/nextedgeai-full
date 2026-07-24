package com.syncari.api.rest.controllers.data.studio;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class Entity {
    Map<String, Object> fields = new HashMap<>();
    
    public Entity addField(String id, Object entry) {
        fields.put(id, entry);
        return this;
    }

    public String getId() {
        return fields.get("id").toString();
    }
}
