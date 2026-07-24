package com.syncari.connector.datastore;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.connector.data.UIMetadata;
import com.syncari.connector.database.PostgresService;
import com.syncari.connector.service.query.SqlQueries;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component(Constants.DATASTORE)
public class SyncariDatastoreService extends AbstractPostgresDatastoreService {

    @Override
    public String getName() {
        return Constants.DATASTORE;
    }

    public UIMetadata getUIMetadata() {
        return super.getUIMetadata()
                .setDisplayName("Syncari Datastore")
                .setIconPath("/assets/icons/logos/syncari.svg");
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
    public boolean validate(ConnectorInfo connector) {
        return true;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }

    public void provision(ConnectorInfo connector, String userName, String pwd, boolean readOnly) {
        switchToDefaultDb(connector);
        String schema = getValue(connector, SCHEMA_NAME);
        String customerDbName = schema;
        try (Connection conn1 = getConnection(connector)) {
            try (Statement stmt = conn1.createStatement()) {
                // Create db
                createDB(stmt, customerDbName);
            }
        } catch (Exception e) {
            handleException(e, connector);
        } finally {
            connector.getMetaConfig().put(Constants.DATABASE_NAME, customerDbName);
        }

        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql;

                // Create read only group
                String groupName = createGroup(stmt, schema);
                createSchema(stmt, connector);

                // Create user
                createUser(stmt, userName, pwd);

                // Alter group add user
                sql = String.format(SqlQueries.ALTER_GROUP, groupName, userName);
                stmt.execute(sql);
                log.info("Successfully added user {} to group {}", userName, groupName);

                // Grant Usage permission to group for db
                sql = String.format(SqlQueries.GRANT_DB_USAGE, customerDbName, groupName);
                stmt.execute(sql);
                log.info("Successfully granted usage perm to group {} for db {}", groupName, customerDbName);

                // Grant Usage permission to group for schema
                sql = String.format(SqlQueries.GRANT_USAGE, schema, groupName);
                stmt.execute(sql);
                log.info("Successfully granted usage perm to group {} for schema {}", groupName, schema);

                // Grant Select permission to group for schema
                sql = String.format(SqlQueries.GRANT_SELECT, schema, groupName);
                stmt.execute(sql);
                log.info("Successfully granted select perm to group {} for schema {}", groupName, schema);

                // Alter Default Privileges to maintain the permissions on new tables
                sql = String.format(SqlQueries.ALTER_DEFAULT, schema, groupName);
                stmt.execute(sql);
                log.info("Successfully altered default priv to group {} for schema {}", groupName, schema);

                // Revoke CREATE privileges from group
                revokeCreatePrivilege(stmt, schema, groupName);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    @Retryable(value = { RuntimeException.class }, maxAttempts = 3, backoff = @Backoff(delay = 5000))
    public void deprovision(ConnectorInfo connector, String userName) {
        String schema = getValue(connector, SCHEMA_NAME);
        dropUser(connector, userName);
        dropSchema(connector);
        closeDatasource(connector);
        switchToDefaultDb(connector);
        terminateDBConnections(connector, schema);
        dropDb(connector, schema);
        dropGroup(connector, generateGroupName(schema));
        closeDatasource(connector);
    }

    private void terminateDBConnections(ConnectorInfo connector, String dbName) {
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(
                        "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = '%s'", dbName);
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    private void switchToDefaultDb(ConnectorInfo connector) {
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "postgres");
    }

}
