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
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.SchemaService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.sql.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j
public class SYN_16351_FindDataStoreDiffWithMongo {
    private static final int CONNECTION_TIMEOUT = 30000; // 5 mins, connection timeout should be greater than validation timeout
    private static final int VALIDATION_TIMEOUT = 30000;
    private static final int MAX_LIFETIME = 300000; // 5 mins
    protected static final int SOCKET_TIMEOUT = 300; // only used for Postgres SQL right now, in seconds
    private static final int IDLE_TIMEOUT = 300000;
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
    public static String COUNT_QUERY = "Select count(syncariid) as totalCount from %s";
    public static String ID_QUERY = "SELECT syncariid AS syncariid FROM %s where syncariid in (%s)";
    public static String DELETE_QUERY = "DELETE FROM %s where syncariid in (%s)";
    private static final int PAGE_SIZE = 500;


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

    @ChangeSet(order = "001", id = "findDataStoreDiffWithMongo", author = "sibin", runAlways = true)
    public void findDataStoreDiffWithMongo(MongoTemplate template) {
        SchemaService schemaService = MigrationContext.getSchemaService();
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        Connector datastore = datastoreService.findActiveDatastore()
                .orElseThrow(() -> new RuntimeException("Datastore connector missing"));
        ConnectorInfo info = toConnectorInfo(Optional.of(datastore));
        EntityRepo entityRepo = MigrationContext.getEntityRepo();
        List<String> entityNames = new ArrayList<>();
        String en = System.getProperty("entityName");
        if (StringUtils.isBlank(en)) {
            log.info("No entity name provided. Loading all entities of the instance");
            Schema schema = schemaService.getSyncariSchema(true);
            entityNames = schema.getEntities().stream().map(e -> e.getApiName()).collect(Collectors.toList());
        } else {
            entityNames.add(en);
        }
        log.info("Entities to be processed {}", entityNames);
        for (String entityName : entityNames) {
            EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(entityName).orElse(null);
            if (syncariEntity == null) {
                log.error("Syncari entity for apiName {} not found", entityName);
                continue;
            }
            var featureService = MigrationContext.getFeatureService();
            if (featureService.isEnabled(Features.Datastore)) {
                log.info("Datastore enabled");
                log.info("Finding stale records from syncari");
                long count = entityRepo.count(entityName, false);
                log.info("Total record count for {} in syncari is {}", entityName, count);
                int page = 0;
                List<String> idsToBeDeleted = new ArrayList<>();
                List<String> idsFromSyncari = List.of();
                do {
                    idsFromSyncari = entityRepo.findEntities(entityName, PageRequest.of(page, PAGE_SIZE)).getContent()
                            .stream().map(e -> e.getId()).collect(Collectors.toList());
                    List<String> idsFromDS = fetch(info, syncariEntity.getResolvedDataStoreName(), idsFromSyncari);
                    idsToBeDeleted.addAll(idsFromSyncari.stream().filter(element -> !idsFromDS.contains(element))
                            .collect(Collectors.toList()));
                    page++;

                } while (idsFromSyncari.size() >= PAGE_SIZE);
                log.info("Number of Ids to be deleted for entity {} is {}", entityName, idsToBeDeleted.size());
                log.info("Ids to be deleted for entity {} are {}", entityName, idsToBeDeleted);
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

    protected List<Map<String, Object>> executeDmlQuery(ConnectorInfo connector, String sql, Map<String, String> fieldNames) {
        List<Map<String, Object>> extractedVals = new ArrayList<>();
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                log.info("SQL to be executed is {} and timeout is {}", sql, QUERY_TIMEOUT);
                stmt.setQueryTimeout(QUERY_TIMEOUT);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        Map<String, Object> extractedVal = new HashMap<>();
                        Set<Entry<String, String>> fielNameAndDataType = fieldNames.entrySet();
                        fielNameAndDataType.forEach(x -> {
                            String fieldName = x.getKey();
                            String dataType = x.getValue();
                            Object val = extractVal(rs, dataType, fieldName);
                            if (null != val) {
                                extractedVal.put(fieldName, val);
                            }
                        });
                        extractedVals.add(extractedVal);
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return extractedVals;
    }

    protected Object extractVal(ResultSet rs, String dataType, String fieldName) {
        try {
            switch (dataType) {
                case "date":
                    return rs.getDate(fieldName);
                case "datetime": {
                    final Timestamp timestamp = rs.getTimestamp(fieldName);
                    return timestamp == null ? null : ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
                }
                case "timestamp": {
                    final Timestamp timestamp = rs.getTimestamp(fieldName);
                    return timestamp == null ? null : timestamp.toInstant();
                }
                case "string": {
                    return rs.getString(fieldName);
                }
                default:
                    return rs.getObject(fieldName);
            }
        } catch (SQLException e) {
            log.error("Exception occurred {} and cause is {}", e.getMessage(), e.getCause());
        }
        return null;
    }

    public long count(ConnectorInfo connectorInfo, String datastoreName) {
        String query = String.format(COUNT_QUERY, getTableName(datastoreName, connectorInfo));
        // totalCount is an alias in Count query
        List<Map<String, Object>> result = executeDmlQuery(connectorInfo, query, Map.of("totalCount", "long"));
        if (CollectionUtils.isNotEmpty(result)) {
            return (Long) result.stream().findFirst().get().get("totalCount");
        }
        return 0;
    }

    public List<String> fetch(ConnectorInfo connectorInfo, String datastoreName, List<String> idsToQuery) {
        if (idsToQuery.isEmpty()) {
            return List.of();
        }
        String query = String.format(ID_QUERY, getTableName(datastoreName, connectorInfo), idsToQuery.stream().map(id -> "'" + id + "'").collect(Collectors.joining(", ")));
        List<Map<String, Object>> result = executeDmlQuery(connectorInfo, query, Map.of("syncariid", "string"));
        List<String> ids = List.of();
        if (CollectionUtils.isNotEmpty(result)) {
            ids = result.stream().map(entry -> (String) entry.get("syncariid")).collect(Collectors.toList());
        }
        return ids;
    }

    public void delete(ConnectorInfo connectorInfo, String datastoreName, List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        String query = String.format(DELETE_QUERY, getTableName(datastoreName, connectorInfo), ids.stream().map(s -> {
            return "'" + s + "'";
        }).collect(Collectors.joining(", ")));
        try (Connection conn = getConnection(connectorInfo)) {
            try (Statement stmt = conn.createStatement()) {
                log.info("SQL to be executed is {} and timeout is {}", query, QUERY_TIMEOUT);
                stmt.setQueryTimeout(QUERY_TIMEOUT);
                int count = stmt.executeUpdate(query);
                log.info("{} records removed from datastore for ", count);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected String getTableName(String entity, ConnectorInfo connector) {
        StringBuilder table = new StringBuilder(getEscapeChar());
        table.append(getCased(entity)).append(getEscapeChar());
        return !StringUtils.isBlank(getSchemaName(connector)) ? getSchemaName(connector) + "." + table : table.toString();
    }

    protected String getEscapeChar() {
        return "\"";
    }

    protected String getCased(String name) {
        return StringUtils.isBlank(name) ? name : name.toLowerCase();
    }

    protected String getSchemaName(ConnectorInfo connector) {
        return getValue(connector, SCHEMA_NAME);
    }

}
