package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.database.DatabaseService;
import com.syncari.connector.service.query.SqlQueries;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.DatastoreService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class SYN_19659_GeneratePostgresUsernamePwd {

    private static final int CONNECTION_TIMEOUT = 30000; // 5 mins, connection timeout should be greater than validation timeout
    private static final int VALIDATION_TIMEOUT = 30000;
    private static final int MAX_LIFETIME = 300000; // 5 mins
    protected static final int SOCKET_TIMEOUT = 300; // only used for Postgres SQL right now, in seconds
    private static final int IDLE_TIMEOUT = 300000;
    public static final String CONNECTION_TIMEOUT_PARAM = "connectionTimeout";
    public static final String SOCKET_TIMEOUT_PARAM = "socketTimeout";
    // This is a postgres specific limit, but might make sense to limit all variables to less than this
    private static final String JDBC_URL = "jdbc:postgresql://%s/%s?OpenSourceSubProtocolOverride=true&socketTimeout=%s";

    protected static final String SSL_FACTORY = "&sslmode=%s&sslfactory=org.postgresql.ssl.LibPQFactory";

    protected static void loadDriver(String driverClass) {
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {

        }
    }

    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        return Optional.empty();
    }

    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }

    protected String getJdbcURL(ConnectorInfo connector) {
        String serverCert = connector.getAuthConfig().getHeader("cert");
        String clientCert = connector.getAuthConfig().getHeader("clientCert");
        String clientKey = connector.getAuthConfig().getHeader("clientKey");
        String verifyMode = Boolean.valueOf(connector.getAuthConfig().getHeader("verifyCert")) ? "verify-full" : "verify-ca";
        //String clusterName = "35.197.29.117";
        //String clusterName = "35.230.89.186"; // non prod
        //String clusterName = getValue(connector, Constants.CLUSTER_NAME);
        String clusterName = System.getProperty("clusterName");
        if (StringUtils.isBlank(clusterName)) {
            clusterName = getValue(connector, Constants.CLUSTER_NAME);
            log.info("No clusterName provided hence taking from connector {}", clusterName);
        }
        log.info("ClusterName to be used is local from this file :: {}", clusterName);
        String jdbcURL = StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL, clusterName, getValue(connector, Constants.DATABASE_NAME),
                connector.getMetaConfig().getOrDefault(SOCKET_TIMEOUT_PARAM, SOCKET_TIMEOUT))
                : clusterName;
        if (!StringUtils.isBlank(serverCert)) {
            String rootCertPath = createFileIfAbsent(String.format("pgservercert_%s_%s.crt", connector.getInstanceId(), connector.getId()), serverCert);
            jdbcURL = jdbcURL.concat(String.format(SSL_FACTORY, verifyMode)).concat("&sslrootcert=").concat(rootCertPath);
        }
        if (!StringUtils.isBlank(clientCert) && !StringUtils.isBlank(clientKey)) {
            // see https://jdbc.postgresql.org/documentation/head/ssl-client.html
            String clientCertPath = createFileIfAbsent(String.format("pgclientcert_%s_%s.crt", connector.getInstanceId(), connector.getId()), clientCert);
            File file = new File("/tmp/" + String.format("pgclientkey_%s_%s", connector.getInstanceId(), connector.getId()));
            try (PEMParser pemParser = new PEMParser(new StringReader(clientKey))) {
                final PEMKeyPair o = (PEMKeyPair) pemParser.readObject();
                JcaPEMKeyConverter pemKeyConverter = new JcaPEMKeyConverter();
                final PrivateKey privateKey = pemKeyConverter.getPrivateKey(o.getPrivateKeyInfo());
                FileUtils.writeByteArrayToFile(file, privateKey.getEncoded());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            String clientKeyPath = file.getAbsolutePath();
            jdbcURL = jdbcURL.concat("&sslcert=").concat(clientCertPath).concat("&sslkey=")
                    .concat(clientKeyPath);
        }
        return jdbcURL;
    }

    protected String createFileIfAbsent(String fileName, String content) {
        File file = new File("/tmp/" + fileName);
        try {
            FileUtils.write(file, content, Charset.forName("utf8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file.getAbsolutePath();
    }


    protected LoadingCache<ConnectionInfoWrapper, HikariDataSource> poolCache = CacheBuilder.newBuilder()
            .maximumSize(100000).build(new CacheLoader<>() {
                @Override
                public HikariDataSource load(ConnectionInfoWrapper connectorWrapper) {
                    ConnectorInfo connector = connectorWrapper.getConnectionInfo();
                    HikariDataSource ds = new HikariDataSource();
                    int size = 2;
                    ds.setMaximumPoolSize(size);
                    ds.setValidationTimeout(VALIDATION_TIMEOUT);
                    ds.setMaxLifetime(MAX_LIFETIME);
                    ds.setInitializationFailTimeout(-1);
                    ds.setConnectionTimeout((int) connector.getMetaConfig().getOrDefault(CONNECTION_TIMEOUT_PARAM, CONNECTION_TIMEOUT));
                    String urlStr = getJdbcURL(connector);
                    Properties props = new Properties();
                    Optional<Properties> additionalProperties = getAdditionalProperties(connector);
                    additionalProperties.ifPresent(additional -> {
                        props.putAll(additional);
                    });
                    ds.setDataSourceProperties(props);
                    ds.setJdbcUrl(urlStr);
                    ds.setUsername(connector.getAuthConfig().getUserName());
                    ds.setPassword(connector.getAuthConfig().getPassword());
                    ds.setMinimumIdle(0);
                    ds.setIdleTimeout(IDLE_TIMEOUT);
                    return ds;
                }

                ;
            });


    // This generates username and password, create read only group and provide permissions
    @ChangeSet(order = "001", id = "generateUsernamePwd", author = "rohit", runAlways = true)
    public void generateUsernamePwd(MongoTemplate template) {
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        Connector datastore = datastoreService.findActiveDatastore()
                .orElseThrow(() -> new RuntimeException("Datastore connector missing"));
        ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
        String userName = "syncari_ds_" + SyncariContext.getSyncariId().toLowerCase() + "_1";
        String pwd =   StringUtils.capitalize(RandomStringUtils.randomAlphabetic(8).toLowerCase()) + RandomStringUtils.randomNumeric(2);
        String customerDbName = "syncari_" + SyncariContext.getSyncariId().toLowerCase();


        log.info("Storing read only user creds for {} with username {} and pwd {}", SyncariContext.getSyncariId(), userName, pwd);

        try (Connection conn1 = getConnection(info)) {
            try (Statement stmt = conn1.createStatement()) {

                // Create or find a group
                String groupName = createGroup(stmt, SyncariContext.getSyncariId().toLowerCase());
                log.info("Successfully created group {}", groupName);

                // Create User
                createUser(stmt, userName, pwd);

                // Alter group add user
                String sql = String.format(SqlQueries.ALTER_GROUP, groupName, userName);
                stmt.execute(sql);
                log.info("Successfully added user {} to group {}", userName, groupName);

                // Grant Usage permission to group for db
                sql = String.format(SqlQueries.GRANT_DB_USAGE, customerDbName, groupName);
                stmt.execute(sql);
                log.info("Successfully granted usage perm to group {} for db {}", groupName, customerDbName);

                // Grant Usage permission to group for schema
                sql = String.format(SqlQueries.GRANT_USAGE, customerDbName, groupName);
                stmt.execute(sql);
                log.info("Successfully granted usage perm to group {} for schema {}", groupName, customerDbName);

                // Grant Select permission to group for schema
                sql = String.format(SqlQueries.GRANT_SELECT, customerDbName, groupName);
                stmt.execute(sql);
                log.info("Successfully granted select perm to group {} for schema {}", groupName, customerDbName);

                // Alter Default Privileges to maintain the permissions on new tables
                sql = String.format(SqlQueries.ALTER_DEFAULT, customerDbName, groupName);
                stmt.execute(sql);
                log.info("Successfully altered default priv to group {} for schema {}", groupName, customerDbName);

                // Revoke CREATE privileges from group
                revokeCreatePrivilege(stmt, customerDbName, groupName);
            }
        } catch (Exception e) {
            log.error("Exception occurred creating user ", e);
        }
    }

    private void revokeCreatePrivilege(Statement stmt, String schema, String groupName) throws SQLException {
        String sql = String.format(SqlQueries.REVOKE_CREATE, schema, groupName);
        stmt.execute(sql);
        log.info("Successfully revoked create from group {} for schema {}", groupName, schema);
    }

    protected String createGroup(Statement stmt, String syncariId) throws SQLException {
        String groupName = "readonly_syncari_" + syncariId;
        boolean groupExists = false;
        try (ResultSet rs = stmt.executeQuery(String.format(SqlQueries.SELECT_GROUP, groupName))) {
            while (rs.next()) {
                // The group already exists, just return
                groupExists = true;
                break;
            }
        }
        if (groupExists) {
            log.info("Not creating group {}, it already exists", groupName);
            return groupName;
        }
        String sql = String.format(SqlQueries.CREATE_GROUP, groupName);
        stmt.execute(sql);
        log.info("Successfully created group {}", groupName);
        return groupName;
    }

    protected String createUser(Statement stmt, String userName, String pwd) throws SQLException {
        boolean userExists = false;
        try (ResultSet rs = stmt.executeQuery(String.format(SqlQueries.SELECT_USER, userName))) {
            while (rs.next()) {
                userExists = true;
                break;
            }
        }
        if (userExists) {
            log.info("Not creating user {}, it already exists", userName);
            return userName;
        }
        stmt.execute(String.format(SqlQueries.CREATE_USER, userName, pwd));
        log.info("Successfully created user {} with pwd {}", userName, pwd);
        return userName;
    }

    protected Connection getConnection(ConnectorInfo connector) throws ClassNotFoundException, SQLException {
        log.info("JDBC URL being used is {}", getJdbcURL(connector));
        HikariDataSource dataSource = poolCache.getUnchecked(new ConnectionInfoWrapper(connector));
        return ConnectorHelper.withBackoff(() -> dataSource.getConnection());
    }

    @Data
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    class ConnectionInfoWrapper {

        private final ConnectorInfo connectionInfo;

        public ConnectorInfo getConnectionInfo() {
            return connectionInfo;
        }

        @EqualsAndHashCode.Include
        private final String connectionHash;

        ConnectionInfoWrapper(ConnectorInfo connectionInfo) {
            this.connectionInfo = connectionInfo;
            connectionHash = connectionInfo.connectionHash();
        }
    }

    public ConnectorInfo toConnectorInfo(Optional<Connector> datastore) {
        ConnectorMetadataService connectorMetaService = MigrationContext.getConnectorMetaDataService();
        Connector connector = datastore.get();
        ConnectorInfo info = new ConnectorInfo(connector.getId(), connector.getName(), connector.getEndpoint(),
                SyncariContext.getOrganziation() == null ? "" : SyncariContext.getSyncariId());
        info.setAuthConfig(connector.getAuthConfig());
        info.setMetaConfig(connector.getMetaConfig());
        info.setDatastoreType(connector.getDatastoreType());
        if (connector.getMetadata() == null && StringUtils.isNotBlank(connector.getMetadataId())) {
            Optional<ConnectorMetadata> metadata = connectorMetaService.findById(connector.getMetadataId());
            metadata.ifPresent(m -> {
                connector.setMetadata(m);
                if (connector.getDailyQuota() == 0) {
                    connector.setDailyQuota(m.getDefaultApiLimit());
                }
            });
        }
        if (connector.getMetadata() != null) {
            info.setCustom(connector.getMetadata().isCustom());
            if (connector.getMetadata().isCustom()) {
                CloudFunctionInfo cfInfo = connectorMetaService.getCloudFunctionInfo(connector.getMetadata());
                info.setCloudFunctionInfo(cfInfo);
            }
            info.setConnectorMetadataName(connector.getMetadata().getName());
        }
        if (connector.getSetting() != null) {
            info.setInternalConfig(connector.getSetting().getInternalConfig());
        }
        info.setAlterLengthIfRequired(true);
        info.getMetaConfig().put(DatabaseService.POOL_SIZE, 5);
        return info;
    }
}
