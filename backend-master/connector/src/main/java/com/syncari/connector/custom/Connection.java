package com.syncari.connector.custom;

import java.util.HashMap;
import java.util.Map;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import lombok.ToString;

/**
 * CAUTION: This object should not contain syncari specific info in the object. 
 * The external synapse developers will have access to this object.
 */
@Data
@ToString
public class Connection {
    private String name;
    private String endpoint;
    private AuthConfig authConfig;
    private String idFieldName;
    private String watermarkFieldName;
    private String createdAtFieldName;
    private String updatedAtFieldName;
    private String oAuthRedirectUrl;
    private Map<String, Object> metaConfig = new HashMap<String, Object>();

    public static Connection fromConnectorInfo(ConnectorInfo connectorInfo) {
        Connection connection = new Connection();
        connection.setName(StringUtils.isNotBlank(connectorInfo.getConnectorMetadataName()) ? connectorInfo.getConnectorMetadataName() : connectorInfo.getName());
        String endPoint = connectorInfo.getEndpoint();
        // The `validate` method in ConnectorService strips the "/", we do not know the history there.
        // We instead handle it specifically to put it back.
        if (StringUtils.isNotEmpty(endPoint) && !endPoint.endsWith("/")) {
            endPoint += "/";
        }
        connection.setEndpoint(endPoint);
        connection.setAuthConfig(connectorInfo.getAuthConfig());
        // TODO: Fix this date issue with json serialiazer
        connection.getAuthConfig().setLastRefreshed(null);
        // The endpoints should always be same.
        if (StringUtils.isNotEmpty(connection.getAuthConfig().getEndpoint())) {
            if (!connection.getAuthConfig().getEndpoint().endsWith("/")) {
                connection.getAuthConfig().setEndpoint(connection.getAuthConfig().getEndpoint() + "/");
            }
        } else {
            connection.getAuthConfig().setEndpoint(endPoint);
        }
        connection.setIdFieldName(connectorInfo.getIdFieldName());
        connection.setWatermarkFieldName(connectorInfo.getWatermarkFieldName());
        connection.setCreatedAtFieldName(connectorInfo.getCreatedAtFieldName());
        connection.setUpdatedAtFieldName(connectorInfo.getUpdatedAtFieldName());
        connection.setOAuthRedirectUrl(connectorInfo.getOAuthRedirectUrl());
        connection.setMetaConfig(connectorInfo.getMetaConfig());
        return connection;
    }
}
