package com.syncari.core.insights;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AuthType;
import com.syncari.core.config.AppConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class DatastoreConnectionHelper {

    public static Map<String, Object> createConnectionConfig(ConnectorInfo connectorInfo, String orgName, AppConfig appConfig) {
        String metadataName = connectorInfo.getConnectorMetadataName();

        if (Constants.SNOWFLAKE_DATASTORE.equals(metadataName)) {
            return createSnowflakeConnectionConfig(connectorInfo, orgName, appConfig);
        } else {
            return createPostgresConnectionConfig(connectorInfo, orgName, appConfig);
        }
    }

    private static Map<String, Object> createSnowflakeConnectionConfig(ConnectorInfo connectorInfo, String orgName, AppConfig appConfig) {
        Map<String, Object> data_warehouse_config = new HashMap<>();
        Map<String, Object> dbConfig = new HashMap<>();

        String endpoint = (String)connectorInfo.getMetaConfig().get("endpoint");
        String accountName = (String)connectorInfo.getMetaConfig().get("accountName");
        String dbName = (String)connectorInfo.getMetaConfig().get("dbName");

        if (StringUtils.isBlank(endpoint) || StringUtils.isBlank(accountName) || StringUtils.isBlank(dbName)) {
            throw new RuntimeException("Endpoint, account name, and database name are required for connector: " + connectorInfo.getName());
        }

        if (endpoint.startsWith("https://")) {
            endpoint = endpoint.substring(8);
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        String host = endpoint;
        String port = "443";

        dbConfig.put("accountName", accountName != null ? accountName : orgName);
        String warehouseName = (String)connectorInfo.getMetaConfig().get("warehouseName");
        if (warehouseName != null) {
            dbConfig.put("warehouse", warehouseName);
        }
        String role = (String)connectorInfo.getMetaConfig().get("role");
        if (role != null) {
            dbConfig.put("role", role);
        }


        addAuthConfig(dbConfig, connectorInfo);
        dbConfig.put("host", host);
        dbConfig.put("port", port);
        dbConfig.put("database", dbName);

        String schemaName = (String)connectorInfo.getMetaConfig().get("schemaName");
        if (schemaName != null) {
            dbConfig.put("schema", schemaName);
        }

        data_warehouse_config.put("configuration", dbConfig);
        return data_warehouse_config;
    }

    private static Map<String, Object> createPostgresConnectionConfig(ConnectorInfo connectorInfo, String orgName, AppConfig appConfig) {
        Map<String, Object> data_warehouse_config = new HashMap<>();
        Map<String, Object> dbConfig = new HashMap<>();

        String port = (String)connectorInfo.getMetaConfig().get("port");
        String dbName = (String)connectorInfo.getMetaConfig().get("dbName");
        String clusterName = (String)connectorInfo.getMetaConfig().get("clusterName");

        if (StringUtils.isBlank(port) || StringUtils.isBlank(dbName) || StringUtils.isBlank(clusterName)) {
            throw new RuntimeException("Port, database name, and cluster name are required for connector: " + connectorInfo.getName());
        }

        String [] hostParts = clusterName.split(":");
        String host = hostParts.length > 0 ? hostParts[0] : null;
        
        dbConfig.put("accountName", orgName);

        addAuthConfig(dbConfig, connectorInfo);

        String envName = appConfig.getEnvName();
        String metadataName = connectorInfo.getConnectorMetadataName();
        if (Constants.DATASTORE.equals(metadataName) && StringUtils.isNotEmpty(envName)) {
            dbConfig.put("host", appConfig.getDatastorePublicHost());
        } else {
            dbConfig.put("host", host);
        }
        dbConfig.put("port", port);
        dbConfig.put("database", dbName);

        String schemaName = (String)connectorInfo.getMetaConfig().get("schemaName");
        if (schemaName != null) {
            dbConfig.put("schema", schemaName);
        }

        data_warehouse_config.put("configuration", dbConfig);
        return data_warehouse_config;
    }

    private static void addAuthConfig(Map<String, Object> dbConfig, ConnectorInfo connectorInfo) {
        String authType = connectorInfo.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();
        if (authType.equalsIgnoreCase(AuthType.Oauth.toString())) {
            dbConfig.put("authenticator", "oauth");
            dbConfig.put("token", connectorInfo.getAuthConfig().getAccessToken());
        } else {
            dbConfig.put("user", connectorInfo.getAuthConfig().getUserName());
            dbConfig.put("password", connectorInfo.getAuthConfig().getPassword());
        }
    }
}