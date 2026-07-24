package com.syncari.connector.datastore;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.ParamValue;
import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.connector.data.DatastoreTableMetadata;
import com.syncari.connector.database.PostgresService;
import com.syncari.connector.service.query.SqlQueries;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;

import java.math.BigInteger;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Slf4j
public abstract class AbstractPostgresDatastoreService extends PostgresService implements Datastore {

    @Override
    public ConnectorType getType() {
        return ConnectorType.Datastore;
    }

    @Override
    public List<Map<String, Object>> retrieveData(ConnectorInfo connector, String query, Map<String, String> fields) {
        return executeDmlQuery(connector, query, fields);
    }

    @Override
    public List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query, Map<Integer, ParamValue> paramValues,
                                               List<DatastoreFieldMetadata> fields, Set<DatastoreTableMetadata> datastoreTableMetadatas){
        return executePreparedStmtToGetData(connector, query,paramValues, fields, datastoreTableMetadatas);
    }
    @Override
    public List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query,
                                                      List<DatastoreFieldMetadata> fields) {
        return executeQueryToGetData(connector, query, fields, new HashSet<>());
    }

    @Override
    public void executeDdlSql(ConnectorInfo connector, String sql){
        executeDdlQuery(connector, sql);
    }

    @Override
    public Map<String, String> preProcessFieldNames(List<String> datastoreNames) {
        if(datastoreNames == null || datastoreNames.isEmpty()) {
            return Map.of();
        }
        Map<String, String> fieldMap = new HashedMap<>();
        List<String> potentialDuplicates = new ArrayList<>();
        datastoreNames.forEach(a -> {
            var datastoreNameBase = a;
            if(datastoreNameBase != null && datastoreNameBase.length() > 59) {
                datastoreNameBase = datastoreNameBase.substring(0, 55);
                var datastoreName = datastoreNameBase;
                int i=1;
                while(potentialDuplicates.contains(datastoreName)) {
                    datastoreName = datastoreNameBase + "_" + i;
                    i++;
                }
                datastoreNameBase = datastoreName;
                fieldMap.put(a, datastoreNameBase);
            }
            potentialDuplicates.add(datastoreNameBase);

        });
        return fieldMap;
    }

    @Override
    public boolean checkForSyncariIdIndex(ConnectorInfo info, String tablename) {
        String sql = String.format(SqlQueries.GET_INDEXES, tablename);
        List<Map<String, Object>> results = executeDmlQuery(info, sql, Map.of("column_name", "string"));
        for(Map<String, Object> result: results) {
            String columnName = (String) result.get("column_name");
            if(columnName.equalsIgnoreCase("syncariid")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean createSyncariIdIndex(ConnectorInfo info, String tableName, String syncariId) {
        String dbName = String.format("syncari_%s", syncariId);
        String sql = String.format("create index if not exists %s_syncariid_key on %s.%s (syncariid);", tableName, dbName, tableName);
        try {
            executeDdlSql(info, sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean renameTable(ConnectorInfo info, String tableName, String newName, String syncariId) {
        String dbName = String.format("syncari_%s", syncariId);
        String sql = String.format(SqlQueries.RENAME_TABLE, dbName + ".\"" + tableName + "\"", newName);
        try {
            executeDdlSql(info, sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean alterLength(ConnectorInfo info, String tableName, String column, int newLength, String syncariId) {
        String dbName = String.format("syncari_%s", syncariId);
        String sql = String.format(SqlQueries.ALTER_LENGTH, dbName + ".\"" + tableName + "\"", column, newLength);
        try {
            executeDdlSql(info, sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean dropIndex(ConnectorInfo info, String index, String syncariId) {
        String dbName = String.format("syncari_%s", syncariId);
        String sql = String.format(SqlQueries.DROP_INDEX, dbName + "." + index);
        try {
            executeDdlSql(info, sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void getIndexes(ConnectorInfo info, String tablename) {
        String sql = String.format(SqlQueries.GET_INDEXES, tablename);
        List<Map<String, Object>> results = executeDmlQuery(info, sql, Map.of("index_name", "string", "column_name", "string"));
        for(Map<String, Object> result: results) {
            String indexName = (String) result.get("index_name");
            String columnName = (String) result.get("column_name");
            log.info("Index found for column {} - {}", columnName, indexName);
        }
    }

    @Override
    public void getConstraints(ConnectorInfo info, String tablename) {
        String sql = String.format(SqlQueries.GET_CONSTRAINTS, tablename);
        List<Map<String, Object>> results = executeDmlQuery(info, sql, Map.of("table_name", "string", "constraint_name", "string"));
        for(Map<String, Object> result: results) {
            String table = (String) result.get("table_name");
            String constraint = (String) result.get("constraint_name");
            log.info("Constraint found for table {} - {}", table, constraint);
        }
    }

    @Override
    public boolean dropConstraint(ConnectorInfo info, String constraint, String tableName, String syncariId) {
        String dbName = String.format("syncari_%s", syncariId);
        String sql = String.format(SqlQueries.DROP_CONSTRAINT, dbName + ".\"" + tableName + "\"", constraint);
        try {
            executeDdlSql(info, sql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String createGroup(Statement stmt, String schema) throws SQLException {
        return super.createGroup(stmt, schema);
    }

    @Override
    public void revokeCreatePrivilege(Statement stmt, String schema, String groupName) throws SQLException {
        super.revokeCreatePrivilege(stmt, schema, groupName);
    }

    @Override
    public void handleException(Exception e, ConnectorInfo connector) {
        super.handleException(e, connector);
    }

    @Override
    public String createUser(Statement stmt, String userName, String pwd) throws SQLException {
        return super.createUser(stmt, userName, pwd);
    }

    @Override
    public String createDB(Statement stmt, String name) throws SQLException {
        return super.createDB(stmt, name);

    }

    @Override
    public long count(ConnectorInfo connectorInfo , String datastoreName){
        String query = String.format(COUNT_QUERY, getTableName(datastoreName,connectorInfo));
        // totalCount is an alias in Count query
        List<Map<String, Object>> result =  this.retrieveData(connectorInfo, query, Map.of("totalCount","long"));
        if (CollectionUtils.isNotEmpty(result)){
            return (Long)result.stream().findFirst().get().get("totalCount");
        }
        return 0;
    }

    @Override
    public String generateCTE(String relationName, String query){
        return "WITH \"" + relationName + "\" AS ( " + query + " ) \n";
    }

    // This returns tablename-> List<column info map>
    public Map<String, List<Map<String, String>>> getSchemaMetadata(ConnectorInfo connector){
        return super.getSchemaMetadata(connector);
    }

    public Long getNextSequenceValue(ConnectorInfo config, String sequenceName, BigInteger startValue) { return null; }
    public boolean createSequence(ConnectorInfo config, String sequenceName, Long startValue) { return false; }
    public boolean deleteSequence(ConnectorInfo config, String sequenceName) { return false; }
}
