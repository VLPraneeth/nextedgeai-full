package com.syncari.core.event.store.repo;

import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.StoreSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DFIMaterializedViewInfo {

    @Autowired
    BigQueryHelper helper;

    public List<MaterializedViewConfig> getDFIMatViewConfig() {
        return List.of(
                getOverallScoreOverTimeMatViewConfig(),
                getOverallScoreOverTimeByCategoryMatViewConfig(),
                getCurrentScoreByCategoryMatViewConfig(),
                getScoreOverTimeByEntityMatViewConfig(),
                getScoreOverTimeByCategoryAndEntityMatViewConfig(),
                getScoreOverTimeByEntityAndRuleMatViewConfig(),
                getCurrentScoreByEntityAndCategoryMatViewConfig(),
                getCurrentScoreByEntityMatViewConfig()
        );
    }

    public MaterializedViewConfig getOverallScoreOverTimeMatViewConfig() {
        String overallScoreOverTimeSQL = String.format("SELECT\n" +
                "  DATE(evaluatedAt) AS evaluatedAt,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  evaluatedAt", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_OVERALL_SCORE_BY_TIME, overallScoreOverTimeSQL);
    }

    public MaterializedViewConfig getOverallScoreOverTimeByCategoryMatViewConfig() {
        String overallScoreOverTimeByCategorySQL = String.format("SELECT\n" +
                "  DATE(evaluatedAt) AS evaluatedAt,\n" +
                "  categoryId,\n" +
                "  MAX(categoryName) AS categoryName,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  evaluatedAt, categoryId", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_OVERALL_SCORE_BY_TIME_AND_CATEGORY, overallScoreOverTimeByCategorySQL);
    }

    public MaterializedViewConfig getCurrentScoreByCategoryMatViewConfig() {
        String currentScoreByCategorySQL = String.format("SELECT\n" +
                "  categoryId,\n" +
                "  MAX(categoryName) AS categoryName,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  categoryId", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_OVERALL_SCORE_BY_CATEGORY, currentScoreByCategorySQL);
    }

    public MaterializedViewConfig getScoreOverTimeByEntityMatViewConfig() {
        String scoreOverTimeByEntitySQL = String.format("SELECT\n" +
                "  DATE(evaluatedAt) AS evaluatedAt,\n" +
                "  entityId,\n" +
                "  MAX(entityName) AS entityName,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  evaluatedAt, entityId", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_SCORE_OVER_TIME_BY_ENTITY, scoreOverTimeByEntitySQL);
    }

    public MaterializedViewConfig getScoreOverTimeByCategoryAndEntityMatViewConfig() {
        String scoreOverTimeByCategoryAndEntitySQL = String.format("SELECT\n" +
                "  DATE(evaluatedAt) AS evaluatedAt,\n" +
                "  categoryId,\n" +
                "  MAX(categoryName) AS categoryName,\n" +
                "  entityId,\n" +
                "  MAX(entityName) AS entityName,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  evaluatedAt, categoryId, entityId", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_SCORE_OVER_TIME_BY_ENTITY_AND_CATEGORY, scoreOverTimeByCategoryAndEntitySQL);
    }

    public MaterializedViewConfig getScoreOverTimeByEntityAndRuleMatViewConfig() {
        String scoreOverTimeByEntityAndRuleSQL = String.format("SELECT\n" +
                "  DATE(evaluatedAt) AS evaluatedAt,\n" +
                "  entityId,\n" +
                "  MAX(entityName) AS entityName,\n" +
                "  ruleId,\n" +
                "  MAX(ruleName) AS ruleName,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  evaluatedAt, entityId, ruleId", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_SCORE_OVER_TIME_BY_ENTITY_AND_RULE, scoreOverTimeByEntityAndRuleSQL);
    }

    public MaterializedViewConfig getCurrentScoreByEntityAndCategoryMatViewConfig() {
        String currentScoreByEntityAndCategorySQL = String.format("SELECT\n" +
                "  entityId,\n" +
                "  MAX(entityName) AS entityName,\n" +
                "  categoryId,\n" +
                "  MAX(categoryName) AS categoryName,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  entityId, categoryId", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_CURRENT_SCORE_BY_ENTITY_AND_CATEGORY, currentScoreByEntityAndCategorySQL);
    }

    public MaterializedViewConfig getCurrentScoreByEntityMatViewConfig() {
        String currentScoreByEntitySQL = String.format("SELECT\n" +
                "  entityId,\n" +
                "  MAX(entityName) AS entityName,\n" +
                "  COUNTIF(result) AS passed,\n" +
                "  COUNT(*) AS total\n" +
                "FROM\n" +
                "  `%s`\n" +
                "WHERE\n" +
                "  isDeleted = FALSE\n" +
                "  AND evaluatedAt IS NOT NULL\n" +
                "GROUP BY\n" +
                "  entityId", helper.getFullTableName(StoreSchema.DFI_RESULTS_TABLE_NAME));
        return new MaterializedViewConfig(StoreSchema.DFI_RESULTS_TABLE_NAME, StoreSchema.DFI_CURRENT_SCORE_BY_ENTITY, currentScoreByEntitySQL);
    }

}
