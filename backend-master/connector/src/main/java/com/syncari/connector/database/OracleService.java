package com.syncari.connector.database;

import com.google.common.collect.Lists;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.query.SqlQueries;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.ORACLE)
public class OracleService extends DatabaseService implements SynapseInfoService {

    @Autowired
    DateUtil dateUtil;

    private static final long ORACLE_MAX_VARCHAR_LENGTH = 4000;

    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String JDBC_URL = "jdbc:oracle:thin:@(description= (retry_count=3)(retry_delay=3)(address=(protocol=tcps)(port=%s)(host=%s))(connect_data=(service_name=%s))(security=(ssl_server_dn_match=yes)))";
    private static final String DESCRIBE_ENTITY =
            "SELECT object_name AS table_name, " +
                    "object_type AS table_type " +
                    "FROM ALL_OBJECTS " +
                    "WHERE owner = '%s' " +
                    "AND object_type IN ('TABLE', 'VIEW') " +
                    "ORDER BY table_name";

    private static final String DESCRIBE_FIELD =
            "SELECT column_name, " +
                    "data_type, " +
                    "CASE WHEN nullable = 'Y' THEN 'YES' ELSE 'NO' END as is_nullable, " +
                    "data_default AS column_default, " +
                    "CASE WHEN char_length > 0 THEN char_length ELSE data_precision END AS max_length, " +
                    "data_precision AS numeric_precision, " +
                    "data_scale AS numeric_scale " +
                    "FROM all_tab_columns " +
                    "WHERE owner = '%s' " +
                    "AND table_name = '%s'";
    private static final String CREATE_TABLE = "CREATE TABLE %s (%s);";

    static {
        loadDriver("oracle.jdbc.driver.OracleDriver");
    }

    @Override
    public String getName() {
        return Constants.ORACLE;
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/rdsoracle.svg")
                .setDisplayName("Oracle DB")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl(helpArticlesBaseUrl + "/23184521798804");
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField bootstrappable = new AuthField().setDataType("checkbox").setName("bootstrapable")
                .setLabel("Instantiate with Syncari entities");
        AuthField cluster = new AuthField().setRequired(true).setDataType("text").setName(Constants.CLUSTER_NAME)
                .setLabel("Host");
        AuthField sid = new AuthField().setRequired(true).setDataType("text").setName(Constants.DATABASE_NAME)
                .setLabel("Service Name");
        AuthField port = new AuthField().setRequired(true).setDataType("text").setName(Constants.PORT)
                .setLabel("Port");
        AuthField schemaName = new AuthField().setRequired(true).setDataType("text").setName(SCHEMA_NAME)
                .setLabel("Schema Name");

        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("oracle_timezone_label"));
        timeZone.setHelpSummary(i18n("db_timezone_help"));

        return List.of(cluster, sid, port, schemaName, bootstrappable, timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    protected Connection getConnection(ConnectorInfo connector) throws ClassNotFoundException, SQLException {
        String zoneId = connector.getMetaConfig().getOrDefault(TIME_ZONE_ID, "UTC").toString();
        Connection connection = super.getConnection(connector);
        try (Statement stmt = connection.createStatement()) {
            String sql = String.format("ALTER SESSION SET TIME_ZONE = '%s'", zoneId);
            log.info(sql);
            stmt.execute(sql);
            log.info("Successfully altered session timezone to {}", zoneId);
        } catch (Exception e) {
            handleException(e, connector);
        }
        return connection;
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
        String schemaName = getValue(connector, SCHEMA_NAME);
        if (StringUtils.isBlank(schemaName)) {
            throw new RuntimeException("schema_name_required");
        }
        String port = getValue(connector, Constants.PORT);
        if (StringUtils.isBlank(port)) {
            throw new RuntimeException("port_name_required");
        }

        String zoneId = connector.getMetaConfig().getOrDefault(TIME_ZONE_ID, "UTC").toString();
        try {
            ZoneId z = ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new RuntimeException(i18n("db_invalid_timezone_id"));
        }
        return true;
    }

    @Override
    protected String getJdbcURL(ConnectorInfo connector) {
        String jdbcURL = StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL, getValue(connector, Constants.PORT), getValue(connector, Constants.CLUSTER_NAME), getValue(connector, Constants.DATABASE_NAME))
                : connector.getEndpoint();
        log.info(jdbcURL);
        return jdbcURL;
    }

    protected String getDecoratedValuePlaceHolder(AttributeSchema attr){
        if (StringUtils.equalsAnyIgnoreCase(attr.getDataType(), "timestamp","datetime")){
            return ("TO_UTC_TIMESTAMP_TZ(?)");
        }
        return ("?");
    }

    protected String getCreateBindingParamList(Map<String, AttributeSchema> columnToApiNameMap){
        return String.join(COMMA,
                columnToApiNameMap.keySet().stream().map(
                        c -> getDecoratedValuePlaceHolder(columnToApiNameMap.get(c))
                ).collect(Collectors.toList()));
    }

    protected String getUpdateBindingParamList(Set<String> finalColumnList, Map<String, AttributeSchema> apiToAttr){
        return String.join(COMMA,
                finalColumnList.stream().map(
                        c -> getDecoratedFieldName(c) +"="+getDecoratedValuePlaceHolder(apiToAttr.get(c.toLowerCase()))
                ).collect(Collectors.toList()));
    }

    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        Properties properties = new Properties();
        properties.put("oracle.jdbc.timezoneAsRegion", "true");
        return Optional.of(properties);
    }

    @Override
    protected String getSchemaName(ConnectorInfo connector) {
        return getValue(connector, SCHEMA_NAME).toUpperCase();
    }

    @Override
    String getDescribeSql(ConnectorInfo connectorInfo) {
        return String.format(DESCRIBE_ENTITY, getSchemaName(connectorInfo));
    }

    @Override
    String getDescribeFieldSqlForLateBindingViews(ConnectorInfo connectorInfo) {
        return "";
    }

    @Override
    String getDescribeFieldSql(ConnectorInfo connectorInfo, String entityName) {
        return String.format(DESCRIBE_FIELD, getSchemaName(connectorInfo), entityName);
    }

    @Override
    String getSelectByIdsSql() {
        return optionallyRemoveTrailingSemiColon(SqlQueries.SELECT_BY_IDS);
    }

    @Override
    String getWatermarkCondition(SyncRequest request, long offset, int pageSize) {
        validateIdWMFields(request);
        String watermarkField = getCased(request.getEntitySchema().getWatermarkField().getApiName());
        String dataType = request.getEntitySchema().getWatermarkField().getDataType();
        String query = "";
        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        if ("timestamp".equals(dataType)) {
            ZoneId zoneId = ZoneId.of(getZoneId(request.getConnector()));
            String startString = dateUtil.format(start, TIMESTAMP_FORMAT, zoneId);
            String endString = dateUtil.format(end, TIMESTAMP_FORMAT, zoneId);
            query += String.format(" %s >= TO_TIMESTAMP_TZ('%s %s', 'YYYY-MM-DD HH24:MI:SS TZR') AND %s < TO_TIMESTAMP_TZ( '%s %s' , 'YYYY-MM-DD HH24:MI:SS TZR')",
                    watermarkField, startString, getZoneId(request.getConnector()), watermarkField, endString, getZoneId(request.getConnector()));
        } else {
            String startString = String.valueOf(start);
            String endString = String.valueOf(end);
            query += String.format(" %s => '%s' AND %s < '%s'", watermarkField, startString, watermarkField, endString);
        }
        query += String.format(" ORDER BY %s OFFSET %s ROWS FETCH NEXT %s ROWS ONLY", watermarkField, offset, pageSize);
        return query;
    }

    @Override
    protected String optionallyRemoveTrailingSemiColon(String query) {
        return query.replace(";", "");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    String getEscapeChar() {
        return "";
    }

    @Override
    boolean isUpperCase() {
        return true;
    }

    protected int getInitializationFailTimeout(){
        // Time to initialize the connection in milli seconds before failing the same - 30 sec
        return 30000;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }
    @Override
    public boolean isSink() {
        return true;
    }

    @Override
    String getDatatype(AttributeSchema from) {
        if (from.isMultiValueField()) return "VARCHAR2(" + ORACLE_MAX_VARCHAR_LENGTH + ")";

        switch (from.getDataType()) {
            case "boolean":
                return "NUMBER(1)";
            case "int":
            case "integer":
            case "double":
            case "float":
            case "number":
                int precision = from.getPrecision();
                int scale = from.getScale();
                String suffix = (precision == 0 && scale == 0) ? "" : "(" + precision + "," + scale + ")";
                return "NUMBER " + suffix;
            case "datetime":
            case "timestamp":
                return "TIMESTAMP";
            case "date":
                return "DATE";
            case "textarea":
                return "CLOB";
            default:
                if (ORACLE_MAX_VARCHAR_LENGTH < from.getLength()) {
                    return "CLOB";
                }
                return String.format("VARCHAR2(%s)", Math.min(ORACLE_MAX_VARCHAR_LENGTH, from.getLength() == 0 ? 256 : from.getLength()));
        }
    }

    protected String normalizeFieldType(String type) {
        if (type.contains("timestamp")) {
            return "timestamp";
        }
        if (type.contains("float")){
            return "float";
        }
        return type;
    }

    @Override
    void doCreateObject(CreateObjectRequest request) {
        List<AttributeSchema> attributes = new ArrayList<>();
        attributes.addAll(request.getSchema().getAttributes());
        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                List<String> fields = attributes.stream()
                        .map(a -> {
                            normalizeField(a);
                            return getFieldStr(a);
                        })
                        .collect(Collectors.toList());
                String tableName = getTableName(request.getSchema().getApiName(), request.getConnector());
                String sql = String.format(CREATE_TABLE, tableName,
                        String.join(COMMA, fields));
                sql = optionallyRemoveTrailingSemiColon(sql);
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        if (request.getSchema().getApiName().equalsIgnoreCase("user") && request.getSchema().getDisplayName().equalsIgnoreCase("user")) {
            request.getSchema().setApiName(request.getSchema().getApiName().concat("_SYNCARI"));
        }
        Optional<EntitySchema> describe = describe(new DescribeRequest(request.getConnector(), request.getSchema().getApiName()));
        if (describe.isEmpty()) {
            doCreateObject(request);
            describe = describe(new DescribeRequest(request.getConnector(), request.getSchema().getApiName()));
        }
        return describe.get();
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        request.setEntity(request.getEntity().toUpperCase());
        return super.describe(request);
    }

    private void normalizeField(AttributeSchema attributeSchema){
        String apiName = attributeSchema.getApiName();
        if ("DATE".equalsIgnoreCase(apiName) || "TIMESTAMP".equalsIgnoreCase(apiName)){
            attributeSchema.setApiName(apiName.concat("F"));
        }
    }

    @Override
    protected long getTimestampEpochMillis(Timestamp timestamp, ZoneId zoneId) {
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.atZone(zoneId).toInstant().toEpochMilli();
    }

    protected boolean hasMultiInsertSupport(){
        return false;
    }

    protected void postProcessCreate(Connection conn, PreparedStatement stmt, List<EntityData> data, SyncResponse response){
        List<String> generatedIds = new ArrayList<>();
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            while (rs.next()) {
                generatedIds.add(rs.getString(1));
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
        return StringUtils.isNotEmpty(idField) ? conn.prepareStatement(sql, new String[]{idField}) : super.getStmt(conn, sql, idField);
    }

    @Override
    protected Object computedValue(AttributeSchema attr, Object columnValue, String zoneId) {
        if (columnValue != null && "timestamp".equalsIgnoreCase(attr.getDataType())) {
            if (columnValue instanceof Instant){
                return ((Instant) columnValue).atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            if (columnValue instanceof ZonedDateTime) {
                return ((ZonedDateTime) columnValue).toInstant().atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            return ((Timestamp) columnValue).toInstant().atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        }
        if(columnValue != null && "datetime".equalsIgnoreCase(attr.getDataType())) {
            if (columnValue instanceof Instant){
                return ((Instant) columnValue).atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            if (columnValue instanceof ZonedDateTime) {
                return ((ZonedDateTime) columnValue).toInstant().atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            return ((Timestamp) columnValue).toInstant().atZone(ZoneId.of(zoneId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (columnValue != null && "date".equalsIgnoreCase(attr.getDataType())) {
            return new Date(((java.util.Date) columnValue).toInstant().toEpochMilli());
        }
        return columnValue;
    }

    protected long getLastModified(String datatype, ResultSet rs, String field, ZoneId zoneId) throws SQLException {
        switch (getSyncariDatatype(datatype)) {
            case "timestamp":
                return getTimestampEpochMillis(rs.getTimestamp(getCased(field)), zoneId);
            default:
                return super.getLastModified(datatype, rs, field);
        }
    }

    @Override
    protected int getNullObjectType() {
        return Types.VARCHAR;
    }

    List<AttributeSchema> getAttributes(ConnectorInfo connectorInfo, Connection conn, String entityName) {
        List<AttributeSchema> attributes = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            String sql = getDescribeFieldSql(connectorInfo, entityName);
            log.debug(sql);
            try(ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String rawType = rs.getString("DATA_TYPE");
                    // Avoiding Long, Long Raw and Bfile columns as they makes the stream slower and complex
                    if (StringUtils.startsWithAny(rawType.toLowerCase(), "long", "bfile")){
                        continue;
                    }
                    String type = getSyncariDatatype(rs.getString("DATA_TYPE"));
                    AttributeSchema attr = new AttributeSchema(name, type);
                    attr.setDisplayName(name);
                    attr.setNillable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                    attr.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                    String maxLength=null;
                    try{
                        maxLength = rs.getString("MAX_LENGTH");
                    } catch (Exception e){

                    }
                    if (maxLength != null) {
                        try {
                            attr.setLength(Integer.valueOf(maxLength));
                        } catch (NumberFormatException e) {
                            attr.setLength(Integer.MAX_VALUE);
                            log.warn("Setting integer max for field %s on table %s since its length is %s", name, entityName, maxLength);
                        }
                    }
                    if("number".equals(type)){
                        attr.setPrecision(rs.getInt("NUMERIC_PRECISION"));
                        attr.setScale(rs.getInt("NUMERIC_SCALE"));
                    }
                    attributes.add(attr);
                }
            }
        } catch (Exception e) {
            handleException(e, connectorInfo);
        }
        return attributes;
    }

    protected Object extractVal(ResultSet rs, String dataType, String fieldName) {
        try{
            switch (dataType) {
                case "date":
                    return rs.getDate(fieldName);
                case "datetime":{
                    final Timestamp timestamp = rs.getTimestamp(fieldName);
                    return timestamp==null? null :ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
                }
                case "timestamp": {
                    final Timestamp timestamp = rs.getTimestamp(fieldName);
                    return timestamp==null? null :timestamp.toInstant();
                }
                case "string": {
                    Object string = rs.getObject(fieldName);
                    return string==null? null :string.toString();
                }
                default:
                    return rs.getObject(fieldName);
            }
        }catch (SQLException e){
            log.error("Exception occurred {} and cause is {}", e.getMessage(), e.getCause());
        }
        return null;
    }

}
