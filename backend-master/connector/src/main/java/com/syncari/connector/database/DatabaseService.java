package com.syncari.connector.database;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.exception.EntityException;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.*;
import com.syncari.connector.service.query.SqlQueries;
import com.syncari.utils.DateTimeUtil;
import com.syncari.utils.I18n;
import com.syncari.utils.Pair;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Date;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.ExceptionUtils.rethrow;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public abstract class DatabaseService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService  {
    protected static final long MAX_VARCHAR_LENGTH = 65535;
    public static final List<String> WM_FIELD_TYPES = List.of("date", "datetime", "timestamp");
    protected static final int CONNECTION_TIMEOUT = 300000; // 5 mins, connection timeout should be greater than validation timeout
    protected static final int VALIDATION_TIMEOUT = 120000;
    protected static final int MAX_LIFETIME = 900000; // 15 mins
    protected static final int SOCKET_TIMEOUT = 300; // only used for Postgres SQL right now, in seconds
    protected static final int IDLE_TIMEOUT = 1800000;
    protected static final String STRING = "string";
    private static final String NUMBER = "number";
    public static final String COMMA = ",";
    static final int QUERY_SIZE = 1000;
    public static final String SCHEMA_NAME = "schemaName";
    public static final String POOL_SIZE = "poolSize";
    public static final String CONNECTION_TIMEOUT_PARAM = "connectionTimeout";
    public static final String SOCKET_TIMEOUT_PARAM = "socketTimeout";
    public static final String TIME_ZONE_ID = "timeZoneId";
    private final int QUERY_TIMEOUT = 600;
    // This is a postgres specific limit, but might make sense to limit all variables to less than this
    protected static final int MAX_BIND_PARAMETER_SIZE = 32767;

    protected static final String CASE_CONFIGURATION = "caseConfiguration";
    public static String COUNT_QUERY = "Select count(syncariid) as totalCount from %s";
    public static String COUNT_WITH_INNERQUERY = "Select count(*) as \"totalCount\" from (%s) alias";

    @Autowired
    CompositeKeyHelper compositeKeyHelper;

    protected static void loadDriver(String driverClass) {
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {

        }
    }

    protected Optional<Properties> getAdditionalProperties(ConnectorInfo connector) {
        return Optional.empty();
    }

    protected LoadingCache<ConnectionInfoWrapper, HikariDataSource> poolCache = CacheBuilder.newBuilder()
            .maximumSize(100000)
            .expireAfterAccess(20, java.util.concurrent.TimeUnit.MINUTES)
            .removalListener(notification -> {
                if (notification.getValue() != null) {
                    HikariDataSource ds = (HikariDataSource) notification.getValue();
                    try {
                        ds.close();
                        log.info("Connection pool closed successfully");
                    } catch (Exception e) {
                        log.warn("Error closing connection pool: {}", e.getMessage());
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
                    String urlStr = getJdbcURL(connector);
                    Properties props = new Properties();
                    Optional<Properties> additionalProperties = getAdditionalProperties(connector);
                    additionalProperties.ifPresent(props::putAll);
                    ds.setDataSourceProperties(props);
                    ds.setJdbcUrl(urlStr);
                    ds.setUsername(connector.getAuthConfig().getUserName());
                    ds.setPassword(connector.getAuthConfig().getPassword());
                    ds.setMinimumIdle(0);
                    ds.setIdleTimeout(IDLE_TIMEOUT);
                    ds.setConnectionTestQuery("SELECT 1");
                    return ds;
                };
            });

    protected LoadingCache<ConnectionInfoWrapper, HikariDataSource> poolCache2 = CacheBuilder.newBuilder()
            .maximumSize(100000)
            .expireAfterAccess(20, java.util.concurrent.TimeUnit.MINUTES)
            .removalListener(notification -> {
                if (notification.getValue() != null) {
                    HikariDataSource ds = (HikariDataSource) notification.getValue();
                    try {
                        ds.close();
                        log.info("Read-only connection pool closed successfully");
                    } catch (Exception e) {
                        log.warn("Error closing read-only connection pool: {}", e.getMessage());
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
                    ds.setValidationTimeout(VALIDATION_TIMEOUT);
                    ds.setMaxLifetime(MAX_LIFETIME);
                    ds.setInitializationFailTimeout(getInitializationFailTimeout());
                    ds.setConnectionTimeout((int) connector.getMetaConfig().getOrDefault(CONNECTION_TIMEOUT_PARAM, CONNECTION_TIMEOUT));
                    String urlStr = getJdbcURL(connector);
                    Properties props = new Properties();
                    Optional<Properties> additionalProperties = getAdditionalProperties(connector);
                    additionalProperties.ifPresent(props::putAll);
                    ds.setDataSourceProperties(props);
                    ds.setJdbcUrl(urlStr);
                    ds.setUsername(connector.getAuthConfig().getUserName());
                    ds.setPassword(connector.getAuthConfig().getPassword());
                    ds.setMinimumIdle(0);
                    ds.setIdleTimeout(IDLE_TIMEOUT);
                    ds.setConnectionTestQuery("SELECT 1");
                    ds.setReadOnly(true);
                    return ds;
                };
            });

    protected Connection getConnection(ConnectorInfo connector) throws ClassNotFoundException, SQLException {
        HikariDataSource dataSource = poolCache.getUnchecked(new ConnectionInfoWrapper(connector));
        return ConnectorHelper.withBackoff(() -> dataSource.getConnection());
    }

    protected Connection getReadOnlyConnection(ConnectorInfo connector) throws SQLException {
        HikariDataSource dataSource = poolCache2.getUnchecked(new ConnectionInfoWrapper(connector));
        return ConnectorHelper.withBackoff(() -> dataSource.getConnection());
    }

    protected void closeDatasource(ConnectorInfo connector) {
        final ConnectionInfoWrapper connectionInfoWrapper = new ConnectionInfoWrapper(connector);
        poolCache.getUnchecked(connectionInfoWrapper).close();
        poolCache.invalidate(connectionInfoWrapper);
    }

    abstract String getDescribeSql(ConnectorInfo connectorInfo);

    abstract String getDescribeFieldSqlForLateBindingViews(ConnectorInfo connectorInfo);

    abstract String getDescribeFieldSql(ConnectorInfo connectorInfo, String entityName);

    abstract String getSelectByIdsSql();

    abstract String getEscapeChar();

    abstract boolean isUpperCase();

    protected String getCased(String name) {
        if(StringUtils.isBlank(name)) return name;
        return isUpperCase() ? name.toUpperCase() : name;
    }

    abstract String getSchemaName(ConnectorInfo connectorInfo);

    protected int getInitializationFailTimeout(){
        return -1;
    }

    protected abstract String getJdbcURL(ConnectorInfo connector);
    protected String getZoneId(ConnectorInfo connector) {
        return connector.getMetaConfig().getOrDefault(DatabaseService.TIME_ZONE_ID, "UTC").toString();
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (request.getEntitySchema() == null)
            throw new RuntimeException("Schema cannot be null");
        if (request.getEntitySchema().getWatermarkField() == null)
            throw new RuntimeException("Watermark field cannot be null");

        WatermarkInfo watermark = request.getWatermark();
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            Pair<Long, Stream<EntityData>> response = null;
            try (Connection conn = getConnection(request.getConnector())) {
                try (Statement stmt = conn.createStatement()) {
                    List<String> fieldNames = getFields(request.getEntitySchema());
                    String sql = String.format(SqlQueries.SELECT,
                            getTableName(request.getEntityName(), request.getConnector()),
                            getWatermarkCondition(request, offset, pageSize));
                    sql = optionallyRemoveTrailingSemiColon(sql);
                    log.info(sql);
                    List<EntityData> result = extractData(request, stmt, fieldNames, sql);
                    response = Pair.of(Long.valueOf(result.size()), result.stream());
                }
            } catch (Exception e) {
                handleException(e, request.getConnector());
            }
            return response;
        };

        int pageSize = request.getPageSize() == 0 ? QUERY_SIZE : Math.min(request.getPageSize(), QUERY_SIZE);
        DefaultDataIterator iterator = createIterator(request, watermark, generator, pageSize);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private String sortedKeyPredicate(String nextPageUrl, AttributeSchema idSchema, int pageSize) {

        var columnNames = getCompositeKeyPredicate(idSchema);

        if (StringUtils.isBlank(nextPageUrl)) {
            return String.format(" ORDER BY %s LIMIT %s", columnNames, pageSize);
        } else {
            return String.format(" WHERE %s > (%s) ORDER BY %s LIMIT %s", columnNames, nextPageUrl, columnNames, pageSize);
        }
    }

    private String getNextPageUrl(List<EntityData> result, EntitySchema entitySchema) {
        if (result != null && result.size() > 0) {
            return compositeKeyHelper.getCompositeValuePredicate(entitySchema, result.get(result.size() - 1).getId()).orElse(null);
        }
        return null;
    }

    public FetchResponse getBySortedKeys(SyncRequest request) {

        if (request.getEntitySchema() == null)
            throw new RuntimeException("Schema cannot be null");

        WatermarkInfo watermark = request.getWatermark();
        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, changestream) -> {
            DataWithCursor response = null;
            try (Connection conn = getConnection(request.getConnector())) {
                try (Statement stmt = conn.createStatement()) {
                    List<String> fieldNames = getFields(request.getEntitySchema());
                    String sql = String.format(SqlQueries.SELECT_BY_ORDERED_KEYS,
                            getTableName(request.getEntityName(), request.getConnector()),
                            sortedKeyPredicate(changestream, request.getEntitySchema().getIdField(), pageSize));
                    log.debug(sql);
                    List<EntityData> result = extractData(request, stmt, fieldNames, sql);
                    response = new DataWithCursor(changestream, getNextPageUrl(result, request.getEntitySchema()), result);
                }
            } catch (Exception e) {
                handleException(e, request.getConnector());
            }
            return response;
        };

        int pageSize = request.getPageSize() == 0 ? QUERY_SIZE : Math.min(request.getPageSize(), QUERY_SIZE);

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(watermark, watermark.getChangeStream(),
                watermark.getOffset(), generator, new ArrayList<>(), pageSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }


    protected DefaultDataIterator createIterator(SyncRequest request, WatermarkInfo watermark,
            Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator, int pageSize) {
        AttributeSchema wmField = request.getEntitySchema().getWatermarkField();
        DefaultDataIterator iterator = new DBIterator(watermark, watermark.getOffset(), generator,
                new ArrayList<>(), request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit(), 
                getZoneId(request.getConnector()), wmField.getDataType());
        return iterator;
    }

    protected String getNextCursor(List<EntityData> result, Integer pageSize, String prevCursor) {
        if (result != null && result.size() > 0) {
            return result.get(result.size() - 1).getId();
        }
        return null;
    }

    public FetchResponse getByCursorBasedWatermark(SyncRequest request) {
        if (request.getEntitySchema() == null)
            throw new RuntimeException("Schema cannot be null");
        if (request.getEntitySchema().getWatermarkField() == null)
            throw new RuntimeException("Watermark field cannot be null");

        WatermarkInfo watermark = request.getWatermark();
        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, nextID) -> {
            DataWithCursor response = null;
            try (Connection conn = getConnection(request.getConnector())) {
                try (Statement stmt = conn.createStatement()) {
                    List<String> fieldNames = getFields(request.getEntitySchema());
                    String sql = String.format(SqlQueries.SELECT,
                            getTableName(request.getEntityName(), request.getConnector()),
                            getCursorWatermarkCondition(request, nextID, pageSize));
                    log.debug(sql);
                    List<EntityData> result = extractData(request, stmt, fieldNames, sql);
                    response = new DataWithCursor(nextID, getNextCursor(result, pageSize, nextID), result);
                }
            } catch (Exception e) {
                handleException(e, request.getConnector());
            }
            return response;
        };

        int pageSize = request.getPageSize() == 0 ? QUERY_SIZE : Math.min(request.getPageSize(), QUERY_SIZE);
        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(watermark, watermark.getChangeStream(),
                watermark.getOffset(), generator, new ArrayList<>(), pageSize, request.getWatermark().getLimit(), false);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {

        String idsAsString = getIds(request).stream()
                .map(id -> compositeKeyHelper.getCompositeValuePredicate(request.getEntitySchema(), id))
                .filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.joining(","));

        List<EntityData> result = new ArrayList<>();
        if(!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }

        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                List<String> fieldNames = getFields(request.getEntitySchema());
                String fieldNamesAsString = String.join(", ",
                        fieldNames.stream().map(i -> getDecoratedFieldName(i)).collect(Collectors.toList()));
                //String idField = request.getEntitySchema().getIdField().getApiName();
                String sql = String.format(getSelectByIdsSql(), fieldNamesAsString,
                        getTableName(request.getEntityName(), request.getConnector()), getCompositeKeyPredicate(request.getEntitySchema().getIdField()),
                        idsAsString);
                log.debug(sql);
                result = extractData(request, stmt, fieldNames, sql);
                log.debug("Found {} records for getByIds {}", result.size(), idsAsString);
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return result;
    }

    private String getCompositeKeyPredicate(AttributeSchema idField) {

        if (idField == null || !idField.isIdField()) {
            return Constants.SYNCARI_ID;
        } else if (StringUtils.isBlank(idField.getCompositeKey())) {
            return getDecoratedFieldName(idField.getApiName());
        } else {
            String[] keys = idField.getCompositeKey().split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            return String.format("(%s)", Arrays.stream(keys).map(this::getDecoratedFieldName).collect(Collectors.joining(",")));
        }
    }

    private String getCompositeKeyPreparedStmt(EntitySchema entitySchema) {
        AttributeSchema idField = entitySchema.getIdField();
         return StringUtils.isBlank(idField.getCompositeKey()) ?
                String.format("%s=?", getDecoratedFieldName(idField.getApiName()))
                : entitySchema.getCompositeKeyFields().stream().map(f -> String.format("%s=?", getDecoratedFieldName(f.getApiName()))).collect(Collectors.joining(" AND "));
    }


    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return -1L;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        if (request.getData() == null || request.getData().isEmpty()
                || request.getData().get(request.getConnector().getId()) == null
                || request.getData().get(request.getConnector().getId()).isEmpty())
            return response;

        List<EntityData> data = request.getData().get(request.getConnector().getId());
        // LinkedHashMap needed to preserve column order
        Map<String, AttributeSchema> columnToApiNameMap = new LinkedHashMap<String, AttributeSchema>();
        for (AttributeSchema attr : request.getEntitySchema().getAttributes()) {
        	String decoratedFieldName = getDecoratedFieldName(attr.getApiName());
        	columnToApiNameMap.put(decoratedFieldName, attr);
        }
        String tableName = getTableName(request.getEntityName(), request.getConnector());
		if (columnToApiNameMap.size() > 150) {
			var partitioned = Lists.partition(data, 100);
			partitioned.forEach(partition -> {
				SyncResponse response2 = doCreate(request, partition, columnToApiNameMap, tableName, response, true);
				response.getErrors().addAll(response2.getErrors());
				response.getResults().addAll(response2.getResults());
				response.setSuccess(response2.isSuccess());
			});
		} else {
			return doCreate(request, data, columnToApiNameMap, tableName, response, true);
		}
        return response;
    }

	private SyncResponse doCreate(SyncRequest request, List<EntityData> data,
			Map<String, AttributeSchema> columnToApiNameMap, String tableName, SyncResponse response, boolean retry) {
		try {

            // Temp Fix
            data.stream().forEach(d -> {
                if( StringUtils.isEmpty(d.getId()) &&
                        request.getEntitySchema().hasIdField() &&
                        StringUtils.isNotEmpty(d.getValueAsString(request.getEntitySchema().getIdField().getApiName())))
                {
                    d.setId(d.getValueAsString(request.getEntitySchema().getIdField().getApiName()));
                }
            });

			List<EntityData> listOfEmptyIdData = data.stream().filter(x -> StringUtils.isEmpty(x.getId()))
					.collect(Collectors.toList());
			List<EntityData> listOfNonEmptyIdData = data.stream().filter(x -> StringUtils.isNotEmpty(x.getId()))
					.collect(Collectors.toList());
			if (!CollectionUtils.isEmpty(listOfNonEmptyIdData)) {
				doCreateHelper(request, listOfNonEmptyIdData, columnToApiNameMap, tableName, response);
			}
			if (!CollectionUtils.isEmpty(listOfEmptyIdData)) {
				Map<String, AttributeSchema> copyColumnToApiNameMap = new HashMap<>();
				copyColumnToApiNameMap.putAll(columnToApiNameMap);
				if (request.getEntitySchema().hasIdField()) {
					String decoratedName = getDecoratedFieldName(request.getEntitySchema().getIdField().getApiName());
					if (copyColumnToApiNameMap.containsKey(decoratedName)) {
						log.info("Creating entity with entity id {} and entity attrib schema as {}", decoratedName,
								copyColumnToApiNameMap.get(decoratedName));
						copyColumnToApiNameMap.remove(decoratedName);
					}
				}
				doCreateHelper(request, listOfEmptyIdData, copyColumnToApiNameMap, tableName, response);
			}
		} catch (Exception e) {
            if (retry) {
                response = new SyncResponse();
                // try one by one
                for (EntityData entry : data) {
                    try {
                        doCreate(request, List.of(entry), columnToApiNameMap, tableName, response, false);
                    } catch (Exception e2) {
                        captureErrorResponse(List.of(entry), response, e2);
                    }
                }
            } else {
                captureErrorResponse(data, response, e);
            }
		}
		return response;
	}

    private void captureErrorResponse(List<EntityData> data, SyncResponse response, Exception e) {
        for (EntityData entry : data) {
            Result result = new Result(false, entry.getId(), entry.getSyncariEntityId());
            result.getErrors().add(e.getMessage());
            response.getResults().add(result);
        }
    }

    private void doCreateHelper(SyncRequest request, List<EntityData> data,
                                Map<String, AttributeSchema> columnToApiNameMap, String tableName, SyncResponse response) throws SQLIntegrityConstraintViolationException {
        int columnsSize = columnToApiNameMap.size();

        int recordsPerBatch = columnsSize > 0 ? (columnsSize < MAX_BIND_PARAMETER_SIZE ? MAX_BIND_PARAMETER_SIZE / columnsSize : 1) : data.size();

        var lists = Lists.partition(data, recordsPerBatch);

        for (List<EntityData> d : lists) {
            doCreateHelperBatch(request, d, columnToApiNameMap, tableName, response);
        }
    }

	protected boolean hasMultiInsertSupport(){
		return true;
	}

	private void doCreateHelperBatch(SyncRequest request, List<EntityData> data,
                                  Map<String, AttributeSchema> columnToApiNameMap, String tableName, SyncResponse response)throws SQLIntegrityConstraintViolationException {
        String entryFormat = "( %s )";
        final String zoneId = getZoneId(request.getConnector());
        String columnList = String.format(entryFormat, String.join(COMMA, columnToApiNameMap.keySet()));
        String singleRowBindList = String.format(entryFormat, getCreateBindingParamList(columnToApiNameMap));
        String allRowsBindList = String.join(COMMA, Collections.nCopies(data.size(), singleRowBindList));
        String sql = String.format(SqlQueries.INSERT, tableName, columnList, hasMultiInsertSupport() ? allRowsBindList : singleRowBindList);
        sql = optionallyRemoveTrailingSemiColon(sql);
        log.debug(sql);

        String idFieldString = "";

        if (request.getEntitySchema().hasIdField()){
            AttributeSchema idField = request.getEntitySchema().getIdField();
            if(StringUtils.isBlank(idField.getCompositeKey())){
                idFieldString = idField.getApiName();
            }
        }

        Map<String, Integer> columnLengthMap = new HashMap<>();
        EntitySchema described = null;
        try (Connection conn = getConnection(request.getConnector())) {
            try (PreparedStatement stmt = getStmt(conn, sql, idFieldString)) {
                int parameterIndex = 1;
                for (EntityData entry : data) {
                    if(!hasMultiInsertSupport()) {
                        parameterIndex = 1;
                    }
                    for (Entry<String, AttributeSchema> e : columnToApiNameMap.entrySet()) {
                        Object columnValue = null;
                        // do not set id field if this is composite key, as there is no actual field in the external DB
                        if(e.getValue().isIdField() && StringUtils.isBlank(e.getValue().getCompositeKey())) {
                            columnValue = entry.getId();
                            columnValue = convertIdValue(e.getValue().getDataType(), (String)columnValue);
                        } else {
                            columnValue = entry.getValue(e.getValue().getApiName());
                        }
                        if(e.getValue().isWatermarkField()) {
                            columnValue = preprocessWmField(request.getEntitySchema(), e.getValue().getApiName(), columnValue);
                        }

                        if (columnValue == null)  {
                            stmt.setNull(parameterIndex, getNullObjectType());
                        } else {
                            Object computedValue = computedValue(e.getValue(), columnValue, zoneId);
                            stmt.setObject(parameterIndex, computedValue);
                            AttributeSchema attr = e.getValue();
                            if(STRING.equalsIgnoreCase(getSyncariDatatype(attr.getDataType()))) {
                                int newLength = computedValue.toString().getBytes().length;
                                described = captureAlter(request, described, columnLengthMap, attr, newLength, conn);
                            } else if(NUMBER.equalsIgnoreCase(getSyncariDatatype(attr.getDataType()))) {
                                doAlterNumericIfNeeded(request, described, attr, conn);
                            }
                        }
                        parameterIndex++;
                    }
                    if(!hasMultiInsertSupport()) {
                        stmt.addBatch();
                    }
                }
                for (Entry<String, Integer> entry : columnLengthMap.entrySet()) {
                    alterLength(request.getConnector(), request.getEntityName(), described.getField(entry.getKey()).get(), entry.getValue());
                }
                log.debug(stmt.toString());
                if(hasMultiInsertSupport()){
                    stmt.execute();
                } else {
                    stmt.executeBatch();
                }
                postProcessCreate(conn, stmt, data, response);
            }

            log.info("Successfully inserted {} records in datastore for {}", data.size(), tableName);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw e;
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
    }

    protected void postProcessCreate(Connection conn, PreparedStatement stmt, List<EntityData> data, SyncResponse response) {
        for (EntityData ed: data) {
            response.getResults().add(new Result(true, ed.getId(), ed.getSyncariEntityId()));
        }
    }

    protected PreparedStatement getStmt(Connection conn, String sql, String idField) throws SQLException {
        return conn.prepareStatement(sql);
    }

    protected void alterLength(ConnectorInfo connector, String entity, AttributeSchema field, Integer newLength) {
        // default no-op;
    }

    protected void alterNumeric(ConnectorInfo connector, String entity, AttributeSchema field) {
        // default no-op;
    }

    protected int getNullObjectType() {
        return Types.JAVA_OBJECT;
    }

    protected String getDecoratedFieldName(String attr) {
        return getEscapeChar() + getCased(attr) + getEscapeChar();
    }

    protected String getCreateBindingParamList(Map<String, AttributeSchema> columnToApiNameMap){
        return String.join(COMMA, Collections.nCopies(columnToApiNameMap.size(), "?"));
    }

    protected String getUpdateBindingParamList(Set<String> finalColumnList, Map<String, AttributeSchema> apiToAttr){
        return String.join("=?,", finalColumnList.stream().map(c -> getDecoratedFieldName(c)).collect(Collectors.toList())).concat("=?");
    }

    protected Object computedValue(AttributeSchema attr, Object columnValue, String zoneId) {
        return computedValue(attr, columnValue);
    }

    protected Object computedValue(AttributeSchema attr, Object columnValue) {
        return columnValue;
    }

    protected long getFieldLength(long length) {
        log.debug("New length {} max length {} service {}", length, getMaxVarcharLength(), this.getClass().getName());
        return length >= getMaxVarcharLength() ? getMaxVarcharLength() : length;
    }

    protected long getMaxVarcharLength() {
        return MAX_VARCHAR_LENGTH;
    }

    protected boolean fetchByLastBatchWatermark() {
        return false;
    }
    
    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), request.getEntityName()));
        entity.ifPresent(en -> {
            if(isLowerCaseConfEnabled(request.getConnector())){
                request.getSchema().setApiName(request.getSchema().getApiName().toLowerCase());
            }
            Optional<AttributeSchema> field = en.getField(request.getSchema().getApiName());
            if (field.isPresent())
                return;
            try (Connection conn = getConnection(request.getConnector())) {
                try (Statement stmt = conn.createStatement()) {
                    String sql = String.format(SqlQueries.ADD_COLUMN,
                            getTableName(request.getEntityName(), request.getConnector()),
                            getFieldStr(request.getSchema()));
                    log.debug(sql);
                    stmt.execute(sql);
                }
            } catch (Exception e) {
                handleException(e, request.getConnector());
            }
        });
        return request.getSchema();
    }

    @Override
    public UpdateFieldResponse updateField(UpdateFieldRequest request) {
        Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), request.getEntityName()));
        AtomicReference<Boolean> updateRef = new AtomicReference<>(false);
        UpdateFieldResponse response = new UpdateFieldResponse();
        entity.ifPresent(en -> {
            String apiName = !StringUtils.isBlank(request.getOldName()) ? request.getOldName() : request.getSchema().getApiName();
            Optional<AttributeSchema> field = en.getField(apiName);
            if (field.isEmpty())
                updateRef.set(false);
            else{
                try (Connection conn = getConnection(request.getConnector())) {
                    try (Statement stmt = conn.createStatement()) {
                        String tableName = getTableName(request.getEntityName(), request.getConnector());
                        String columnName = field.get().getApiName();
                        String sql = StringUtils.EMPTY;
                        if(!StringUtils.isBlank(request.getOldName()) && !StringUtils.isBlank(request.getNewName())
                                && !request.getNewName().equalsIgnoreCase(request.getOldName())) {
                            sql = String.format(SqlQueries.RENAME_COLUMN, tableName, getDecoratedFieldName(request.getOldName()),
                                    getDecoratedFieldName(request.getNewName()));
                            columnName = request.getNewName();
                            log.debug(sql);
                            stmt.execute(sql);
                            updateRef.set(true);
                        }
                        if (request.getSchema().getLength() > 0
                                && field.get().getLength() != request.getSchema().getLength()
                                && request.getSchema().getLength() > field.get().getLength()
                                && STRING.equalsIgnoreCase(request.getSchema().getDataType())) {
                            sql = String.format(SqlQueries.ALTER_LENGTH,
                                    tableName, getDecoratedFieldName(columnName), getFieldLength(request.getSchema().getLength()));
                            log.debug(sql);
                            stmt.execute(sql);
                            updateRef.set(true);
                        } else if (request.getSchema().getDataType() != null && !getDatatype(request.getSchema()).equalsIgnoreCase(getDatatype(field.get()))) {
                            // check the date types of incoming schema and existing schema and not the syncari schema
                            sql = String.format(SqlQueries.ALTER_TYPE,
                                    tableName, getDecoratedFieldName(columnName), getDatatype(request.getSchema()));
                            log.debug(sql);
                            stmt.execute(sql);
                            updateRef.set(true);
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



    @Override
    public EntitySchema updateObject(UpdateObjectRequest request) {
        if(StringUtils.isBlank(request.getOldName()) || StringUtils.isBlank(request.getNewName()) ||
                request.getOldName().equalsIgnoreCase(request.getNewName())) return request.getSchema();
        Optional<EntitySchema> newNameEntity = describe(new DescribeRequest(request.getConnector(), request.getNewName()));
        newNameEntity.ifPresentOrElse(e -> {
            log.info("New name {} entity is present, we do not need to rename even if it is different name. Old name is {}", request.getNewName(), request.getOldName());
        },()-> {
            Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), request.getOldName()));
            entity.ifPresent(en -> {
                try (Connection conn = getConnection(request.getConnector())) {
                    try (Statement stmt = conn.createStatement()) {
                        String oldTable = getTableName(request.getOldName(), request.getConnector());
                        String sql = String.format(SqlQueries.RENAME_TABLE, oldTable, request.getNewName());
                        log.debug(sql);
                        stmt.execute(sql);
                    }
                } catch (Exception e) {
                    handleException(e, request.getConnector());
                }
            });
        });
        Optional<EntitySchema> oldEntity = describe(new DescribeRequest(request.getConnector(), request.getOldName()));
        return oldEntity.isPresent() ? oldEntity.get() : request.getSchema();
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                String sql = getDeleteFieldSQL(request);
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
    }

    public String getDeleteFieldSQL(DeleteFieldRequest request) {
        return String.format(SqlQueries.DROP_COLUMN, 
            getTableName(request.getEntityName(), request.getConnector()), getDecoratedFieldName(request.getFieldName()));
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        doCreateObject(request);
        return describe(new DescribeRequest(request.getConnector(), request.getSchema().getApiName())).get();
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {
        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.DROP_TABLE,
                        getTableName(request.getDatastoreName(), request.getConnector()));
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
    }

    @Override
    public void truncateObject(DeleteObjectRequest request) {
        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.TRUNCATE_TABLE,
                        getTableName(request.getDatastoreName(), request.getConnector()));
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
    }

    private SyncResponse doUpdate(SyncRequest request, List<EntityData> data, SyncResponse response, boolean retry) {
        if (data == null || data.isEmpty())
            return response;

        Map<String, AttributeSchema> apiToAttr = new HashMap<String, AttributeSchema>();
        request.getEntitySchema().getAttributes().stream().forEach(a -> apiToAttr.put(a.getApiName().toLowerCase(), a));
        String tableName = getTableName(request.getEntityName(), request.getConnector());
        EntitySchema described = null;
        EntitySchema entitySchema = request.getEntitySchema();
        String idColumn = getIdColumn(request.getEntitySchema());
        Map<String, Integer> columnLengthMap = new HashMap<>();

        final String zoneId = getZoneId(request.getConnector());
        Map<Set<String>, List<EntityData>> dataSets = data.stream().collect(Collectors.groupingBy(e -> e.getValues().keySet()));
        try (Connection conn = getConnection(request.getConnector())) {
            for (Entry<Set<String>, List<EntityData>> set : dataSets.entrySet()) {
                Set<String> finalColumns = new LinkedHashSet<String>();
                set.getKey().forEach(k -> {
                    Optional<AttributeSchema> field = entitySchema.getField(k);
                    if (field.isPresent() && !idColumn.equalsIgnoreCase(k)) {
                        finalColumns.add(field.get().getApiName());
                    }
                });
                if(finalColumns.isEmpty()) {
                    log.warn("No values to be updated");
                    continue;
                }
                entitySchema.getWatermarkAttr().ifPresent(attr -> {
                    if(WM_FIELD_TYPES.contains(entitySchema.getWatermarkField().getDataType())) {
                        finalColumns.add(entitySchema.getWatermarkField().getApiName());
                    }
                });
                String columnList = getUpdateBindingParamList(finalColumns, apiToAttr);
                String sql = String.format(SqlQueries.UPDATE_BY_ID, tableName, columnList, getCompositeKeyPreparedStmt(request.getEntitySchema()));
                sql = optionallyRemoveTrailingSemiColon(sql);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (EntityData d : set.getValue()) {
                        int parameterIndex = 1;
                        final Result syncResult = new Result(true, d.getId(), d.getSyncariEntityId());
                        response.getResults().add(syncResult);
                        for (String columnName : finalColumns) {
                            Object columnValue = d.getValue(columnName);
                            AttributeSchema attr = apiToAttr.get(columnName.toLowerCase());
                            if(attr.isWatermarkField()) {
                                columnValue = preprocessWmField(entitySchema, columnName, columnValue);
                            }
                            if (columnValue == null) {
                                stmt.setNull(parameterIndex, getNullObjectType());
                            } else {
                                if(STRING.equalsIgnoreCase(getSyncariDatatype(attr.getDataType()))) {
                                    // Using bytes to account for multi byte characters
                                    int newLength = columnValue.toString().getBytes().length;
                                    described = captureAlter(request, described, columnLengthMap, attr, newLength, conn);
                                } else if(NUMBER.equalsIgnoreCase(getSyncariDatatype(attr.getDataType()))) {
                                    doAlterNumericIfNeeded(request, described, attr, conn);
                                }
                                Object computedValue = computedValue(attr, columnValue, zoneId);
                                stmt.setObject(parameterIndex, computedValue);
                            }
                            parameterIndex++;
                        }
                        // set the id for where condition
                        // get the type of the id and return the appropriate value and do set Object?
                        String dataType = entitySchema.hasIdField() ? entitySchema.getIdField().getDataType() : STRING;

                        for (Object obj : convertIdValue(entitySchema, dataType, d.getId())) {
                            stmt.setObject(parameterIndex++, obj);
                        }
                        stmt.addBatch();
                        syncResult.setAdditionalInfo(stmt.toString());
                        log.debug(syncResult.getAdditionalInfo());

                    }
                    columnLengthMap.forEach((k, v) -> {
                        alterLength(request.getConnector(), request.getEntityName(), apiToAttr.get(k), v);
                    });
                    final int[] dbResults = stmt.executeBatch();
                    updateResults(dbResults, response.getResults());
                }
            }
            log.debug("Successfully updated {} records in datastore for {}", data.size(), tableName);
        } catch(BatchUpdateException e) {
            if (retry) {
                log.info("retrying records individually since batch update failed. total batch size : "+data.size());
                // try one by one
                for (EntityData entry : data) {
                    SyncResponse resp = new SyncResponse();
                    try {
                        doUpdate(request, List.of(entry), resp, false);
                        response.getErrors().addAll(resp.getErrors());
                        response.getResults().addAll(resp.getResults());
                    } catch (Exception e2) {
                        captureErrorResponse(List.of(entry), response, e2);
                    }
                }
            } else {
                captureErrorResponse(data, response, e);
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
    return response;
    }


    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<EntityData> data = request.getData().get(request.getConnector().getId());
        if (data == null || data.isEmpty())
            return response;
        return doUpdate(request, data, response, true);
    }

    protected void updateResults(int[] dbResults, List<Result> results) {
        for (int i = 0; i < dbResults.length; i++) {
            final Result result = results.get(i);
            if (dbResults[i] != 1) {
                result.setSuccess(false);
                result.setErrorCode(ErrorCodes.UPDATE_FAILED.name());
                result.addError("Expected 1 successful update but found " + dbResults[i] + ", " + result.getAdditionalInfo());
            }
        }
    }

    private Object convertIdValue(String dataType, String value) {

        switch (dataType) {
            case "int":
            case "integer" :
                return Long.parseLong(value);
            default:
                return value;
        }
    }

    private List<Object> convertIdValue(EntitySchema entitySchema, String dataType, String value) {

        if (entitySchema.hasIdField() && StringUtils.isBlank(entitySchema.getIdField().getCompositeKey())) {
            return List.of(convertIdValue(dataType, value));
        } else {
            return compositeKeyHelper.getCompositeValueTyped(entitySchema, value);
        }
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<EntityData> data = request.getData().get(request.getConnector().getId());
        if (data == null || data.isEmpty())
            return response;
        if(!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }

        String ids =  String.join(COMMA, data.stream().map(d -> compositeKeyHelper.getCompositeValuePredicate(request.getEntitySchema(), d.getId()))
                .filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList()));

        String syncariId =  getCompositeKeyPredicate(request.getEntitySchema().getIdField());
        String condition = syncariId + " IN (" + ids + ")";
        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.DELETE,
                        getTableName(request.getEntityName(), request.getConnector()), condition);
                sql = optionallyRemoveTrailingSemiColon(sql);
                log.info(sql);
                stmt.execute(sql);
            }
            log.info("Successfully deleted {} records in datastore", data.size());
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }

        List<Result> results = data.stream().map(d -> new Result(true, d.getId(),
                d.getSyncariEntityId())).collect(Collectors.toList());
        response.setResults(results);
        return response;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        try (Connection conn = getConnection(request.getConnector())) {
            return doDescribe(request, conn, true, getLateBindingViewNameToFieldsMap(request.getConnector()));
        } catch (Exception e1) {
            handleException(e1, request.getConnector());
        }
        return Optional.empty();
    }

    public Optional<EntitySchema> describe(DescribeRequest request, Connection conn) {
        try {
            return doDescribe(request, conn, true, getLateBindingViewNameToFieldsMap(request.getConnector()));
        } catch (Exception e1) {
            handleException(e1, request.getConnector());
        }
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schema = new ArrayList<>();
        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(getDescribeSql(request.getConnector()));
                Map<String, List<AttributeSchema>> lateBindingViewNameToFieldsMap = getLateBindingViewNameToFieldsMap(request.getConnector());
                while (rs.next()) {
                    String entityName = rs.getString("TABLE_NAME");
                    String entityType = rs.getString("TABLE_TYPE");
                    Optional<EntitySchema> described = doDescribe(
                            new DescribeRequest(request.getConnector(), entityName), conn, false, lateBindingViewNameToFieldsMap);
                    described.ifPresent(e -> {
                        if("view".equalsIgnoreCase(entityType)) {
                            e.setReadOnly(true);
                        }
                        schema.add(e);
                    });
                }
                rs.close();
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return schema;
    }

    private Map<String, List<AttributeSchema>> getLateBindingViewNameToFieldsMap(ConnectorInfo connector) {
        Map<String, List<AttributeSchema>> viewToFieldsMap = new HashMap<>();
        String sql = getDescribeFieldSqlForLateBindingViews(connector);
        if (StringUtils.isBlank(sql)) {
            return viewToFieldsMap;
        }
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    String viewName = rs.getString("VIEW_NAME");
                    if (StringUtils.isBlank(viewName)) {
                        continue;
                    }
                    viewToFieldsMap.putIfAbsent(viewName, new ArrayList<>());
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    if (StringUtils.isBlank(columnName) || StringUtils.isBlank(dataType)) {
                        continue;
                    }
                    AttributeSchema attr = new AttributeSchema(columnName, getSyncariDatatype(dataType));
                    attr.setDisplayName(columnName);
                    viewToFieldsMap.get(viewName).add(attr);
                }
                rs.close();
            }
        } catch (Exception e) {
            log.error("Encountered exception while fetching late binding views for connector id/name {}/{} with error {} ", connector.getId(),
                    connector.getName(), e.getMessage(), e);
        }
        return viewToFieldsMap;
    }

    @Override
    public List<EntityData> search(SearchRequest request) {
        List<EntityData> response = new ArrayList<>();
        log.debug(request.getQuery());
        Optional<Integer> limit = Optional.of(1000);
        try (Connection conn = getConnection(request.getConnector())) {
            try (PreparedStatement stmt = conn.prepareStatement(request.getQuery())) {
                int parameterIndex = 1;
                if (request.getParams() != null && !request.getParams().isEmpty()) {
                    for (Object param : request.getParams()) {
                        stmt.setObject(parameterIndex, param);
                        parameterIndex++;
                    }
                }

                List<EntityData> result = new ArrayList<>();
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    EntityData data = new EntityData();
                    data.setConnectorId(request.getConnector().getId());
                    getFieldNames(rs).forEach((name, datatype) -> {
                        Object value = null;
                        try {
                            switch (datatype) {
                                case "date":
                                    value = rs.getDate(name);
                                    break;
                                case "datetime": {
                                    final Timestamp timestamp = rs.getTimestamp(name);
	                            value = timestamp==null? null :ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
	                            break;
	                        }
	                        case "timestamp": 
	                        case "timestamp_ltz": 
	                        case "timestamp_ntz": 
	                        case "timestamp_tz": {
	                            final Timestamp timestamp = rs.getTimestamp(name);
	                            value = timestamp==null? null :timestamp.toInstant();
	                            break;
	                        }
	                        default:
	                        	value = rs.getObject(name);
	        		        }
        		        } catch (SQLException e) {
        		        }
        		        data.addValue(name, value);
        		    });
        		    result.add(data);
        		    if(!limit.isEmpty() && result.size() >= limit.get()) {
        		    	log.info("Hit limit of {}, breaking", limit.get());
        		    	 break;
        		    }
        		}
        		return result;
			}
		} catch (Exception e) {
			handleException(e, request.getConnector());
		}
		return response;
	}

	private Map<String, String> getFieldNames(ResultSet rs) throws SQLException {
		Map<String, String> fieldNames = new HashMap<String, String>();
		for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
			fieldNames.put(rs.getMetaData().getColumnName(i), rs.getMetaData().getColumnTypeName(i).toLowerCase());
		return fieldNames;
	}

    private List<EntitySchema> getAllEntitySchema(ConnectorInfo connector){
        List<EntitySchema> schema = new ArrayList<>();
        try (Connection conn = getConnection(connector)) {
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

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            // describe only one table
            List<EntitySchema> entities = getAllEntitySchema(config);
            entities.stream().findFirst().ifPresent(e -> {
                DescribeRequest describeRequest = new DescribeRequest(config, e.getApiName());
                describe(describeRequest);
            });
        } catch (Exception ex) {
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(ex.getMessage());
        }
        return response;
    }
    
    @Override
    public List<Capability> getCapabilities() {
		return List.of(Capability.create, Capability.update, Capability.delete, Capability.search, Capability.getById,
				Capability.getByWatermark, Capability.schemaEditInSyncari, Capability.userEditableId, Capability.userEditableWm);
    }

    List<String> getFieldNames(SyncRequest request) {
        List<EntityData> data = request.getData().get(request.getConnector().getId());
        Set<String> fieldNames = new HashSet<>();
        for (EntityData d : data) {
            fieldNames.addAll(d.getValues().keySet());
        }
        return new ArrayList<>(fieldNames);
    }

    String getFieldStr(AttributeSchema a) {
        // field_name datatype NOT NULL,
        String template = getEscapeChar()+"%s" +getEscapeChar()+" %s %s %s";
//        String nullClause = a.isNillable() ? "" : "NOT NULL";
        String nullClause = "";
        String uniqueClause = "";
        if (Constants.SYNCARI_ID.equalsIgnoreCase(a.getApiName())) {
            nullClause = " NOT NULL ";
            if (a.isUnique()) {
                uniqueClause = " UNIQUE ";
            }
        }
        return String.format(template, getCased(a.getApiName()), getDatatype(a), nullClause, uniqueClause);
    }

    String getDatatype(AttributeSchema from) {
        //multivalued fields will have max length
        if (from.isMultiValueField()) return "VARCHAR("+MAX_VARCHAR_LENGTH+")";

        switch (from.getDataType()) {
            case "boolean":
            case "checkbox":
            case "bool":
                return "BOOLEAN";
            case "double":
            case "float":
            case "number":
                return "NUMERIC";
            case "datetime":
            case "timestamp":
                return "TIMESTAMPTZ";
            case "int":
            case "integer":
                return "INTEGER";
            case "date":
                return "DATE";
            default:
                return String.format("VARCHAR(%s)", Math.min(MAX_VARCHAR_LENGTH, from.getLength() == 0 ? 256 : from.getLength()));
        }
    }

    protected String getSyncariDatatype(String from) {
        switch (normalizeFieldType(from.toLowerCase())) {
            case "boolean":
            case "bool":
            case "tinyint":
                return "boolean";
            case "long":
            case "double":
            case "real":
            case "float":
            case "numeric":
            case "number":
            case "decimal":
            case "binary_float":
            case "binary_double":
                return "number";
            case "timestamp":
            case "timestamp with time zone":
            case "timestamp without time zone":
            case "timestamp with local time zone":
            case "timestamp_ltz":
            case "timestamp_ntz":
            case "timestamp_tz":
                return "timestamp";
            case "integer":
            case "bigint":
            case "int":
            case "int8":
            case "smallint":
                return "integer";
            case "date":
                return "date";
            case "datetime":
                return "datetime";
            default:
                // To handle datatypes like: numeric(8,2). https://docs.aws.amazon.com/redshift/latest/dg/r_Numeric_types201.html
                if (from.toLowerCase().startsWith("numeric") || from.toLowerCase().startsWith("decimal")) {
                    return "number";
                }
                return STRING;
        }
    }

    protected String normalizeFieldType(String type) {
        return type;
    }
    
    protected long getLastModified(String datatype, ResultSet rs, AttributeSchema field, ZoneId zoneId) throws SQLException {
        return getLastModified(datatype, rs, field.getApiName());
    }

    protected long getLastModified(String datatype, ResultSet rs, String field) throws SQLException {
        switch (getSyncariDatatype(datatype)) {
        case "timestamp":
            return rs.getTimestamp(getCased(field)).getTime();
        case "integer":
        case "number":
            return rs.getLong(getCased(field));
        case "date":
        	return rs.getDate(getCased(field)).getTime();
        case "datetime":
            Timestamp timestamp = rs.getTimestamp(getCased(field));
            return timestamp.toInstant().toEpochMilli();
        default:
            throw new RuntimeException("Unsupported datatype "+datatype+" for watermark for field " + field);
        }
    }

    protected long getTimestampEpochMillis(Timestamp timestamp, ZoneId zoneId) {
        return timestamp.getTime();
    }

    List<String> getFields(EntitySchema entity) {
        return entity.getAttributes().stream()
                .filter(a -> !a.isIdField() || (a.isIdField() && StringUtils.isBlank(a.getCompositeKey())))
                .map(a -> a.getApiName()).collect(Collectors.toList());
    }

    List<AttributeSchema> getAttributes(ConnectorInfo connectorInfo, Connection conn, String entityName) {
        List<AttributeSchema> attributes = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            String sql = getDescribeFieldSql(connectorInfo, entityName);
            log.debug(sql);
            try(ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String type = getSyncariDatatype(rs.getString("DATA_TYPE"));
                    AttributeSchema attr = new AttributeSchema(name, type);
                    if(type != null && type.equalsIgnoreCase("timestamp") || type.equalsIgnoreCase("datetime") || type.equalsIgnoreCase("date")) {
                        if(isNoTimezoneWatermark(rs.getString("DATA_TYPE"))) {
                            attr.setNoTimezoneWatermark(true);
                        }
                    }
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
                    if(NUMBER.equals(type)){
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

    protected boolean isNoTimezoneWatermark(String dataType) {
        return false;
    }


    String getWatermarkCondition(SyncRequest request, long offset, int pageSize) {
        String watermarkField = getDecoratedFieldName(request.getEntitySchema().getWatermarkField().getApiName());
        return watermarkField + " >= " + request.getWatermark().getStart() + " AND " + watermarkField + " <= "
                + request.getWatermark().getEnd() + " ORDER BY "+watermarkField+ " LIMIT " + pageSize + " OFFSET " + offset;
    }

    String getCursorWatermarkCondition(SyncRequest request, String nextID, int pageSize) {
        String idField = getDecoratedFieldName(getCased(request.getEntitySchema().getIdField().getApiName()));
        String watermarkField = getDecoratedFieldName(request.getEntitySchema().getWatermarkField().getApiName());
        if (StringUtils.isEmpty(nextID)){
            return  watermarkField + " >= " + request.getWatermark().getStart() + " AND " + watermarkField + " <= "
                    + request.getWatermark().getEnd() + " ORDER BY " + idField + " LIMIT " + pageSize;
        }
        if ("string".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType())) {
            nextID = String.format("'%s'", nextID);
        }
        return watermarkField + " >= " + request.getWatermark().getStart() + " AND " + watermarkField + " <= "
                + request.getWatermark().getEnd() + " AND " + idField + " >= " + nextID + " ORDER BY " + idField + " LIMIT " + pageSize;
    }

    protected void validateIdWMFields(SyncRequest request) {
        if (!request.getEntitySchema().hasIdField()) {
            throw new EntityException(request.getConnector().getName(), request.getEntityName(),
                ErrorCodes.SCHEMA_ERROR, HttpStatus.BAD_REQUEST.toString(), String.format(i18n("idfield_required"), getName()));
        }
        if (!request.getEntitySchema().hasWatermarkField()) {
            throw new EntityException(request.getConnector().getName(), request.getEntityName(),
                ErrorCodes.SCHEMA_ERROR, HttpStatus.BAD_REQUEST.toString(), String.format(i18n("wmfield_required"), getName()));
        }
    }

    private List<String> getIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        return entityList.stream().map(e -> e.getId()).collect(Collectors.toList());
    }

    List<EntityData> extractData(SyncRequest request, Statement stmt, List<String> fieldNames, String sql)
            throws SQLException {

        try(ResultSet rs = stmt.executeQuery(sql)) {
            return doExtract(request, fieldNames, rs, Optional.empty());
        }
    }

	private List<EntityData> doExtract(SyncRequest request, List<String> fieldNames, ResultSet rs, Optional<Integer> limit) throws SQLException {
		List<EntityData> result = new ArrayList<>();
		if (!request.getEntitySchema().hasIdField()) {
		    throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
		}
		String idFieldName = Constants.SYNCARI_ID;
		AttributeSchema idField = request.getEntitySchema().getIdField();
		if (request.getEntitySchema().hasIdField()) {
		    idFieldName = request.getEntitySchema().getIdField().getApiName();
		}
		Optional<AttributeSchema> wmField = Optional.empty();
		if (request.getEntitySchema().hasWatermarkField()) {
			wmField = Optional.of(request.getEntitySchema().getWatermarkField());
		}
        final String casedIdField = getCased(idFieldName);

        while (rs.next()) {
		    EntityData data = new EntityData(request.getEntityName());
		    data.setConnectorId(request.getConnector().getId());

		    if(wmField.isPresent()) {
				try {
					data.setLastModified(
							getLastModified(wmField.get().getDataType(), rs, wmField.get(), ZoneId.of(getZoneId(request.getConnector()))));
				} catch (Exception ex) {
					log.debug(ExceptionUtils.getStackTrace(ex));
				}
		    }
		    EntitySchema schema = request.getEntitySchema();
		    fieldNames.forEach(f -> {
		        Object value = extractValue(rs,schema, getCased(f));
		        data.addValue(f, value);
		    });

            // if composite key, construct a value
            if (idField != null && !StringUtils.isBlank(idField.getCompositeKey())) {
                data.setId(compositeKeyHelper.composeKeys(data, request.getEntitySchema()));
            } else {
                data.setId(rs.getString(casedIdField));
            }
            //skip records without ids bcause they cause trouble downstream
            if (StringUtils.isNotEmpty(data.getId())) {
                result.add(data);
            } else {
                log.error("Skipping record without id {}, with first 10 nonnull/nonempty values {}", idFieldName, nonEmptyValues(data));
            }
		    if(!limit.isEmpty() && limit.get() >= result.size()) {
		    	log.info("Hit limit of {}, breaking", limit.get());
		    	 break;
		    }
		}
		return result;
	}

    private Map<String, Object> nonEmptyValues(EntityData data) {
        return data.getValues().entrySet().stream()
                .filter(kv -> StringUtils.isNotEmpty(data.getValueAsString(kv.getKey())))
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected Object extractValue(ResultSet rs, EntitySchema schema, String fieldName) {
        return schema.getField(fieldName).map(field ->
                        extractVal(rs, field.getDataType(), fieldName)
        ).orElseGet(()-> rethrow(() -> rs.getObject(fieldName)));
    }

    protected List<Map<String, Object>> executeDmlQuery(ConnectorInfo connector, String sql, Map<String, String> fieldNames){
        List<Map<String, Object>> extractedVals = new ArrayList<>();
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                log.debug("SQL to be executed is {} and timeout is {}" , sql, QUERY_TIMEOUT);
                stmt.setQueryTimeout(QUERY_TIMEOUT);
                try(ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        Map<String, Object> extractedVal = new HashMap<>();
                        Set<Entry<String, String>> fielNameAndDataType = fieldNames.entrySet();
                        fielNameAndDataType.forEach(x -> {
                            String fieldName = x.getKey();
                            String  dataType = x.getValue();
                            Object val = extractVal(rs, dataType, fieldName);
                            if (null != val){
                                extractedVal.put(fieldName, val);
                            }
                        });
                        extractedVals.add(extractedVal);
                    }
                }
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
        return extractedVals;
    }

    protected List<Map<String, Object>> executeQueryToGetData(ConnectorInfo connector, String sql, List<DatastoreFieldMetadata> fields,
                                                              Set<DatastoreTableMetadata> datastoreTableMetadatas){
        List<Map<String, Object>> extractedVals = new LinkedList<>();
        try (Connection conn = getReadOnlyConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                log.debug("SQL to be executed is {} and timeout is {}" , sql, QUERY_TIMEOUT);
                stmt.setQueryTimeout(QUERY_TIMEOUT);
                Set<DatastoreFieldMetadata> fieldMetadata = new HashSet<>();
                Map<String, String> tableAndSchema = new HashMap<>();
                try(ResultSet rs = stmt.executeQuery(sql)) {
                    processResultSet(rs, extractedVals, connector, fields,tableAndSchema,fieldMetadata);
                }
                if((!CollectionUtils.isEmpty(fieldMetadata)) && (CollectionUtils.isEmpty(fields))){
                    fields.addAll(fieldMetadata);
                }
                if (MapUtils.isNotEmpty(tableAndSchema)){
                    tableAndSchema.forEach((k,v) -> {
                        datastoreTableMetadatas.add(new DatastoreTableMetadata().setTableName(k).setAlias(k).setSchemaName(v));
                    });
                }
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
        return extractedVals;
    }

    public Map<String, List<Map<String, String>>> getSchemaMetadata(ConnectorInfo connector){
        String dbName = (String)connector.getMetaConfig().get("dbName");
        if (StringUtils.isBlank(dbName)) {
            log.error("Cannot fetch schema metadata. DB Name is empty for connector {}", connector.getName());
            throw new RuntimeException("DB Name is empty for connector "+connector.getName());
        }
        String schemaName = (String)connector.getMetaConfig().getOrDefault("schemaName", "PUBLIC");

        Map<String, List<Map<String, String>>> result = new HashMap<>();
        try (Connection conn = getReadOnlyConnection(connector)) {

            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(dbName, schemaName, "%", new String[]{"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                List<Map<String, String>> tableColumnMapList = new ArrayList<>();
                // Get columns information
                ResultSet columns = metaData.getColumns(dbName, schemaName, tableName, "%");
                while (columns.next()) {
                    Map<String, String> columnsData = new HashMap<>();
                    String columnName = columns.getString("COLUMN_NAME");
                    String columnType = columns.getString("TYPE_NAME");
                    int columnSize = columns.getInt("COLUMN_SIZE");
                    log.debug(" Column: " + columnName + " - " + columnType + "(" + columnSize + ")");
                    columnsData.put("columnName", columnName);
                    columnsData.put("columnType", columnType.toUpperCase());
                    columnsData.put("columnSize", ""+columnSize);
                    tableColumnMapList.add(columnsData);
                }
                result.put(tableName, tableColumnMapList);
            }

        }catch (Exception e) {
            handleException(e, connector);
        }

        return result;
    }

    private void processResultSet(ResultSet rs, List<Map<String, Object>> extractedVals,ConnectorInfo connector,
                                  List<DatastoreFieldMetadata> fields,Map<String, String> tableAndSchema,Set<DatastoreFieldMetadata> fieldMetadata){
        try{
            while (rs.next()) {
                Map<String, Object> oneRow = new LinkedHashMap<>();
                if (!CollectionUtils.isEmpty(fields)){
                    fields.forEach(x -> {
                        String fieldName = x.getAliasName();
                        String dataType = x.getDisplayFormat();
                        Object val = extractVal(rs, dataType, fieldName);
                        if (null != val){
                            oneRow.put(fieldName, val);
                        }else{
                            oneRow.put(fieldName, "");
                        }
                    });
                }else{
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int columnCount = resultSetMetaData.getColumnCount();
                    for (int i = 1; i <= columnCount; i++){
                        String fieldName = getBaseColumnName(resultSetMetaData, i);
                        String aliasName = resultSetMetaData.getColumnName(i);
                        String dataType = getSyncariDatatype(resultSetMetaData.getColumnTypeName(i));
                        if (StringUtils.isNotEmpty(resultSetMetaData.getTableName(i))){
                            tableAndSchema.put(resultSetMetaData.getTableName(i),resultSetMetaData.getSchemaName(i));
                        }
                        Object val = extractVal(rs, dataType, aliasName);
                        if (null != val){
                            oneRow.put(aliasName, val);
                        }else{
                            oneRow.put(aliasName, "");
                        }
                        DatastoreFieldMetadata datastoreFieldMetadata = new DatastoreFieldMetadata()
                                .setAliasName(aliasName).setDisplayFormat(dataType)
                                .setApiName(fieldName).setDataType(dataType).setTableName(resultSetMetaData.getTableName(i));
                        fieldMetadata.add(datastoreFieldMetadata);
                    }
                }
                if (MapUtils.isNotEmpty(oneRow)){
                    extractedVals.add(oneRow);
                }
            }
        }catch (Exception e) {
            handleException(e, connector);
        }

    }

    /**
     * Database-specific method to get the base column name.
     * Default implementation uses standard JDBC getColumnName().
     * Override in subclasses for database-specific behavior.
     */
    protected String getBaseColumnName(ResultSetMetaData resultSetMetaData, int columnIndex) throws SQLException {
        return resultSetMetaData.getColumnName(columnIndex);
    }

    protected List<Map<String, Object>> executePreparedStmtToGetData(ConnectorInfo connector, String sql,Map<Integer, ParamValue> paramVal,
                                                                     List<DatastoreFieldMetadata> fields,Set<DatastoreTableMetadata> datastoreTableMetadatas){
        List<Map<String, Object>> extractedVals = new LinkedList<>();
        try (Connection conn = getReadOnlyConnection(connector)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                log.debug("Prepared Stmt to be executed is {} and timeout is {}" , sql, QUERY_TIMEOUT);
                stmt.setQueryTimeout(QUERY_TIMEOUT);
                // Add Param values

                addParamValues(paramVal,stmt,sql);
                Set<DatastoreFieldMetadata> fieldMetadata = new LinkedHashSet<>();
                Map<String, String> tableAndSchema = new LinkedHashMap<>();
                try(ResultSet rs = stmt.executeQuery()) {
                    processResultSet(rs, extractedVals, connector, fields, tableAndSchema, fieldMetadata);
                }
                if((!CollectionUtils.isEmpty(fieldMetadata)) && (CollectionUtils.isEmpty(fields))){
                    fields.addAll(fieldMetadata);
                }
                if (MapUtils.isNotEmpty(tableAndSchema)){
                    tableAndSchema.forEach((k,v) -> {
                        datastoreTableMetadatas.add(new DatastoreTableMetadata().setTableName(k).setAlias(k).setSchemaName(v));
                    });
                }
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
        return extractedVals;
    }

    public String getParamDataType (ParamValue paramValue){
        return paramValue.getParamDataType();
    }
    public void addParamValues(Map<Integer, ParamValue> paramVal, PreparedStatement stmt, String sql) throws SQLException {
        if (MapUtils.isNotEmpty(paramVal) && sql.contains("?")){
            for(int i = 0; i < paramVal.size(); i++){
                String paramType = getParamDataType(paramVal.get(i));
                switch (paramType){
                    case "boolean":
                        stmt.setBoolean(i+1, Boolean.valueOf(paramVal.get(i).getParamValue().toString()));
                        break;
                    case "long":
                    case "double":
                    case "float":
                        stmt.setDouble(i+1, Double.valueOf(paramVal.get(i).getParamValue().toString()));
                        break;
                    case "timestamp":
                        ZonedDateTime dateTime = DateTimeUtil.convert(paramVal.get(i).getParamValue().toString());
                        stmt.setTimestamp(i+1, Timestamp.valueOf(dateTime.toLocalDateTime()));
                        break;
                    case "date":
                    case "datetime":
                        ZonedDateTime zonedDateTime = DateTimeUtil.convert(paramVal.get(i).getParamValue().toString());
                        stmt.setDate(i+1, java.sql.Date.valueOf(zonedDateTime.toLocalDate()));
                        break;
                    case "integer":
                    case "bigint":
                    case "int":
                        stmt.setInt(i+1, Integer.valueOf(paramVal.get(i).getParamValue().toString()));
                        break;
                    default:
                        stmt.setString(i+1, paramVal.get(i).getParamValue().toString());
                }
            }
        }
    }

    protected void executeDdlQuery(ConnectorInfo connector, String sql){
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                log.debug("SQL to be executed is {}" , sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
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

    protected void handleException(Exception e, ConnectorInfo connector) {
        if (connector != null) {
            log.error("Encountered exception for connector id/name {}/{} with error {} ", connector.getId(), 
            connector.getName(), e.getMessage());
        } else {
            log.error("Encountered exception with error {} ", e.getMessage(), e);
        }
        if(e instanceof SQLException) {
            SQLException sqlEx = (SQLException) e;
            String sqlState = sqlEx.getSQLState();
            log.error("SQLException - SQLState: {}, Message: {}", sqlState, sqlEx.getMessage());

            if("22P02".equals(sqlState) || "22018".equals(sqlState)) { // 22018 is standard invalid character value
                throw new NonRetriableException(ErrorCodes.DATATYPE_ERROR, I18n.i18n("incompatible_datatype"), String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.ordinal()), e);
            }
            else if("42P01".equals(sqlState) || "42S02".equals(sqlState)) { // 42S02 is standard table not found
                throw new NonRetriableException(ErrorCodes.TABLE_NOT_FOUND, sqlEx.getMessage(), String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.ordinal()), e);
            }
            else {
                throw new NonRetriableException(ErrorCodes.SQL_ERROR, sqlEx.getMessage(), String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.ordinal()), e);
            }
        }
        if (SQLTimeoutException.class.isInstance(e) || SQLTimeoutException.class.isAssignableFrom(e.getClass())) {
            throw new RetriableException("TIME_OUT", e.getMessage(), "TIME_OUT");
        } else if(UncheckedExecutionException.class.isInstance(e)) {
        	throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, I18n.i18n("auth_failed"), String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.ordinal()), e);
        }else if (ClassNotFoundException.class.isInstance(e)
                || ClassNotFoundException.class.isAssignableFrom(e.getClass())) {
            throw new NonRetriableException(HttpStatus.INTERNAL_SERVER_ERROR.name(), e.getMessage(),
                    String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.ordinal()));
        } else if (e.getMessage().contains("OAuth access token expired")) {
            // Invalidate the connection pool to force new connections with refreshed token
            log.info("OAuth access token expired for connector {} - invalidating connection pool", connector.getId());
            closeDatasource(connector);

            // Refresh the OAuth token if this service supports it
            if (this instanceof OauthAuthenticationService) {
                try {
                    OauthAuthenticationService oauthService = (OauthAuthenticationService) this;
                    AuthConfig newAuthConfig = oauthService.refreshToken(connector);
                    connector.setAuthConfig(newAuthConfig);
                    log.info("Successfully refreshed OAuth token for connector {} - retrying with updated token", connector.getId());
                    // Throw RetriableException to trigger Spring retry with refreshed token
                    throw new RetriableException(ErrorCodes.TOKEN_EXPIRED.name(), "OAuth token refreshed, retrying operation", "TOKEN_EXPIRED");
                } catch (Exception refreshException) {
                    log.error("Failed to refresh OAuth token for connector {}: {}", connector.getId(), refreshException.getMessage());
                    throw new NonRetriableException(ErrorCodes.TOKEN_EXPIRED, e.getMessage(), String.valueOf(HttpStatus.UNAUTHORIZED));
                }
            } else {
                throw new NonRetriableException(ErrorCodes.TOKEN_EXPIRED, e.getMessage(), String.valueOf(HttpStatus.UNAUTHORIZED));
            }
        } else {
            ConnectorHelper.handleException(e);
        }
    }

    protected String getTableName(String entity, ConnectorInfo connector) {
        entity = replaceU0024WithDollar(entity);
        StringBuilder table = new StringBuilder(getEscapeChar());
        table.append(getCased(entity)).append(getEscapeChar());
        return !StringUtils.isBlank(getSchemaName(connector)) ?  getSchemaName(connector) + "." + table :  table.toString();
    }

    void doCreateObject(CreateObjectRequest request) {
        List<AttributeSchema> attributes = new ArrayList<>();
        attributes.addAll(request.getSchema().getAttributes());
        boolean lowerCaseConfEnabled = request.getConnector().getMetaConfig().containsKey(CASE_CONFIGURATION) ?
                (boolean) request.getConnector().getMetaConfig().get(CASE_CONFIGURATION) : false;
        try (Connection conn = getConnection(request.getConnector())) {
            try (Statement stmt = conn.createStatement()) {
                List<String> fields = attributes.stream().map(a -> getFieldStr(a))
                        .map(a -> lowerCaseConfEnabled ? a.toLowerCase() : a)
                        .collect(Collectors.toList());
                String tableName = getTableName(request.getSchema().getApiName(), request.getConnector());
                String sql = String.format(SqlQueries.CREATE_TABLE, lowerCaseConfEnabled ? tableName.toLowerCase() : tableName,
                        String.join(COMMA, fields));
                sql = optionallyRemoveTrailingSemiColon(sql);
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
    }
    
    private boolean needsLengthAlter(Map<String, Integer> columnLengthMap, AttributeSchema attr, int newLength) {
        return newLength > attr.getLength() && (!columnLengthMap.containsKey(attr.getApiName())
                || columnLengthMap.get(attr.getApiName()) < newLength);
    }
    
    private EntitySchema captureAlter(SyncRequest request, EntitySchema described, Map<String, Integer> columnLengthMap,
            AttributeSchema attr, int newLength, Connection connection) {
        if(newLength > attr.getLength() && request.getConnector().isAlterLengthIfRequired()) {
            if(described == null) {
                described = describe(new DescribeRequest(request.getConnector(), request.getEntityName()), connection).get();
            }
            described.getField(attr.getApiName()).ifPresent(f -> {
                if (needsLengthAlter(columnLengthMap, f, newLength)) {
                    columnLengthMap.put(attr.getApiName().toLowerCase(), newLength);
					log.info("Capturing alter column {} from {} to {}, attrName {}", f.getApiName(), f.getLength(),
							newLength, attr.getApiName().toLowerCase());
                }
            });
        }
        return described;
    }

    private boolean needNumericColumnAlter(SyncRequest request, AttributeSchema attr) {
        return request.getConnector().isAlterLengthIfRequired() && (attr.getPrecision() != 0 || attr.getScale() != 0);
    }

    private void doAlterNumericIfNeeded(SyncRequest request, EntitySchema described, AttributeSchema attr, Connection connection) {
        if(request.getConnector().isAlterLengthIfRequired()) {
            if (described == null) {
                described = describe(new DescribeRequest(request.getConnector(), request.getEntityName()), connection).get();
            }
            described.getField(attr.getApiName()).ifPresent(f -> {
                if (needNumericColumnAlter(request, f)) {
                    // alter only if its datastore and precision and scale are nonzero values
                    log.info("Forced altering Numeric field {} in table {}", f.getApiName(), request.getEntityName());
                    alterNumeric(request.getConnector(), request.getEntityName(), f);
                }
            });
        }
    }
    
    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }
    
    public void createSchema(Statement stmt, ConnectorInfo connector) throws SQLException {
        String sql = String.format(SqlQueries.CREATE_SCHEMA, getValue(connector, SCHEMA_NAME));
        log.info(sql);
        stmt.execute(sql);
    }

    protected void dropSchema(ConnectorInfo connector) {
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.DROP_SCHEMA, getValue(connector, SCHEMA_NAME));
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    protected void dropGroup(ConnectorInfo connector, String groupName) {
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.DROP_GROUP, groupName);
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }
    
    protected void dropDb(ConnectorInfo connector, String name) {
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.DROP_DB, name);
                log.info(sql);
                stmt.execute(sql);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }

    protected String generateGroupName(String schema) {
        return "readonly_" + schema.toLowerCase();
    }
    
    protected void dropUser(ConnectorInfo connector, String userName) {
        try (Connection conn = getConnection(connector)) {
            try (Statement stmt = conn.createStatement()) {
                String sql = String.format(SqlQueries.DROP_USER, userName);
                stmt.execute(sql);
                log.info("Successfully dropped user {}", userName);
            }
        } catch (Exception e) {
            handleException(e, connector);
        }
    }
    
    protected String createGroup(Statement stmt, String schema) throws SQLException {
        String groupName = generateGroupName(schema);
        boolean groupExists = false;
        try (ResultSet rs = stmt.executeQuery(String.format(SqlQueries.SELECT_GROUP, groupName))) {
            while (rs.next()) {
                // The group already exists, just return
                groupExists = true;
                break;
            }
        }
        if (groupExists) {
            log.info("Not creating group {}, it already exists", groupName);
            return groupName;
        }
        String sql = String.format(SqlQueries.CREATE_GROUP, groupName);
        stmt.execute(sql);
        log.info("Successfully created group {}", groupName);
        return groupName;
    }

    protected String createUser(Statement stmt, String userName, String pwd) throws SQLException {
        boolean userExists = false;
        try (ResultSet rs = stmt.executeQuery(String.format(SqlQueries.SELECT_USER, userName))) {
            while (rs.next()) {
                // The user already exists, just return
                userExists = true;
                break;
            }
        }
        if (userExists) {
            log.info("Not creating user {}, it already exists", userName);
            return userName;
        }
        stmt.execute(String.format(SqlQueries.CREATE_USER, userName, pwd));
        log.info("Successfully created user {}", userName);
        return userName;
    }
    
    protected String createDB(Statement stmt, String name) throws SQLException {
        boolean dbExists = false;
        try (ResultSet rs = stmt.executeQuery(String.format(SqlQueries.SELECT_DBS, name))) {
            while (rs.next()) {
                // The db already exists, just return
                String existing = rs.getString("datname");
                if(existing != null && name.equalsIgnoreCase(existing)) {
                    dbExists = true;
                    break;
                }
            }
        }
        if (dbExists) {
            log.info("Not creating db {}, it already exists", name);
            return name;
        }
        String sql = String.format(SqlQueries.CREATE_DB, name);
        log.info(sql);
        stmt.execute(sql);
        log.info("Successfully created db {}", name);
        return name;
    }
    
    protected void revokeCreatePrivilege(Statement stmt, String schema, String groupName) throws SQLException {
        String sql = String.format(SqlQueries.REVOKE_CREATE, schema, groupName);
        stmt.execute(sql);
        log.info("Successfully revoked create from group {} for schema {}", groupName, schema);
    }
    
    private String getIdColumn(EntitySchema entity) {
        return entity.hasIdField() ? entity.getIdField().getApiName().toLowerCase() : Constants.SYNCARI_ID;
    }
    
	protected Object preprocessWmField(EntitySchema entitySchema, String columnName, Object columnValue) {
        String wmDatatype = entitySchema.getWatermarkField().getDataType();
        if (entitySchema.isWatermarkField(columnName) && columnValue == null
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

    private Optional<EntitySchema> doDescribe(DescribeRequest request, Connection conn,
                                              boolean isLowerCaseCheckRequired, Map<String, List<AttributeSchema>> viewNameToFieldsMap) {
        String schemaName =
                isLowerCaseCheckRequired && isLowerCaseConfEnabled(request.getConnector()) ? StringUtils.lowerCase(request.getEntity()) : request.getEntity();
        String sanitizedSchemaName = replaceDollarWithU0024(schemaName);
        EntitySchema e = new EntitySchema(sanitizedSchemaName, sanitizedSchemaName);
        List<AttributeSchema> attributes = getAttributes(request.getConnector(), conn, schemaName);
        if (!attributes.isEmpty()) {
            Map<String, ForeignKey> foreignKeyMap = getForeignKeys(request.getConnector(), conn, schemaName);
            setForeignKeys(attributes, foreignKeyMap);
            e.setAttributes(attributes);
            return Optional.of(e);
        }
        if (viewNameToFieldsMap != null && viewNameToFieldsMap.containsKey(request.getEntity())) {
            attributes = viewNameToFieldsMap.get(request.getEntity());
            if (!attributes.isEmpty()) {
                e.setAttributes(attributes);
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    protected boolean isLowerCaseConfEnabled(ConnectorInfo connectorInfo){
        return connectorInfo.getMetaConfig().containsKey(CASE_CONFIGURATION) ?
                (boolean) connectorInfo.getMetaConfig().get(CASE_CONFIGURATION) : false;
    }

    protected Map<String, ForeignKey> getForeignKeys(ConnectorInfo connectorInfo, Connection conn, String entityName) {
        return new HashMap<>();
    }

    protected void setForeignKeys(List<AttributeSchema> attributes, Map<String, ForeignKey> foreignKeyMap) {
    }

    protected String getForeignKeysQuery(String tableName, ConnectorInfo connectorInfo) {
        return null;
    }

    protected String optionallyRemoveTrailingSemiColon(String query) {
        return query;
    }
    
    protected String replaceDollarWithU0024(String input) {
      if(!shouldReplaceDollar()) {
        return input;
      }
      if (input == null) {
        return null;
      }
      return input.replace("$", "_U0024_");
    }

    protected String replaceU0024WithDollar(String input) {
      if(!shouldReplaceDollar()) {
        return input;
      }
      if (input == null) {
        return null;
      }
      return input.replace("_U0024_", "$");
    }
    
    protected boolean shouldReplaceDollar() {
      return false;
    }
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

class DBIterator extends DefaultDataIterator {

    public DBIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords,
            String timeZone, String wmDataType) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords, timeZone, wmDataType);
    }

    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        // if no data is retrieved meaning the window is exhausted - reset the offset
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
