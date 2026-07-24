package com.syncari.core.event.store.repo;

import com.google.cloud.bigquery.*;
import com.syncari.core.SyncariContext;
import com.syncari.core.dfiv2.DFIResponse;
import com.syncari.core.dfiv2.DFIRuleMetric;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.StoreSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.lang.String.format;

@Slf4j
@Component
public class DFIResultCountRepo {
    @Autowired
    BigQueryHelper helper;

    private static final String deleteByRule
            = "DELETE FROM `%s` WHERE entityId = '%s' AND ruleId = '%s'";

    private static final String softDeleteByRule
            = "UPDATE `%s` SET isDeleted = TRUE WHERE entityId = '%s' AND ruleId = '%s' AND evaluatedAt < '%s'";

    private TableId toTableId(String tableName) {
        return TableId.of(SyncariContext.getSyncariId(), tableName);
    }

    public static int generateHash(String input) {
        //to generate a hash between 1 and 10 for partition key
        //DO NOT CHANGE
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be null or empty.");
        }

        int hash = input.hashCode();
        int positiveHash = Math.abs(hash);
        return (positiveHash % 11) + 1;
    }

    private List<InsertAllRequest.RowToInsert> toDFIResultsCountRows(DFIResponse response) {
        List<InsertAllRequest.RowToInsert> rows = new ArrayList<InsertAllRequest.RowToInsert>();
        for (Map.Entry<String, DFIResponse.Result> entry : response.getResults().entrySet()) {
            String ruleId = entry.getKey();
            DFIResponse.Result result = entry.getValue();
            for (DFIResponse.Identifier ids: result.getPassed()) {
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("id", UUID.randomUUID().toString());
                row.put("entityId", response.getEntityId());
                row.put("syncariRecordId", ids.getSyncariRecordId());
                row.put("syncariAttributeId", ids.getSyncariAttributeId());
                row.put("categoryId", result.getCategoryId());
                row.put("ruleId", ruleId);
                row.put("result", true);
                row.put("isDeleted", false);
                row.put("partitionKey", generateHash(response.getEntityId()));
                row.put("evaluatedAt", response.getEvaluatedAt());
                rows.add(InsertAllRequest.RowToInsert.of(row));
            }
            for (DFIResponse.Identifier ids: result.getFailed()) {
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("id", UUID.randomUUID().toString());
                row.put("entityId", response.getEntityId());
                row.put("syncariRecordId", ids.getSyncariRecordId());
                row.put("syncariAttributeId", ids.getSyncariAttributeId());
                row.put("categoryId", result.getCategoryId());
                row.put("ruleId", ruleId);
                row.put("result", false);
                row.put("isDeleted", false);
                row.put("partitionKey", generateHash(response.getEntityId()));
                row.put("evaluatedAt", response.getEvaluatedAt());
                rows.add(InsertAllRequest.RowToInsert.of(row));
            }
        }
        return rows;
    }

    public void insertDFIResults(DFIResponse dfiresults, String tableName) {
        helper.insertRowsWithException(toTableId(tableName), toDFIResultsCountRows(dfiresults));
    }

    public void mergeFromTempTable(String tempTableName) {
        String srcTable = helper.getFullTableName(tempTableName);
        String destTable = helper.getFullTableName(StoreSchema.DFI_RESULTS_COUNT_TABLE_NAME);
        String mergeQuery = String.format("MERGE `%s` AS target\n" +
                "USING `%s` AS source\n" +
                "ON (target.entityId = source.entityId\n" +
                "AND target.categoryId = source.categoryId\n" +
                "AND target.ruleId = source.ruleId\n" +
                "AND target.syncariRecordId = source.syncariRecordId\n" +
                "AND COALESCE(target.syncariAttributeId, 'Not Available') = COALESCE(source.syncariAttributeId, 'Not Available'))\n" +
                "WHEN MATCHED AND source.evaluatedAt > target.evaluatedAt THEN\n" +
                " UPDATE SET target.result = source.result, target.evaluatedAt = source.evaluatedAt\n" +
                " WHEN NOT MATCHED THEN\n" +
                " INSERT (id, entityId, syncariRecordId, syncariAttributeId, categoryId, ruleId, result, isDeleted, partitionKey, evaluatedAt) VALUES (source.id, source.entityId, source.syncariRecordId, source.syncariAttributeId, source.categoryId, source.ruleId, source.result, source.isDeleted, source.partitionKey, source.evaluatedAt)", destTable, srcTable);
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(mergeQuery).build();
            helper.runQuery(queryConfig);
        } catch (Exception e) {
            log.error("Error occured while merging from temp table {} to {}. error : ", srcTable, destTable, e);
            throw new RuntimeException(e);
        }
    }

    public List<DFIRuleMetric> getEntitymetric(String entityId) {
        log.info("getting rule metrics for entityId : "+entityId);
        String tableName = helper.getFullTableName(StoreSchema.DFI_RESULTS_COUNT_TABLE_NAME);
        int partitionKey = generateHash(entityId);

        String query = String.format("SELECT "
                + "ruleId, "
                + "COUNT(CASE WHEN result = true THEN 1 END) AS trueCount, "
                + "COUNT(CASE WHEN result = false THEN 1 END) AS falseCount "
                + "FROM `%s` where partitionKey = %s AND entityId = '%s' "
                + "GROUP BY ruleId;", tableName, partitionKey, entityId);

        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
        TableResult results = helper.runQuery(queryConfig);

        List<DFIRuleMetric> metrics = new ArrayList<>();
        for (FieldValueList row : results.iterateAll()) {
            String ruleId = row.get("ruleId").getStringValue();
            //TODO : remove cast to int after arcade/rules scheme change
            int successCount = ((int) row.get("trueCount").getLongValue());
            int failedCount = ((int) row.get("falseCount").getLongValue());
            metrics.add(new DFIRuleMetric().setRuleId(ruleId).setSuccessCount(successCount).setFailedCount(failedCount));
        }
        return metrics;
    }

    public void softDelete(String ruleId, String entityId, String timestamp) {
        String fullTableName = helper.getFullTableName(StoreSchema.DFI_RESULTS_COUNT_TABLE_NAME);
        String query = format(softDeleteByRule, fullTableName, entityId, ruleId, timestamp);
        QueryJobConfiguration config = QueryJobConfiguration.newBuilder(query).build();
        try {
            helper.runQuery(config);
        } catch (Exception e) {
            log.error("Error updating data in {} for ruleId {} and entityId {}. error : ", fullTableName, ruleId, entityId, e);
            throw e;
        }
    }

    public void deleteByRuleId(String ruleId, String entityId) {
        String fullTableName = helper.getFullTableName(StoreSchema.DFI_RESULTS_COUNT_TABLE_NAME);
        String query = format(deleteByRule, fullTableName, entityId, ruleId);
        QueryJobConfiguration config = QueryJobConfiguration.newBuilder(query).build();
        try {
            helper.runQuery(config);
        } catch (Exception e) {
            log.error("Error deleting data in {} for ruleId {} and entityId {}. error : ", fullTableName, ruleId, entityId, e);
            throw e;
        }
    }

}
