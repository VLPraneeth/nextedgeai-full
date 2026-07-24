package com.syncari.connector.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.query.SqlQueries;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import net.snowflake.client.jdbc.internal.org.bouncycastle.util.io.pem.PemObject;
import net.snowflake.client.jdbc.internal.org.bouncycastle.util.io.pem.PemReader;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.jooq.lambda.function.Function3;
import org.postgresql.jdbc.PgResultSetMetaData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.POSTGRESQL)
public class PostgresService extends DatabaseService implements SynapseInfoService {
    protected static final long POSTGRES_MAX_VARCHAR_LENGTH = 10485760;
    private static final String JDBC_URL = "jdbc:postgresql://%s/%s?OpenSourceSubProtocolOverride=true&socketTimeout=%s";
    protected static final String SSL_FACTORY = "&sslmode=%s&sslfactory=org.postgresql.ssl.LibPQFactory";
    protected static final String REPLICATION_SLOT = "replicationSlot";
    protected static final String REPLICATION_READER_PAGE_SIZE = "replicationReaderPageSize";
    protected static final String USE_CURSOR = "useCursor";
    private static final String dateFormat = "yyyy-MM-dd HH:mm:ss Z";
    protected static final DateTimeFormatter walTimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S][X]");

    public static final String PEEK_REPLICATION_SLOT_CHANGES = "SELECT data FROM pg_logical_slot_peek_changes(?, null, ?, " +
            "'format-version', '2', 'add-msg-prefixes', 'wal2json', 'include-timestamp', 'true', 'include-transaction', 'false', 'add-tables', ?)";
    public static final String GET_REPLICATION_SLOT_CHANGES = "SELECT data FROM pg_logical_slot_get_changes(?, null, ?, " +
            "'format-version', '2', 'add-msg-prefixes', 'wal2json', 'include-timestamp', 'true', 'include-transaction', 'false', 'add-tables', ?)";
    protected static String ALTER_TYPE_STMT = "ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s";

    // Regex patterns for type conversion validation
    // Matches integers (with optional leading/trailing whitespace, optional sign)
    private static final String BIGINT_REGEX = "^\\s*[-+]?\\d+\\s*$";
    // Matches decimal numbers (with optional leading/trailing whitespace, optional sign, optional decimal part)
    private static final String NUMERIC_REGEX = "^\\s*[-+]?(\\d+\\.?\\d*|\\d*\\.?\\d+)([eE][-+]?\\d+)?\\s*$";
    // Matches ISO date format (YYYY-MM-DD)
    private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";
    // Matches ISO timestamp formats
    private static final String TIMESTAMP_REGEX = "^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?$";
    // Matches boolean-like text values
    private static final String BOOLEAN_REGEX = "^\\s*(true|false|t|f|yes|no|y|n|1|0|on|off)\\s*$";
    // Basic JSON validation pattern (starts with { or [)
    private static final String JSON_REGEX = "^\\s*(\\{.*\\}|\\[.*\\])\\s*$";

    @Autowired
    DateUtil dateUtil;
    @Autowired
    ObjectMapper mapper;

    protected String createFileIfAbsent(String fileName, String content){
        File file =new File("/tmp/"+fileName);
        try {
            FileUtils.write(file,content, Charset.forName("utf8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file.getAbsolutePath();
    }

    protected String getJdbcURL(ConnectorInfo connector) {
        String serverCert = connector.getAuthConfig().getHeader("cert");
        String clientCert = connector.getAuthConfig().getHeader("clientCert");
        String clientKey = connector.getAuthConfig().getHeader("clientKey");
        String verifyMode = Boolean.valueOf(connector.getAuthConfig().getHeader("verifyCert")) ? "verify-full" : "verify-ca";
        String jdbcURL = StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL, getValue(connector, Constants.CLUSTER_NAME), getValue(connector, Constants.DATABASE_NAME),
                connector.getMetaConfig().getOrDefault(SOCKET_TIMEOUT_PARAM, SOCKET_TIMEOUT))
                : connector.getEndpoint();
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

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200953783316";
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        AuthMetadata userPwd = ConnectorHelper.getUserPwd();
        userPwd.getFields().add(new AuthField().setRequired(false).setDataType("textarea").setLabel("Server Certificate").setName("cert").setHelpSummary(i18n("postgresql_cert_help")));
        userPwd.getFields().add(new AuthField().setRequired(false).setDataType("textarea").setLabel("Client Certificate").setName("clientCert").setHelpSummary(i18n("postgresql_client_cert_help")));
        userPwd.getFields().add(new AuthField().setRequired(false).setDataType("textarea").setLabel("Client Key").setName("clientKey").setHelpSummary(i18n("postgresql_client_key_help")));
        userPwd.getFields().add(new AuthField().setRequired(false).setDataType("checkbox").setLabel("Verify Certificate & Domain").setName("verifyCert").setHelpSummary(i18n("postgresql_verify_cert_help")));
        return List.of(userPwd);
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField bootstrappable = new AuthField().setDataType("checkbox").setName("bootstrapable")
                .setLabel("Instantiate with NextEdge entities");
        AuthField caseConfiguration = new AuthField().setDataType("checkbox").setName("caseConfiguration")
                .setLabel("Use lower case when creating tables and columns");
        AuthField cluster = new AuthField().setRequired(true).setDataType("text").setName(Constants.CLUSTER_NAME)
                .setLabel("Cluster Name");
        AuthField schemaName = new AuthField().setRequired(true).setDataType("text").setName(SCHEMA_NAME)
                .setLabel("Schema Name");
        AuthField dbName = new AuthField().setRequired(true).setDataType("text").setName(Constants.DATABASE_NAME)
                .setLabel("Database Name");
        AuthField replicationSlotName = new AuthField().setRequired(false).setDataType("text").setName(REPLICATION_SLOT)
                .setLabel("Logical Replication Slot").setHelpSummary(i18n("postgres_replication_slot_summary"));
        AuthField useCursor = new AuthField().setRequired(false).setDataType("checkbox").setName(USE_CURSOR)
                .setLabel("Use Cursor Pagination").setHelpSummary(i18n("postgres_use_cursor_summary"));

        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("postgres_timezone_label"));
        timeZone.setHelpSummary(i18n("db_timezone_help"));
        timeZone.setRequired(true);

        return List.of(cluster, dbName, schemaName, bootstrappable, timeZone, ConnectorHelper.getSupportedAuthPicker(), replicationSlotName, useCursor,caseConfiguration);
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<>(super.getCapabilities());
        capabilities.add(Capability.noWatermark);
        capabilities.add(Capability.compositeId);
        return capabilities;
    }

    @Override
    public String getName() {
        return Constants.POSTGRESQL;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/postgres.svg")
                .setDisplayName("PostgreSQL")
                .setBackgroundColor("#EEF2F6")
                .setHelpUrl(helpArticlesBaseUrl + "/360056927451-PostgreSQL-Setup");
    }

    @Override
    String getDescribeSql(ConnectorInfo connector) {
        return String.format(SqlQueries.DESCRIBE_ENTITY, getSchemaName(connector));
    }

    @Override
    String getDescribeFieldSqlForLateBindingViews(ConnectorInfo connectorInfo) {
        return "";
    }

    @Override
    protected String getDescribeFieldSql(ConnectorInfo connector, String tableName) {
        return String.format(SqlQueries.DESCRIBE_FIELD, tableName, getSchemaName(connector));
    }

    @Override
    String getSelectByIdsSql() {
        return SqlQueries.SELECT_BY_IDS;
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String clusterName = getValue(connector, Constants.CLUSTER_NAME);
        if (StringUtils.isBlank(clusterName)) {
            throw new RuntimeException(i18n("cluster_name_required"));
        }
        String schemaName = getValue(connector, SCHEMA_NAME);
        if (StringUtils.isBlank(schemaName)) {
            throw new RuntimeException(i18n("schema_name_required"));
        }
        String dbName = getValue(connector, Constants.DATABASE_NAME);
        if (StringUtils.isBlank(dbName)) {
            throw new RuntimeException(i18n("db_name_required"));
        }
        String cert = getValue(connector, "cert");
        if (!cert.isBlank()) {
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
    protected String getEscapeChar() {
        return "\"";
    }

    @Override
    boolean isUpperCase() {
        return false;
    }

    @Override
    protected String getCased(String name) {
        return name;
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    protected String getSchemaName(ConnectorInfo connector) {
        return getValue(connector, SCHEMA_NAME);
    }

    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }

    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        return Optional.of(new Properties());
    }

    @Override
    protected int getNullObjectType() {
        return Types.OTHER;
    }

    @Override
    protected Object computedValue(AttributeSchema attr, Object columnValue) {
        try {
            if (columnValue != null && "timestamp".equalsIgnoreCase(attr.getDataType())) {
                if (columnValue instanceof Timestamp) {
                    return columnValue;
                }
                return Timestamp.from(((Instant) columnValue));
            }
            if (columnValue != null && "datetime".equalsIgnoreCase(attr.getDataType())) {
                if (columnValue instanceof Timestamp) {
                    return columnValue;
                }
                return new Date(((ZonedDateTime) columnValue).toInstant().toEpochMilli());
            }
            if (columnValue != null && "date".equalsIgnoreCase(attr.getDataType())) {
                return new Date(((java.util.Date) columnValue).toInstant().toEpochMilli());
            }
            // PostgreSQL doesn't support storing NULL (\0x00) characters in text fields
            if (columnValue != null && "string".equalsIgnoreCase(getSyncariDatatype(attr.getDataType()))) {
                return columnValue.toString().replaceAll("\u0000", "");
            }
            return columnValue;
        } catch (Exception e) {
            log.error("Error computing value for Attribute Name {} Attribute Datatype {} Type of column value {} Value {}",
                    attr.getApiName(), attr.getDataType(),
                    columnValue != null ? columnValue.getClass().getName() : "NULL",
                    columnValue != null ? columnValue.toString() : "",
                    e);
            throw e;
        }
    }


    @Override
    String getWatermarkCondition(SyncRequest request, long offset, int pageSize) {
        if (!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException(String.format(i18n("idfield_required"), getName()));
        }
        if (!request.getEntitySchema().hasWatermarkField()) {
            throw new RuntimeException(String.format(i18n("wmfield_required"), getName()));
        }
        String watermarkField = getCased(request.getEntitySchema().getWatermarkField().getApiName());
        String idField = getIdField(request.getEntitySchema());
        //order by must produce unique ordering for OFFSET clause to work. So we add id as secondary ordering column
        String query = "\"%s\" >= '%s' AND \"%s\" <= '%s' ORDER BY \"%s\",\"%s\" LIMIT %s OFFSET %s";
        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();

        if ("timestamp".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "datetime".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "date".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType())) {
            String startTimestamp = dateUtil.format(start, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
            String endTimestamp = dateUtil.format(end, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
            query = String.format(query, watermarkField, startTimestamp, watermarkField, endTimestamp, watermarkField, idField, pageSize, offset);
        } else {
            query = String.format(query, watermarkField, start, watermarkField, end, watermarkField, idField, pageSize, offset);
        }
        return query;
    }

    @Override
    String getCursorWatermarkCondition(SyncRequest request, String nextID, int pageSize) {
        String idField = getCased(request.getEntitySchema().getIdField().getApiName());
        if ("string".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType()) && StringUtils.isNotEmpty(nextID)) {
            nextID = String.format("'%s'", nextID);
        }
        if (CollectionUtils.isNotEmpty(request.getEntitySchema().getCompositeKeyFields())){
            throw new UnsupportedOperationException("Composite key is not supported for now with cursor based implementation.");
        }
        String query = "";
        if (StringUtils.isNotEmpty(nextID)) {
            query = "\"%s\" >= '%s' AND \"%s\" <= '%s' AND \"" + idField + "\" > " + nextID + " ORDER BY \"%s\" LIMIT %s ";
        } else {
            query = "\"%s\" >= '%s' AND \"%s\" <= '%s' ORDER BY \"%s\" LIMIT %s ";
        }
        String watermarkField = getCased(request.getEntitySchema().getWatermarkField().getApiName());
        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        if ("timestamp".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "datetime".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "date".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType())) {
            String startTimestamp = dateUtil.format(start, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
            String endTimestamp = dateUtil.format(end, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
            query = String.format(query, watermarkField, startTimestamp, watermarkField, endTimestamp, idField, pageSize);
        } else {
            query = String.format(query, watermarkField, start, watermarkField, end, idField, pageSize);
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
    protected void alterNumeric(ConnectorInfo connector, String entity, AttributeSchema field) {
        if (field == null || field.getDataType() == null) {
            return;
        }
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.ALTER_NUMERIC, getTableName(entity, connector),
                        getDecoratedFieldName(field.getApiName()));
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    @Override
    protected long getMaxVarcharLength() {
        return POSTGRES_MAX_VARCHAR_LENGTH;
    }

    @Override
    public String getDeleteFieldSQL(DeleteFieldRequest request) {
        return String.format(SqlQueries.DROP_COLUMN_IF_EXISTS,
                getTableName(request.getEntityName(), request.getConnector()), getDecoratedFieldName(request.getFieldName()));
    }

    @Override
    String getDatatype(AttributeSchema from) {
        switch (from.getDataType()) {
            case "int":
            case "integer":
                return "BIGINT";
            default:
                return super.getDatatype(from);
        }
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {

        if (request.getEntitySchema() == null)
            throw new RuntimeException("Schema cannot be null");

        var entitySchema = request.getEntitySchema();
        var isResync = request.getWatermark().isResync();
        if (!entitySchema.hasWatermarkField() && supportsNoWatermark(request.getConnector())) {
            return isResync ? getBySortedKeys(request) : emptyIterator(request);
        } else if (isCursorBased(request.getConnector())) {
            return super.getByCursorBasedWatermark(request);
        } else {
            return super.getByWatermark(request);
        }
    }

    private FetchResponse emptyIterator(SyncRequest request) {
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            return Pair.of(0L, Stream.empty());
        };

        int pageSize = request.getPageSize() == 0 ? 100 : Math.min(request.getPageSize(), 100);

        WatermarkInfo watermark = new WatermarkInfo();
        DefaultDataIterator iterator = new DBIterator(watermark, watermark.getOffset(), generator,
                new ArrayList<>(), null, pageSize, request.getWatermark().getLimit(), DBIterator.DEFAULT_ZONE, "datetime");

        return new FetchResponse(request.getWatermark(), iterator);

    }

    @Override
    public boolean supportsNoWatermark(ConnectorInfo connectorInfo) {
        return getCapabilities().contains(Capability.noWatermark) && !StringUtils.isBlank(getSlotName(connectorInfo));
    }

    private String getSlotName(ConnectorInfo connectorInfo) {
        return getValue(connectorInfo, REPLICATION_SLOT);
    }

    private boolean isCursorBased(ConnectorInfo connectorInfo) {
        String useCursor = getValue(connectorInfo, USE_CURSOR);
        return StringUtils.isNotEmpty(useCursor) && (Boolean.parseBoolean(useCursor));
    }

    /*
     Get changes from WAL if watermark not enabled for this entity. Note this method DOES NOT return records specific to the entity
     */
    public Pair<Integer, List<EventData>> getByWAL(ConnectorInfo connector, Map<String, EntitySchema> entityMap, int pageSize) {
        String slotName = getSlotName(connector);
        var filterTables = tableToFilter(connector, entityMap);
        log.debug("Filter tables {} while reading from Replication log", filterTables);

        pageSize = getSlotReaderPageSize(connector, pageSize);

        // change this into a capability
        Pair<Long, Stream<EventData>> response = null;
        try (Connection conn = getConnection(connector)) {
            try (PreparedStatement stmt = conn.prepareStatement(PEEK_REPLICATION_SLOT_CHANGES)) {
                stmt.setString(1, slotName);
                stmt.setInt(2, pageSize);
                stmt.setString(3, tableToFilter(connector, entityMap));
                ResultSet rs = stmt.executeQuery();
                return doExtract(connector, entityMap, rs);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
        return Pair.of(0, List.of());
    }

    protected int getSlotReaderPageSize(ConnectorInfo connectorInfo, int defaultValue) {
        var value = getValue(connectorInfo, REPLICATION_READER_PAGE_SIZE);
        return !StringUtils.isBlank(value) ? Integer.parseInt(value) : defaultValue;
    }

    private String tableToFilter(ConnectorInfo connectorInfo, Map<String, EntitySchema> entityMap) {
        var schemaName = getValue(connectorInfo, SCHEMA_NAME);
        return entityMap.keySet().stream().map(entity -> String.format("%s.%s", schemaName, entity)).collect(Collectors.joining(","));
    }

    public void drainWAL(ConnectorInfo connector, Map<String, EntitySchema> entityMap, int pageSize) {
        String slotName = getSlotName(connector);
        var filterTables = tableToFilter(connector, entityMap);
        log.debug("Filter tables {} while reading from Replication log", filterTables);
        // change this into a capability
        Pair<Long, Stream<EventData>> response = null;
        try (Connection conn = getConnection(connector)) {
            try (PreparedStatement stmt = conn.prepareStatement(GET_REPLICATION_SLOT_CHANGES)) {
                stmt.setString(1, slotName);
                stmt.setInt(2, pageSize);
                stmt.setString(3, filterTables);
                stmt.executeQuery();
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }


    private Pair<Integer, List<EventData>> doExtract(ConnectorInfo connectorInfo, Map<String, EntitySchema> entityMap, ResultSet rs) throws SQLException, JsonProcessingException {

        List<EventData> eventsData = new ArrayList<>();
        int numEvents = 0;
        while(rs.next()) {
            // get data
            var data = rs.getString("data");
            Map<String, Object> changeData = mapper.readValue(data, new TypeReference<Map<String, Object>>(){});
            extractData(connectorInfo, entityMap, changeData).ifPresent(ed -> eventsData.add(ed));
            numEvents++;
        }
        return Pair.of(numEvents, eventsData);
    }

    private Optional<EventData> extractData(ConnectorInfo connectorInfo, Map<String, EntitySchema> entityMap, Map<String, Object> changeData) {

        Set<String> allowedOperations = Set.of("U", "I", "D");

        /*
            1. Check if allowed operation
            2. Check if schema is configured one
            3. Convert the table and values into entity data
            4. Add the operation
         */

        return  Optional.of(changeData)
                .filter(cd -> cd.containsKey("action") && allowedOperations.contains(cd.get("action")))
                .filter(cd -> cd.containsKey("schema") && cd.get("schema").equals(getSchemaName(connectorInfo)))
                .filter(cd -> cd.containsKey("table") && entityMap.containsKey(cd.get("table")))
                .map(cd -> {

                    var entitySchema = entityMap.get(cd.get("table"));

                    var action = (String) cd.get("action");
                    var entityData = new EntityData(entitySchema.getApiName());
                    entityData.setConnectorId(connectorInfo.getId());
                    entityData.setId(getIdValue(entitySchema, cd));
                    entityData.setLastModified(getLastModified(cd));
                    extractValues(entitySchema, entityData, cd);
                    var eventData = new EventData().setData(entityData).setConnectorId(connectorInfo.getId());

                    switch(action) {
                        case "I" :
                            entityData.setNew(true);
                            eventData.setOperation(Operation.create);
                            break;
                        case "U":
                            eventData.setOperation(Operation.update);
                            break;
                        case "D" :
                            entityData.setDeleted(true);
                            eventData.setOperation(Operation.delete);
                            break;
                    }
                    return eventData;
                });
    }

    private Long getLastModified(Map<String, Object> changeData) {
        return Optional.of(changeData.get("timestamp").toString()).map(t -> {
            return ZonedDateTime.parse(t, walTimestampFormat).toInstant().toEpochMilli();
        }).orElse(ZonedDateTime.now().toInstant().toEpochMilli());
    }

    private String getIdValue(EntitySchema entitySchema, Map<String, Object> changeData) {

        // get the field names
        if (entitySchema.hasIdField()) {
            var idField = entitySchema.getIdField();
            var fieldNames = StringUtils.isBlank(idField.getCompositeKey()) ? List.of(getCased(idField.getApiName()))
                    : entitySchema.getCompositeKeyFields().stream().map(f -> getCased(f.getApiName())).collect(Collectors.toList());

            Map<String, Object> fieldValues = ((List<Map<String, Object>>) changeData.getOrDefault("columns", new ArrayList<Map<String, Object>>()))
                    .stream().filter(kv -> kv.get("value") != null).collect(Collectors.toMap(kv -> getCased((String)kv.get("name")), kv -> kv.get("value")));

            Map<String, Object> identityValues = ((List<Map<String, Object>>) changeData.getOrDefault("identity", new ArrayList<Map<String, Object>>()))
                    .stream().filter(kv -> kv.get("value") != null).collect(Collectors.toMap(kv -> getCased((String)kv.get("name")), kv -> kv.get("value")));

            return fieldNames.stream()
                    .filter(f -> fieldValues.containsKey(f) || identityValues.containsKey(f))
                    .map(f -> fieldValues.get(f) != null ? fieldValues.get(f) : identityValues.get(f)).map(Object::toString)
                    .collect(Collectors.joining(EntitySchema.COMPOSITE_KEY_DELIMETER));
        } else {
            return null;
        }
    }

    private String getIdField(EntitySchema entitySchema) {
        // get the field names
        if (entitySchema.hasIdField()) {
            var idField = entitySchema.getIdField();
            var fieldNames = StringUtils.isBlank(idField.getCompositeKey()) ? List.of(getCased(idField.getApiName()))
                    : entitySchema.getCompositeKeyFields().stream().map(f -> getCased(f.getApiName())).collect(Collectors.toList());

            return StringUtils.join(fieldNames, "\",\"");
        } else {
            throw new IllegalArgumentException("Entity Schema should have id field exists");
        }
    }

    protected PreparedStatement getStmt(Connection conn, String sql, String idField) throws SQLException {
        return StringUtils.isNotEmpty(idField) ? conn.prepareStatement(sql, new String[]{idField}) : super.getStmt(conn, sql, idField);
    }

    protected void postProcessCreate(Connection conn, PreparedStatement stmt, List<EntityData> data, SyncResponse response) {

        List<Object> generatedIds = new ArrayList<>();
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            while (rs.next()) {
                generatedIds.add(rs.getObject(1));
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

    private void extractValues(EntitySchema entitySchema, EntityData data, Map<String, Object> changeData) {

        List<String> fieldNames = getFields(entitySchema);

        // not using getOrDefault here as it tries to convert to immutable map and fails with null values
        List<Map<String, Object>> values = (List<Map<String, Object>>)changeData.get("columns");
        values = values != null ? values : List.of();

        Map<String, Object> fieldValues = new HashMap<>();
        values.stream().filter(f -> f.containsKey("name")).forEach(f -> {
            fieldValues.put(f.get("name").toString(), f.get("value"));
        });

        //
        fieldNames.forEach(f -> {
            String casedField = getCased(f);
            var value = entitySchema.getField(f).filter(field -> fieldValues.get(casedField) != null).map(field -> {
                switch (field.getDataType()) {
                    case "date":
                        final String date = (String)fieldValues.get(casedField);
                        return date == null ? null : java.sql.Date.valueOf(date);
                    case "datetime": {
                        final String timestamp = (String)fieldValues.get(casedField);
                        if (timestamp != null) {
                            var temporalAccessor = walTimestampFormat.parseBest(timestamp, ZonedDateTime::from, LocalDateTime::from);
                            if (temporalAccessor instanceof ZonedDateTime) {
                                return temporalAccessor;
                            } else {
                                return ((LocalDateTime) temporalAccessor).atZone(ZoneId.of("UTC"));
                            }
                        }
                        return null;
                    }
                    case "timestamp": {
                        final String timestamp = (String)fieldValues.get(casedField);
                        if (timestamp != null) {
                            var temporalAccessor = walTimestampFormat.parseBest(timestamp, ZonedDateTime::from, LocalDateTime::from);
                            if (temporalAccessor instanceof ZonedDateTime) {
                                return ((ZonedDateTime) temporalAccessor).toInstant();
                            } else {
                                return ((LocalDateTime) temporalAccessor).atZone(ZoneId.of("UTC")).toInstant();
                            }
                        }
                        return null;
                    }
                    default:
                        return fieldValues.get(casedField);
                }
            }).orElse(fieldValues.get(casedField));
            data.addValue(f, value);
        });
    }

    public void executeDdlQuery(ConnectorInfo connector, String sql){
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                log.debug("SQL to be executed is {}" , sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    @Override
    protected String getBaseColumnName(ResultSetMetaData resultSetMetaData, int columnIndex) throws SQLException {
        // PostgreSQL-specific implementation to get actual base column name
        return ((PgResultSetMetaData) resultSetMetaData).getBaseColumnName(columnIndex);
    }

    /**
     * Generates the USING clause for ALTER COLUMN TYPE statements.
     * Handles type conversions that PostgreSQL cannot automatically cast.
     *
     * @param columnName     The decorated column name (with quotes)
     * @param sourceDataType The source PostgreSQL data type
     * @param targetDataType The target PostgreSQL data type
     * @return The USING clause expression for the ALTER statement
     */
    protected String getTypeConversionUsingClause(String columnName, String sourceDataType, String targetDataType) {
        String sourceSyncariType = getSyncariDatatype(sourceDataType);
        String targetSyncariType = getSyncariDatatype(targetDataType);

        // Normalize target datatype for comparison
        String targetNormalized = targetDataType.toUpperCase();

        // Text variants to BIGINT
        if (STRING.equalsIgnoreCase(sourceSyncariType) &&
                (targetNormalized.contains("BIGINT") || targetNormalized.equals("INTEGER") || "integer".equals(targetSyncariType))) {
            return String.format("CASE WHEN %s ~ '%s' THEN NULLIF(TRIM(%s), '')::BIGINT ELSE NULL END",
                    columnName, BIGINT_REGEX, columnName);
        }

        // Text variants to NUMERIC/DOUBLE
        if (STRING.equalsIgnoreCase(sourceSyncariType) &&
                (targetNormalized.contains("NUMERIC") || targetNormalized.contains("DOUBLE") ||
                        targetNormalized.contains("FLOAT") || targetNormalized.contains("DECIMAL") ||
                        "number".equals(targetSyncariType))) {
            return String.format("CASE WHEN %s ~ '%s' THEN NULLIF(TRIM(%s), '')::NUMERIC ELSE NULL END",
                    columnName, NUMERIC_REGEX, columnName);
        }

        // Number variants to TEXT/VARCHAR (but not to TIMESTAMP/DATE - those are handled separately)
        if (("number".equalsIgnoreCase(sourceSyncariType) || "integer".equalsIgnoreCase(sourceSyncariType)) &&
                STRING.equalsIgnoreCase(targetSyncariType) &&
                !targetNormalized.contains("TIMESTAMP") && !targetNormalized.contains("DATE")) {
            return String.format("%s::TEXT", columnName);
        }

        // Text variants to DATE
        if (STRING.equalsIgnoreCase(sourceSyncariType) && "date".equalsIgnoreCase(targetSyncariType)) {
            return String.format("CASE WHEN %s ~ '%s' THEN %s::DATE ELSE NULL END",
                    columnName, DATE_REGEX, columnName);
        }

        // Text variants to TIMESTAMP/TIMESTAMPTZ
        if (STRING.equalsIgnoreCase(sourceSyncariType) &&
                ("timestamp".equalsIgnoreCase(targetSyncariType) || "datetime".equalsIgnoreCase(targetSyncariType) ||
                        targetNormalized.contains("TIMESTAMP"))) {
            // Try both date and timestamp formats
            return String.format("CASE WHEN %s ~ '%s' THEN %s::TIMESTAMPTZ " +
                            "WHEN %s ~ '%s' THEN (%s || ' 00:00:00')::TIMESTAMPTZ ELSE NULL END",
                    columnName, TIMESTAMP_REGEX, columnName,
                    columnName, DATE_REGEX, columnName);
        }

        // Boolean to VARCHAR
        if ("boolean".equalsIgnoreCase(sourceSyncariType) && STRING.equalsIgnoreCase(targetSyncariType)) {
            return String.format("%s::TEXT", columnName);
        }

        // VARCHAR to Boolean
        if (STRING.equalsIgnoreCase(sourceSyncariType) && "boolean".equalsIgnoreCase(targetSyncariType)) {
            return String.format("CASE WHEN LOWER(TRIM(%s)) ~ '%s' THEN " +
                            "LOWER(TRIM(%s)) IN ('true', 't', 'yes', 'y', '1', 'on') ELSE NULL END",
                    columnName, BOOLEAN_REGEX, columnName);
        }

        // VARCHAR to JSON/JSONB
        if (STRING.equalsIgnoreCase(sourceSyncariType) &&
                (targetNormalized.contains("JSON") || targetNormalized.contains("JSONB"))) {
            // Use a regex to do basic validation, then cast. If invalid, set to NULL.
            return String.format("CASE WHEN %s ~ '%s' THEN %s::JSONB ELSE NULL END",
                    columnName, JSON_REGEX, columnName);
        }

        // Number variants (integer/numeric) to TIMESTAMP/DATE - treat as epoch seconds
        if (("number".equalsIgnoreCase(sourceSyncariType) || "integer".equalsIgnoreCase(sourceSyncariType)) &&
                ("timestamp".equalsIgnoreCase(targetSyncariType) || "datetime".equalsIgnoreCase(targetSyncariType) ||
                        targetNormalized.contains("TIMESTAMP"))) {
            // Treat numeric value as epoch seconds (or milliseconds if > reasonable seconds threshold)
            // TO_TIMESTAMP returns timestamp without time zone, so cast explicitly to TIMESTAMPTZ
            return String.format("CASE WHEN %s IS NOT NULL THEN " +
                            "CASE WHEN %s > 9999999999 THEN TO_TIMESTAMP(%s::DOUBLE PRECISION / 1000.0)::TIMESTAMPTZ " +
                            "ELSE TO_TIMESTAMP(%s::DOUBLE PRECISION)::TIMESTAMPTZ END " +
                            "ELSE NULL END",
                    columnName, columnName, columnName, columnName);
        }

        // Number variants to DATE - treat as epoch seconds
        if (("number".equalsIgnoreCase(sourceSyncariType) || "integer".equalsIgnoreCase(sourceSyncariType)) &&
                "date".equalsIgnoreCase(targetSyncariType)) {
            return String.format("CASE WHEN %s IS NOT NULL THEN " +
                            "CASE WHEN %s > 9999999999 THEN TO_TIMESTAMP(%s::BIGINT / 1000.0)::DATE " +
                            "ELSE TO_TIMESTAMP(%s::BIGINT)::DATE END " +
                            "ELSE NULL END",
                    columnName, columnName, columnName, columnName);
        }

        // Default: simple cast (for compatible types)
        return String.format("%s::%s", columnName, targetDataType);
    }

    public UpdateFieldResponse updateField(UpdateFieldRequest request) {
        Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), request.getEntityName()));
        AtomicReference<Boolean> updateRef = new AtomicReference<>(false);
        UpdateFieldResponse response = new UpdateFieldResponse();
        entity.ifPresent(en -> {
            String apiName = !StringUtils.isBlank(request.getOldName()) ? request.getOldName() : request.getSchema().getApiName();
            Optional<AttributeSchema> field = en.getField(apiName);
            if (field.isEmpty())
                updateRef.set(false);
            else {
                try (Connection conn = getConnection(request.getConnector())) {
                    try (Statement stmt = conn.createStatement()) {
                        String tableName = getTableName(request.getEntityName(), request.getConnector());
                        String columnName = field.get().getApiName();
                        String sql = StringUtils.EMPTY;
                        if (!StringUtils.isBlank(request.getOldName()) && !StringUtils.isBlank(request.getNewName())
                                && !request.getNewName().equalsIgnoreCase(request.getOldName())) {
                            sql = String.format(SqlQueries.RENAME_COLUMN, tableName, getDecoratedFieldName(request.getOldName()),
                                    getDecoratedFieldName(request.getNewName()));
                            columnName = request.getNewName();
                            log.debug(sql);
                            stmt.execute(sql);
                            updateRef.set(true);
                        }
                        final String decoratedFieldName = getDecoratedFieldName(columnName);
                        if (request.getSchema().getLength() > 0
                                && field.get().getLength() != request.getSchema().getLength()
                                && request.getSchema().getLength() > field.get().getLength()
                                && STRING.equalsIgnoreCase(request.getSchema().getDataType())) {
                            sql = String.format(SqlQueries.ALTER_LENGTH,
                                    tableName, decoratedFieldName, getFieldLength(request.getSchema().getLength()));
                            log.debug(sql);
                            stmt.execute(sql);
                            updateRef.set(true);
                        } else {
                            final String newDatatype = getDatatype(request.getSchema());
                            final String existingDatatype = getDatatype(field.get());
                            if (request.getSchema().getDataType() != null && !newDatatype.equalsIgnoreCase(existingDatatype)) {
                                // Generate the appropriate USING clause for type conversion
                                String usingClause = getTypeConversionUsingClause(decoratedFieldName, existingDatatype, newDatatype);
                                sql = String.format(ALTER_TYPE_STMT,
                                        tableName, decoratedFieldName, newDatatype, usingClause);
                                log.info("Executing type conversion SQL: {}", sql);
                                stmt.execute(sql);
                                updateRef.set(true);
                            }
                        }
                    }
                } catch (Exception e) {
                    handleException(e, request.getConnector());
                }
            }
        });
        response.setFieldUpdated(updateRef.get());
        response.setNewSchema(request.getSchema());
        return response;
    }


}

class PemFile {

    private PemObject pemObject;

    public PemFile(String filename) throws FileNotFoundException, IOException {
        PemReader pemReader = new PemReader(new InputStreamReader(new FileInputStream(filename)));
        try {
            this.pemObject = pemReader.readPemObject();
        } finally {
            pemReader.close();
        }
    }

    public PemObject getPemObject() {
        return pemObject;
    }

}
