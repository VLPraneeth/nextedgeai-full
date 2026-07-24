package com.syncari.connector.database;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.query.SqlQueries;
import com.syncari.connector.ssh.TunnelableService;
import com.syncari.connector.ssh.ConnectionEndpoint;
import com.syncari.connector.ssh.UniversalSshTunnelManager;
import com.syncari.utils.DateUtil;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.sql.Date;
import java.sql.*;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.MYSQL)
public class MysqlService extends DatabaseService implements SynapseInfoService, TunnelableService {
    private static final String JDBC_URL = "jdbc:mysql://%s/%s?autoReconnect=true&serverTimezone=%s&forceConnectionTimeZoneToSession=true&connectionTimeZone=%s&zeroDateTimeBehavior=CONVERT_TO_NULL";
    private static final String JDBC_URL_TEST_CONNECTION = "jdbc:mysql://%s/%s?autoReconnect=true&zeroDateTimeBehavior=CONVERT_TO_NULL";
    private static final String dateFormat = "yyyy-MM-dd HH:mm:ss Z";
    @Autowired
    DateUtil dateUtil;

    @Autowired
    private UniversalSshTunnelManager tunnelManager;



    protected String getJdbcURL(ConnectorInfo connector) {
        ConnectionEndpoint endpoint = getConnectionEndpoint(connector);
        if (endpoint.isTunneled()) {
            log.info("MySQL connection will use SSH tunnel: {}", endpoint);
        }
        String useSsl = connector.getAuthConfig().getHeader("useSsl");
        String sslStr = "";
        if(!StringUtils.isBlank(useSsl) && BooleanUtils.toBoolean(useSsl)) {
            // https://dev.mysql.com/doc/connector-j/5.1/en/connector-j-reference-using-ssl.html
            // Server authentication via server certificate verification is enabled when the
            // connection property verifyServerCertificate is true (which is the
            // default setting when useSSL=true).
            sslStr = "&useSSL=true&requireSSL=true&verifyServerCertificate=false";
        }
        String zoneId = getZoneId(connector);

        String jdbcURL = StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL, endpoint.toHostPort(), getValue(connector, Constants.DATABASE_NAME),zoneId,zoneId)
                : connector.getEndpoint();
        return jdbcURL + sslStr;
    }

    protected String getJdbcURLForTestConnection(ConnectorInfo connector) {
        ConnectionEndpoint endpoint = getConnectionEndpoint(connector);
        String useSsl = connector.getAuthConfig().getHeader("useSsl");
        String sslStr = (!StringUtils.isBlank(useSsl) && BooleanUtils.toBoolean(useSsl)) ? "&useSSL=true&requireSSL=true&verifyServerCertificate=false" : "";
        String jdbcURL = StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL_TEST_CONNECTION, endpoint.toHostPort(), getValue(connector, Constants.DATABASE_NAME))
                : connector.getEndpoint();
        return jdbcURL + sslStr;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            // just see if we can authenticate without any describe call
            getAllEntitySchema(config);
        } catch (Exception ex) {
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(ex.getMessage());
        }
        return response;
    }

    private List<EntitySchema> getAllEntitySchema(ConnectorInfo connector){
        List<EntitySchema> schema = new ArrayList<>();
        try (Connection conn = getConnectionForTest(connector)) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(getDescribeSql(connector));
                while (rs.next()) {
                    String entityName = rs.getString("TABLE_NAME");
                    String entityType = rs.getString("TABLE_TYPE");
                    EntitySchema e = new EntitySchema(entityName, entityName);
                    if("view".equalsIgnoreCase(entityType)) {
                        e.setReadOnly(true);
                    }
                    schema.add(e);
                }
                rs.close();
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
        return schema;
    }

    protected Connection getConnectionForTest(ConnectorInfo connector) throws ClassNotFoundException, SQLException {
        HikariDataSource dataSource = testConnectionPoolCache.getUnchecked(new ConnectionInfoWrapper(connector));
        return ConnectorHelper.withBackoff(() -> dataSource.getConnection());
    }

    private LoadingCache<ConnectionInfoWrapper, HikariDataSource> testConnectionPoolCache = CacheBuilder.newBuilder()
            .maximumSize(100000)
            .expireAfterAccess(20, java.util.concurrent.TimeUnit.MINUTES)
            .removalListener(notification -> {
                if (notification.getValue() != null) {
                    HikariDataSource ds = (HikariDataSource) notification.getValue();
                    try {
                        ds.close();
                        log.info("Test connection pool closed successfully");
                    } catch (Exception e) {
                        log.warn("Error closing test connection pool: {}", e.getMessage());
                    }
                }
            })
            .build(new CacheLoader<>() {
                @Override
                public HikariDataSource load(ConnectionInfoWrapper connectorWrapper) {
                    ConnectorInfo connector = connectorWrapper.getConnectionInfo();
                    HikariDataSource ds = new HikariDataSource();
                    int size = (int) connector.getMetaConfig().getOrDefault(POOL_SIZE, 2);
                    ds.setMaximumPoolSize(size);
                    ds.setPoolName("database-pool-" + connector.getId());
                    ds.setValidationTimeout(VALIDATION_TIMEOUT);
                    ds.setMaxLifetime(MAX_LIFETIME);
                    ds.setInitializationFailTimeout(getInitializationFailTimeout());
                    ds.setConnectionTimeout((int) connector.getMetaConfig().getOrDefault(CONNECTION_TIMEOUT_PARAM, CONNECTION_TIMEOUT));
                    ConnectionEndpoint endpoint = getConnectionEndpoint(connector);
                    if (endpoint.isTunneled()) {
                        log.info("MySQL testConnection using SSH tunnel: {}", endpoint);
                    }
                    String urlStr = getJdbcURLForTestConnection(connector);
                    log.info("MySQL testConnection using URL: {}", urlStr);
                    Properties props = new Properties();
                    Optional<Properties> additionalProperties = getAdditionalProperties(connector);
                    additionalProperties.ifPresent(props::putAll);
                    ds.setDataSourceProperties(props);
                    ds.setJdbcUrl(urlStr);
                    ds.setUsername(connector.getAuthConfig().getUserName());
                    ds.setPassword(connector.getAuthConfig().getPassword());
                    ds.setMinimumIdle(0);
                    ds.setIdleTimeout(IDLE_TIMEOUT);
                    return ds;
                };
            });

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        AuthMetadata useSsl = ConnectorHelper.getUserPwd();
        useSsl.getFields().add(new AuthField().setRequired(false).setDataType("boolean").setLabel("Use SSL Connection")
                .setName("useSsl").setHelpSummary(i18n("mysql_ssl_help")));
        return List.of(useSsl);
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField bootstrappable = new AuthField().setRequired(false).setDataType("checkbox").setName("bootstrapable")
                .setLabel("Instantiate with NextEdge entities");
        AuthField cluster = new AuthField().setRequired(true).setDataType("text").setName(Constants.CLUSTER_NAME)
                .setLabel("Host");
        AuthField dbName = new AuthField().setRequired(true).setDataType("text").setName(Constants.DATABASE_NAME)
                .setLabel("Database Name");

        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("mysql_timezone_label"));
        timeZone.setHelpSummary(i18n("db_timezone_help"));

        List<AuthField> fields = List.of(cluster, dbName, bootstrappable, timeZone, ConnectorHelper.getSupportedAuthPicker());

        // Add reusable SSH fields
        return addSshConfigurationFields(new ArrayList<>(fields));
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public String getName() {
        return Constants.MYSQL;
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/mysql.svg")
                .setDisplayName("Mysql")
                .setBackgroundColor("#F5FDFF")
                .setHelpUrl(helpArticlesBaseUrl + "/360060573291");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200915191956";
    }

    @Override
    String getDescribeSql(ConnectorInfo connector) {
        String sql = String.format(SqlQueries.DESCRIBE_ENTITY, getValue(connector, Constants.DATABASE_NAME));
        log.debug(sql);
        return sql;
    }

    @Override
    String getDescribeFieldSqlForLateBindingViews(ConnectorInfo connectorInfo) {
        return "";
    }

    @Override
    String getDescribeFieldSql(ConnectorInfo connector, String tableName) {
        return String.format(SqlQueries.DESCRIBE_FIELD, tableName, getValue(connector, Constants.DATABASE_NAME));
    }

    @Override
    String getSelectByIdsSql() {
        return SqlQueries.SELECT_BY_IDS;
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String clusterName = getValue(connector, Constants.CLUSTER_NAME);
        if (StringUtils.isBlank(clusterName)) {
            throw new RuntimeException("cluster_name_required");
        }
        String dbName = getValue(connector, Constants.DATABASE_NAME);
        if (StringUtils.isBlank(dbName)) {
            throw new RuntimeException("db_name_required");
        }
        String cert =getValue(connector,"cert");
        if(!cert.isBlank()) {
            validateCertificate(cert);
        }

        String zoneId = connector.getMetaConfig().getOrDefault(TIME_ZONE_ID, "").toString();
        try {
            ZoneId z = ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new RuntimeException(i18n("db_invalid_timezone_id"));
        }
        return true;
    }

    protected void validateCertificate(String cert) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            cf.generateCertificate(new ByteArrayInputStream(cert.strip().getBytes("utf-8")));
        } catch (Exception e) {
            throw new RuntimeException("The certificate is not valid. Make sure that the lines with 'BEGIN CERTIFICATE' and 'END CERTIFICATE' are included");
        }
    }

    @Override
    String getEscapeChar() {
        return "`";
    }

    @Override
    boolean isUpperCase() {
        return false;
    }
    
    @Override
    protected String getCased(String name) {
        return StringUtils.isBlank(name) ? name : name;
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    String getSchemaName(ConnectorInfo connector) {
        return "";
    }

    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }

    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        Properties properties = new Properties();
        properties.put("enabledTLSProtocols", "TLSv1,TLSv1.1,TLSv1.2");
        return Optional.of(properties);
    }
    
    @Override
    protected int getNullObjectType() {
        return Types.OTHER;
    }

    protected void postProcessCreate(Connection conn, PreparedStatement stmt, List<EntityData> data, SyncResponse response) {

        List<Long> generatedIds = new ArrayList<>();
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            while (rs.next()) {
                generatedIds.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            handleException(e, null);
        }
        int recordIndex=0;
        List<Result> results = Lists.newArrayList();
        for(EntityData ed: data){
            String lastId = recordIndex < generatedIds.size()?String.valueOf(generatedIds.get(recordIndex)) : ed.getId();
            results.add(new Result(true, lastId, ed.getSyncariEntityId()));
            recordIndex++;
        }
        response.getResults().addAll(results);
    }

    protected PreparedStatement getStmt(Connection conn, String sql, String idField) throws SQLException {
        return conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }


    @Override
    protected Object computedValue(AttributeSchema attr, Object columnValue) {
        if(columnValue != null && "timestamp".equalsIgnoreCase(attr.getDataType())) {
            if(columnValue instanceof Timestamp) {
                return columnValue;
            }
            return new Timestamp(((Instant)columnValue).toEpochMilli());
        }
        if(columnValue != null && "datetime".equalsIgnoreCase(attr.getDataType())) {
            if(columnValue instanceof Timestamp) {
                return columnValue;
            }
            return new Timestamp(((ZonedDateTime)columnValue).toInstant().toEpochMilli());
        }
        if(columnValue != null && "date".equalsIgnoreCase(attr.getDataType())) {
            return new Date(((java.util.Date)columnValue).toInstant().toEpochMilli());
        }
        // PostgreSQL doesn't support storing NULL (\0x00) characters in text fields
        if(columnValue != null && "string".equalsIgnoreCase(getSyncariDatatype(attr.getDataType()))) {
            return columnValue.toString().replaceAll("\u0000", "");
        }
        return columnValue;
    }
    
    
    @Override
    String getWatermarkCondition(SyncRequest request, long offset, int pageSize) {
        validateIdWMFields(request);
        String watermarkField = getCased(request.getEntitySchema().getWatermarkField().getApiName());
        String idField = getCased(request.getEntitySchema().getIdField().getApiName());
        String dataType = request.getEntitySchema().getWatermarkField().getDataType();
        String escape = "int".equalsIgnoreCase(dataType) || "integer".equalsIgnoreCase(dataType) ? "" : "'";
        //order by must produce unique ordering for OFFSET clause to work. So we add id as secondary ordering column
        String query = "%s >= "+escape+"%s"+escape+" AND %s < "+escape+"%s"+escape+" ORDER BY %s,%s LIMIT %s OFFSET %s";
        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        if("timestamp".equalsIgnoreCase(dataType) ||
                "datetime".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "date".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType())) {
            String startTimestamp = dateUtil.format(start, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
            String endTimestamp = dateUtil.format(end, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
            query = String.format(query, watermarkField, startTimestamp, watermarkField, endTimestamp, watermarkField,idField,pageSize, offset);
        } else {
            query = String.format(query, watermarkField, start, watermarkField, end, watermarkField,idField, pageSize, offset);
        }
        return query;
    }
    
    @Override
    protected void alterLength(ConnectorInfo connector, String entity, AttributeSchema field, Integer newLength) {
        if (field == null || field.getDataType() == null || newLength <= field.getLength()) {
            return;
        }
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.ALTER_LENGTH, getTableName(entity, connector), getDecoratedFieldName(field.getApiName()), 
                    getFieldLength(newLength));
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    @Override
    String getDatatype(AttributeSchema from) {
        if (Constants.SYNCARI_ID.equalsIgnoreCase(from.getApiName())){
            return "VARCHAR(64)";
        }
        switch (from.getDataType()) {
        case "boolean":
        case "checkbox":
        case "bool":
            return "BOOLEAN";
        case "double":
        case "float":
        case "number":
            int precision = from.getPrecision();
            int scale = from.getScale();
            String suffix = (precision == 0 && scale == 0) ? "" : "(" + precision + "," + scale + ")";
            return "NUMERIC " + suffix;
        case "datetime":
            return "DATETIME";
        case "timestamp":
            return "TIMESTAMP";
        case "tinyint":
        case "int":
        case "integer":
            return "INT";
        case "date":
            return "DATE";
            case "textarea":
            return "TEXT";
        default:
            //multivalued fields will have max length
            //Also for MySql if the length is 0 or > 128, create TEXT instead of varchar because text occupies less space
            //MySql has max row length of 65355 bytes in CREATE TABLE DDL.
            return from.isMultiValueField()? "VARCHAR(65535)": (from.getLength() > 128 || from.getLength()==0) ? "TEXT" : String.format("VARCHAR(%s)", from.getLength());
        }
    }

    String getFieldStr(AttributeSchema a) {
        // field_name datatype NOT NULL,
        String template = getEscapeChar()+"%s" +getEscapeChar()+" %s %s %s";
        String nullClause = "";
        String uniqueClause = "";
        if (Constants.SYNCARI_ID.equalsIgnoreCase(a.getApiName())) {
            nullClause = " NOT NULL ";
            if (a.isUnique()) {
                uniqueClause = " UNIQUE ";
            }
        }
        return String.format(template, trimAttributeLength(getCased(a.getApiName())), getDatatype(a), nullClause, uniqueClause);
    }

    private String trimAttributeLength(String cased) {
        return cased.length() > 64 ? cased.substring(0,63) : cased;
    }

    @Override
    protected String getTableName(String entity, ConnectorInfo connector) {
        return getDecoratedFieldName(entity);
    }

    // TunnelableService implementation
    @Override
    public UniversalSshTunnelManager getTunnelManager() {
        return tunnelManager;
    }


    @Override
    public String getDirectHost(ConnectorInfo connector) {
        return getValue(connector, Constants.CLUSTER_NAME);
    }

    @Override
    public int getDirectPort(ConnectorInfo connector) {
        return 3306; // MySQL default port
    }


}
