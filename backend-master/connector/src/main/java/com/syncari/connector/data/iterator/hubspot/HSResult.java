package com.syncari.connector.data.iterator.hubspot;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class HSResult {
    String id;
    Map<String, Object> properties;
    String createdAt;
    String updatedAt;
    boolean archived;
}
