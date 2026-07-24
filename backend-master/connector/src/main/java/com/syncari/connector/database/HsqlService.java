package com.syncari.connector.database;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.service.Transformer;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class HsqlService extends DatabaseService {
    private static final String JDBC_URL = "jdbc:hsqldb:file:/tmp/%s;sql.syntax_pgs=true;lock_file=false";
    private static final String user = "SA";
    private static final String password = "";
    @Autowired
    Transformer transformer;
    @Autowired
    DateUtil dateUtil;
    
    static {
        loadDriver("org.hsqldb.jdbc.JDBCDriver");
    }

    protected String getJdbcURL(ConnectorInfo connector) {
        return getJdbcURL(getDbName(connector));
    }

    protected String getJdbcURL(String dbName) {
        return String.format(JDBC_URL, dbName);
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }

    @Override
    public Map<String, String> getEntityMappings() {
        // TODO Auto-generated method stub
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return "HSQL";
    }

    @Override
    public String getCategory() {
        return "Database";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return null;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    protected Connection getConnection(String dbName) throws SQLException {
        return DriverManager.getConnection(getJdbcURL(dbName), user, password);
    }

    public static String getDbName(ConnectorInfo connector) {
        return connector.getId() + "_" + connector.getInstanceId() + "_" + connector.getMetaConfig().get("fileName").toString();
    }

    String getDescribeSql(ConnectorInfo connector) {
        return "select * from information_schema.tables where TABLE_SCHEMA='PUBLIC'";
    }

    @Override
    String getDescribeFieldSqlForLateBindingViews(ConnectorInfo connectorInfo) {
        return "";
    }

    @Override
    String getDescribeFieldSql(ConnectorInfo connector, String tableName) {
        String table = tableName.toUpperCase();
        return String.format("select * from information_schema.columns where TABLE_NAME='%s'", table);
    }

    @Override
    String getSelectByIdsSql() {
        return "";
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
        return "PUBLIC";
    }

    protected static String getValue(ConnectorInfo connector, String key) {
        Object schema = connector.getMetaConfig().get(key);
        return schema == null ? "" : schema.toString();
    }
    
    protected Connection getConnection(ConnectorInfo connector) throws ClassNotFoundException, SQLException {
        return getConnection(getDbName(connector));
    }
    
    @Override
    String getDatatype(AttributeSchema from) {
        if("timestamp".equalsIgnoreCase(from.getDataType())) {
            return "timestamp";
        }
        if("datetime".equalsIgnoreCase(from.getDataType())) {
            return "datetime";
        }
        if("other".equalsIgnoreCase(from.getDataType())) {
            return "other";
        }
        return super.getDatatype(from);
    }
    
    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        super.doCreateObject(request);
        return request.getSchema();
    }
    
    @Override
    String getWatermarkCondition(SyncRequest request, long offset, int pageSize) {
        String watermarkField = request.getEntitySchema().getWatermarkField().getApiName();
        String query = "\"%s\" >= %s AND \"%s\" < %s ORDER by \"%s\" LIMIT %s OFFSET %s";
        long start = request.getWatermark().getStart();
        long end = request.getWatermark().getEnd();
        return String.format(query, watermarkField, start, watermarkField, end, watermarkField, pageSize, offset);
    }
    
    @Override
    List<EntityData> extractData(SyncRequest request, Statement stmt, List<String> fieldNames, String sql)
            throws SQLException {
        List<EntityData> extractData = super.extractData(request, stmt, fieldNames, sql);
        return extractData.stream().map(e -> (EntityData)e.getValue("entity_data")).collect(Collectors.toList());
    }

    protected DefaultDataIterator createIterator(SyncRequest request, WatermarkInfo watermark,
            Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator, int pageSize) {
        return new HSQLCloseableDataIterator(watermark, watermark.getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit(), this,
                getDbName(request.getConnector()));
    }

    public void cleanupDB(String dbName) {
        String pathname = "/tmp/"+dbName;
        try {
            log.info("Cleaning Hsql local DB at {} ", pathname);
            File folder = new File("/tmp");
            shutdownDB(dbName);
            File[] files = folder.listFiles( f ->  f.getName().matches( dbName+".*"));
            for (File file : files) {
                if (!file.delete()) {
                    log.error("Can't remove " + file.getAbsolutePath());
                }
            }
            log.info("Successfully cleanedup local DB. Total files removed: {} ", files.length);
        } catch (Exception e) {
            log.error("Error while deleting file {}", pathname);
        }
    }

    protected void shutdownDB(String pathname) throws SQLException {
        try(Connection c = getConnection(pathname)){
            c.createStatement().execute("SHUTDOWN");
        }
    }

    public long count(ConnectorInfo connectorInfo, String entityName, long startWatermark) {
        String tableName = getTableName(entityName, connectorInfo);
        try {
            try(Connection connection = getConnection(connectorInfo)) {
                try (Statement stmt = connection.createStatement()) {
                    try (ResultSet resultSet = stmt.executeQuery("SELECT COUNT(1) FROM " + tableName +" where \"updated_at\" >="+startWatermark)) {
                        while (resultSet.next()) {
                            return resultSet.getLong(1);
                        }
                        return 0l;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public long maxWatermark(ConnectorInfo connectorInfo, String entityName) {
        String tableName = getTableName(entityName, connectorInfo);
        try {
            String sql = String.format("SELECT max(\"updated_at\") FROM %s",tableName);
            try(Connection connection=getConnection(connectorInfo)){
                try(Statement stmt = connection.createStatement()) {
                    try(ResultSet resultSet = stmt.executeQuery(sql)) {
                        while (resultSet.next()) {
                            return resultSet.getLong(1);
                        }
                    }
                }
            }
            return 0l;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return 0l;
        }

    }

    public int deleteAllData(ConnectorInfo connectorInfo, String entityName) {
        String tableName = getTableName(entityName, connectorInfo);
        int result = 0;
        try {
            String sql = String.format("DROP TABLE %s",tableName);
            try(Connection connection=getConnection(connectorInfo)){
                try(Statement stmt = connection.createStatement()) {
                    result = stmt.executeUpdate(sql);
                }
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return 0;
        }

    }
}
class HSQLCloseableDataIterator extends DefaultDataIterator {

    private HsqlService dbService;
    private String dbName;
    
    public HSQLCloseableDataIterator(WatermarkInfo baseWatermark, long offset,
            Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator, List<EntityData> data,
            AttributeSchema watermarkField, int maxRecords) {
        super(baseWatermark, offset, generator, data, watermarkField, maxRecords);
    }
    
    public HSQLCloseableDataIterator(WatermarkInfo baseWatermark, long offset,
            Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator,
            List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords,HsqlService dbService, String dbName) {
        super(baseWatermark, offset, generator, data, watermarkField, pageSize, maxRecords);
        this.dbService = dbService;
        this.dbName = dbName;
    }
    
    public boolean hasNext() {
        boolean hasNext = super.hasNext();
        if(!hasNext) {
            dbService.cleanupDB(dbName);
        }
        return hasNext;
    }

    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        // if no data is retrieved meaning the window is exhausted - reset the offset
        if (data.isEmpty()) return 0;
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
