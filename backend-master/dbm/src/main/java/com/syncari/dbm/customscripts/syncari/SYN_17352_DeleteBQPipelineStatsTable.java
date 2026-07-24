package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableId;
import com.syncari.core.MigrationContext;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.FieldDefinition;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j

public class SYN_17352_DeleteBQPipelineStatsTable {

    public static final String PIPELINE_STATS_TABLE_NAME = "pipelineStats";

    @ChangeSet(order = "001", id = "deletebqpipelinestatstable", author = "rohit", runAlways = true)
    public void deletebqpipelinestatstable(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        BigQueryHelper helper = MigrationContext.getBigQueryHelper();
        List<Organization> orgs = organizationRepo.findAllCustomers();
        orgs.forEach(o -> {
            List<Instance> instances = o.getInstances();
            instances.forEach(i -> {
                try{
                    if (!dryRun){
                        Map<String, List<FieldDefinition>> tables = getTables(i.getSyncariId());
                        for (Map.Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
                            TableId tableId = TableId.of(i.getSyncariId(), entry.getKey());
                            log.info("Deleting table pipelineStats for syncariid {}", i.getSyncariId());
                            helper.deleteTableIfExists(tableId);
                        }
                    }else{
                        log.info("Running in dry run mode , not deleting table pipelineStats for syncariid {}", i.getSyncariId());
                    }

                }catch (Exception e){
                    log.error("Exception occurred while deleting table pipelineStats for syncariid {}", i.getSyncariId());
                }

            });
        });
    }

    private static Map<String, List<FieldDefinition>> getTables(String syncariId) {
        Map<String, List<FieldDefinition>> tables = new LinkedHashMap<>();
        tables.put(PIPELINE_STATS_TABLE_NAME, getPipelineStatsFields(syncariId));
        return tables;
    }

    private static List<FieldDefinition> getPipelineStatsFields(String syncariId) {
        List<FieldDefinition> fields = new ArrayList<>();
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "pipelineId", StandardSQLTypeName.STRING, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "connectorId", StandardSQLTypeName.STRING, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "connectorName", StandardSQLTypeName.STRING, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "batchId", StandardSQLTypeName.STRING, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "stageId", StandardSQLTypeName.STRING, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "stageName", StandardSQLTypeName.STRING, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "stageType", StandardSQLTypeName.STRING, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "targetId", StandardSQLTypeName.DATE, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "targetName", StandardSQLTypeName.TIMESTAMP, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "targetType", StandardSQLTypeName.TIMESTAMP, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "latency", StandardSQLTypeName.INT64, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "recordsProcessed", StandardSQLTypeName.INT64, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "duplicateCount", StandardSQLTypeName.INT64, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "dedupeCount", StandardSQLTypeName.INT64, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "emptyInputCount", StandardSQLTypeName.INT64, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "emptyOutputCount", StandardSQLTypeName.INT64, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "changeCount", StandardSQLTypeName.INT64, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "occuredDate", StandardSQLTypeName.DATE, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "occuredDateHour", StandardSQLTypeName.TIMESTAMP, false));
        fields.add(new FieldDefinition(syncariId, PIPELINE_STATS_TABLE_NAME, "occuredTime", StandardSQLTypeName.TIMESTAMP, true));
        return fields;
    }

}
