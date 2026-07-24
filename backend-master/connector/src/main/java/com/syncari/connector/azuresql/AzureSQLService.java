package com.syncari.connector.azuresql;

import com.microsoft.aad.msal4j.*;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.syncari.connector.Constants;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.database.CompositeKeyHelper;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.connector.Constants.SCHEMA_NAME;
import static com.syncari.utils.ExceptionUtils.rethrow;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.AZURE_SQL)
public class AzureSQLService implements CommonDataService, MetadataService, SynapseInfoService, OauthAuthenticationService {

    private static final String TOKEN_ENDPOINT = "tokenEndpoint";
    private static final int QUERY_SIZE = 1000;
    private static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String SYNCARI_WATERMARK = "syncari_watermark";
    public static final String QUOTE = "'";
    public static final String TIME_ZONE_ID = "timeZoneId";

    @Autowired
    DateUtil dateUtil;

    @Autowired
    CompositeKeyHelper compositeKeyHelper;

    private static final Map<String, String> SQL_TO_SYNCARI_DATATYPE_MAP = Map.ofEntries(
            Map.entry("bit","boolean"),
            Map.entry("int","integer"),
            Map.entry("bigint","integer"),
            Map.entry("float","double"),
            Map.entry("tinyint","integer"),
            Map.entry("numeric","double"),
            Map.entry("smallint","integer"),
            Map.entry("decimal","double"),
            Map.entry("real","double"),
            Map.entry("money","double"),
            Map.entry("smallmoney","double"),
            Map.entry("nchar","string"),
            Map.entry("nvarchar","string"),
            Map.entry("char","string"),
            Map.entry("varchar","string"),
            Map.entry("text","string"),
            Map.entry("ntext","string"),
            Map.entry("uniqueidentifier","string"),
            Map.entry("datetimeoffset","datetime"),
            Map.entry("datetime","datetime"),
            Map.entry("date","date"),
            Map.entry("datetime2","datetime"),
            Map.entry("smalldatetime","datetime")
    );

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        try {
            getAccessToken(config);
            Connection connection = getDBConnection(config);
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT SUSER_SNAME()");
            if (rs.next()) {
                final TestConnectionResponse success = new TestConnectionResponse(null, "", List.of());
                success.setAuthConfig(config.getAuthConfig());
                rs.close();
                connection.close();
                return success;
            } else {
                log.error("Authentication failed");
                rs.close();
                connection.close();
                return new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR, List.of("User not found"));
            }
        } catch (Exception e) {
            log.error("Authentication failed", e);
            return new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR, List.of(e.getMessage()));
        }
    }

    private Connection getDBConnection(ConnectorInfo connector) {
        try {
            AuthConfig config = connector.getAuthConfig();
            SQLServerDataSource ds = new SQLServerDataSource();
            ds.setServerName(getValue(connector, Constants.SERVER_NAME));
            ds.setDatabaseName(getValue(connector, Constants.DATABASE_NAME));
            if(isUserPwd(config)) {
                ds.setUser(connector.getAuthConfig().getUserName());
                ds.setPassword(connector.getAuthConfig().getPassword());
            } else {
                ds.setAccessToken(config.getAccessToken());
            }
            ds.setTrustServerCertificate(true);
            return ds.getConnection();
        } catch (Exception e) {
            throw new RuntimeException("Error in establishing DB connection - " + e.getMessage());
        }
    }

    protected AuthConfig getAccessToken(ConnectorInfo connector) {
        try {
            AuthConfig authConfig = connector.getAuthConfig();
            if(isUserPwd(authConfig)) return  authConfig;
            // Retrieve the access token from the AD.
            String spn = "https://database.windows.net/";
            String accessTokenURL = getValue(connector, TOKEN_ENDPOINT);
            if(StringUtils.isBlank(accessTokenURL)) throw new RuntimeException("Access token endpoint is required");
            String clientId = authConfig.getClientId();
            String clientSecret = authConfig.getClientSecret();

            String scope = spn + "/.default";
            Set<String> scopes = new HashSet<>();
            scopes.add(scope);

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            IClientCredential credential = ClientCredentialFactory.createFromSecret(clientSecret);
            ConfidentialClientApplication clientApplication = ConfidentialClientApplication
                    .builder(clientId, credential).executorService(executorService).authority(accessTokenURL).build();
            CompletableFuture<IAuthenticationResult> future = clientApplication
                    .acquireToken(ClientCredentialParameters.builder(scopes).build());

            IAuthenticationResult authenticationResult = future.get();
            authConfig.setAccessToken(authenticationResult.accessToken());
            authConfig.setRefreshToken(authenticationResult.accessToken());
            long expiresIn = authenticationResult.expiresOnDate().toInstant().getEpochSecond() - Instant.now().getEpochSecond();
            authConfig.setExpiresIn(String.valueOf(expiresIn));
            authConfig.setLastRefreshed(Instant.now());
            return authConfig;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch access token - " + exception.getMessage());
        }
    }

    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if(!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }
        if (request.getEntitySchema().getWatermarkField() == null)
            throw new RuntimeException("Watermark field cannot be null");
        WatermarkInfo watermark = request.getWatermark();
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            Pair<Long, Stream<EntityData>> response = null;
            try (Connection conn = getDBConnection(request.getConnector())) {
                try (Statement stmt = conn.createStatement()) {
                    List<String> fieldNames = getFields(request.getEntitySchema());
                    String sql = String.format(AzureSQLQueries.SELECT,
                            getTableName(request.getEntityName(), request.getConnector()),
                            getWatermarkCondition(request, offset, pageSize));
                    log.info(sql);
                    List<EntityData> result = extractData(request, stmt, fieldNames, sql);
                    response = Pair.of(Long.valueOf(result.size()), result.stream());
                }
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                throw new RuntimeException("Failed to fetch records - " + e.getMessage());
            }
            return response;
        };

        int pageSize = request.getPageSize() == 0 ? QUERY_SIZE : Math.min(request.getPageSize(), QUERY_SIZE);
        DefaultDataIterator iterator = createIterator(request, watermark, generator, pageSize);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    String getWatermarkCondition(SyncRequest request, long offset, int pageSize) {
        String watermarkField = request.getEntitySchema().getWatermarkField().getApiName();
        String idField = request.getEntitySchema().getIdField().getApiName();
        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        String startTimestamp = String.valueOf(request.getWatermark().getStart());
        String endTimestamp = String.valueOf(request.getWatermark().getEnd());

        if(SYNCARI_WATERMARK.equalsIgnoreCase(watermarkField)) {
            return " ORDER BY " + addQuotes(idField) + " OFFSET " + offset + " ROWS FETCH NEXT "+pageSize+" ROWS ONLY";
        }
        if ("time".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "datetime".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType()) ||
                "date".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType())) {
            startTimestamp = dateUtil.format(start, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
            endTimestamp = dateUtil.format(end, dateFormat, ZoneId.of(getZoneId(request.getConnector())));
        }

        if ("datetime".equalsIgnoreCase(request.getEntitySchema().getWatermarkField().getDataType())) {
            startTimestamp = "TRY_CONVERT(DATETIME,'" + startTimestamp+"')";
            endTimestamp = "TRY_CONVERT(DATETIME,'" + endTimestamp+"')";
        }

        return " WHERE " + addQuotes(watermarkField) + " >= " + startTimestamp
                + " AND " + addQuotes(watermarkField) + " <= " + endTimestamp
                + " ORDER BY "+ addQuotes(watermarkField) + "," + addQuotes(idField) + " OFFSET " + offset + " ROWS FETCH NEXT "+pageSize+" ROWS ONLY";
    }

    protected DefaultDataIterator createIterator(SyncRequest request, WatermarkInfo watermark,
                                                 Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator, int pageSize) {
        if(request.getEntitySchema().getWatermarkField().isSyncariDefined()) pageSize = 1000;
        AttributeSchema wmField = request.getEntitySchema().getWatermarkField();
        DefaultDataIterator iterator = new AzureIterator(watermark, watermark.getOffset(), generator,
                new ArrayList<>(), request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit(),
                wmField.getDataType());
        return iterator;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        String idsAsString = String.join(",", getIds(request, true));
        boolean isComposite = !StringUtils.isBlank(request.getEntitySchema().getIdField().getCompositeKey());
        int idCount = request.getData().get(request.getConnector().getId()).size();

        List<EntityData> result;
        if(!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }

        List<String> fieldNames = getFields(request.getEntitySchema());
        String sql = getQuery(request, isComposite, idsAsString);

        log.debug("Fetching {} records by IDs for entity {} on connector {}",
                idCount, request.getEntityName(), request.getConnector().getName());
        log.debug("SQL Query: {}", sql);

        try (Connection conn = getDBConnection(request.getConnector())) {
            log.debug("Database connection established for connector {}", request.getConnector().getName());
            try (Statement stmt = conn.createStatement()) {
                log.debug("Executing SQL query for {} IDs", idCount);
                result = extractData(request, stmt, fieldNames, sql);
                log.debug("Found {} records for getByIds {}", result.size(), idsAsString);//todo would be a log if this is fetched
            }
        } catch (Exception e) {
            log.error("Unexpected exception while fetching by IDs for entity {} on connector {}: {}",
                    request.getEntityName(), request.getConnector().getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch by id - " + e.getMessage());
        }
        return result;
    }

    private String getQuery(SyncRequest request, Boolean isComposite, String idsAsString) {
        String sql = "";
        if(isComposite) {
            List<String> ids = getIds(request, false);
            List<String> idPredicates = new ArrayList<>();
            String[] keys = request.getEntitySchema().getCompositeKeyFields().stream().map(a -> a.getApiName()).toArray(String[]::new);
            for(String id : ids) {
                List<String> innerPredicate = new ArrayList<>();
                String[] values = id.split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
                for (int i = 0; i < keys.length; i++) {
                    innerPredicate.add("\"" + keys[i] + "\"" + " = " + QUOTE + values[i] + QUOTE);
                }
                idPredicates.add("("+innerPredicate.stream().collect(Collectors.joining(" AND "))+")");
            }
            sql = String.format(AzureSQLQueries.SELECT_BY_IDS_COMPOSITE,
                    getTableName(request.getEntityName(), request.getConnector()),
                    StringUtils.join(idPredicates, " OR "));
        } else {
            sql = String.format(AzureSQLQueries.SELECT_BY_IDS,
                    getTableName(request.getEntityName(), request.getConnector()), addQuotes(request.getEntitySchema().getIdField().getApiName()),
                    idsAsString);
        }
        log.debug(sql);
        return sql;
    }

    private List<EntityData> extractData(SyncRequest request, Statement stmt, List<String> fieldNames, String sql) throws SQLException {
        log.debug("Starting extractData for entity {}", request.getEntityName());
        ResultSet rs = stmt.executeQuery(sql);
        log.debug("SQL query executed successfully, processing result set");
        List<EntityData> result = new ArrayList<>();
        if (!request.getEntitySchema().hasIdField()) {
            log.debug("Id field not defined for entity " + request.getEntityName());
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }
        AttributeSchema idField = request.getEntitySchema().getIdField();
        Optional<AttributeSchema> wmField = request.getEntitySchema().getWatermarkAttr();
        final long startWM = request.getWatermark() == null ? Instant.now().toEpochMilli() : request.getWatermark().getStart();
        String zoneId = getZoneId(request.getConnector());
        log.debug("Starting extractData to results");
        while (rs.next()) {
            EntityData data = new EntityData(request.getEntityName());
            data.setConnectorId(request.getConnector().getId());
            wmField.ifPresent(wm -> {
                if (!wm.isSyncariDefined()) {
                    data.setLastModified(getLastModified(wm.getDataType(), rs, wm.getApiName(), zoneId));
                } else {
                    //fabricated wm. set it to end of current wm to avoid pruning in DefaultDataIterator
                    data.setLastModified(startWM);
                }
            });

            EntitySchema schema = request.getEntitySchema();
            fieldNames.forEach(f -> {
                if (schema.hasField(f) && !schema.getField(f).get().isSyncariDefined()) {
                    Object value = schema.getField(f).map(field ->
                            extractVal(rs, field.getDataType(), f, zoneId)
                    ).orElseGet(() -> rethrow(() -> rs.getObject(f)));
                    data.addValue(f, value);
                }
            });

            if (idField != null && !StringUtils.isBlank(idField.getCompositeKey())) {
                data.setId(compositeKeyHelper.composeIdKeys(data, request.getEntitySchema()));
            } else {
                data.setId(rs.getString(idField.getApiName()));
            }

            result.add(data);
        }
        log.debug("Completed extractData: extracted {} valid records", result.size());
        return result;
    }

    private List<String> getIds(SyncRequest request, Boolean quote) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        String datatype = request.getEntitySchema().getIdField().getDataType();
        return entityList.stream().map(e -> {
            if("string".equalsIgnoreCase(datatype)) {
                return quote ? "'"+e.getId()+"'" : e.getId();
            } else return e.getId();
        }).collect(Collectors.toList());
    }

    List<String> getFields(EntitySchema entity) {
        return entity.getAttributes().stream()
                .filter(a -> !a.isIdField() || (a.isIdField() && StringUtils.isNotBlank(a.getCompositeKey())))
                .map(a -> a.getApiName()).collect(Collectors.toList());
    }

    protected String getDecoratedFieldName(String attr) {
        return "'" + attr + "'";
    }

    protected String getTableName(String entity, ConnectorInfo connector) {
        return !StringUtils.isBlank(getValue(connector, SCHEMA_NAME)) ? getValue(connector, SCHEMA_NAME) + "." + addQuotes(entity) : addQuotes(entity);
    }

    protected String addQuotes(String name) {
        return "\"" + name + "\"";
    }

    @SneakyThrows
    protected long getLastModified(String datatype, ResultSet rs, String field, String zoneId) {
        Calendar zone = Calendar.getInstance(TimeZone.getTimeZone(zoneId));
        //Datatype is always a syncari datatype and not SQL.
        switch (datatype) {
            case "integer":
                return rs.getLong(field) == 0L ? Instant.now().toEpochMilli() : rs.getLong(field);
            case "date":
                Date date = rs.getDate(field, zone);
                return date == null ? Instant.now().toEpochMilli() : date.toInstant().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
            case "datetime":
                Timestamp timestamp = rs.getTimestamp(field, zone);
                return timestamp == null ? Instant.now().toEpochMilli() : timestamp.toInstant().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
            default:
                throw new RuntimeException("Unsupported datatype " + datatype + " for watermark for field " + field);
        }
    }

    private Object extractVal(ResultSet rs, String dataType, String fieldName, String zoneId) {
        Calendar zone = Calendar.getInstance(TimeZone.getTimeZone(zoneId));
        try{
            switch (dataType) {
                case "date":
                    Date date = rs.getDate(fieldName, zone);
                    return date == null ? null : new Date(date.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli());
                case "datetime":{
                    final Timestamp timestamp = rs.getTimestamp(fieldName, zone);
                    return timestamp == null ? null : timestamp.toInstant().atZone(ZoneOffset.UTC);
                }
                case "string": {
                    return rs.getString(fieldName);
                }
                default:
                    return rs.getObject(fieldName);
            }
        }catch (SQLException e){
            log.error("Exception occurred {} and cause is {}", e.getMessage(), e.getCause());
        }
        return null;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return null;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        try (Connection connection = getDBConnection(request.getConnector())) {
            try (Statement stmt = connection.createStatement()) {
                String query = String.format(AzureSQLQueries.DESCRIBE_ENTITY, getValue(request.getConnector(), SCHEMA_NAME), request.getEntity());
                log.debug(query);
                ResultSet rs = stmt.executeQuery(query);
                EntitySchema e = new EntitySchema(request.getEntity(), StringUtils.capitalize(request.getEntity()));
                while (rs.next()) {
                    populateEntitySchema(rs, e);
                }
                if(!e.getAttributes().isEmpty()) {
                    e.addField(getDefaultWmField());
                }
                rs.close();
                connection.close();
                return Optional.of(e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch schema - " + e.getMessage());
        }
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schema = new ArrayList<>();
        Map<String, EntitySchema> schemamap = new HashMap<>();
        try (Connection connection = getDBConnection(request.getConnector())) {
            try (PreparedStatement stmt = connection.prepareStatement(AzureSQLQueries.GET_TABLES)) {
                stmt.setString(1,getValue(request.getConnector(), SCHEMA_NAME));
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String entityName = rs.getString("table_name");
                    if(!schemamap.containsKey(entityName)) {
                        EntitySchema entity = new EntitySchema(entityName, StringUtils.capitalize(entityName));
                        schemamap.put(entityName, entity);
                    }
                    populateEntitySchema(rs, schemamap.get(entityName));
                    AttributeSchema defaultWmField = getDefaultWmField();
                    if(!schemamap.get(entityName).getAttributes().isEmpty() && schemamap.get(entityName).hasField(defaultWmField.getApiName())) {
                        schemamap.get(entityName).addField(defaultWmField);
                    }
                }
                rs.close();
                connection.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch schema - " + e.getMessage());
        }
        return List.copyOf(schemamap.values());
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return null;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        return null;
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {

    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new RuntimeException("OAuth2 Authorization Code flow not supported by Business Central");
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        return getAccessToken(connector);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "";
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        final AuthMetadata simpleOAuthType = new AuthMetadata(AuthType.SimpleOAuth, new ArrayList<>(List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField())), "Simple OAuth", "");

        return List.of(
                ConnectorHelper.getUserPwd(),
                simpleOAuthType
        );
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField server = new AuthField().setRequired(true).setDataType("text").setName(Constants.SERVER_NAME)
                .setLabel("Server Name");
        AuthField schemaName = new AuthField().setRequired(true).setDataType("text").setName(SCHEMA_NAME)
                .setLabel("Schema Name");
        AuthField dbName = new AuthField().setRequired(true).setDataType("text").setName(Constants.DATABASE_NAME)
                .setLabel("Database Name");
        AuthField tokenEndpoint = new AuthField().setName(TOKEN_ENDPOINT).setLabel("Access Token URL").setDataType("string")
                .setDescription("This is used to get an access code. It's generally of the format https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token");
        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("db_timezone_label"));
        timeZone.setHelpSummary(i18n("db_timezone_help"));
        timeZone.setRequired(false);
        return List.of(server, schemaName, dbName, tokenEndpoint, timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    protected String getZoneId(ConnectorInfo connector) {
        return connector.getMetaConfig().getOrDefault(TIME_ZONE_ID, "UTC").toString();
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
    public String getName() {
        return Constants.AZURE_SQL;
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/azuresql.svg")
                .setDisplayName("Azure SQL")
                .setBackgroundColor("#F5FDFF")
                .setHelpUrl(helpArticlesBaseUrl + "/20985728379412-Azure-SQL-Setup");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "20982278540948";
    }

    @Override
    public List<Capability> getCapabilities() {
        return List.of(Capability.userEditableId, Capability.userEditableWm, Capability.schemaEditInSyncari, Capability.compositeId);
    }

    @Override
    public boolean isSink() {
        return false;
    }

    protected boolean isUserPwd(AuthConfig authConfig) {
        return (!StringUtils.isBlank(authConfig.getUserName()) && !StringUtils.isBlank(authConfig.getPassword()));
    }

    private Optional<EntitySchema> populateEntitySchema(ResultSet rs, EntitySchema entitySchema) throws SQLException {
        String name = rs.getString("name");
        String type = SQL_TO_SYNCARI_DATATYPE_MAP.getOrDefault(rs.getString("datatype"), "string");
        AttributeSchema attr = new AttributeSchema(name, type);
        attr.setDisplayName(name);
        attr.setNillable("YES".equalsIgnoreCase(rs.getString("is_nullable")));
        String maxLength=null;
        try{
            maxLength = rs.getString("max_length");
        } catch (Exception e){

        }
        if (maxLength != null) {
            try {
                attr.setLength(Integer.valueOf(maxLength));
            } catch (NumberFormatException e) {
                attr.setLength(Integer.MAX_VALUE);
                log.warn("Setting integer max for field {} on table {} since its length is {}", name, entitySchema.getApiName(), maxLength);
            }
        }
        if("double".equals(type)){
            attr.setPrecision(rs.getInt("precision"));
            attr.setScale(rs.getInt("scale"));
        }
        entitySchema.addField(attr);
        return Optional.of(entitySchema);
    }

    private AttributeSchema getDefaultWmField() {
        AttributeSchema attr = new AttributeSchema(SYNCARI_WATERMARK, "integer");
        attr.setDisplayName("Default Watermark Field");
        attr.setSyncariDefined(true);
        return attr;
    }

}

class AzureIterator extends DefaultDataIterator {

    public AzureIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords,
                         String wmDataType) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords, "UTC", wmDataType);
    }

    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        // if no data is retrieved meaning the window is exhausted or if using fabricated field - reset the offset
        if(data.isEmpty()) return 0;
        return offset + data.size();
    }

    @Override
    public long getLastOffset() {
        return offset;
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.RECORD_COUNT, getEffectivePageSize());
    }
}
