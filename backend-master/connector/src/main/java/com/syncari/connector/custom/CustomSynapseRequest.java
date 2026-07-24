package com.syncari.connector.custom;

import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@ToString
public class CustomSynapseRequest {

    private RequestType type;
    private Connection connection;
    private String host;
    private String syncariId;

    public Map<String, Object> body;

    public CustomSynapseRequest(RequestType type, Connection connection, Map<String, Object> body, String host, String syncariId) {
        this.type = type;
        this.connection = connection;
        this.body = body;
        this.host = host;
        this.syncariId = syncariId;
    }
}
