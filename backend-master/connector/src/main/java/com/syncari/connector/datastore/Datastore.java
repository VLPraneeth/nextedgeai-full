package com.syncari.connector.datastore;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ParamValue;
import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.connector.data.DatastoreTableMetadata;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Datastore extends AuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    
    void provision(ConnectorInfo connector, String userName, String pwd, boolean readOnly);
    
    void deprovision(ConnectorInfo connector, String userName);

    List<Map<String, Object>> retrieveData(ConnectorInfo connector, String query, Map<String, String> fields);

    long count(ConnectorInfo connectorInfo , String datastoreName);

    List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query, List<DatastoreFieldMetadata> fields);

    List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query, Map<Integer, ParamValue> paramValues,
                                               List<DatastoreFieldMetadata> fields, Set<DatastoreTableMetadata> datastoreTableMetadatas);

    default Map<String, String> preProcessFieldNames(List<String> datastoreNames) {
    	return Map.of();
    }

    void executeDdlSql(ConnectorInfo connector, String sql);

    default boolean checkForSyncariIdIndex(ConnectorInfo info, String tableName) {
        return true;
    }

    default boolean createSyncariIdIndex(ConnectorInfo info, String tableName, String syncariId) {
        return false;
    }

    default boolean renameTable(ConnectorInfo info, String tableName, String newName, String syncariId) {
        return false;
    }

    default boolean alterLength(ConnectorInfo info, String tableName, String column, int newLength, String syncariId) {
        return false;
    }

    default boolean dropIndex(ConnectorInfo info, String index, String syncariId) {
        return false;
    }

    default void getIndexes(ConnectorInfo info, String tablename) {}

    default void getConstraints(ConnectorInfo info, String tablename) {}

    default boolean dropConstraint(ConnectorInfo info, String constraint, String tableName, String syncariId) {
        return false;
    }

    default String generateCTE(String relationName, String query){return null;}

    default Long getNextSequenceValue(ConnectorInfo info, String sequenceName){return null;}
    default boolean createSequence(ConnectorInfo info, String sequenceName, Long startValue){return false;}
    default boolean deleteSequence(ConnectorInfo info, String sequenceName){return false;}
}
