package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.custom.Connection;
import com.syncari.connector.custom.RequestType;

import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@ToString
public class SynapseRequest {
    private RequestType type;
    private Connection connection;
    private Map<String, Object> body;

    public SynapseRequest(RequestType type, Connection connection, Map<String, Object> body) {
        this.type = type;
        this.connection = connection;
        this.body = body;
    }
}

