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
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class DropInsightsViews {


    private static final int CONNECTION_TIMEOUT = 30000; // 5 mins, connection timeout should be greater than validation timeout
    private static final int VALIDATION_TIMEOUT = 30000;
    private static final int MAX_LIFETIME = 300000; // 5 mins
    protected static final int SOCKET_TIMEOUT = 300; // only used for Postgres SQL right now, in seconds
    private static final int IDLE_TIMEOUT = 300000;
    private static final String STRING = "string";
    public static final String COMMA = ",";
    static final int QUERY_SIZE = 1000;
    public static final String SCHEMA_NAME = "schemaName";
    public static final String POOL_SIZE = "poolSize";
    public static final String CONNECTION_TIMEOUT_PARAM = "connectionTimeout";
    public static final String SOCKET_TIMEOUT_PARAM = "socketTimeout";
    public static final String TIME_ZONE_ID = "timeZoneId";
    private final int QUERY_TIMEOUT = 30;
    // This is a postgres specific limit, but might make sense to limit all variables to less than this
    private static final String JDBC_URL = "jdbc:postgresql://%s/%s?OpenSourceSubProtocolOverride=true&socketTimeout=%s";

    protected static final String CASE_CONFIGURATION = "caseConfiguration";
    protected static final String SSL_FACTORY = "&sslmode=%s&sslfactory=org.postgresql.ssl.LibPQFactory";
    public static final String DELETE_VIEW = "DROP VIEW %s.\"%s\"";



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
        String clusterName = "35.230.89.186"; // non prod
        log.info("ClusterName to be used is local from this file :: {}", clusterName);
        //getValue(connector, Constants.CLUSTER_NAME)
        String jdbcURL = StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL, clusterName, getValue(connector, Constants.DATABASE_NAME),
                connector.getMetaConfig().getOrDefault(SOCKET_TIMEOUT_PARAM, SOCKET_TIMEOUT))
                : clusterName;
        if(!StringUtils.isBlank(serverCert)) {
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
        }
        return jdbcURL;
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

    @ChangeSet(order = "001", id = "deleteDataStoreViews", author = "rohit", runAlways = true)
    public void deleteDataStoreViews(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var datastoreService = MigrationContext.getDatastoreService();
        var featureService = MigrationContext.getFeatureService();
        var syncariId = MigrationContext.getSyncariId();
        if(featureService.isEnabled(Features.Datastore)) {
            log.info("Datastore enabled");
            if(!dryRun) {
                log.info("Deleting views from script");
                this.deleteDatasetViews(syncariId);
            }else{
                log.info("Datastore is enabled but running in drymode so not deprovisioning");
            }
        }
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

    public void deleteDatasetViews(String syncariId) {
        ConnectorService connectorService = MigrationContext.getConnectorService();
        DatasetRepo datasetRepo = MigrationContext.getDatasetRepo();
        String schema = getSyncariSchema(syncariId);
        log.info("Deleting all dataset views for schema : {} and db {} in dbscript", schema, schema);
        List<Dataset> datasets = datasetRepo.findAllWithoutVersion();
        ConnectorInfo info = toConnectorInfo(Optional.of(connectorService.getSyncariDatastore().get()));
        datasets.forEach(ds -> {
            String deleteViewQuery =  String.format(DELETE_VIEW, schema, ds.getName());
            try{
                executeDdlQuery(info, deleteViewQuery);
            }catch (Exception exception){
                log.error("Exception occurred while deleting ds view {} for schema {} with message in script {}", deleteViewQuery, schema, ExceptionUtils.getStackTrace(exception));
            }
        });
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

    public void executeDdlQuery(ConnectorInfo connector, String sql){
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                log.info("SQL to be executed from script is {}" , sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            log.error("Exception occurred in script");
        }
    }

    private String getSyncariSchema(String syncariId){
        return "syncari_"+syncariId.toLowerCase();
    }

}
