package com.syncari.connector.datastore;

import com.syncari.connector.*;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.UIMetadata;
import com.syncari.connector.service.query.SqlQueries;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.util.List;
import java.util.Optional;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.POSTGRESQL_DATASTORE)
public class PostgresqlDatastoreService extends AbstractPostgresDatastoreService  {

    private static final String SCHEMA_PRIVILEGE_QUERY = "SELECT has_schema_privilege('%s', '%s')";

    @Override
    public String getName() {
        return Constants.POSTGRESQL_DATASTORE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata()
                .setDisplayName("PostgreSQL")
                .setIconPath("/assets/icons/logos/postgres.svg");
    }

    @Override
    protected String getDescribeFieldSql(ConnectorInfo connector, String tableName) {
        return String.format(SqlQueries.DESCRIBE_FIELD, tableName.toLowerCase(), getSchemaName(connector));
    }

    @Override
    protected String getCased(String name) {
        return StringUtils.isBlank(name) ? name : name.toLowerCase();
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.Datastore;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return super.getSupportedAuthTypes();
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        if(StringUtils.isBlank(connector.getName())){
            throw new RuntimeException(i18n("datastore_name_required"));
        }

        String userName = connector.getAuthConfig().getUserName();
        if(StringUtils.isBlank(userName)){
            throw new RuntimeException(i18n("user_name_required"));
        }
        return super.validate(connector);
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField cluster = new AuthField().setRequired(true).setDataType("text").setName(Constants.CLUSTER_NAME)
                .setLabel("Cluster Name");
        AuthField schemaName = new AuthField().setRequired(true).setDataType("text").setName(SCHEMA_NAME)
                .setLabel("Schema Name");
        AuthField dbName = new AuthField().setRequired(true).setDataType("text").setName(Constants.DATABASE_NAME)
                .setLabel("Database Name");

        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("postgres_timezone_label"));
        timeZone.setHelpSummary(i18n("db_timezone_help"));
        timeZone.setRequired(true);

        return List.of(cluster, dbName, schemaName, timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try (Connection conn = getConnection(config)) {
            try (Statement stmt = conn.createStatement()) {
                String schema = config.getMetaConfig().getOrDefault("schemaName", "").toString();
                validateSchemaPrivilege(stmt, schema, "USAGE");
                validateSchemaPrivilege(stmt, schema, "CREATE");
            }
        } catch (Exception ex) {
            handleException(ex, config);
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(ex.getMessage());
        }
        return response;
    }

    private void validateSchemaPrivilege(Statement stmt, String schemaName, String privilege) throws SQLException {
        String schemaPrivilegeQuery = String.format(SCHEMA_PRIVILEGE_QUERY, schemaName, privilege);
        ResultSet rs = stmt.executeQuery(schemaPrivilegeQuery);
        if(rs.next()) {
            boolean hasSchemaPrivilege = rs.getBoolean("has_schema_privilege");
            if (!hasSchemaPrivilege) {
                throw new RuntimeException(String.format("User does not have '%s' privilege on Schema '%s'", privilege, schemaName));
            }
        } else {
            throw new RuntimeException(String.format("Unable to find '%s' privilege on Schema '%s'", privilege, schemaName));
        }
    }


    public void provision(ConnectorInfo connector, String userName, String pwd, boolean readOnly) {
        // No-op
    }

    public void deprovision(ConnectorInfo connector, String userName) {
        // No-op
    }

    @Override
    public Long getNextSequenceValue(ConnectorInfo config, String sequenceName, BigInteger startValue) {
        try (Connection conn = getConnection(config)) {
            String createSql =
                    "INSERT INTO global_sequence (instance_id,sequence_name,current_value) VALUES (?,?,?) " +
                            "ON CONFLICT(\"instance_id\", \"sequence_name\") DO UPDATE SET current_value = global_sequence.current_value + 1 RETURNING current_value";
            try (PreparedStatement pstmt = conn.prepareStatement(createSql)) {
                pstmt.setString(1, config.getInstanceId());
                pstmt.setString(2, sequenceName);
                pstmt.setBigDecimal(3, new BigDecimal(startValue.toString()));

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new Long(rs.getString("current_value"));
                    }
                }
            }
        } catch (Exception ex) {
            handleException(ex, config);
        }
        return null;
    }

    @Override
    public boolean createSequence(ConnectorInfo config, String sequenceName, Long startValue) {
        try (Connection conn = getConnection(config)) {
            String createSql =
                    "INSERT INTO global_sequence (instance_id,sequence_name,current_value) VALUES (?,?,?)";
            try (PreparedStatement pstmt = conn.prepareStatement(createSql)) {
                pstmt.setString(1, config.getInstanceId());
                pstmt.setString(2, sequenceName);
                pstmt.setBigDecimal(3, new BigDecimal(startValue));
                pstmt.executeUpdate();
                return true;
            }
        } catch (Exception ex) {
            log.error("{}", ExceptionUtils.getStackTrace(ex));
            return false;
        }
    }

    @Override
    public boolean deleteSequence(ConnectorInfo config, String sequenceName) {
        try (Connection conn = getConnection(config)) {
            String deleteSql = "delete from global_sequence where instance_id = ? and sequence_name=?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                pstmt.setString(1, config.getInstanceId());
                pstmt.setString(2, sequenceName);
                pstmt.executeUpdate();
                return true;
            }
        } catch (Exception ex) {
            log.error("{}", ExceptionUtils.getStackTrace(ex));
            return false;
        }
    }

    public String executeQuery(String query, ConnectorInfo info) {
        log.info("Query : {}", query);
        if (!isReadOnlyQuery(query)) {
            return "";
        }
        StringBuilder result = new StringBuilder();

        try (Connection connection = getReadOnlyConnection(info);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {

            int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    result.append(resultSet.getString(i)).append("\t");
                }
                result.append("\n");
            }
        } catch (SQLException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            return "Error executing query: " + e.getMessage();
        }
        log.info("Result : {}", result);
        return result.toString();
    }

    private boolean isReadOnlyQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String normalizedQuery = query.trim().toLowerCase();

        // Check if query starts with SELECT and doesn't contain any data modification keywords
        boolean isSelect = normalizedQuery.startsWith("select");
        boolean containsModification = normalizedQuery.contains("insert into") ||
                normalizedQuery.contains("update ") ||
                normalizedQuery.contains("delete from") ||
                normalizedQuery.contains("drop ") ||
                normalizedQuery.contains("alter ") ||
                normalizedQuery.contains("create ") ||
                normalizedQuery.contains("truncate ");

        return isSelect && !containsModification;
    }

}
