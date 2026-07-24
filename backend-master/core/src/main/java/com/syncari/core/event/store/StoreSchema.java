package com.syncari.core.event.store;

import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TimePartitioning;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.*;

public class StoreSchema {
	public static final String EVENT_LOG_TABLE_NAME = "auditLog";
	public static final String SYNC_LOG_TABLE_NAME = "syncLog";
	public static final String ERROR_LOG_TABLE_NAME = "errorLog";
	public static final String TXN_LOG_TABLE_NAME = "transactionLog";
	public static final String TXNS_LOG_TABLE_NAME = "transactionsLog";
	public static final String DFI_RESULTS_TABLE_NAME = "dfiRuleResults";
	public static final String DFI_RESULTS_COUNT_TABLE_NAME = "dfiRuleResultsCount";
	public static final String NODE_AUDIT_TABLE_NAME = "nodeAudit";
	public static final String WEBHOOK_TXN_LOG_TABLE_NAME = "webhookReceiverLogs";
	public static final String ABAC_AUDIT_LOG_TABLE_NAME = "abacAuditLog";

	public static final String DFI_SCORE_OVER_TIME_BY_ENTITY = "dfiScoreOverTimeByEntity";
	public static final String DFI_SCORE_OVER_TIME_BY_ENTITY_AND_CATEGORY = "dfiScoreOverTimeByEntityAndCategory";
	public static final String DFI_SCORE_OVER_TIME_BY_ENTITY_AND_RULE = "dfiScoreOverTimeByEntityAndRule";
	public static final String DFI_CURRENT_SCORE_BY_ENTITY = "dfiCurrentScoreByEntity";
	public static final String DFI_CURRENT_SCORE_BY_ENTITY_AND_CATEGORY = "dfiCurrentScoreByEntityAndCategory";

	public static final String DFI_OVERALL_SCORE_BY_TIME = "dfiOverallScoreByTime";
	public static final String DFI_OVERALL_SCORE_BY_TIME_AND_CATEGORY = "dfiOverallScoreByTimeAndCategory";
	public static final String DFI_OVERALL_SCORE_BY_CATEGORY = "dfiOverallScoreByCategory";

	private static final Map<String, RangePartitionInfo> RANGE_PARTITION_TABLE_CONFIG = new HashMap<>(){
		{
			put(DFI_RESULTS_COUNT_TABLE_NAME, new RangePartitionInfo().setPartitionKeyMaxValue(10L).setFieldName("partitionKey"));
		}
	};

	public static TimePartitioning.Type getTimestampPartitionPeriodByTable(String tableName) {
        return TimePartitioning.Type.DAY;
    }

	public static boolean isRangePartitionedTable(String tableName) {
		return RANGE_PARTITION_TABLE_CONFIG.containsKey(tableName);
	}

	public static Long getRangePartitionMaxRangeValue(String tableName){
		if (!RANGE_PARTITION_TABLE_CONFIG.containsKey(tableName))
			return -1L;
		return RANGE_PARTITION_TABLE_CONFIG.get(tableName).getPartitionKeyMaxValue();
	}

	public static String getRangePartitionField(String tableName){
		if (!RANGE_PARTITION_TABLE_CONFIG.containsKey(tableName))
			return null;
		return RANGE_PARTITION_TABLE_CONFIG.get(tableName).getFieldName();
	}

	public static Map<String, List<FieldDefinition>> getDFITables(String syncariId) {
		Map<String, List<FieldDefinition>> tables = new LinkedHashMap<>();
		tables.put(DFI_RESULTS_TABLE_NAME, getdfiResultsFields(syncariId));
		tables.put(DFI_RESULTS_COUNT_TABLE_NAME, getdfiCountResultsFields(syncariId));
		return tables;
	}

	public static Map<String, List<FieldDefinition>> getTables(String syncariId) {
		Map<String, List<FieldDefinition>> tables = new LinkedHashMap<>();
		tables.put(EVENT_LOG_TABLE_NAME, getEventLogFields(syncariId));
		tables.put(SYNC_LOG_TABLE_NAME, getSyncLogFields(syncariId));
		tables.put(ERROR_LOG_TABLE_NAME, getErrorLogFields(syncariId));
		tables.put(TXN_LOG_TABLE_NAME, getTxnLogFields(syncariId));
		tables.put(TXNS_LOG_TABLE_NAME, getTxnLogsFields(syncariId));
		tables.put(NODE_AUDIT_TABLE_NAME, getNodeAuditFields(syncariId));
		tables.put(WEBHOOK_TXN_LOG_TABLE_NAME, getWebhookLogsFields(syncariId));
		tables.put(ABAC_AUDIT_LOG_TABLE_NAME, getAbacAuditLogFields(syncariId));
		return tables;
	}

	public static Optional<String> getPartitionField(String table) {
		switch (table) {
			case TXNS_LOG_TABLE_NAME: return Optional.of("occurredTime");
			case NODE_AUDIT_TABLE_NAME: return Optional.of("occurredTime");
			case WEBHOOK_TXN_LOG_TABLE_NAME: return Optional.of("receivedOn");
			case DFI_RESULTS_TABLE_NAME: return Optional.of("evaluatedAt");
			case DFI_RESULTS_COUNT_TABLE_NAME: return Optional.of("partitionKey");
			case ABAC_AUDIT_LOG_TABLE_NAME: return Optional.of("createdAt");
			default: return Optional.empty();
		}
	}

	public static List<String> getClusterFields(String table) {
		switch (table) {
			case TXNS_LOG_TABLE_NAME:
				return List.of("entityId", "operation", "batchId", "syncariRecordId");
			case NODE_AUDIT_TABLE_NAME:
				return List.of("entityId");
			case WEBHOOK_TXN_LOG_TABLE_NAME:
			    return List.of("connectorId");
			case ABAC_AUDIT_LOG_TABLE_NAME:
				return List.of("resourceType");
			case DFI_RESULTS_TABLE_NAME:
				return List.of("entityId", "categoryId", "ruleId");
			case DFI_RESULTS_COUNT_TABLE_NAME:
				return List.of("entityId", "ruleId");
			default:
				return List.of();
		}
	}

	public static List<String> getPrimaryKeys(String table) {
		switch (table) {
			case TXNS_LOG_TABLE_NAME: return List.of("id");
			case NODE_AUDIT_TABLE_NAME: return List.of("id");
			case WEBHOOK_TXN_LOG_TABLE_NAME: return List.of("id");
			case DFI_RESULTS_TABLE_NAME: return List.of("id");
			case DFI_RESULTS_COUNT_TABLE_NAME: return List.of("id");
			case ABAC_AUDIT_LOG_TABLE_NAME: return List.of("id");
			default: return List.of();
		}
	}

	private static List<FieldDefinition> getdfiResultsFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "id", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "entityId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "entityName", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "categoryName", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "ruleName", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "syncariRecordId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "syncariAttributeId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "categoryId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "ruleId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "result", StandardSQLTypeName.BOOL, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "isDeleted", StandardSQLTypeName.BOOL, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "evaluatedAt", StandardSQLTypeName.TIMESTAMP, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "updatedAt", StandardSQLTypeName.TIMESTAMP, true));
		return fields;
	}

	private static List<FieldDefinition> getdfiCountResultsFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "id", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "entityId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "syncariRecordId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "syncariAttributeId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "categoryId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "ruleId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "result", StandardSQLTypeName.BOOL, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "partitionKey", StandardSQLTypeName.INT64, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_COUNT_TABLE_NAME, "evaluatedAt", StandardSQLTypeName.TIMESTAMP, true));
		fields.add(new FieldDefinition(syncariId, DFI_RESULTS_TABLE_NAME, "isDeleted", StandardSQLTypeName.BOOL, true));
		return fields;
	}

	private static List<FieldDefinition> getTxnLogsFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "id", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "syncariRecordId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "entityName", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "entityId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "batchId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "operation", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "isNew", StandardSQLTypeName.BOOL, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "notes", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "sourceTransactionId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "errors", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "sources", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "destinations", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "changes", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "additionalInfo", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occurredDate", StandardSQLTypeName.DATE, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occurredDateHour", StandardSQLTypeName.TIMESTAMP, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occurredTime", StandardSQLTypeName.TIMESTAMP, true));
		return fields;
	}

	private static List<FieldDefinition> getTxnLogFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "syncariId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "entityName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "entityId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "batchId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "operation", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "isNew", StandardSQLTypeName.BOOL, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "sourceSynapses", StandardSQLTypeName.ARRAY, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occurredDate", StandardSQLTypeName.DATE, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occurredDateHour", StandardSQLTypeName.TIMESTAMP, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occurredTime", StandardSQLTypeName.TIMESTAMP, true));
		return fields;
	}

	private static List<FieldDefinition> getErrorLogFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "connectorId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "connectorName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "batchId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "syncariEntityName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "syncariRecordId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "externalEntityName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "externalRecordId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "operation", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "errorCode", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "errorDetails", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occuredDate", StandardSQLTypeName.DATE, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occuredDateHour", StandardSQLTypeName.TIMESTAMP, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occuredTime", StandardSQLTypeName.TIMESTAMP, true));
		return fields;
	}

	private static List<FieldDefinition> getSyncLogFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "connectorId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "connectorName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "direction", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "batchId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "syncariEntityApiName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "operation", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "graphId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "graphName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "recordCount", StandardSQLTypeName.INT64, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "latency", StandardSQLTypeName.INT64, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "errorCode", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "errorDetails", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "syncMode", StandardSQLTypeName.STRING, false)); // (initial / incremental)
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occuredDate", StandardSQLTypeName.DATE, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occuredDateHour", StandardSQLTypeName.TIMESTAMP, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occuredTime", StandardSQLTypeName.TIMESTAMP, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "failedWinningRecord", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "failedRecords", StandardSQLTypeName.ARRAY, false));
		return fields;
	}

	private static List<FieldDefinition> getEventLogFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "eventtype", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "event_subtype", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "body", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, TXNS_LOG_TABLE_NAME, "occuredtime", StandardSQLTypeName.TIMESTAMP, false));
		return fields;
	}

	private static List<FieldDefinition> getNodeAuditFields(String syncariId) {
		List<FieldDefinition> fields = new ArrayList<>();
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "id", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "entityId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "entityPipelineId", StandardSQLTypeName.STRING, true));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "pipelineId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "pipelineName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "syncariAttributeId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "scope", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "nodeId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "nodeName", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "nodeType", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "batchId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "syncariRecordId", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "externalRecordIds", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "batchMode", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "runMode", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "input", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "output", StandardSQLTypeName.JSON, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "error", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "errorDetails", StandardSQLTypeName.STRING, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "startTime", StandardSQLTypeName.INT64, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "endTime", StandardSQLTypeName.INT64, false));
		fields.add(new FieldDefinition(syncariId, NODE_AUDIT_TABLE_NAME, "occurredTime", StandardSQLTypeName.TIMESTAMP, false));
		return fields;
	}
	
	private static List<FieldDefinition> getWebhookLogsFields(String syncariId) {
      List<FieldDefinition> fields = new ArrayList<>();
      fields.add(new FieldDefinition(syncariId, WEBHOOK_TXN_LOG_TABLE_NAME, "id", StandardSQLTypeName.STRING, true));
      fields.add(new FieldDefinition(syncariId, WEBHOOK_TXN_LOG_TABLE_NAME, "connectorId", StandardSQLTypeName.STRING, true));
      fields.add(new FieldDefinition(syncariId, WEBHOOK_TXN_LOG_TABLE_NAME, "receivedOn", StandardSQLTypeName.TIMESTAMP, false));
      fields.add(new FieldDefinition(syncariId, WEBHOOK_TXN_LOG_TABLE_NAME, "payload", StandardSQLTypeName.STRING, false));
      fields.add(new FieldDefinition(syncariId, WEBHOOK_TXN_LOG_TABLE_NAME, "headers", StandardSQLTypeName.STRING, false));
      fields.add(new FieldDefinition(syncariId, WEBHOOK_TXN_LOG_TABLE_NAME, "verified", StandardSQLTypeName.BOOL, false));
      fields.add(new FieldDefinition(syncariId, WEBHOOK_TXN_LOG_TABLE_NAME, "authenticated", StandardSQLTypeName.BOOL, false));
      return fields;
  }

 private static List<FieldDefinition> getAbacAuditLogFields(String syncariId) {
     List<FieldDefinition> fields = new ArrayList<>();
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "id", StandardSQLTypeName.STRING, true));
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "createdAt", StandardSQLTypeName.TIMESTAMP, true));
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "resourceType", StandardSQLTypeName.STRING, true));
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "resource", StandardSQLTypeName.STRING, false));
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "action", StandardSQLTypeName.STRING, true));
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "user", StandardSQLTypeName.STRING, false));
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "allowed", StandardSQLTypeName.BOOL, true));
     fields.add(new FieldDefinition(syncariId, ABAC_AUDIT_LOG_TABLE_NAME, "policy", StandardSQLTypeName.STRING, false));
     return fields;
 }
}

@Data
@Accessors(chain = true)
class RangePartitionInfo {
	String fieldName;
	Long partitionKeyMaxValue;
}