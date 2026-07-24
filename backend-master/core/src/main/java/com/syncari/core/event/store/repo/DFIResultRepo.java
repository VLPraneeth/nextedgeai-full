package com.syncari.core.event.store.repo;

import com.google.cloud.bigquery.*;
import com.syncari.core.dfiv2.DFIResponse;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.StoreSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.lang.String.format;

@Slf4j
@Component
public class DFIResultRepo {
    @Autowired
    BigQueryHelper helper;

    private static final String softDeleteByRule
            = "UPDATE `%s` SET isDeleted = TRUE, updatedAt = CURRENT_TIMESTAMP() WHERE entityId = '%s' AND ruleId = '%s' AND evaluatedAt < '%s'";

    private static final String deleteByRule = "DELETE FROM `%s` WHERE entityId = '%s' AND ruleId = '%s'";

    private List<Map<String, Object>> toDFIResultsRowsJson(DFIResponse response) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, DFIResponse.Result> entry : response.getResults().entrySet()) {
            String ruleId = entry.getKey();
            DFIResponse.Result result = entry.getValue();
            for (DFIResponse.Identifier ids: result.getPassed()) {
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("id", UUID.randomUUID().toString());
                row.put("entityId", response.getEntityId());
                row.put("entityName", response.getEntityName());
                row.put("categoryName", result.getCategoryName());
                row.put("ruleName", result.getRuleName());
                row.put("syncariRecordId", ids.getSyncariRecordId());
                row.put("syncariAttributeId", ids.getSyncariAttributeId());
                row.put("categoryId", result.getCategoryId());
                row.put("ruleId", ruleId);
                row.put("result", true);
                row.put("isDeleted", false);
                row.put("evaluatedAt", response.getEvaluatedAt());
                row.put("updatedAt", response.getEvaluatedAt());
                rows.add(row);
            }
            for (DFIResponse.Identifier ids: result.getFailed()) {
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("id", UUID.randomUUID().toString());
                row.put("entityId", response.getEntityId());
                row.put("entityName", response.getEntityName());
                row.put("categoryName", result.getCategoryName());
                row.put("ruleName", result.getRuleName());
                row.put("syncariRecordId", ids.getSyncariRecordId());
                row.put("syncariAttributeId", ids.getSyncariAttributeId());
                row.put("categoryId", result.getCategoryId());
                row.put("ruleId", ruleId);
                row.put("result", false);
                row.put("isDeleted", false);
                row.put("evaluatedAt", response.getEvaluatedAt());
                row.put("updatedAt", response.getEvaluatedAt());
                rows.add(row);
            }
        }
        return rows;
    }

    public boolean insertDFIResults(DFIResponse dfiresults) {
        return helper.insertRowsWithException(StoreSchema.DFI_RESULTS_TABLE_NAME, toDFIResultsRowsJson(dfiresults));
    }

    public void softDelete(String ruleId, String entityId, String timestamp) {
        String fullTableName = helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME);
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
        String fullTableName = helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME);
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
