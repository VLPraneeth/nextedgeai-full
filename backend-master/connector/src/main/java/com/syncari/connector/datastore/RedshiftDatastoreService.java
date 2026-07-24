package com.syncari.connector.datastore;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.utils.Pair;
import org.springframework.stereotype.Component;

import com.syncari.connector.database.RedshiftService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedshiftDatastoreService extends RedshiftService implements Datastore {
    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }

    @Override
    public String getCategory() {
        return "Datawarehouse";
    }

    @Override
    public String getName() {
        return Constants.DATASTORE;
    }

    @Override
    public UIMetadata getUIMetadata() {
        return super.getUIMetadata().setIconPath("")
                .setDisplayName(Constants.DATASTORE);
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.Datastore;
    }

    @Override
    public List<Map<String, Object>> retrieveData(ConnectorInfo connector, String query, Map<String, String> fields) {
        return executeDmlQuery(connector, query, fields);
    }

    @Override
    public List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query, Map<Integer, ParamValue> paramValues, List<DatastoreFieldMetadata> fields, Set<DatastoreTableMetadata> datastoreTableMetadatas) {
        return executeQueryToGetData(connector, query, fields, Set.of());
    }

    @Override
    public List<Map<String, Object>> retrievePairData(ConnectorInfo connector, String query, List<DatastoreFieldMetadata> fields) {
        return executeQueryToGetData(connector, query, fields, Set.of());
    }

    @Override
    public void executeDdlSql(ConnectorInfo connector, String sql){
        executeDdlQuery(connector, sql);
    }

    @Override
    public long count(ConnectorInfo connectorInfo , String datastoreName){
        throw new UnsupportedOperationException("This operation is not supported for redshift");
    }
}
