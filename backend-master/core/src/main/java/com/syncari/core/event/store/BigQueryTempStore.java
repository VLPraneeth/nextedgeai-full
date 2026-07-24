package com.syncari.core.event.store;

import com.google.cloud.bigquery.*;
import com.syncari.connector.exception.UnknownException;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.TempEventStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component
public class BigQueryTempStore implements TempEventStore {

    @Autowired
    AppConfig appConfig;

    @Autowired
    BigQuery bigQuery;

    @Autowired
    BigQueryHelper helper;

    private List<FieldDefinition> getTableSchema(String syncariId, String tableName) {
        List<FieldDefinition> fielDefn = StoreSchema.getDFITables(syncariId).getOrDefault(tableName, null);
        if (fielDefn == null){
            log.error("No schema found for tableId : "+tableName);
            throw new RuntimeException("No schema found for tableId : "+tableName);
        }
        return fielDefn;
    }

    protected void createDatasetIfNotExists(String datasetName) {
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

    private void setPartitionDetails(TableId tableId, StandardTableDefinition.Builder tableBuilder) {
        Optional<String> partitionField = StoreSchema.getPartitionField(tableId.getTable());
        if (partitionField.isEmpty())
            return;
        if (StoreSchema.isRangePartitionedTable(tableId.getTable())) {
            RangePartitioning partitioning =
                    RangePartitioning.newBuilder()
                            .setField(partitionField.get())
                            .setRange(
                                    RangePartitioning.Range.newBuilder()
                                            .setStart(1L)
                                            .setInterval(1L)
                                            .setEnd(StoreSchema.getRangePartitionMaxRangeValue(tableId.getTable()))
                                            .build())
                            .build();
            tableBuilder.setRangePartitioning(partitioning);

        }
        tableBuilder.setTimePartitioning(TimePartitioning
                .newBuilder(StoreSchema.getTimestampPartitionPeriodByTable(tableId.getTable())).setRequirePartitionFilter(true).setField(partitionField.get()).build());
    }

    protected void createTableIfNotExists(TableId tableId, List<FieldDefinition> fields) {
        try {
            Table table = bigQuery.getTable(tableId);
            if (table == null) {
                Schema schema = Schema.of(getFieldDefs(fields));
                StandardTableDefinition.Builder tableBuilder = StandardTableDefinition.of(schema).toBuilder();
                setPartitionDetails(tableId, tableBuilder);
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


    Map<String, List<FieldDefinition>> getTables(String syncariId, String tableName, String tempTableName) {
        Map<String, List<FieldDefinition>> tables = new HashMap<>();
        tables.put(tempTableName, getTableSchema(syncariId, tableName));
        return tables;
    }

    private List<FieldDefinition> cloneFieldDefinitions(List<FieldDefinition> fields, String tempTableName) {
        List<FieldDefinition> newFields = new ArrayList<>();
        for (FieldDefinition field: fields) {
            newFields.add(new FieldDefinition(field.syncariId, tempTableName, field.fieldName, field.type, field.required));
        }
        return newFields;
    }

    @Override
    public void provision(String syncariId, String tableName, String tempTableName) {
        createDatasetIfNotExists(syncariId);
        Map<String, List<FieldDefinition>> tables = getTables(syncariId, tableName, tempTableName);
        for (Map.Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
            TableId tableId = TableId.of(syncariId, entry.getKey());
            createTableIfNotExists(tableId, cloneFieldDefinitions(entry.getValue(), tempTableName));
        }
        log.info("provisioned table {}", tableName);
    }

    @Override
    public void deprovision(String syncariId, String tableName, String tempTableName) {
        Map<String, List<FieldDefinition>> tables = getTables(syncariId, tableName, tempTableName);
        for (Map.Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
            TableId tableId = TableId.of(syncariId, entry.getKey());
            helper.deleteTableIfExists(tableId);
        }
        log.info("de-provisioned table {}", tableName);
    }

    @Override
    public void verifyProvisioned(String syncariId, String tableName, String tempTableName) {
        bigQuery.getDataset(syncariId);
        Map<String, List<FieldDefinition>> tables = getTables(syncariId, tableName, tempTableName);
        for (Map.Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
            TableId tableId = TableId.of(syncariId, entry.getKey());
            bigQuery.getTable(tableId);
        }
    }

}
