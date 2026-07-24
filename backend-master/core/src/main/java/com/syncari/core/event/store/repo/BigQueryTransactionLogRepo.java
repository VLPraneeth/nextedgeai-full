package com.syncari.core.event.store.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.syncari.connector.Operation;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.event.store.AttributeDefinitionAwareDataTypeDeserializer;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.FieldDefinition;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.model.FieldChange;
import com.syncari.core.model.MergeOperation;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.Destination;
import com.syncari.core.model.misc.Source;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.pipeline.NodeError;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component
public class BigQueryTransactionLogRepo {
    @Autowired
	BigQueryHelper helper;
	@Autowired
	BigQuery bigQuery;
	@Autowired
	DateUtil dateUtil;
	@Autowired
	AppConfig appConfig;
	@Autowired
	AttributeDefinitionAwareDataTypeDeserializer datatypeDeserializer;

	public static final String GET_MERGES_FOR_BATCH =  "SELECT * "
			+ "FROM `%s` WHERE batchId = '%s' AND operation = 'merge' "
			+ "AND occurredTime > @occurredAt "
			+ "ORDER BY occurredTime asc limit @limit offset @offset";
	public static final String GET_TRANSCATIONS_BATCH_SYNCARIDS =  "SELECT * " +
		"FROM `%s` WHERE batchId = '%s' AND syncariRecordId IN UNNEST (@syncariIds) AND occurredTime >= @occurredAt";

	public static final String GET_TRANSCATIONS_BATCH =  "SELECT * "
			+ "FROM `%s` WHERE batchId = '%s' AND occurredTime > @occurredAt";
	public static final String GET_TRANSCATIONS_BY_ID =  "SELECT * FROM `%s` WHERE id = '%s' AND occurredTime >= @occurredAt";

	public static final String GET_TRANSCATIONS_BY_IDS =  "SELECT * FROM `%s` WHERE id IN UNNEST (@transactionLogIds) AND occurredTime >= @occurredAt";

	public static final String COUNT_TRANSCATIONS_BATCH =  "SELECT count(*) AS count FROM `%s` WHERE batchId = '%s' AND occurredTime >= @occurredAt";

	public static final String GET_DESTINATION_TXNS_BY_SOURCE =  "SELECT * FROM `%s` WHERE entityName = @entityName AND operation in ('%s', '%s') AND sourceTransactionId IN UNNEST (@transactionLogIds) AND occurredTime > @occurredAt";

	private static final String findTransactionsQuery = "SELECT * FROM `%s` WHERE entityName = @entityName %s AND occurredTime >= @occurredTime";
	private static final String findTransactionsBatchSyncariIdQuery = "SELECT * FROM `%s` WHERE batchId = @batchId AND occurredTime >= @occurredAt AND %s";
	//private static final String query = "SELECT * FROM `%s` WHERE batchId = @batchId AND errors IS NOT NULL %s AND occurredTime >= @occurredAt ORDER BY occurredTime %s %s";

	private static final String query = "SELECT * FROM `%s` WHERE batchId = @batchId AND errors IS NOT NULL %s ORDER BY occurredTime %s %s %s";
	private static final String queryByDate = "SELECT * FROM `%s` WHERE %s ORDER BY occurredTime %s %s %s";
	private static final String queryByDateCursor = "SELECT * FROM `%s` WHERE %s ORDER BY id desc LIMIT %s";
	private static final String deleteAll = "TRUNCATE TABLE `%s`";

	private static final String findAll = "SELECT * FROM `%s` WHERE occurredTime >= @occurredTime %s ORDER BY id ASC LIMIT %s";
	private static final String findById = "SELECT * FROM `%s` WHERE id=@id";
	private static final String count = "SELECT COUNT(*) as total FROM `%s` WHERE occurredTime >= @occurredTime";
	public static String mostActiveSynapse = "SELECT ANY_VALUE(sources) as sources,count(1) AS transactionCount from `%s` where occurredTime between @startDate AND @endDate group by TO_JSON_STRING(sources)";


	public void updateField(FieldDefinition def) {
		Map<String, List<FieldDefinition>> tables = StoreSchema.getTables(def.syncariId);
		tables.get(StoreSchema.TXNS_LOG_TABLE_NAME);
		TableId tableId = TableId.of(def.syncariId, StoreSchema.TXNS_LOG_TABLE_NAME);
		Table table = bigQuery.getTable(tableId);
		Schema schema = table.getDefinition().getSchema();
		FieldList fields = schema.getFields();
		Field newField = Field.newBuilder(def.fieldName, def.type).setMode(!def.required ? Field.Mode.NULLABLE : Field.Mode.REQUIRED).build();
		List<Field> fieldsToBeUpdated = new ArrayList<Field>();
		for (Field f : fields) {
			if (f.getName().equals(def.fieldName)) {
				fieldsToBeUpdated.add(newField);
			} else {
				fieldsToBeUpdated.add(f);
			}
		}
		Schema newSchema = Schema.of(fieldsToBeUpdated);
		table.toBuilder().setDefinition(StandardTableDefinition.of(newSchema)).build().update();
		log.info("Successfully updated {} on table {}", def.fieldName, StoreSchema.TXNS_LOG_TABLE_NAME);
	}

	public List<TransactionLog> insertTransactionLogs(List<TransactionLog> logs) {
		helper.insertRowsWithException(toTableId(StoreSchema.TXNS_LOG_TABLE_NAME), toTxnsRows(logs));
		return logs;
	}

	public long count() {
		return count(Instant.EPOCH);
	}

	public long count(Instant start) {
		String fullTableName = helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME);
		String formatted = format(count, fullTableName);
		QueryParameterValue occurredTime = QueryParameterValue.timestamp(dateUtil.formatDate(start, DateUtil.dateTimeFormatMicro));
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("occurredTime", occurredTime).build();
		TableResult tableResult = helper.runQuery(config);
		for (FieldValueList row : tableResult.iterateAll()) {
			return row.get("total").getLongValue();
		}
		return 0;
	}

	public void deleteAll() {
		String fullTableName = helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME);
		String formatted = format(deleteAll, fullTableName);

		QueryParameterValue occurredTime = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.EPOCH, DateUtil.dateTimeFormatMicro));
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("occurredTime", occurredTime)
				.build();

		helper.runQuery(config);
		log.info("All transactions deleted from {}", fullTableName);
	}

	public Optional<TransactionLog> findById(String id) {
		String formatted = format(findById, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("id", QueryParameterValue.string(id))
				.build();
		log.debug(config.getQuery());
		TableResult r = helper.runQuery(config);
		List<TransactionLog> transactionLogs = toTransactionLogs(r);
		if(transactionLogs.isEmpty()) return Optional.empty();
		if(transactionLogs.size() > 1) throw new RuntimeException("Multiple records found for id "+id);
		return Optional.of(transactionLogs.get(0));
	}

	public org.springframework.data.domain.Page<TransactionLog> findAll() {
		String fullTableName = helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME);
		String formatted = format(findAll, fullTableName);
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted).addNamedParameter("occurredTime",
				QueryParameterValue.timestamp(dateUtil.formatDate(Instant.EPOCH, DateUtil.dateTimeFormatMicro))).build();
		TableResult r = helper.runQuery(config);
		Page<TransactionLog> transactionLogPage = helper.constructPage(new PageCursor(), toTransactionLogs(r));
		return new PageImpl(transactionLogPage.getRecords());
	}

	public org.springframework.data.domain.Page<TransactionLog> findAll(Instant start) {
		String fullTableName = helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME);
		var cursor = new PageCursor().setPageSize(100);
		String formatted = format(findAll, fullTableName, "", cursor.getPageSize());
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted).addNamedParameter("occurredTime",
				QueryParameterValue.timestamp(dateUtil.formatDate(start, DateUtil.dateTimeFormatMicro))).build();
		TableResult r = helper.runQuery(config);

		// I will keep fetching until I get an empty page and add all fetched records to a list
		List<TransactionLog> allLogs = new ArrayList<>();
		do {
			Page<TransactionLog> transactionLogPage = helper.constructPage(cursor, toTransactionLogs(r));
			if(transactionLogPage.getRecords().isEmpty()) break;
			allLogs.addAll(transactionLogPage.getRecords());

			var lastRecord = transactionLogPage.getRecords().get(transactionLogPage.getRecords().size() -1);
			String idClause = " AND id > '" + lastRecord.getId() + "'";
			formatted = format(findAll, fullTableName, idClause, cursor.getPageSize());
			config = QueryJobConfiguration.newBuilder(formatted).addNamedParameter("occurredTime",
					QueryParameterValue.timestamp(dateUtil.formatDate(start, DateUtil.dateTimeFormatMicro))).build();
			r = helper.runQuery(config);
		} while(true);
		return new PageImpl(allLogs);
	}

	public List<TransactionLog> findTransactions(String entityName, List<String> syncariIds, long start) {
		if(syncariIds.isEmpty()) return List.of();
		String syncariIdClause = "";
		List<String> nonBlank = syncariIds.stream().filter(s -> !StringUtils.isBlank(s)).collect(Collectors.toList());
		if(!nonBlank.isEmpty()) {
			syncariIdClause = String.format(" AND syncariRecordId in (%s) ", String.join(",", syncariIds.stream().map(s -> '"'+s+'"').collect(Collectors.toList())));
		} else {
			return List.of();
		}
		String formatted = format(findTransactionsQuery, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), syncariIdClause);
		//BQ timestamps are microseconds
		QueryParameterValue occurredTime = QueryParameterValue.timestamp(start * 1000);
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("occurredTime", occurredTime)
				.addNamedParameter("entityName", QueryParameterValue.string(entityName))
				.build();
		log.debug(config.getQuery());
		log.debug("occurredTime {} ", occurredTime);
		TableResult r = helper.runQuery(config);
		return toTransactionLogs(r);
	}

	public List<TransactionLog> findByBatchIdAndSyncariIdIn(String batchId, List<String> syncariIds, Date start) {
		if(syncariIds.isEmpty()) return List.of();
		String syncariIdClause = "";
		List<String> nonBlank = syncariIds.stream().filter(s -> !StringUtils.isBlank(s)).collect(Collectors.toList());
		if(!nonBlank.isEmpty()) {
			syncariIdClause = String.format(" syncariRecordId in (%s) ", String.join(",", syncariIds.stream().map(s -> '"'+s+'"').collect(Collectors.toList())));
		} else {
			return List.of();
		}
		String formatted = format(findTransactionsBatchSyncariIdQuery, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), syncariIdClause);
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("batchId", QueryParameterValue.string(batchId))
				.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start.getTime()), DateUtil.dateTimeFormatMicro)))
				.build();
		log.debug(config.getQuery());
		TableResult r = helper.runQuery(config);
		return toTransactionLogs(r);
	}

	public Page<TransactionLog> query(String batchId, String nodeId, String error, Date start, PageCursor cursor) {
		if(cursor == null) throw new RuntimeException("Cursor is required");
		if (StringUtils.isBlank(batchId)) throw new RuntimeException("BatchId is required");
		if (StringUtils.isBlank(nodeId)) throw new RuntimeException("NodeId is required");
		if (StringUtils.isBlank(error)) throw new RuntimeException("Error is required");
		if (start == null) throw new RuntimeException("Start is required");

		cursor.validate();
		String sort = cursor.isForward() ? "desc" : "asc";
		String limitClause = helper.getLimitClause(cursor);
		String offsetClause = helper.getOffSetClause(cursor);

		// double escape " once for Regex, once for BigQuery
		error = error.replace("\\", "\\\\");
		error = error.replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)").replaceAll("\\+", "\\\\+").replaceAll("'", "\\\\'");
		String clause = " AND NOT (operation IN ('external_update', 'external_create') AND sourceTransactionId IS NOT NULL) AND TO_JSON_STRING(errors) like '%\"nodeId\":\"" + nodeId + "\"%' AND REGEXP_CONTAINS(TO_JSON_STRING(errors), r'\"error\":\"" + error + "\"') AND" +
				" occurredTime > @startDate ";
		String formatted = format(query, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), clause, sort, limitClause, offsetClause);
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("batchId", QueryParameterValue.string(batchId))
				.addNamedParameter("startDate", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start.getTime()), DateUtil.dateTimeFormatMicro)))
				.build();
		log.debug(config.getQuery());
		TableResult r = helper.runQuery(config);
		List<TransactionLog> transactionLogs = toTransactionLogs(r);
		attachDestinationLogs(Optional.empty(), transactionLogs);

		return helper.constructPage(cursor, transactionLogs);
	}

	public List<TransactionLog> queryByCursor(Optional<String> transactionId, Date startDate, Date endDate, Optional<String> entityName,
											  Optional<String> syncariId, Optional<String> operation, int limit) {
		List<String> clauses = new ArrayList<>();

		entityName.ifPresent(s -> clauses.add(" entityName=@entityName "));
		syncariId.ifPresent(s -> clauses.add(" syncariRecordId=@syncariRecordId "));
		operation.ifPresent(s -> clauses.add(" operation=@operation "));
		clauses.add(" occurredTime >= @startDate ");
		clauses.add(" occurredTime <= @endDate");
		clauses.add(" NOT (operation IN ('external_update', 'external_create') AND sourceTransactionId IS NOT NULL)");
		transactionId.ifPresent(txn -> clauses.add(" id < @id"));
		String mainClause = StringUtils.join(clauses, " AND ");
		String formatted = format(queryByDateCursor, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), mainClause, limit);
		QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(formatted);
		entityName.ifPresent(e -> builder.addNamedParameter("entityName", QueryParameterValue.string(e)));
		syncariId.ifPresent(s -> builder.addNamedParameter("syncariRecordId", QueryParameterValue.string(s)));
		operation.ifPresent(o -> builder.addNamedParameter("operation", QueryParameterValue.string(o)));
		transactionId.ifPresent(t -> builder.addNamedParameter("id", QueryParameterValue.string(t)));
		QueryParameterValue startDateT = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(startDate.getTime()), DateUtil.dateTimeFormatMicro));
		builder.addNamedParameter("startDate", startDateT);
		log.debug("startDate {} ", startDateT);
		QueryParameterValue endDateT = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(endDate.getTime()), DateUtil.dateTimeFormatMicro));
		builder.addNamedParameter("endDate", endDateT);
		log.debug("endDate {} ", endDateT);
		QueryJobConfiguration config = builder.build();
		log.debug(config.getQuery());
		TableResult r = helper.runQuery(config);
		List<TransactionLog> transactionLogs = toTransactionLogs(r);
		attachDestinationLogs(entityName, transactionLogs);
		return transactionLogs;
	}

	public Page<TransactionLog> query(Optional<Date> startDate, Optional<Date> endDate,
									  Optional<String> entityName, Optional<String> syncariRecordId,
									  Optional<String> operation, PageCursor cursor) {
		if (cursor == null) throw new RuntimeException("Cursor is required");
		cursor.validate();
		String sort = "desc";
		String limitClause = helper.getLimitClause(cursor);
		String offSetClause = helper.getOffSetClause(cursor);
		List<String> clauses = new ArrayList<>();
		String mainClause = "";
		entityName.ifPresent(s -> clauses.add(" entityName=@entityName "));
		syncariRecordId.ifPresent(s -> clauses.add(" syncariRecordId=@syncariRecordId "));
		operation.ifPresent(s -> clauses.add(" operation=@operation "));
		startDate.ifPresent(date -> clauses.add(" occurredTime >= @startDate "));
		endDate.ifPresent(date -> clauses.add(" occurredTime <= @endDate" ));
		// add operation filter
		clauses.add(" NOT (operation IN ('external_update', 'external_create') AND sourceTransactionId IS NOT NULL)");
		if(!clauses.isEmpty()) mainClause = StringUtils.join(clauses, " AND ");
		String formatted = format(queryByDate, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), mainClause, sort, limitClause, offSetClause);
		QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(formatted);
		entityName.ifPresent(e -> builder.addNamedParameter("entityName", QueryParameterValue.string(e)));
		syncariRecordId.ifPresent(s -> builder.addNamedParameter("syncariRecordId", QueryParameterValue.string(s)));
		operation.ifPresent(o -> builder.addNamedParameter("operation", QueryParameterValue.string(o)));
		if(startDate.isPresent()) {
			QueryParameterValue startDateT = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(startDate.get().getTime()), DateUtil.dateTimeFormatMicro));
			builder.addNamedParameter("startDate", startDateT);
			log.debug("startDate {} ", startDateT);
		}
		if(endDate.isPresent()) {
			QueryParameterValue endDateT = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(endDate.get().getTime()), DateUtil.dateTimeFormatMicro));
			builder.addNamedParameter("endDate", endDateT);
			log.debug("endDate {} ", endDateT);
		}
		QueryJobConfiguration config = builder.build();
		log.debug(config.getQuery());
		TableResult r = helper.runQuery(config);
		List<TransactionLog> transactionLogs = toTransactionLogs(r);
		return helper.constructPage(cursor, transactionLogs);
	}

	private TableId toTableId(String tableName) {
		return TableId.of(SyncariContext.getSyncariId(), tableName);
	}

	private List<RowToInsert> toTxnsRows(List<TransactionLog> logs) {

		var mapper = helper.getMapper();

		List<RowToInsert> rows = new ArrayList<RowToInsert>();
		logs.stream().forEach(e -> {
			Map<String, Object> content = new HashMap<String, Object>();
			content.put("id", !StringUtils.isBlank(e.getId()) ? e.getId() : ObjectId.get().toHexString());
			e.setId(content.get("id").toString());
			content.put("syncariRecordId", e.getSyncariId());
			if(e.getOperation() == Operation.merge){
				String syncariEntityId = e.getMergeOperation().getWinningRecord().getSyncariEntityId();
				content.put("syncariRecordId", syncariEntityId);
			}
			content.put("entityName", e.getEntityName());
			content.put("entityId", e.getEntityId());
			content.put("batchId", e.getBatchId());
			content.put("notes", e.getNotes());
			content.put("operation", e.getOperation().name());
			content.put("isNew", e.isNew());
			content.put("sourceTransactionId", e.getSourceTransactionId());
			Instant occurredAt = Instant.ofEpochMilli(e.getOccurredAt());
			content.put("occurredDate", dateUtil.formatDate(occurredAt, DateUtil.dateOnlyFormat));
			content.put("occurredDateHour", occurredAt.truncatedTo(ChronoUnit.HOURS).getEpochSecond());
			content.put("occurredTime", occurredAt.toEpochMilli() / 1000.0d);
			//json
			try {
				content.put("errors", mapper.writeValueAsString(e.getErrors()));
				content.put("sources", mapper.writeValueAsString(e.getSources()));
				content.put("destinations", mapper.writeValueAsString(e.getDestinations()));
				content.put("changes", mapper.writeValueAsString(e.getChanges()));
				content.put("additionalInfo", mapper.writeValueAsString (e.getAdditionalInfo()));
			} catch (JsonProcessingException ex) {
				throw new RuntimeException(ex);
			}
			rows.add(RowToInsert.of(content));
		});
		return rows;
	}

	public Page<TransactionLog> findMergesByBatchId(String syncCycleId, Date start, PageCursor cursor) {
		try {
			String formatted = format(GET_MERGES_FOR_BATCH, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), syncCycleId);

			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted)
					.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start.getTime()), DateUtil.dateTimeFormatMicro)))
					.addNamedParameter("limit", QueryParameterValue.int64(cursor.getPageSize()))
					.addNamedParameter("offset",
							QueryParameterValue.int64(cursor.getPageSize() * cursor.getPageNumber()))
					.build();

			TableResult r = helper.runQuery(queryConfig);
			List<TransactionLog> transactionLogs = toTransactionLogs(r);
			Page<TransactionLog> transactionLogPage = new Page<>();
			transactionLogPage.setRecords(transactionLogs);
			cursor.setPageNumber(cursor.getPageNumber() + 1);
			return transactionLogPage;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, List<TransactionLog>> findTransactionsByBatch(String batchId, Date start, List<String> syncariIds) {
		try {
			String formatted = format(GET_TRANSCATIONS_BATCH_SYNCARIDS, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), batchId);

			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted)
					.addNamedParameter("syncariIds", QueryParameterValue.array(syncariIds.toArray(new String[0]), String.class))
					.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start.getTime()), DateUtil.dateTimeFormatMicro)))
					.build();

			TableResult r = helper.runQuery(queryConfig);
			List<TransactionLog> transactionLogs = toTransactionLogs(r);
			return transactionLogs.stream()
					.collect(Collectors.groupingBy(tx -> tx.getSyncariId()));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, List<TransactionLog>> findTransactionsByBatch(String batchId, Date start) {
		try {
			String formatted = format(GET_TRANSCATIONS_BATCH, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), batchId);

			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted)
					.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start.getTime()), DateUtil.dateTimeFormatMicro)))
					.build();

			TableResult r = helper.runQuery(queryConfig);
			List<TransactionLog> transactionLogs = toTransactionLogs(r);
			return transactionLogs.stream()
					.collect(Collectors.groupingBy(tx -> tx.getSyncariId()));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	public Optional<TransactionLog> findByTransactionLogId(String transactionLogId, long start) {
		try {
			String formatted = format(GET_TRANSCATIONS_BY_ID, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), transactionLogId);

			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted)
					.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start), DateUtil.dateTimeFormatMicro)))
					.build();

			TableResult r = helper.runQuery(queryConfig);
			List<TransactionLog> logs = toTransactionLogs(r);
			return logs.isEmpty()? Optional.empty() : Optional.of(logs.get(0));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public List<TransactionLog> findByTransactionLogIds(List<String> transactionLogIds, long start) {
		try {
			String formatted = format(GET_TRANSCATIONS_BY_IDS, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));

			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted)
					.addNamedParameter("transactionLogIds", QueryParameterValue.array(transactionLogIds.toArray(new String[0]), String.class))
					.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start), DateUtil.dateTimeFormatMicro)))
					.build();

			TableResult r = helper.runQuery(queryConfig);
			List<TransactionLog> logs = toTransactionLogs(r);
			return toTransactionLogs(r);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String mostActiveSynapse(String formattedQuery, Instant startDate, Instant endDate){
		TableResult query = helper.runQuery(helper.getQueryConfigForTimestamp(startDate, endDate, formattedQuery));
		Map<String, Long> countBySynapse = new HashMap<>();
		var mapper = helper.getMapper();
		for (FieldValueList row : query.iterateAll()) {
			long count = row.get("transactionCount").getLongValue();
			String sourcesStr = helper.getStringOrEmpty(row.get("sources"));
			if(!StringUtils.isBlank(sourcesStr)) {
				try {
					List<Source> sources = mapper.readValue(sourcesStr, mapper.getTypeFactory().constructCollectionType(List.class, Source.class));
					sources.forEach(source-> countBySynapse.put(source.getConnectorId(),
							countBySynapse.getOrDefault(source,0l)+count));
				} catch (JsonProcessingException e) {
					log.error("JsonProcessingException occurred while processing sources {}", ExceptionUtils.getStackTrace(e));
				}
			}
		}
		return countBySynapse.entrySet().stream().max((e1, e2) ->(int) (e1.getValue() -e2.getValue())).map(e->e.getKey()).orElse(null);
	}

	
	private List<TransactionLog> toTransactionLogs(TableResult rows) {
		List<TransactionLog> logs = new ArrayList<>();
		var mapper = helper.getMapper();
		ObjectMapper customMapper = getMapperWithCustomDataTypeDeserializer();
		for (FieldValueList row : rows.iterateAll()) {
			TransactionLog transactionLog = new TransactionLog();
			long occurredTime = row.get("occurredTime").getTimestampValue() / 1000;
			transactionLog.setOccurredAt(occurredTime);
			transactionLog.setCreatedAt(Date.from(Instant.ofEpochMilli(occurredTime)));
			transactionLog.setId(helper.getStringOrEmpty(row.get("id")));
			transactionLog.setSyncariId(helper.getStringOrEmpty(row.get("syncariRecordId")));
			transactionLog.setEntityName(helper.getStringOrEmpty(row.get("entityName")));
			transactionLog.setEntityId(helper.getStringOrEmpty(row.get("entityId")));
			transactionLog.setBatchId(helper.getStringOrEmpty(row.get("batchId")));
			transactionLog.setNotes(helper.getStringOrEmpty(row.get("notes")));
			transactionLog.setSourceTransactionId(helper.getStringOrEmpty(row.get("sourceTransactionId")));
			transactionLog.setOperation(Operation.valueOf(helper.getStringOrEmpty(row.get("operation"))));
			transactionLog.setNew(Boolean.valueOf(helper.getStringOrEmpty(row.get("isNew"))));
			transactionLog.setOccurredAt(occurredTime);
			try {
				String errors = helper.getStringOrEmpty(row.get("errors"));
				if(!StringUtils.isBlank(errors)) {
					transactionLog.setErrors(mapper.readValue(errors, mapper.getTypeFactory().constructCollectionType(List.class, NodeError.class)));
				}
				String sources = helper.getStringOrEmpty(row.get("sources"));
				if(!StringUtils.isBlank(sources)) {
					transactionLog.setSources(mapper.readValue(sources, mapper.getTypeFactory().constructCollectionType(List.class, Source.class)));
				}
				String destinations = helper.getStringOrEmpty(row.get("destinations"));
				if(!StringUtils.isBlank(destinations)) {
					transactionLog.setDestinations(mapper.readValue(destinations, mapper.getTypeFactory().constructCollectionType(List.class, Destination.class)));
				}
				String changes = helper.getStringOrEmpty(row.get("changes"));
				if(!StringUtils.isBlank(changes)) {
					transactionLog.setChanges(mapper.readValue(changes,new TypeReference<Map<String, FieldChange>>() {}));
				}
				String additionalInfoStr = helper.getStringOrEmpty(row.get("additionalInfo"));
				if(!StringUtils.isBlank(additionalInfoStr)) {
					Map<String, Object> additionalInfo = mapper.readValue(additionalInfoStr, Map.class);
					try{
						if (additionalInfo.containsKey("mergeDetails")) {
							additionalInfo.put("mergeDetails", customMapper.convertValue(additionalInfo.get("mergeDetails"), new TypeReference<MergeOperation>(){}));
						} else if (additionalInfo.containsKey("mergeSkipDetails")) {
							additionalInfo.put("mergeSkipDetails", customMapper.convertValue(additionalInfo.get("mergeSkipDetails"), new TypeReference<MergeOperation>(){}));
						}
					}catch (IllegalArgumentException exception){
						log.error("IllegalArgumentException exception occurred {}", ExceptionUtils.getStackTrace(exception));
						if (!exception.getMessage().contains("Missing type id when trying to resolve subtype of [simple type, class com.syncari.core.datatype.Datatype]")){
							throw exception;
						}else {
							// Fallback to original approach if custom deserializer fails
							((Map)additionalInfo.get("mergeDetails")).put("loserReferencedEntities", new ArrayList<>());
							additionalInfo.put("mergeDetails", customMapper.convertValue(additionalInfo.get("mergeDetails"), new TypeReference<MergeOperation>(){}));
						}
					}
					transactionLog.setAdditionalInfo(additionalInfo);
				}

				logs.add(transactionLog);
			} catch (Exception e) {
				log.error("{}", ExceptionUtils.getStackTrace(e));
				throw new RuntimeException(e);
			}
		}
		return logs;
	}

	public Long countTransactionsByBatch(String batchId, Date start) {
		try {
			String formatted = format(COUNT_TRANSCATIONS_BATCH, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), batchId);

			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted)
					.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(start.getTime()), DateUtil.dateTimeFormatMicro)))
					.build();

			TableResult r = helper.runQuery(queryConfig);
			for (FieldValueList row : r.iterateAll()) {
				return row.get("count").getLongValue();
			}
			return 0L;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public List<TransactionLog> findDestinationLogs(String entityName, List<TransactionLog> sourceTxns, long occurredAt) {
		try {
			String formatted = format(GET_DESTINATION_TXNS_BY_SOURCE, helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME), Operation.external_create, Operation.external_update);
			QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted)
					.addNamedParameter("entityName", QueryParameterValue.string(entityName))
					.addNamedParameter("transactionLogIds", QueryParameterValue.array(sourceTxns.stream()
							.map(TransactionLog::getId).collect(Collectors.toList()).toArray(new String[0]), String.class))
					.addNamedParameter("occurredAt", QueryParameterValue.timestamp(dateUtil.formatDate(Instant.ofEpochMilli(occurredAt), DateUtil.dateTimeFormatMicro)))
					.build();

			TableResult r = helper.runQuery(queryConfig);
			return toTransactionLogs(r);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void attachDestinationLogs(Optional<String> entityName, List<TransactionLog> logs) {
		if (entityName.isPresent()) {
			attachDestinationLogs(entityName.get(), logs);
		} else {
			logs.stream().collect(Collectors.groupingBy(l -> l.getEntityName())).forEach((entity, txns) -> attachDestinationLogs(entity, txns));
		}
	}
	private void attachDestinationLogs(String entityName, List<TransactionLog> logs) {
		List<TransactionLog> sourceTxns = logs.stream()
				.filter(l -> l.getOperation() == Operation.create || l.getOperation() == Operation.update)
				//Eliminate duplicates
				.collect(Collectors.toMap(
			        TransactionLog::getId,
			        l -> l,
			        (existing, replacement) -> existing
			    ))
				.values().stream()
				.collect(Collectors.toList());

		// get min occurredAt from source transactions
		Map<String, List<TransactionLog>> source2destinationTxns = new HashMap<>();
		Map<String, TransactionLog> sourceTxnMap = sourceTxns.stream().collect(Collectors.toMap(TransactionLog::getId, l -> l));
		long minOccurredAt = sourceTxns.stream().mapToLong(TransactionLog::getOccurredAt).min().orElse(0);
		List<TransactionLog> destinationTxns = findDestinationLogs(entityName, sourceTxns, minOccurredAt);

		// for each destination txs, get the sourceTransactionId and lookup the source txn
		destinationTxns.forEach(d -> {
			String sourceTxnId = d.getSourceTransactionId();
			if (sourceTxnId != null) {
				TransactionLog sourceTxn = sourceTxnMap.get(sourceTxnId);
				if (sourceTxn != null) {
					source2destinationTxns.computeIfAbsent(sourceTxnId, k -> new ArrayList<>()).add(d);
				}
			}
		});

		sourceTxns.forEach(s -> {
			if (source2destinationTxns.containsKey(s.getId())) {
				Map<String, FieldChange> sourceFieldChanges = s.getChanges();
				for (TransactionLog destTxn : source2destinationTxns.get(s.getId())) {
					Map<String, FieldChange> destFieldChanges = destTxn.getChanges();
					for (Map.Entry<String, FieldChange> entry : destFieldChanges.entrySet()) {
						FieldChange destFieldChange = entry.getValue();
						FieldChange sourceFieldChange = sourceFieldChanges.get(destFieldChange.getFieldId());
						if (sourceFieldChange != null) {
							sourceFieldChange.getOutgoingExternalValues().putAll(destFieldChange.getOutgoingExternalValues());
						} else {
							// add the field change from destination to source
							sourceFieldChanges.put(destFieldChange.getFieldId(), destFieldChange);
						}
					}
					// add all errors from destinations
					List<NodeError> errors = new ArrayList<>();
					errors.addAll(s.getErrors());
					errors.addAll(destTxn.getErrors());
					s.setErrors(errors);
				}
			}
		});
	}

	public void setRequirePartitionFilter(boolean required) {
		TableId tableId = TableId.of(SyncariContext.getSyncariId(), StoreSchema.TXNS_LOG_TABLE_NAME);
		Table table = bigQuery.getTable(tableId);
		StandardTableDefinition.Builder tableBuilder = StandardTableDefinition.of(table.getDefinition().getSchema()).toBuilder();
		Optional<String> partitionField = StoreSchema.getPartitionField(tableId.getTable());
		if(partitionField.isEmpty()) {
			log.warn("Partition field not found for table {}. Skipping", StoreSchema.TXNS_LOG_TABLE_NAME);
			return;
		}
		tableBuilder.setTimePartitioning(TimePartitioning
				.newBuilder(TimePartitioning.Type.DAY).setField(partitionField.get()).setRequirePartitionFilter(required).build());
		TableDefinition tableDefinition = tableBuilder.build();
		TableInfo tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build();
		bigQuery.update(tableInfo);
		log.info("Successfully altered table {} to require partition filter {}", StoreSchema.TXNS_LOG_TABLE_NAME, required);
	}

	public BigQueryHelper getHelper() {
		return helper;
	}

	private ObjectMapper getMapperWithCustomDataTypeDeserializer() {
		ObjectMapper customMapper = helper.getMapper().copy();
		SimpleModule module = new SimpleModule();
		module.addDeserializer(Datatype.class, datatypeDeserializer);
		customMapper.registerModule(module);
		return customMapper;
	}

}
