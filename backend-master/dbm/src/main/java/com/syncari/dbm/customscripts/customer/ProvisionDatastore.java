package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.database.DatabaseService;
import com.syncari.connector.database.RedshiftService;
import com.syncari.connector.datastore.SyncariDatastoreService;
import com.syncari.connector.service.query.SqlQueries;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.InsightsDBStaticIndexes;
import com.syncari.core.model.misc.FeatureStage;
import com.syncari.core.model.misc.FeatureStatus;
import com.syncari.core.repositories.customer.FeatureRepo;
import com.syncari.core.service.*;
import com.syncari.utils.I18n;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Slf4j
public class ProvisionDatastore {
    private static final int CONNECTION_TIMEOUT = 30000; // 5 mins, connection timeout should be greater than validation timeout
    private static final int VALIDATION_TIMEOUT = 30000;
    private static final int MAX_LIFETIME = 300000; // 5 mins
    protected static final int SOCKET_TIMEOUT = 300; // only used for Postgres SQL right now, in seconds
    private static final int IDLE_TIMEOUT = 300000;
    protected static final String SSL_FACTORY = "&sslmode=%s&sslfactory=org.postgresql.ssl.LibPQFactory";


    public static final String CONNECTION_TIMEOUT_PARAM = "connectionTimeout";
    public static final String SOCKET_TIMEOUT_PARAM = "socketTimeout";
    public static final String TIME_ZONE_ID = "timeZoneId";
    private final int QUERY_TIMEOUT = 30;
    // This is a postgres specific limit, but might make sense to limit all variables to less than this
    //private static final String JDBC_URL = "jdbc:postgresql://%s/%s?OpenSourceSubProtocolOverride=true&socketTimeout=%s";
    private static final String JDBC_URL = "jdbc:postgresql://%s:%s/%s?user=%s&password=%s&OpenSourceSubProtocolOverride=true&socketTimeout=%s";

    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        return Optional.empty();
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
                };
            });

    protected String getJdbcURL(ConnectorInfo connector) {
        String userName = connector.getAuthConfig().getUserName();
        String passWord = connector.getAuthConfig().getPassword();
        log.info("Username to be used is {}, password is {}", userName, passWord);

        //String clusterName = "35.197.29.117";
        String clusterName = "35.230.89.186"; // non prod
        //String clusterName = "127.0.0.1";
        log.info("ClusterName to be used is local from this file :: {}", clusterName);
        //getValue(connector, Constants.CLUSTER_NAME)
        String jdbcURL = StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL,clusterName, "5432", getValue(connector, Constants.DATABASE_NAME),userName, passWord,
                connector.getMetaConfig().getOrDefault(SOCKET_TIMEOUT_PARAM, SOCKET_TIMEOUT))
                : clusterName;
        /*if(!StringUtils.isBlank(serverCert)) {
            String rootCertPath = createFileIfAbsent(String.format("pgservercert_%s_%s.crt",connector.getInstanceId(),connector.getId()), serverCert);
            jdbcURL = jdbcURL.concat(String.format(SSL_FACTORY, verifyMode)).concat("&sslrootcert=").concat(rootCertPath);
        }
        if(!StringUtils.isBlank(clientCert) && !StringUtils.isBlank(clientKey)) {
            // see https://jdbc.postgresql.org/documentation/head/ssl-client.html
            String clientCertPath = createFileIfAbsent(String.format("pgclientcert_%s_%s.crt",connector.getInstanceId(),connector.getId()),clientCert);
            File file = new File("/tmp/"+String.format("pgclientkey_%s_%s",connector.getInstanceId(),connector.getId()));
            try(PEMParser pemParser = new PEMParser(new StringReader(clientKey))) {
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
        }*/
        return jdbcURL;
    }

    @ChangeSet(order = "001", id = "provisionDatastore", author = "abhinav", runAlways = true)
    public void provisionDatastore(MongoTemplate template) {
        boolean enableInsights = Boolean.parseBoolean(System.getProperty("enableInsights","false"));
        AppConfig appConfig = MigrationContext.getAppConfig();

        String uname = appConfig.getDatastoreUser();
        String pwd = appConfig.getDatastorePwd();
        pwd = pwd.replaceAll("^\'|\'$", "");

        FeatureService featureService = MigrationContext.getFeatureService();
        UserService userService = MigrationContext.getUserService();

        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        // check if datastore is already provisioned
        if(featureService.isEnabled(Features.Datastore)) {
            log.info("Skipping datastore provisioning for {} as the feature is already enabled", SyncariContext.getSyncariId());
            if (enableInsights){
                if (featureService.isEnabled(Features.Insights)){
                    log.info("Insights for {} as the feature is already enabled", SyncariContext.getSyncariId());
                }else{
                    setStatus(Features.Insights,FeatureStatus.active);
                }
            }
            return;
        }
        try{
            log.info("Provisioning datastore for instance with syncariId {}", SyncariContext.getSyncariId());
            featureService.enableFeature(Features.Datastore);
            provision(SyncariContext.getSyncariId(),uname, pwd);
            if (enableInsights){
                setStatus(Features.Insights,FeatureStatus.active);
            }
        }catch (Throwable e){
            log.info("Exception occurred while provisioning datastore for syncariid {} with error message {}", SyncariContext.getSyncariId(),e.getMessage());
        }

    }

    private Feature setStatus(Features feature, FeatureStatus status){
        FeatureRepo featureRepo = MigrationContext.getFeatureRepo();
        Optional<Feature> byName = featureRepo.findByName(feature.name());
        Feature f;
        if(byName.isEmpty()) {
            f = new Feature(feature.name(), FeatureStage.GA, status);
        } else {
            f = byName.get();
            f.setStatus(status);
        }
        return featureRepo.save(f);
    }

    public void provision(String syncariId, String usrName, String password) {
        SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        EncryptionService encryptionService = MigrationContext.getEncryptionService();
        ConnectorService connectorService = MigrationContext.getConnectorService();
        FeatureService featureService = MigrationContext.getFeatureService();

        if(StringUtils.isBlank(syncariId)) {
            throw new SyncariValidationException(I18n.i18n("schema_required"));
        }
        String newSchema = getSyncariSchema(syncariId);
        String userName = generateUsername(newSchema);
        String pwd = generatePassword();
        log.info("Storing read only user creds for {}", newSchema);
        Resource resource = new Resource(ResourceType.DATASTORE);
        resource.getConfiguration().put(DatastoreService.DATASTORE_USER_NAME, userName);
        resource.getConfiguration().put(DatastoreService.DATASTORE_PASSWORD, encryptionService.encrypt(pwd));
        resource.getConfiguration().put(Constants.DATABASE_NAME, newSchema);
        subscriptionService.addResource(syncariId, resource);
        // Refresh the instance set in context
        SyncariContext.setInstance(subscriptionService.getInstance(SyncariContext.getSyncariId()));
        Connector c = datastoreService.createOrGetSyncariDSConnector(syncariId);
        Optional<Connector> connector = connectorService.find(c.getId(), false);
        connector.get().getAuthConfig().setUserName(usrName);
        connector.get().getAuthConfig().setPassword(password);
        connectorService.save(connector.get());
        log.info("Created Syncari Synapse for {}", newSchema);
        ConnectorInfo info = datastoreService.toConnectorInfo(Optional.of(connector.get()));
        try{
            provision(info, userName, pwd);
            log.info("Created datastore schema for {}", newSchema);
            instantiateSchema(newSchema, newSchema, connector.get());
            log.info("Syncari datastore provisioned for {}", newSchema);
        }catch (Exception e){
            log.info("Provisioning datastore for instance with syncariId {} failed with error message {}", SyncariContext.getSyncariId(),e.getMessage());
            featureService.disableFeature(Features.Datastore);
            featureService.disableFeature(Features.Insights);
        }
        createdDatasetIndexes(newSchema, newSchema,info);
        log.info("Syncari datasets index created for {}", newSchema);
    }

    private void switchToDefaultDb(ConnectorInfo connector) {
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "postgres");
    }

    private String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }

    public void provision(ConnectorInfo connector, String userName, String pwd) {
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        switchToDefaultDb(connector);
        String schema = getValue(connector, DatabaseService.SCHEMA_NAME);
        String customerDbName = schema;
        SyncariDatastoreService postgresqlDatastoreService = ((SyncariDatastoreService)datastoreService.getService(connector));
        try (Connection conn1 = getConnection(connector)) {
            try (Statement stmt = conn1.createStatement()) {
                // Create db
                postgresqlDatastoreService.createDB(stmt, customerDbName);
            }
        } catch (Exception e) {
            postgresqlDatastoreService.handleException(e, connector);
        } finally {
            connector.getMetaConfig().put(Constants.DATABASE_NAME, customerDbName);
        }

        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql;

                // Create read only group
                String groupName = postgresqlDatastoreService.createGroup(stmt, schema);
                postgresqlDatastoreService.createSchema(stmt, connector);

                // Create user
                postgresqlDatastoreService.createUser(stmt, userName, pwd);

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
                postgresqlDatastoreService.revokeCreatePrivilege(stmt, schema, groupName);
            }
        } catch (Exception e) {
            postgresqlDatastoreService.handleException(e, connector);
        }
    }



    protected String createFileIfAbsent(String fileName, String content){
        File file =new File("/tmp/"+fileName);
        try {
            FileUtils.write(file,content, Charset.forName("utf8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file.getAbsolutePath();
    }

    protected Connection getConnection(ConnectorInfo connector) throws ClassNotFoundException, SQLException {
        log.info("JDBC URL being used is {}", getJdbcURL(connector));
        HikariDataSource dataSource = poolCache.getUnchecked(new ConnectionInfoWrapper(connector));
        return ConnectorHelper.withBackoff(() -> dataSource.getConnection());
    }

    private String getSyncariSchema(String syncariId){
        return "syncari_"+syncariId.toLowerCase();
    }

    private String generateUsername(String schema) {
        return "syncari_ds_" + schema.toLowerCase();
    }

    private String generatePassword() {
        // Should contain atleast 1 number, 1 uppercase, 1 lowercase and 8 min characters
        return StringUtils.capitalize(RandomStringUtils.randomAlphabetic(8).toLowerCase()) + RandomStringUtils.randomNumeric(2);
    }

    private void instantiateSchema(String schema, String dbName, Connector c) {
        SchemaService schemaService = MigrationContext.getSchemaService();
        ConnectorService connectorService = MigrationContext.getConnectorService();
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        log.info("Calling instantiateSchema for {}", schema);
        c.getMetaConfig().put(RedshiftService.DATABASE_NAME, dbName);
        c.getMetaConfig().put(RedshiftService.SCHEMA_NAME, schema);
        List<EntityDefinition> syncariSchema = schemaService.getEntities(connectorService.getSyncariConnector().getId());
        syncariSchema.forEach(e -> {
            datastoreService.doCreate(e, c);
        });
    }

    private void createdDatasetIndexes(String schema, String dbName,ConnectorInfo info) {
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        log.info("Calling createdDatasetIndexes for creating all static indexes for datasets for schema : {} and db {}", schema, dbName);
        Map<String, String> indexes = InsightsDBStaticIndexes.indexes;
        indexes.entrySet().forEach(indexEntry -> {
            String indexTobeCreated = String.format(indexEntry.getValue(), dbName);;
            log.info("Index to be created is {}", indexTobeCreated);
            try{
                datastoreService.getService(info).executeDdlSql(info, indexTobeCreated);
            }catch (Exception exception){
                log.error("Exception occurred for index {} creation for schema {} with message {}", indexTobeCreated, schema, ExceptionUtils.getStackTrace(exception));
            }
        });
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
}
