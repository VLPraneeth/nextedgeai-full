package com.syncari.core.event.store;

import com.google.cloud.bigquery.*;
import com.syncari.connector.exception.UnknownException;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.repo.DFIMaterializedViewInfo;
import com.syncari.core.event.store.repo.MaterializedViewConfig;
import com.syncari.core.model.Event;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.misc.SyncLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component
public class DFIEventStore implements EventStore {
    public static final int BAD_REQUEST = 400;
    public static final Long MAT_VIEW_REFRESH_INTERVAL_IN_MINUTES = 60L;

    @Autowired
    BigQuery bigQuery;
    @Autowired
    AppConfig appConfig;
    @Autowired
    BigQueryHelper helper;
    @Autowired
    DFIMaterializedViewInfo dfiMaterializedViewInfo;

    private TableId toTableId(String tableName) {
        return TableId.of(SyncariContext.getSyncariId(), tableName);
    }

    private List<MaterializedViewConfig> getDFIMaterializedViews() {
        return dfiMaterializedViewInfo.getDFIMatViewConfig();
    }

    public boolean doesMaterializedViewExist(String viewName) {
        try {
            return bigQuery.getTable(toTableId(viewName)) != null;
        } catch (BigQueryException e) {
            if (e.getCode() == 404) {
                return false;
            } else {
                log.error("Error checking if view {} exists. error :  ", viewName, e);
                return false;
            }
        }
    }

    public void createMaterializedView(MaterializedViewConfig viewConfig) {
        if (doesMaterializedViewExist(viewConfig.getViewName())) {
            log.info("Mat view {} already exists", viewConfig.getViewName());
            return;
        }
        TableId tableId = TableId.of(appConfig.getGcpProjectId(), SyncariContext.getSyncariId(), viewConfig.getViewName());
        MaterializedViewDefinition materializedViewDefinition = MaterializedViewDefinition.newBuilder(viewConfig.getSql())
                .setEnableRefresh(true)
                .setRefreshIntervalMs(MAT_VIEW_REFRESH_INTERVAL_IN_MINUTES * 60 * 1000)
                .build();
        TableInfo tableInfo = TableInfo.newBuilder(tableId, materializedViewDefinition).build();

        try {
            bigQuery.create(tableInfo);
            log.info("Materialized view {} created successfully", helper.getFullTableName(viewConfig.getViewName()));
        } catch (Exception e) {
            log.error("Error creating materialized view {}. Error : ", helper.getFullTableName(viewConfig.getViewName()), e);
        }
    }

    private void createMatViews() {
        for (MaterializedViewConfig config: getDFIMaterializedViews()) {
            createMaterializedView(config);
        }
    }

    private void createDatasetIfNotExists(String datasetName) {
        try {
            DatasetId datasetId = DatasetId.of(appConfig.getGcpProjectId(), datasetName);
            Dataset dataset = bigQuery.getDataset(datasetId);
            if (dataset == null) {
                bigQuery.create(DatasetInfo.of(datasetName));
                dataset = bigQuery.getDataset(datasetId);
                if (dataset == null) throw new RuntimeException(format("Could not create dataset %s", datasetId));
                log.info(format("Bigquery Dataset %s created successfully", dataset.getDatasetId()));
            }else{
                log.info(format("Bigquery Dataset %s already exists", dataset.getDatasetId()));
            }
        } catch (BigQueryException e) {
            throw new UnknownException(e.getMessage());
        }
    }

    private List<Field> getFieldDefs(List<FieldDefinition> fields) {
        return fields.stream().map(field -> helper.toBQField(field)).collect(Collectors.toList());
    }

    private void createTableIfNotExists(TableId tableId, List<FieldDefinition> fields) {
        try {
            Table table = bigQuery.getTable(tableId);
            if (table == null) {
                Schema schema = Schema.of(getFieldDefs(fields));
                StandardTableDefinition.Builder tableBuilder = StandardTableDefinition.of(schema).toBuilder();
                Optional<String> partitionField = StoreSchema.getPartitionField(tableId.getTable());
                if (partitionField.isPresent()) {
                    List<FieldDefinition> filteredFields = fields.stream().filter(f -> f.fieldName.equals(partitionField.get())).collect(Collectors.toList());
                    if (filteredFields.size() != 1)
                        log.error("cannot create partition as there are conflicting field definition");
                    else {
                        FieldDefinition tablePartitionField = filteredFields.get(0);
                        if(tablePartitionField.type.equals(StandardSQLTypeName.TIMESTAMP))
                            partitionField.ifPresent(p -> {
                                tableBuilder.setTimePartitioning(TimePartitioning
                                        .newBuilder(TimePartitioning.Type.DAY).setRequirePartitionFilter(true).setField(p).build());
                            });
                        else if (filteredFields.get(0).type.equals(StandardSQLTypeName.INT64)) {
                            RangePartitioning partitioning = RangePartitioning.newBuilder()
                                    .setField(StoreSchema.getRangePartitionField(tableId.getTable()))
                                    .setRange(RangePartitioning.Range.newBuilder()
                                            .setStart(1L)
                                            .setEnd(StoreSchema.getRangePartitionMaxRangeValue(tableId.getTable())+1)
                                            .setInterval(1L)
                                            .build())
                                    .build();
                            tableBuilder.setRangePartitioning(partitioning);
                        }
                    }
                }

                List<String> clusterFields = StoreSchema.getClusterFields(tableId.getTable());
                if(!clusterFields.isEmpty()) {
                    tableBuilder.setClustering(Clustering.newBuilder().setFields(clusterFields).build());
                }
                List<String> primaryKeys = StoreSchema.getPrimaryKeys(tableId.getTable());
                if(!primaryKeys.isEmpty()) {
                    TableConstraints tableConstraints = TableConstraints.newBuilder().setPrimaryKey(PrimaryKey.newBuilder().setColumns(primaryKeys).build()).build();
                    tableBuilder.setTableConstraints(tableConstraints);
                }
                TableDefinition tableDefinition = tableBuilder.build();
                TableInfo tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build();
                bigQuery.create(tableInfo);
                table = bigQuery.getTable(tableId);
                if (table == null) throw new RuntimeException(format("Could not create Bigquery table ", tableId));
                log.info(format("Bigquery Table %s created successfully", table.getTableId()));
            }else{
                log.info(format("Bigquery Table %s already exists", table.getTableId()));
            }
        } catch (BigQueryException e) {
            throw new UnknownException(e.getMessage());
        }
    }

    public void deleteMaterializedView(MaterializedViewConfig viewConfig) {
        try {
            boolean deleted = bigQuery.delete(toTableId(viewConfig.getViewName()));
            if (deleted) {
                log.info("Materialized view {} deleted.", viewConfig.getViewName());
            } else {
                log.info("Materialized view {} not found.", viewConfig.getViewName());
            }
        } catch (BigQueryException e) {
            log.error("Error deleting views {}. Error : ", viewConfig.getViewName(), e);
        }
    }

    private void deleteMatViews() {
        for (MaterializedViewConfig config: getDFIMaterializedViews()) {
            deleteMaterializedView(config);
        }
    }

    @Override
    public void provision(String syncariId, String tableName) {
        createDatasetIfNotExists(syncariId);
        List<FieldDefinition> fields = StoreSchema.getDFITables(syncariId).get(tableName);
        TableId tableId = TableId.of(syncariId, tableName);
        createTableIfNotExists(tableId, fields);
    }

    @Override
    public void provision(String syncariId) {
        createDatasetIfNotExists(syncariId);
        Map<String, List<FieldDefinition>> tables = StoreSchema.getDFITables(syncariId);
        for (Map.Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
            TableId tableId = TableId.of(syncariId, entry.getKey());
            createTableIfNotExists(tableId, entry.getValue());
        }
        createMatViews();
    }

    @Override
    public void deprovision(String syncariId) {
        Map<String, List<FieldDefinition>> tables = StoreSchema.getDFITables(syncariId);
        for (Map.Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
            TableId tableId = TableId.of(syncariId, entry.getKey());
            helper.deleteTableIfExists(tableId);
        }
        deleteMatViews();
    }

    @Override
    public void verifyProvisioned(String syncariId) {
        throw new NotImplementedException();
    }

    @Override
    public void insert(List<Event> events) {
        throw new NotImplementedException();
    }

    @Override
    public void insertSyncLogs(List<SyncLog> txnLogs) {
        throw new NotImplementedException();
    }

    @Override
    public void insertErrorLogs(List<SyncError> errorLogs) {
        throw new NotImplementedException();
    }

    @Override
    public List<TransactionLog> insertTransactionLogs(List<TransactionLog> logs) {
        throw new NotImplementedException();
    }

    @Override
    public void addFieldToTable(FieldDefinition def) {
        Map<String, List<FieldDefinition>> tables = StoreSchema.getDFITables(def.syncariId);
        tables.get(StoreSchema.TXNS_LOG_TABLE_NAME);
        TableId tableId = TableId.of(def.syncariId, StoreSchema.TXNS_LOG_TABLE_NAME);
        Table table = bigQuery.getTable(tableId);
        Schema schema = table.getDefinition().getSchema();
        FieldList fields = schema.getFields();
        Field newField = Field.newBuilder(def.fieldName, def.type).setMode(!def.required ? Field.Mode.NULLABLE : Field.Mode.REQUIRED).build();
        List<Field> field_list = new ArrayList<Field>();
        boolean found = false;
        for (Field f : fields) {
            if (f.getName().equals(def.fieldName)) {
                found = true;
                field_list.add(newField);
            } else {
                field_list.add(f);
            }
        }
        if (!found) {
            field_list.add(newField);
        }
        Schema newSchema = Schema.of(field_list);
        table.toBuilder().setDefinition(StandardTableDefinition.of(newSchema)).build().update();
        log.info("Successfully added {} to table {}", def.fieldName, StoreSchema.TXNS_LOG_TABLE_NAME);
    }
}
