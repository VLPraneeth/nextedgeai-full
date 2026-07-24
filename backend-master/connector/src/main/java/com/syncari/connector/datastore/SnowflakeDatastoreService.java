package com.syncari.connector.datastore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.syncari.connector.*;
import com.syncari.connector.data.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.syncari.connector.database.SnowflakeService;

import static com.syncari.utils.I18n.i18n;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.SNOWFLAKE_DATASTORE)
public class SnowflakeDatastoreService extends SnowflakeService implements Datastore {
    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd(), ConnectorHelper.getAccessTokenOauthType());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField endpoint = new AuthField().setRequired(true).setDataType("text").setName("endpoint")
                .setLabel("Snowflake URL")
                .setHelpSummary("The full Snowflake URL (e.g., https://account_name.snowflakecomputing.com)");
        AuthField account = new AuthField().setRequired(true).setDataType("text").setName("accountName")
                .setLabel("Account Name");
        AuthField warehouse = new AuthField().setRequired(true).setDataType("text").setName("warehouseName")
                .setLabel("Warehouse Name");
        AuthField database = new AuthField().setRequired(true).setDataType("text").setName("dbName")
                .setLabel("Database Name");
        AuthField schema = new AuthField().setRequired(true).setDataType("text").setName("schemaName")
                .setLabel("Schema Name");
        AuthField role = new AuthField().setRequired(false).setDataType("text").setName("role")
                .setLabel("User Role")
                .setHelpSummary("Choose the role for the user with full privileges to the schema/database/warehouse. If not provided, default role of 'PUBLIC' will be used");

        return List.of(endpoint, account, warehouse, database, schema, role, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Datawarehouse";
    }

    @Override
    public String getName() {
        return Constants.SNOWFLAKE_DATASTORE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/snowflake.svg")
                .setDisplayName("Snowflake");
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        if(StringUtils.isBlank(connector.getName())){
            throw new RuntimeException(i18n("datastore_name_required"));
        }
        if(!connector.getMetaConfig().containsKey("endpoint") ||
           StringUtils.isBlank((String) connector.getMetaConfig().get("endpoint"))) {
            throw new RuntimeException(i18n("snowflake_datastore_endpoint_required"));
        }

        // Validate endpoint format
        String endpoint = (String) connector.getMetaConfig().get("endpoint");
        String cleanEndpoint = endpoint;
        if (cleanEndpoint.startsWith("https://")) {
            cleanEndpoint = cleanEndpoint.substring(8);
        } else if (cleanEndpoint.startsWith("http://")) {
            cleanEndpoint = cleanEndpoint.substring(7);
        }
        if (cleanEndpoint.endsWith("/")) {
            cleanEndpoint = cleanEndpoint.substring(0, cleanEndpoint.length() - 1);
        }

        if (!cleanEndpoint.contains(".snowflakecomputing.com")) {
            throw new RuntimeException("Snowflake URL must contain '.snowflakecomputing.com' domain (e.g., https://account_name.snowflakecomputing.com)");
        }
        if(!connector.getMetaConfig().containsKey("accountName") ||
           StringUtils.isBlank((String) connector.getMetaConfig().get("accountName"))) {
            throw new RuntimeException(i18n("snowflake_datastore_account_name_required"));
        }
        if(!connector.getMetaConfig().containsKey("warehouseName") ||
           StringUtils.isBlank((String) connector.getMetaConfig().get("warehouseName"))) {
            throw new RuntimeException(i18n("snowflake_datastore_warehouse_name_required"));
        }
        if(!connector.getMetaConfig().containsKey("dbName") ||
           StringUtils.isBlank((String) connector.getMetaConfig().get("dbName"))) {
            throw new RuntimeException(i18n("snowflake_datastore_db_name_required"));
        }
        if(!connector.getMetaConfig().containsKey("schemaName") ||
           StringUtils.isBlank((String) connector.getMetaConfig().get("schemaName"))) {
            throw new RuntimeException(i18n("snowflake_datastore_schema_name_required"));
        }

        String authType = connector.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();
        if (authType.equalsIgnoreCase(AuthType.Oauth.toString())) {
            String clientId = connector.getAuthConfig().getClientId();
            if(StringUtils.isBlank(clientId)){
                throw new RuntimeException(i18n("client_id_required"));
            }
            String clientSecret = connector.getAuthConfig().getClientSecret();
            if(StringUtils.isBlank(clientSecret)){
                throw new RuntimeException(i18n("client_secret_required"));
            }
        } else {
            String userName = connector.getAuthConfig().getUserName();
            if(StringUtils.isBlank(userName)){
                throw new RuntimeException(i18n("user_name_required"));
            }
            String password = connector.getAuthConfig().getPassword();
            if(StringUtils.isBlank(password)){
                throw new RuntimeException(i18n("password_required"));
            }
        }

        return true;
    }

    @Override
    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        Properties props = super.getAdditionalProperties(connector).orElse(new Properties());
        String dbValue = props.getProperty("db");
        if (dbValue != null) {
            props.remove("db");
            props.setProperty("database", dbValue);
        }

        // Set default shorter connection timeout for Snowflake if not specified
        if (!connector.getMetaConfig().containsKey("connectionTimeout")) {
            connector.getMetaConfig().put("connectionTimeout", 30000); // 30 seconds
        }

        return Optional.of(props);
    }

    @Override
    protected String getJdbcURL(ConnectorInfo connector) {
        String endpoint = (String) connector.getMetaConfig().get("endpoint");
        if (endpoint.startsWith("https://")) {
            endpoint = endpoint.substring(8);
        } else if (endpoint.startsWith("http://")) {
            endpoint = endpoint.substring(7);
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return String.format("jdbc:snowflake://%s/", endpoint);
    }

    @Override
    protected String getTableName(String entityName, ConnectorInfo connector) {
        String schemaName = (String) connector.getMetaConfig().get("schemaName");
        String baseTableName = super.getTableName(entityName, connector);
        return String.format("%s.%s", schemaName, baseTableName);
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.Datastore;
    }

    @Override
    public void provision(ConnectorInfo connector, String userName, String pwd, boolean readOnly) {
        // TODO Auto-generated method stub
    }

    @Override
    public void deprovision(ConnectorInfo connector, String userName) {
        // TODO Auto-generated method stub
    }

    @Override
    public List<Map<String, Object>> retrieveData(ConnectorInfo connector, String query, Map<String, String> fields) {
        return executeDmlQuery(connector, query, fields);
    }

    @Override
    public List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query, List<DatastoreFieldMetadata> fields) {
        return executeQueryToGetData(connector, query, fields, new java.util.HashSet<>());
    }

    @Override
    public List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query, Map<Integer, ParamValue> paramValues, List<DatastoreFieldMetadata> fields, Set<DatastoreTableMetadata> datastoreTableMetadatas) {
        return executePreparedStmtToGetData(connector, query, paramValues, fields, datastoreTableMetadatas);
    }

    @Override
    public void executeDdlSql(ConnectorInfo connector, String sql){
        ensureSchemaContext(connector, sql);
    }

    private void ensureSchemaContext(ConnectorInfo connector, String sql) {
        try (java.sql.Connection conn = getConnection(connector)) {
            try (java.sql.Statement stmt = conn.createStatement()) {
                // Set database and schema context
                String dbName = (String) connector.getMetaConfig().get("dbName");
                String schemaName = (String) connector.getMetaConfig().get("schemaName");
                stmt.execute(String.format("USE DATABASE %s", dbName));
                stmt.execute(String.format("USE SCHEMA %s", schemaName));

                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    @Override
    protected int getInitializationFailTimeout() {
        return 30000; // 30 seconds
    }

    @Override
    public long count(ConnectorInfo connectorInfo, String datastoreName) {
        String countQuery = String.format("SELECT COUNT(*) FROM %s", getTableName(datastoreName, connectorInfo));
        try (java.sql.Connection conn = getConnection(connectorInfo)) {
            try (java.sql.Statement stmt = conn.createStatement()) {
                String dbName = (String) connectorInfo.getMetaConfig().get("dbName");
                String schemaName = (String) connectorInfo.getMetaConfig().get("schemaName");
                stmt.execute(String.format("USE DATABASE %s", dbName));
                stmt.execute(String.format("USE SCHEMA %s", schemaName));
                try (java.sql.ResultSet rs = stmt.executeQuery(countQuery)) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        } catch (Exception e) {
            handleException(e, connectorInfo);
        }
        return 0;
    }
}
