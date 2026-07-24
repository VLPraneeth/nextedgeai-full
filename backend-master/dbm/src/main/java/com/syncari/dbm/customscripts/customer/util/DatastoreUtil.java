package com.syncari.dbm.customscripts.customer.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.database.DatabaseService;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.service.ConnectorMetadataService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class DatastoreUtil {
    private static final int CONNECTION_TIMEOUT = 30000; // 5 mins, connection timeout should be greater than validation timeout
    private static final int VALIDATION_TIMEOUT = 30000;
    private static final int MAX_LIFETIME = 300000; // 5 mins
    protected static final int SOCKET_TIMEOUT = 300; // only used for Postgres SQL right now, in seconds
    private static final int IDLE_TIMEOUT = 300000;
    public static final String CONNECTION_TIMEOUT_PARAM = "connectionTimeout";
    public static final String SOCKET_TIMEOUT_PARAM = "socketTimeout";
    private static final String JDBC_URL = "jdbc:postgresql://%s/%s?OpenSourceSubProtocolOverride=true&socketTimeout=%s";
    protected static final String SSL_FACTORY = "&sslmode=%s&sslfactory=org.postgresql.ssl.LibPQFactory";


    protected static void loadDriver(String driverClass) {
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {

        }
    }

    public LoadingCache<ConnectionInfoWrapper, HikariDataSource> poolCache = CacheBuilder.newBuilder()
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

    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }

    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        return Optional.empty();
    }

    public String getJdbcURL(ConnectorInfo connector) {
        String serverCert = connector.getAuthConfig().getHeader("cert");
        String clientCert = connector.getAuthConfig().getHeader("clientCert");
        String clientKey = connector.getAuthConfig().getHeader("clientKey");
        String verifyMode = Boolean.valueOf(connector.getAuthConfig().getHeader("verifyCert")) ? "verify-full" : "verify-ca";
        //String clusterName = "35.197.29.117";
        //String clusterName = "35.230.89.186"; // non prod
        //String clusterName = getValue(connector, Constants.CLUSTER_NAME);
        String clusterName = System.getProperty("clusterName");
        if(StringUtils.isBlank(clusterName)) {
            clusterName = getValue(connector, Constants.CLUSTER_NAME);
            log.info("No clusterName provided hence taking from connector {}", clusterName);
        }
        log.info("ClusterName to be used is local from this file :: {}", clusterName);
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

    public Connection getConnection(ConnectorInfo connector) throws ClassNotFoundException, SQLException {
        log.info("JDBC URL being used is {}", getJdbcURL(connector));
        HikariDataSource dataSource = poolCache.getUnchecked(new ConnectionInfoWrapper(connector));
        return ConnectorHelper.withBackoff(() -> dataSource.getConnection());
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
