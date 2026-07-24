package com.syncari.connector.database;

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
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.REDSHIFT)
public class RedshiftService extends DatabaseService implements SynapseInfoService {
    private static final String JDBC_URL = "jdbc:redshift://%s/%s";
    public static final String CLUSTER_NAME = "clusterName";
    public static final String DATABASE_NAME = "dbName";
    private static final String dateTimeFormat = "yyyy-MM-dd HH:mm:ss Z";

    private static final String dateFormat = "yyyy-MM-dd";

    @Autowired
    DateUtil dateUtil;
    static {
        loadDriver("com.amazon.redshift.jdbc.Driver");

    }

    protected String getJdbcURL(ConnectorInfo connector) {
        return StringUtils.isBlank(connector.getEndpoint())
                ? String.format(JDBC_URL, getValue(connector, CLUSTER_NAME), getValue(connector, DATABASE_NAME))
                : connector.getEndpoint();
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200973176980";
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField bootstrappable = new AuthField();
        bootstrappable.setDataType("checkbox");
        bootstrappable.setName("bootstrapable");
        bootstrappable.setLabel("Instantiate with Syncari entities");
        AuthField accountName = new AuthField();
        accountName.setDataType("text");
        accountName.setName(CLUSTER_NAME);
        accountName.setLabel("Cluster Name");
        AuthField schemaName = new AuthField();
        schemaName.setDataType("text");
        schemaName.setName(SCHEMA_NAME);
        schemaName.setLabel("Schema Name");
        AuthField dbName = new AuthField();
        dbName.setDataType("text");
        dbName.setName(DATABASE_NAME);
        dbName.setLabel("Database Name");

        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("db_timezone_label"));
        timeZone.setHelpSummary(i18n("db_timezone_help"));
        timeZone.setRequired(false);

        return List.of(accountName, dbName, schemaName, bootstrappable, timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    protected Object preprocessWmField(EntitySchema entitySchema, String columnName, Object columnValue) {
        String wmDatatype = getSyncariDatatype(entitySchema.getWatermarkField().getDataType());
        if(entitySchema.isWatermarkField(columnName) && columnValue == null
                && WM_FIELD_TYPES.contains(wmDatatype)) {
            switch (wmDatatype) {
                case "date":
                    return new Date();
                case "datetime":
                    return ZonedDateTime.now();
                case "timestamp":
                    return Instant.now();

                default:
                    break;
            }
        }
        return columnValue;
    }

    @Override
    public String getCategory() {
        return "Datawarehouse";
    }

    @Override
    public String getName() {
        return Constants.REDSHIFT;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/redshift.svg")
                .setDisplayName("Redshift")
                .setBackgroundColor("#F3F9FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360052656731-Redshift-Setup");
    }

    @Override
    String getDescribeSql(ConnectorInfo connector) {
        return String.format(SqlQueries.DESCRIBE_ENTITY, getSchemaName(connector));
    }

    @Override
    String getDescribeFieldSqlForLateBindingViews(ConnectorInfo connectorInfo) {
        return String.format(SqlQueries.DESCRIBE_LATE_BINDING_VIEWS, getSchemaName(connectorInfo));
    }

    @Override
    String getDescribeFieldSql(ConnectorInfo connector, String tableName) {
        return String.format(SqlQueries.DESCRIBE_FIELD, tableName.toLowerCase(), getSchemaName(connector));
    }

    @Override
    String getSelectByIdsSql() {
        return SqlQueries.SELECT_BY_IDS;
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String clusterName = getValue(connector, CLUSTER_NAME);
        if (StringUtils.isBlank(clusterName)) {
            throw new RuntimeException("cluster_name_required");
        }
        String schemaName = getValue(connector, SCHEMA_NAME);
        if (StringUtils.isBlank(schemaName)) {
            throw new RuntimeException("schema_name_required");
        }
        String dbName = getValue(connector, DATABASE_NAME);
        if (StringUtils.isBlank(dbName)) {
            throw new RuntimeException("db_name_required");
        }
        return true;
    }

    @Override
    String getEscapeChar() {
        return "\"";
    }

    @Override
    boolean isUpperCase() {
        return false;
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    String getSchemaName(ConnectorInfo connector) {
        return getValue(connector, SCHEMA_NAME);
    }

    public void provision(ConnectorInfo connector, String userName, String pwd, boolean readOnly) {
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                createSchema(stmt, connector);
                String schema = getValue(connector, SCHEMA_NAME);
                String sql;
                // Create read only group
                String groupName = createGroup(stmt, schema);

                // Create user
                createUser(stmt, userName, pwd);

                // Alter group add user
                sql = String.format(SqlQueries.ALTER_GROUP, groupName, userName);
                stmt.execute(sql);
                log.info("Successfully added user {} to group {}", userName, groupName);

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

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (request.getEntitySchema() == null)
            throw new RuntimeException("Schema cannot be null");
        return super.getByCursorBasedWatermark(request);
    }

    @Override
    String getCursorWatermarkCondition(SyncRequest request, String nextID, int pageSize) {
        String idField = getCased(request.getEntitySchema().getIdField().getApiName());
        String query = "";
        long prevWatermark = 0;
        if (StringUtils.isNotEmpty(nextID)) {
            if(nextID.contains("#")) {
                String[] parts = nextID.split("#", 2);
                if(parts.length == 2) {
                    try {
                        prevWatermark = Long.valueOf(parts[0]);
                        nextID = formatId(request, parts[1]);
                        query = "\"%s\" > '%s' AND \"%s\" <= '%s' AND (%s > '%s' OR (%s = '%s' AND \"" + idField + "\" > " + nextID + ")) ORDER BY \"%s\",\"%s\" LIMIT %s ";
                    } catch (Exception e) {
                        log.error("Error when parsing redshift changestream - {}, {}", nextID, e.getMessage());
                        query = "\"%s\" > '%s' AND \"%s\" <= '%s' AND \"" + idField + "\" > " + formatId(request, nextID) + " ORDER BY \"%s\",\"%s\" LIMIT %s ";
                    }
                } else {
                    query = "\"%s\" > '%s' AND \"%s\" <= '%s' AND \"" + idField + "\" > " + formatId(request, nextID) + " ORDER BY \"%s\",\"%s\" LIMIT %s ";
                }
            } else {
                query = "\"%s\" > '%s' AND \"%s\" <= '%s' AND \"" + idField + "\" > " + formatId(request, nextID) + " ORDER BY \"%s\",\"%s\" LIMIT %s ";
            }
        } else {
            query = "\"%s\" > '%s' AND \"%s\" <= '%s' ORDER BY \"%s\",\"%s\" LIMIT %s ";
        }
        String watermarkField = getCased(request.getEntitySchema().getWatermarkField().getApiName());
        long previousBatchMaxWatermark = getPreviousBatchMaxWatermark(request);
        long start = !request.getWatermark().isResync() && previousBatchMaxWatermark > -1 ? previousBatchMaxWatermark : request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        if ("timestamp".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "datetime".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "date".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType())) {
            boolean dateType =  "date".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType());
            String startTimestamp = dateType ? dateUtil.format(start, dateFormat, ZoneId.of(getZoneId(request.getConnector()))) : dateUtil.format(start, dateTimeFormat, ZoneId.of(getZoneId(request.getConnector())));
            String endTimestamp = dateType ? dateUtil.format(end, dateFormat, ZoneId.of(getZoneId(request.getConnector()))) : dateUtil.format(end, dateTimeFormat, ZoneId.of(getZoneId(request.getConnector())));
            if(dateType && startTimestamp.equalsIgnoreCase(endTimestamp)) {
                startTimestamp = dateUtil.subtractDays(startTimestamp, dateFormat, 1);
            }
            String prevTimestamp = "";
            if(prevWatermark > 0) {
                prevTimestamp = dateUtil.format(prevWatermark, dateTimeFormat, ZoneId.of(getZoneId(request.getConnector())));
                query = String.format(query, watermarkField, startTimestamp, watermarkField, endTimestamp,
                        String.format("date_trunc('second', \"%s\")", watermarkField), prevTimestamp, String.format("date_trunc('second', \"%s\")", watermarkField), prevTimestamp, watermarkField, idField, pageSize);
            } else {
                query = String.format(query, watermarkField, startTimestamp, watermarkField, endTimestamp, watermarkField,
                        idField, pageSize);
            }
        } else {
            query = String.format(query, watermarkField, start, watermarkField, end, watermarkField, idField, pageSize);
        }
        log.debug("Redshift watermark condition - {}", query);
        return query;
    }

    private String formatId(SyncRequest request, String nextID) {
        if ("string".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType()) && StringUtils.isNotEmpty(nextID)) {
            nextID = String.format("'%s'", nextID);
        }
        return nextID;
    }

    private long getPreviousBatchMaxWatermark(SyncRequest request) {
        if (request.getWatermark() != null && request.getWatermark().getStreamState() != null &&
                request.getWatermark().getStreamState().getLastModified() > 0) {
            return request.getWatermark().getStreamState().getLastModified();
        }
        return -1;
    }

    @Override
    protected String getNextCursor(List<EntityData> result, Integer pageSize, String prevCursor) {
        if (result.size() >= pageSize) {
            EntityData lastRecord = result.get(result.size() - 1);
            String lastModified = String.valueOf(lastRecord.getLastModified());
            String id = lastRecord.getId();
            return lastModified + "#" + id;
        }
        return null;
    }

    @Override
    String getWatermarkCondition(SyncRequest request, long offset, int pageSize) {
        String watermarkField = getCased(request.getEntitySchema().getWatermarkField().getApiName());
        String query = "\"%s\" >= '%s' AND \"%s\" <= '%s' ORDER BY \"%s\" LIMIT %s OFFSET %s";
        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        String dataType = request.getEntitySchema().getWatermarkField().getDataType();
        if("timestamp".equalsIgnoreCase(dataType) ||
                "datetime".equalsIgnoreCase(dataType) ||
                "date".equalsIgnoreCase(dataType)) {
            String startTimestamp = dateUtil.format(start, dateTimeFormat, ZoneId.of(getZoneId(request.getConnector())));
            String endTimestamp = dateUtil.format(end, dateTimeFormat, ZoneId.of(getZoneId(request.getConnector())));
            query = String.format(query, watermarkField, startTimestamp, watermarkField, endTimestamp, watermarkField,pageSize, offset);
        } else {
            query = String.format(query, watermarkField, start, watermarkField, end, watermarkField, pageSize, offset);
        }
        return query;
    }
    @Override
    protected Object computedValue(AttributeSchema attr, Object columnValue) {
        if(columnValue != null && ("timestamp".equalsIgnoreCase(attr.getDataType()) || "datetime".equalsIgnoreCase(attr.getDataType()) || "date".equalsIgnoreCase(attr.getDataType()))) {
            if(columnValue instanceof Instant) {
                return new java.sql.Timestamp(((Instant) columnValue).toEpochMilli());
            }else if (columnValue instanceof ZonedDateTime){
                return new java.sql.Timestamp(((ZonedDateTime) columnValue).toInstant().toEpochMilli());
            }else if (columnValue instanceof Date){
                return new java.sql.Timestamp(((Date) columnValue).toInstant().toEpochMilli());
            }
        }
        return columnValue;
    }
    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector){
        Properties props = new Properties();
        props.setProperty("ssl", "true");
        return Optional.of(props);
    }

    public void deprovision(ConnectorInfo connector, String userName) {
        String schema = getValue(connector, SCHEMA_NAME);
        dropUser(connector, userName);
        dropSchema(connector);
        dropGroup(connector, generateGroupName(schema));
    }

    protected void dropGroup(ConnectorInfo connector, String groupName) {
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format("DROP GROUP %s", groupName);
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    @Override
    protected void alterLength(ConnectorInfo connector, String entity, AttributeSchema field, Integer newLength) {
        if (field == null || field.getDataType() == null || newLength <= field.getLength()) {
            return;
        }
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.ALTER_LENGTH, getTableName(entity, connector), field.getApiName(), 
                    getFieldLength(newLength));
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    String getDatatype(AttributeSchema from) {
        //multivalued fields will have max length
        if (from.isMultiValueField()) return "VARCHAR("+MAX_VARCHAR_LENGTH+")";

        switch (from.getDataType()) {
            case "double":
            case "float":
            case "number":
                return "NUMERIC(24,8)";
            default:
                return super.getDatatype(from);
        }
    }

    @Override
    protected boolean fetchByLastBatchWatermark() {
        return true;
    }
}
