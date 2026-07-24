package com.syncari.connector.database;

import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.exception.AuthenticationException;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.query.SnowflakeQueries;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.SNOWFLAKE)
public class SnowflakeService extends DatabaseService implements SynapseInfoService, OauthAuthenticationService {
    private static final String ACCOUNT_NAME = "accountName";
    private static final String SCHEMA_NAME = "schemaName";
    private static final String DB_NAME = "dbName";
    private static final String WAREHOUSE_NAME = "warehouseName";
    private static final String SF_ROLE = "role";
    private static final String dateTimeFormat = "yyyy-MM-dd HH:mm:ss.SSS Z";
    private static final String dateFormat = "yyyy-MM-dd";
    private static final String keysetPaginationClauseTemplate = "(\"%s\",\"%s\") > ('%s',%s)  ORDER BY \"%s\",\"%s\" LIMIT %s ";
    private static final String simplePaginationClauseTemplate = "\"%s\" >= '%s'  ORDER BY \"%s\",\"%s\" LIMIT %s ";
    @Autowired
    Transformer transformer;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    private static final String OAUTH_AUTHORIZE = "/oauth/authorize";
    private static final String GET_ACCESS_TOKEN = "/oauth/token-request";

    static{
        loadDriver("com.snowflake.client.jdbc.SnowflakeDriver");
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
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd(), ConnectorHelper.getAccessTokenOauthType());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200590215700";
    }


    @Override
    public List<AuthField> getConfigureFields() {
        AuthField bootstrappable = new AuthField();
        bootstrappable.setDataType("checkbox");
        bootstrappable.setName("bootstrapable");
        bootstrappable.setLabel("Instantiate with Syncari entities");
        AuthField accountName = new AuthField();
        accountName.setDataType("text");
        accountName.setName(ACCOUNT_NAME);
        accountName.setLabel("Account Name");
        accountName.setHelpSummary("The account name in snowflake. " +
            "This is typically the first part in the full URL (account_name). Ex. <account_name>.snowflakecomputing.com");
        AuthField whName = new AuthField();
        whName.setDataType("text");
        whName.setName(WAREHOUSE_NAME);
        whName.setLabel("Warehouse Name");
        whName.setHelpSummary("The warehouse name in snowflake. The connection user should have privileges to this warehouse.");
        AuthField dbName = new AuthField();
        dbName.setDataType("text");
        dbName.setName(DB_NAME);
        dbName.setLabel("Database Name");
        dbName.setHelpSummary("The database name in snowflake");
        AuthField schemaName = new AuthField();
        schemaName.setDataType("text");
        schemaName.setName(SCHEMA_NAME);
        schemaName.setLabel("Schema Name");
        schemaName.setHelpSummary("The schema name in snowflake");
        AuthField roleField = new AuthField();
        roleField.setDataType("text");
        roleField.setName(SF_ROLE);
        roleField.setLabel("User Role");
        roleField.setRequired(false);
        roleField.setHelpSummary("Choose the role for the user with full privileges to the schema/database/warehouse. If not provided, default role of 'PUBLIC' will be used");

        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("db_timezone_label"));
        timeZone.setHelpSummary(i18n("db_timezone_help"));
        timeZone.setRequired(false);

        return List.of(accountName, whName, dbName, schemaName, roleField, timeZone, bootstrappable, ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Datawarehouse";
    }

    @Override
    public String getName() {
        return Constants.SNOWFLAKE;
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/snowflake.svg")
                .setDisplayName("Snowflake")
                .setBackgroundColor("#F0F8FB")
                .setHelpUrl(helpArticlesBaseUrl + "/360052204712-Snowflake-Setup");
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<>(super.getCapabilities());
        capabilities.add(Capability.schemaCreateField);
        capabilities.add(Capability.compositeId);
        return capabilities;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        if(!config.getMetaConfig().containsKey(ACCOUNT_NAME) || config.getMetaConfig().get(ACCOUNT_NAME) == null) {
            throw new RuntimeException(i18n("account_name_required"));
        }
        if(!config.getMetaConfig().containsKey(DB_NAME) || config.getMetaConfig().get(DB_NAME) == null) {
            throw new RuntimeException(i18n("db_name_required"));
        }
        if(!config.getMetaConfig().containsKey(SCHEMA_NAME) || config.getMetaConfig().get(SCHEMA_NAME) == null) {
            throw new RuntimeException(i18n("schema_name_required"));
        }
        TestConnectionResponse testConnectionResponse = super.testConnection(config, entityNames);
        if(!testConnectionResponse.isSuccess() && testConnectionResponse.getMessage().contains("No active warehouse selected in the current session")) {
            String role = (String) config.getMetaConfig().get(SF_ROLE);
            String schema = (String) config.getMetaConfig().get(SCHEMA_NAME);
            testConnectionResponse.setMessage("Check if " + role + " role has access to the schema " + schema);
        }
        return testConnectionResponse;
    }
    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector){
        Properties props = new Properties();
        Object warehouseName = connector.getMetaConfig().get(WAREHOUSE_NAME);
        if(warehouseName != null) {
            props.setProperty("warehouse", warehouseName.toString());
        }
        props.setProperty("schema", connector.getMetaConfig().get(SCHEMA_NAME).toString());
        props.setProperty("db", connector.getMetaConfig().get(DB_NAME).toString());
        Object role = connector.getMetaConfig().get(SF_ROLE);
        if(role != null && !StringUtils.isBlank(role.toString())) {
            props.setProperty("role", role.toString());
        } else {
            props.setProperty("role", "PUBLIC");
        }
        String authType = connector.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken).toString();
        if(authType.equalsIgnoreCase(AuthType.Oauth.name())) {
            if(StringUtils.isBlank(connector.getAuthConfig().getAccessToken())) {
                throw new AuthenticationException(connector.getId(), connector.getName(), "OAuth access token is missing");
            }
            props.setProperty("authenticator", "oauth");
            props.setProperty("token", connector.getAuthConfig().getAccessToken());
        }
        return Optional.of(props);
    }

    protected String getJdbcURL(ConnectorInfo connector) {
        return String.format("jdbc:snowflake://%s.snowflakecomputing.com/", connector.getMetaConfig().get(ACCOUNT_NAME));
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        try {
            return super.describe(request);
        } catch (Exception e) {
            handleException(request.getConnector(), e);
            throw e;
        }
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        try {
            return super.describeAll(request);
        } catch (Exception e) {
            handleException(request.getConnector(), e);
            throw e;
        }
    }

    private static void handleException(ConnectorInfo connectorInfo, Exception e) {
        if(e instanceof NonRetriableException && e.getMessage().contains("No active warehouse selected in the current session")) {
            if(!connectorInfo.getMetaConfig().containsKey(SF_ROLE)) {
                throw new RuntimeException("Invalid value for role");
            }
            if(!connectorInfo.getMetaConfig().containsKey(SCHEMA_NAME)) {
                throw new RuntimeException("Invalid value for schema");
            }
            String role = (String) connectorInfo.getMetaConfig().get(SF_ROLE);
            String schema = (String) connectorInfo.getMetaConfig().get(SCHEMA_NAME);
            throw new NonRetriableException(((NonRetriableException) e).getErrorCode(), "Check if " + role + " role has access to the schema " + schema, ((NonRetriableException) e).getStatusCode(), (Exception) e.getCause());
        }
    }

    @Override
    protected boolean isNoTimezoneWatermark(String datatype) {
        if(datatype.equalsIgnoreCase("timestamp_ntz")) {
            return true;
        }
        return false;
    }

    @Override
    String getDescribeSql(ConnectorInfo connector) {
        return String.format(SnowflakeQueries.DESCRIBE_ENTITY, connector.getMetaConfig().get(SCHEMA_NAME).toString());
    }

    @Override
    String getDescribeFieldSqlForLateBindingViews(ConnectorInfo connectorInfo) {
        return "";
    }

    @Override
    String getDescribeFieldSql(ConnectorInfo connector, String tableName) {
        return String.format(SnowflakeQueries.DESCRIBE_FIELD, replaceU0024WithDollar(tableName), connector.getMetaConfig().get(SCHEMA_NAME).toString());
    }

    @Override
    String getSelectByIdsSql() {
        return SnowflakeQueries.SELECT_BY_IDS;
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
    String getSchemaName(ConnectorInfo connector) {
        return "";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {

        if (request.getEntitySchema() == null)
            throw new RuntimeException("Schema cannot be null");

        var entitySchema = request.getEntitySchema();
        var isResync = request.getWatermark().isResync();
        if (!entitySchema.hasWatermarkField() && supportsNoWatermark(request.getConnector())) {
            return isResync ? getBySortedKeys(request) : emptyIterator(request);
        } else {
            return super.getByCursorBasedWatermark(request);
        }
    }

    @Override
    public String getCursorWatermarkCondition(SyncRequest request, String cursor, int pageSize) {
        if (CollectionUtils.isEmpty(request.getEntitySchema().getCompositeKeyFields())) {
            return getCursorWatermarkConditionNonComposite(request, cursor, pageSize);
        }

        String query = "";
        long prevWatermark = 0;

        List<String> idFields = request.getEntitySchema().getCompositeKeyFields().stream()
                .map(AttributeSchema::getApiName)
                .map(this::getCased)
                .collect(Collectors.toList());

        String nextID = formatId(request, cursor);
        log.info("Next Id is {} and id datatype is {}", cursor, request.getEntitySchema().getIdField().getDataType());

        List<String> nextIDValues;
        String nextIDValuesTuple = "";
        if (StringUtils.isNotEmpty(cursor)) {
            if (cursor.contains("#")) {
                String[] parts = cursor.split("#", 2);
                if (parts.length == 2) {
                    try {
                        prevWatermark = Long.valueOf(parts[0]);
                        nextID = parts[1];
                    } catch (Exception e) {
                        log.error("Error when parsing Snowflake changestream - {}, {}", cursor, e.getMessage());
                    }
                }
            }

            nextIDValues = new ArrayList<>();
            String[] values = nextID.split("\\|");
            for (String value : values) {
                nextIDValues.add(value.trim());
            }

            nextIDValuesTuple = nextIDValues.stream()
                    .map(v -> {
                        if (v.startsWith("'") && v.endsWith("'")) {
                            return v;
                        }
                        return formatId(request, v);
                    })
                    .collect(Collectors.joining(","));
        }

        String idFieldsTuple = String.join("\",\"", idFields);

        final AttributeSchema watermarkField = request.getEntitySchema().getWatermarkField();
        String watermarkFieldName = getCased(watermarkField.getApiName());
        long previousBatchMaxWatermark = getPreviousBatchMaxWatermark(request);
        long start = !request.getWatermark().isResync() && previousBatchMaxWatermark > -1 ? previousBatchMaxWatermark : request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        if ("timestamp".equalsIgnoreCase(watermarkField.getDataType()) ||
                "datetime".equalsIgnoreCase(watermarkField.getDataType()) ||
                "date".equalsIgnoreCase(watermarkField.getDataType())) {
            boolean dateType = "date".equalsIgnoreCase(watermarkField.getDataType());
            var zoneId = getTimezone(request);
            String startTimestamp = dateType ? dateUtil.format(start, dateFormat, zoneId) : dateUtil.format(start, dateTimeFormat, zoneId);
            String endTimestamp = dateType ? dateUtil.format(end, dateFormat, zoneId) : dateUtil.format(end, dateTimeFormat, zoneId);
            if (dateType && startTimestamp.equalsIgnoreCase(endTimestamp)) {
                startTimestamp = dateUtil.subtractDays(startTimestamp, dateFormat, 1);
            }
            String timestamp = (prevWatermark > 0)
                    ? (dateType ? dateUtil.format(prevWatermark, dateFormat, zoneId) : dateUtil.format(prevWatermark, dateTimeFormat, zoneId))
                    : startTimestamp;

            if (StringUtils.isNotEmpty(nextIDValuesTuple)) {
                query = String.format(keysetPaginationClauseTemplate, watermarkFieldName, idFieldsTuple,
                        timestamp, nextIDValuesTuple, watermarkFieldName, idFieldsTuple, pageSize);
            } else {
                query = String.format(simplePaginationClauseTemplate, watermarkFieldName,
                        timestamp, watermarkFieldName, idFieldsTuple, pageSize);
            }
        } else {
            query = String.format(simplePaginationClauseTemplate, watermarkFieldName, start, watermarkFieldName, idFieldsTuple, pageSize);
        }
        log.info("Snowflake composite field watermark condition - {}", query);
        return query;
    }

    public String getCursorWatermarkConditionNonComposite(SyncRequest request, String cursor, int pageSize) {
        String idField = getCased(request.getEntitySchema().getIdField().getApiName());
        String query = "";
        long prevWatermark = 0;
        String nextID = formatId(request, cursor);
        log.info("Next Id is {} and id datatype is {}", cursor, request.getEntitySchema().getIdField().getDataType());
        if (StringUtils.isNotEmpty(cursor)) {
            if (cursor.contains("#")) {
                String[] parts = cursor.split("#", 2);
                if(parts.length == 2) {
                    try {
                        prevWatermark = Long.valueOf(parts[0]);
                        nextID = formatId(request, parts[1]);
                    } catch (Exception e) {
                        log.error("Error when parsing Snowflake changestream - {}, {}", cursor, e.getMessage());
                    }
                }
            }
        }
        final AttributeSchema watermarkField = request.getEntitySchema().getWatermarkField();
        String watermarkFieldName = getCased(watermarkField.getApiName());
        long previousBatchMaxWatermark = getPreviousBatchMaxWatermark(request);
        long start = !request.getWatermark().isResync() && previousBatchMaxWatermark > -1 ? previousBatchMaxWatermark : request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        if (("timestamp".equalsIgnoreCase(watermarkField.getDataType()) ||
                "datetime".equalsIgnoreCase(watermarkField.getDataType()) ||
                "date".equalsIgnoreCase(watermarkField.getDataType())) && (StringUtils.isNotEmpty(nextID))) {
            boolean dateType = "date".equalsIgnoreCase(watermarkField.getDataType());
            var zoneId = getTimezone(request);
            String startTimestamp = dateType ? dateUtil.format(start, dateFormat, zoneId) : dateUtil.format(start, dateTimeFormat, zoneId);
            String endTimestamp = dateType ? dateUtil.format(end, dateFormat, zoneId) : dateUtil.format(end, dateTimeFormat, zoneId);
            if(dateType && startTimestamp.equalsIgnoreCase(endTimestamp)) {
                startTimestamp = dateUtil.subtractDays(startTimestamp, dateFormat, 1);
            }
            String prevTimestamp = "";
            if (prevWatermark > 0) {
                prevTimestamp = dateType ? dateUtil.format(prevWatermark, dateFormat, zoneId) : dateUtil.format(prevWatermark, dateTimeFormat, zoneId);
                query = String.format(keysetPaginationClauseTemplate, watermarkFieldName, idField, prevTimestamp, nextID, watermarkFieldName, idField, pageSize);
            } else {
                query = String.format(keysetPaginationClauseTemplate, watermarkFieldName, idField, startTimestamp, nextID, watermarkFieldName, idField, pageSize);
            }

        } else {
            query = String.format(simplePaginationClauseTemplate, watermarkFieldName, start, watermarkFieldName, idField, pageSize);
        }
        log.info("Snowflake watermark condition - {}", query);
        return query;
    }

    private ZoneId getTimezone(SyncRequest request) {
        return request.getEntitySchema().getWatermarkField().isNoTimezoneWatermark() ?
                ZoneId.of(getZoneId(request.getConnector())) : ZoneId.of("UTC");
    }

    private String formatId(SyncRequest request, String nextID) {
        if ("string".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType()) && StringUtils.isNotEmpty(nextID)) {
            nextID = String.format("'%s'", nextID.replace("'", "''"));
        }
        return nextID;
    }

    @Override
    public long getLastModified(String datatype, ResultSet rs, AttributeSchema field, ZoneId zoneId) throws SQLException {

        long lastModified = super.getLastModified(datatype, rs, field.getApiName());
        if (field.isNoTimezoneWatermark()) {
            // we are getting epoch at this time zone, but we convert to UTC strip off zone and attach local zone
            // for example if input timestamp 2025-03-04 15:52:08 PM (1741103528000) in Phoenix timezone then we want to convert to UTC timezone that correponds to it.
            return Instant.ofEpochMilli(lastModified).atZone(ZoneId.of("UTC")).withZoneSameLocal(zoneId).toInstant().toEpochMilli();
        }
        return lastModified;
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
    protected Object computedValue(AttributeSchema attr, Object columnValue) {
        if(columnValue != null && "timestamp".equalsIgnoreCase(attr.getDataType())) {
            return dateUtil.format(((Instant)columnValue).toEpochMilli(), dateTimeFormat);
        }
        if(columnValue != null && "datetime".equalsIgnoreCase(attr.getDataType())) {
            return dateUtil.format(((Instant)columnValue).toEpochMilli(), dateTimeFormat);
        }
        if(columnValue != null && "date".equalsIgnoreCase(attr.getDataType())) {
            return new java.sql.Date(((java.util.Date)columnValue).toInstant().toEpochMilli());
        }
        return columnValue;
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
    protected Map<String, ForeignKey> getForeignKeys(ConnectorInfo connectorInfo, Connection conn, String entityName) {
        Map<String, ForeignKey> foreignKeyMap = new HashMap<>();
        try (Statement stmt = conn.createStatement()) {
            String sql = getForeignKeysQuery(replaceU0024WithDollar(entityName), connectorInfo);
            log.info(sql);
            try(ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    ForeignKey foreignKey = new ForeignKey();
                    foreignKey.setReferenceTo(rs.getString("pk_table_name"));
                    foreignKey.setReferenceTargetField(rs.getString("pk_column_name"));
                    foreignKeyMap.put(rs.getString("fk_column_name"), foreignKey);
                }
            }
        } catch (Exception e) {
            handleException(e, connectorInfo);
        }
        return foreignKeyMap;
    }

    @Override
    protected void setForeignKeys(List<AttributeSchema> attributes, Map<String, ForeignKey> foreignKeyMap) {
        for(AttributeSchema attribute: attributes) {
            if(foreignKeyMap.containsKey(attribute.getApiName())) {
                ForeignKey key = foreignKeyMap.get(attribute.getApiName());
                attribute.setDataType("reference");
                attribute.setReferenceTo(key.getReferenceTo());
                attribute.setReferenceTargetField(key.getReferenceTargetField());
            }
        }
    }

    @Override
    protected String getForeignKeysQuery(String tableName, ConnectorInfo connectorInfo) {
        return String.format(SnowflakeQueries.SHOW_IMPORTED_KEYS, connectorInfo.getMetaConfig().get(DB_NAME),
                connectorInfo.getMetaConfig().get(SCHEMA_NAME), replaceU0024WithDollar(tableName));
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(),
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(),
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(oAuthRequest.getEndpoint() + GET_ACCESS_TOKEN, map);
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

        return tokenHandler.refreshToken(config, config.getEndpoint() + GET_ACCESS_TOKEN, map);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return OAUTH_AUTHORIZE + "?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code&state={{state}}";
    }
    
    @Override
    protected boolean shouldReplaceDollar() {
      return true;
    }

    @Override
    protected int getInitializationFailTimeout() {
        // to prevent endless retries on OAuth token expiration
        return 30000;
    }

}
