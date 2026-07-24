package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class EntityParams {
    EntitySchema schema;
    private Map<String , Object> sourceParams;
    private Map<String , Object> destParams;
    ConnectorInfo connector;

    public Object getSourceParam(String key) {
        return sourceParams == null ? null : sourceParams.get(key);
    }

    public Object getDestParam(String key) {
        return destParams == null ? null : destParams.get(key);
    }
}
