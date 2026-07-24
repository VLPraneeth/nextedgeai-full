package com.syncari.core.event.store;

import static java.lang.String.format;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.cloud.bigquery.*;
import com.syncari.core.dfiv2.DFIResponse;
import com.syncari.core.event.store.repo.DFIMaterializedViewInfo;
import com.syncari.core.event.store.repo.MaterializedViewConfig;
import com.syncari.core.service.FeatureService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.syncari.connector.Operation;
import com.syncari.connector.exception.UnknownException;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.Event;
import com.syncari.core.model.PipelineStats;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.misc.SyncLog;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.ErrorNotificationService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BigQueryEventStore implements EventStore {
	public static final int BAD_REQUEST = 400;
	public static final int TOO_LARGE = 413;
	public static final String PAYLOAD_SIZE_LIMIT_MESSAGE_PREFIX = "Request payload size exceeds the limit";
    public static final String NO_SUCH_FIELD_MESSAGE_PREFIX = "no such field";
	@Autowired
	BigQuery bigQuery;
	@Autowired
	AppConfig appConfig;
	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	@Autowired
	DateUtil dateUtil;
    @Autowired
	@Qualifier("defaultEmailService")
    EmailService emailService;
	@Autowired
	FeatureService featureService;
    @Autowired
    ErrorNotificationService notificationService;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	BigQueryHelper helper;
	@Autowired
	DFIEventStore dfiEventStore;

	@Override
	public void provision(String syncariId) {
		createDatasetIfNotExists(syncariId);
		Map<String, List<FieldDefinition>> tables = StoreSchema.getTables(syncariId);
		for (Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
			TableId tableId = TableId.of(syncariId, entry.getKey());
			createTableIfNotExists(tableId, entry.getValue());
		}
	}
	public void provision(String syncariId, String tableName) {
		createDatasetIfNotExists(syncariId);
		List<FieldDefinition> fields = StoreSchema.getTables(syncariId).get(tableName);
		TableId tableId = TableId.of(syncariId, tableName);
		createTableIfNotExists(tableId, fields);
	}

	@Override
	public void deprovision(String syncariId) {
		Map<String, List<FieldDefinition>> tables = StoreSchema.getTables(syncariId);
		for (Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
			TableId tableId = TableId.of(syncariId, entry.getKey());
			helper.deleteTableIfExists(tableId);
		}
		dfiEventStore.deprovision(syncariId);
		helper.deleteDatasetIfExists(syncariId);
	}

	@Override
	public void verifyProvisioned(String syncariId) {
		bigQuery.getDataset(syncariId);
		Map<String, List<FieldDefinition>> tables = StoreSchema.getTables(syncariId);
		for (Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
			TableId tableId = TableId.of(syncariId, entry.getKey());
			bigQuery.getTable(tableId);
		}
	}

	@Override
	public void insert(List<Event> events) {
		helper.insertRows(toTableId(StoreSchema.EVENT_LOG_TABLE_NAME), toRows(events));
	}

	@Override
	public void insertSyncLogs(List<SyncLog> logs) {
		helper.insertRows(toTableId(StoreSchema.SYNC_LOG_TABLE_NAME), toSyncLogRows(logs));
	}

	@Override
	public void insertErrorLogs(List<SyncError> logs) {
		helper.insertRows(toTableId(StoreSchema.ERROR_LOG_TABLE_NAME), toErrorLogRows(logs));
		notifyErrorLogErrors(logs);
	}


	public List<TransactionLog> insertTransactionLogs(List<TransactionLog> logs) {
		helper.insertRows(toTableId(StoreSchema.TXN_LOG_TABLE_NAME), toTxnRows(logs));
		notifyTxLogErrors(logs);
		return logs;
	}

	@Override
	public void addFieldToTable(FieldDefinition def) {
		Map<String, List<FieldDefinition>> tables = StoreSchema.getTables(def.syncariId);
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

	private void notifyErrorLogErrors(List<SyncError> logs) {
		Set<Pair<String, String>> errors = logs.stream().map(log -> {
			String errorMessage = log.getErrorDetails() != null ? log.getErrorDetails() : log.getErrorCode();
			return Pair.of(log.getSyncariEntityName(),
					String.format("Sync has errors for entity %s : %s", log.getSyncariEntityName(), errorMessage));
		}).collect(Collectors.toSet());

		errors.forEach(e -> {
			notificationService.sendErrorNotification(ErrorCategory.SYNC, ErrorPriority.P1, e.x, "Sync error occured",
					e.y);
		});
	}
	
	private void notifyTxLogErrors(List<TransactionLog> logs) {
		Set<Pair<String, String>> errors = logs.stream().filter(log -> CollectionUtils.isNotEmpty(log.getErrors()))
				.map(log -> {
					String errorMessage = String.join(", ",
							log.getErrors().stream().map(err -> err.getError()).collect(Collectors.toSet()));
					return Pair.of(log.getEntityId(), String.format("Sync transaction has errors for entity %s : %s",
							log.getEntityName(), errorMessage));
				}).collect(Collectors.toSet());

		errors.forEach(e -> {
			notificationService.sendErrorNotification(ErrorCategory.SYNC, ErrorPriority.P1, e.x,
					"Sync transaction error occured", e.y);
		});
	}
	
	private TableId toTableId(String tableName) {
		return TableId.of(SyncariContext.getSyncariId(), tableName);
	}

	private List<RowToInsert> toStatsRows(List<PipelineStats> stats) {
		return stats.stream().map(e -> {
			Map<String, Object> content = new HashMap<>();
			content.put("connectorId",e.getConnectorId());
			content.put("connectorName", e.getConnectorName());
			content.put("pipelineId", e.getPipelineId());
			content.put("batchId", e.getBatchId());
			content.put("stageId", e.getStageId());
			content.put("stageName", e.getStageName());
			content.put("stageType", e.getStageType());
			content.put("targetId",e.getTargetId());
			content.put("targetType", e.getTargetType());
			content.put("latency", e.getLatency());
			content.put("recordsProcessed", e.getRecordsProcessed());
			content.put("duplicateCount", e.getDuplicateCount());
			content.put("dedupeCount", e.getDedupeCount());
			content.put("emptyInputCount", e.getEmptyInputCount());
			content.put("emptyOutputCount", e.getEmptyOutputCount());
			content.put("changeCount", e.getChangeCount());
			content.put("occuredDate", dateUtil.formatDate(e.getOccurredAt(), DateUtil.dateOnlyFormat));
			content.put("occuredDateHour", e.getOccurredAt().truncatedTo(ChronoUnit.HOURS).getEpochSecond());
			content.put("occuredTime", e.getOccurredAt().getEpochSecond());
			return RowToInsert.of(content);
		}).collect(Collectors.toList());

	}
	private List<RowToInsert> toRows(List<Event> events) {
		List<RowToInsert> rows = new ArrayList<InsertAllRequest.RowToInsert>();
		events.stream().forEach(e -> {
			Map<String, Object> content = new HashMap<String, Object>();
			content.put("eventtype", e.getType());
			content.put("event_subtype", e.getSubType());
			String date = format.format(e.getOccuredTime());
			content.put("occuredtime", date);
			if (e.getDetails() != null) {
				ObjectMapper mapper = new ObjectMapper();
				try {
					String bodyString = mapper.writeValueAsString(e.getDetails());
					content.put("body", bodyString);
				} catch (JsonProcessingException ex) {
					log.error(format("Error parsing body: %s", e.getDetails()));
				}
			}
			rows.add(RowToInsert.of(content));
		});
		return rows;
	}

	private List<RowToInsert> toSyncLogRows(List<SyncLog> logs) {
		List<RowToInsert> rows = new ArrayList<InsertAllRequest.RowToInsert>();
		logs.stream().forEach(e -> {
			Map<String, Object> content = new HashMap<String, Object>();
			content.put("connectorId", e.getConnectorId());
			content.put("connectorName", e.getConnectorName());
			content.put("batchId", e.getBatchId());
			content.put("direction", e.getDirection());
			content.put("recordCount", e.getRecordCount());
			content.put("latency", e.getLatency());
			content.put("errorCode", e.getErrorCode());
			content.put("errorDetails", e.getErrorDescription());
			content.put("syncariEntityApiName", e.getEntityName());
			content.put("graphId", e.getGraphId());
			content.put("graphName", e.getGraphName());
			content.put("operation", e.getOperation());
			content.put("syncMode", e.getSyncMode());
			content.put("failedWinningRecord", e.getFailedWinningRecord());
			content.put("failedRecords", e.getFailedRecords());
			content.put("occuredDate", dateUtil.formatDate(e.getOccuredTime(), DateUtil.dateOnlyFormat));
			content.put("occuredDateHour", e.getOccuredTime().truncatedTo(ChronoUnit.HOURS).getEpochSecond());
			content.put("occuredTime", e.getOccuredTime().getEpochSecond());
			rows.add(RowToInsert.of(content));
		});
		return rows;
	}
	
	private List<RowToInsert> toErrorLogRows(List<SyncError> logs) {
	    List<RowToInsert> rows = new ArrayList<InsertAllRequest.RowToInsert>();
	    logs.stream().forEach(e -> {
	        Map<String, Object> content = new HashMap<String, Object>();
	        content.put("connectorId", e.getConnectorId());
	        content.put("connectorName", e.getConnectorName());
	        content.put("batchId", e.getBatchId());
	        content.put("errorCode", e.getErrorCode());
	        content.put("errorDetails", e.getErrorDetails());
	        content.put("syncariEntityName", e.getSyncariEntityName());
	        content.put("syncariRecordId", e.getSyncariRecordId());
	        content.put("externalRecordId", e.getExternalRecordId());
	        content.put("externalEntityName", e.getExternalEntityName());
	        content.put("operation", e.getOperation());
	        content.put("occuredDate", dateUtil.formatDate(e.getOccuredTime(), DateUtil.dateOnlyFormat));
	        content.put("occuredDateHour", e.getOccuredTime().truncatedTo(ChronoUnit.HOURS).getEpochSecond());
	        content.put("occuredTime", e.getOccuredTime().getEpochSecond());
	        rows.add(RowToInsert.of(content));
	    });
	    return rows;
	}

	private List<RowToInsert> toTxnRows(List<TransactionLog> logs) {
		List<RowToInsert> rows = new ArrayList<InsertAllRequest.RowToInsert>();
		logs.stream().forEach(e -> {
			Map<String, Object> content = new HashMap<String, Object>();
			content.put("syncariId", e.getSyncariId());
			if(e.getOperation() == Operation.merge){
				String syncariEntityId = e.getMergeOperation().getWinningRecord().getSyncariEntityId();
				content.put("syncariId", syncariEntityId);
			}
			content.put("entityName", e.getEntityName());
			content.put("entityId", e.getEntityId());
			content.put("batchId", e.getBatchId());
			content.put("operation", e.getOperation().name());
			content.put("sourceSynapses", e.getSources().stream().map(s->s.getConnectorId()).collect(Collectors.toList()));
			Instant occurredAt = Instant.ofEpochMilli(e.getOccurredAt());
			content.put("occurredDate", dateUtil.formatDate(occurredAt, DateUtil.dateOnlyFormat));
			content.put("occurredDateHour", occurredAt.truncatedTo(ChronoUnit.HOURS).getEpochSecond());
			content.put("occurredTime", occurredAt.getEpochSecond());
			content.put("isNew", e.isNew());

			rows.add(RowToInsert.of(content));
		});
		return rows;
	}

	private void createTableIfNotExists(TableId tableId, List<FieldDefinition> fields) {
		try {
			Table table = bigQuery.getTable(tableId);
			if (table == null) {
				Schema schema = Schema.of(getFieldDefs(fields));
				StandardTableDefinition.Builder tableBuilder = StandardTableDefinition.of(schema).toBuilder();
				Optional<String> partitionField = StoreSchema.getPartitionField(tableId.getTable());
				partitionField.ifPresent(p -> {
					tableBuilder.setTimePartitioning(TimePartitioning
							.newBuilder(TimePartitioning.Type.DAY).setRequirePartitionFilter(true).setField(p).build());
				});
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

	public void setDateUtil(DateUtil util) {
		this.dateUtil = util;
	}

	public void setBigQuery(BigQuery bigQuery) {
		this.bigQuery = bigQuery;
	}
	public void setHelper(BigQueryHelper helper) {
		this.helper = helper;
	}

	public void setNotificationService(ErrorNotificationService notificationService) {
		this.notificationService = notificationService;
	}

}
