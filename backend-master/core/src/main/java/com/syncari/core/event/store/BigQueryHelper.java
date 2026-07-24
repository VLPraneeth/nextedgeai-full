package com.syncari.core.event.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.google.api.core.ApiFuture;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.google.cloud.bigquery.storage.v1.*;
import com.syncari.connector.exception.NonRetriableInternalException;
import com.syncari.connector.exception.UnknownException;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

@Slf4j
@Component
public class BigQueryHelper{
	public static final int BAD_REQUEST = 400;
	public static final int TOO_LARGE = 413;
    public static final String NO_SUCH_FIELD_MESSAGE_PREFIX = "no such field";
	@Autowired
	BigQuery bigQuery;
	@Autowired
	AppConfig appConfig;
	@Autowired
	DateUtil dateUtil;
	@Autowired
	ObjectMapper mapper;
	@Autowired
	BigQueryWriteClient bigQueryWriteClient;
	@Autowired
	@Qualifier("bigQueryWriteSettings")
	private BigQueryWriteSettings bigQueryWriteSettings;



	public String getFullTableName(String table) {
		// [<gcp-projectid>.<gcp-profile>.eventlog_<customer-dbname>]
		String tableName = format("%s.%s.%s", appConfig.getGcpProjectId(), SyncariContext.getSyncariId(), table);
		return tableName;
	}

	private TableName getTableName(String table) {
		return TableName.of(appConfig.getGcpProjectId(), SyncariContext.getSyncariId(), table);
	}
	public TableResult runQuery(QueryJobConfiguration queryConfig)  {
		try {
			return bigQuery.query(queryConfig);
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}

	public MaterializedViewDefinition getViewDefinition(String viewName, String datasetName){
		String projectId = appConfig.getGcpProjectId();
		TableId tableId = TableId.of(datasetName, viewName);
		Table table = bigQuery.getTable(tableId);
		TableDefinition tableDefinition = table.getDefinition();
		return (MaterializedViewDefinition) tableDefinition;

	}


	public QueryJobConfiguration getQueryConfig(Instant startDate, Instant endDate, String queryString) {
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(queryString)
				.addNamedParameter("startDate",
						QueryParameterValue.date(dateUtil.formatDate(startDate, DateUtil.dateOnlyFormat)))
				.addNamedParameter("endDate",
						QueryParameterValue.date(dateUtil.formatDate(endDate, DateUtil.dateOnlyFormat)))
				.build();
		log.debug(config.getQuery());
		log.debug("startDate {} endDate {}",QueryParameterValue.date(dateUtil.formatDate(startDate, DateUtil.dateOnlyFormat)),
				QueryParameterValue.date(dateUtil.formatDate(endDate, DateUtil.dateOnlyFormat)));
		return config;
	}

	public QueryJobConfiguration getQueryConfigForTimestamp(Instant startDate, Instant endDate, String queryString) {
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(queryString)
				.addNamedParameter("startDate",
						QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro)))
				.addNamedParameter("endDate",
						QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro)))
				.build();
		log.debug(config.getQuery());
		log.debug("startDate {} endDate {}",QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro)),
				QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro)));
		return config;
	}

	public QueryJobConfiguration getQueryConfigSyncErrors(Instant startDate, Instant endDate, String queryString) {
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(queryString)
				.addNamedParameter("startDate",
						QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro)))
				.addNamedParameter("endDate",
						QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro)))
				.build();
		log.debug(config.getQuery());
		log.debug("startDate {} endDate {}",QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro)),
				QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro)));
		return config;
	}

	public QueryJobConfiguration getQueryConfig(String queryString, PageCursor pageCursor) {
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(queryString)
				.addNamedParameter("limit",
						QueryParameterValue.int64(pageCursor.getPageSize()))
				.addNamedParameter("offset",
						QueryParameterValue.int64(pageCursor.getPageSize() * pageCursor.getPageNumber()))
				.build();
		log.debug(config.getQuery());
		return config;
	}

	public QueryJobConfiguration getQueryConfig(Instant startDate, Instant endDate, String queryString, PageCursor pageCursor) {
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(queryString)
				.addNamedParameter("startDate",
						QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro)))
				.addNamedParameter("endDate",
						QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro)))
				.addNamedParameter("limit",
						QueryParameterValue.int64(pageCursor.getPageSize()))
				.addNamedParameter("offset",
						QueryParameterValue.int64(pageCursor.getPageSize() * pageCursor.getPageNumber()))
				.build();
		log.debug(config.getQuery());
		log.debug("startDate {} endDate {} limit {} offset {}", QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro)),
				QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro)), QueryParameterValue.int64(pageCursor.getPageSize())
				, QueryParameterValue.int64(pageCursor.getPageSize() * pageCursor.getPageNumber()));
		return config;
	}

	public String getStringOrEmpty(FieldValue field) {
		return (field == null || field.getValue() == null) ? null : field.getStringValue();
	}

	public long getLong(FieldValue field) {
		return (field == null || field.getValue() == null) ? 0l : field.getLongValue();
	}

	public void addFields(List<FieldDefinition> newFields) {
		Map<Pair<String, String>, List<FieldDefinition>> byTableName = newFields.stream().collect(Collectors.groupingBy(f -> Pair.of(f.syncariId, f.tableName)));
		byTableName.forEach((key, fieldList) -> {
			var syncariId = key.x;
			var tableName = key.y;
			TableId tableId = TableId.of(syncariId, tableName);
			Table existing = bigQuery.getTable(tableId);
			if (existing != null) {
				var existingFields = new ArrayList<>(existing.getDefinition().getSchema().getFields());
				Set<String> existingFieldNames = existingFields.stream().map(f -> f.getName()).collect(Collectors.toSet());
				fieldList.forEach(field ->{
					log.info("Creating field {}",field);
					if(existingFieldNames.contains(field.fieldName)){
						log.warn("Field already exists.Skipping field for {} tableName {} fieldName {}", syncariId, tableName, field.fieldName);
					}else{
						existingFields.add(toBQField(field));
					}
				});
				Schema newSchema = Schema.of(existingFields);
				existing.toBuilder().setDefinition(StandardTableDefinition.of(newSchema)).build().update();
			}else{
				log.warn("Table not found. Skipping table update for {} tableName {} fields {}", syncariId, tableName, newFields);
			}
		});
	}

	Field toBQField(FieldDefinition field){
		if(field.type == StandardSQLTypeName.ARRAY) {
			// TODO the type may not be string always
			return Field.newBuilder(field.fieldName, LegacySQLTypeName.STRING, new Field[0]).setMode(Field.Mode.REPEATED).build();
		}
		return Field.newBuilder(field.fieldName, field.type).setMode(field.required ? Field.Mode.REQUIRED : Field.Mode.NULLABLE).build();
	}
	public void addField(String syncariId, String tableName, String fieldName, StandardSQLTypeName type, boolean required) {
		addFields(List.of(new FieldDefinition(syncariId,tableName, fieldName, type, required)));
	}

	void insertRows(TableId tableId, List<RowToInsert> rows) {
		try {
			insertRowsWithException(tableId, rows);
		} catch (Exception e) {
			log.debug("Error inserting rows to BigQuery", e);
			if (e instanceof NonRetriableInternalException && !((NonRetriableInternalException) e).getStatusCode().equals("BIGQUERY_INSERT_ERROR")) {
				throw e;
			}
		}
	}

	public ObjectMapper getMapper() {
		var convertor = new StdConverter<ZonedDateTime, Date>() {
			@Override
			public Date convert(ZonedDateTime zonedDateTime) {
				return Date.from(zonedDateTime.toInstant());
			}
		};
		var module = new SimpleModule().addSerializer(ZonedDateTime.class, new StdDelegatingSerializer(convertor));
		return mapper.copy().registerModule(module);
	}

	private TableSchema getSchema(String tableName) {
		String syncariId = SyncariContext.getSyncariId();
		if (!(StoreSchema.getDFITables(syncariId).containsKey(tableName) || StoreSchema.getTables(syncariId).containsKey(tableName))) {
			log.error("Cannot find schema for table {}", tableName);
			throw new RuntimeException(String.format("Cannot find schema for table %s", tableName));
		}
		List<FieldDefinition> fields = StoreSchema.getDFITables(syncariId).containsKey(tableName) ?
				StoreSchema.getDFITables(syncariId).get(tableName) : StoreSchema.getTables(syncariId).get(tableName);
		var bqFields = new ArrayList<>();
		fields.forEach(field ->{
			bqFields.add(toBQField(field));
		});

		TableSchema.Builder schemaBuilder = TableSchema.newBuilder();
		List<TableFieldSchema.Type> types = List.of(TableFieldSchema.Type.STRING, TableFieldSchema.Type.INT64, TableFieldSchema.Type.BOOL,
				TableFieldSchema.Type.TIMESTAMP);

		for (FieldDefinition def : fields) {
			TableFieldSchema.Builder fieldBuilder = TableFieldSchema.newBuilder()
					.setName(def.fieldName)
					.setType(TableFieldSchema.Type.valueOf(def.type.name()))
					.setMode(def.required ? TableFieldSchema.Mode.REQUIRED : TableFieldSchema.Mode.NULLABLE);
			schemaBuilder.addFields(fieldBuilder);
		}

		return schemaBuilder.build();
	}

	public boolean insertRowsWithException(String tableName, List<Map<String, Object>> rows) {
		WriteStream writeStream = WriteStream.newBuilder()
				.setType(WriteStream.Type.COMMITTED)
				.build();
		TableName parentTable = getTableName(tableName);
		WriteStream createdStream = bigQueryWriteClient.createWriteStream(CreateWriteStreamRequest.newBuilder()
				.setParent(parentTable.toString())
				.setWriteStream(writeStream)
				.build());
		try {
			JsonStreamWriter.Builder builder = JsonStreamWriter.newBuilder(createdStream.getName(), getSchema(tableName));
			builder.setCredentialsProvider(bigQueryWriteSettings.getCredentialsProvider());
			try (JsonStreamWriter jsonStreamWriter = builder.build()) {
				JSONArray jsonArray = rows.stream()
						.map(JSONObject::new)
						.collect(collectingAndThen(toList(), JSONArray::new));
				ApiFuture<AppendRowsResponse> future = jsonStreamWriter.append(jsonArray);
				AppendRowsResponse response = future.get();
				return !response.hasError();
			}
		} catch (Exception e) {
			log.error("Error writing to table {} using storage API. Error : ", tableName, e);
			return false;
		}
	}

	public void insertRowsWithException(TableId tableId, List<RowToInsert> rows) {
		if(rows.isEmpty()){
			log.debug("Warning : Rows are empty for table {}",tableId);
			return;
		}
		List<List<RowToInsert>> rowPartitions = ListUtils.partition(rows, 10000);
		for(List<RowToInsert> rowPartition : rowPartitions) {
			InsertAllRequest request = InsertAllRequest.of(tableId, rowPartition);
			int retryCount = 5;
			int backOffMs = 1000;
			Exception original = null;
			while (retryCount > 0) {
				try {
					doInsert(request, true);
					return;
				} catch (Exception ex) {
					if (ex instanceof NonRetriableInternalException) {
						throw ex;
					}
					if (ex instanceof BigQueryException && isPayloadTooLarge((BigQueryException) ex)) {
						int partitionSize = rowPartition.size() >= 10 ? rowPartition.size()/10 : 1;
						// insert row by row
						List<List<RowToInsert>> partitions = ListUtils.partition(rowPartition, partitionSize);
						partitions.forEach(partition -> {
							try {
								if (partition.size() == 1) {
									doInsert(InsertAllRequest.of(tableId, partition), true);
								} else {
									insertRows(tableId, partition);
								}
							} catch (Exception e) {
								log.error(ExceptionUtils.getStackTrace(e));
							}
						});
						retryCount = 0;
						return;
					} else {
						original = ex;
					}
				}

				try {
					Thread.sleep(backOffMs);
				} catch (InterruptedException e) {
				}

				backOffMs *= 2;
				retryCount--;
			}
			if (retryCount == 0) {
				if (original != null) {
                    log.error("error", original);
                    throw new NonRetriableInternalException("BIGQUERY_ERROR", original.getMessage(), "BIGQUERY_ERROR", original);
				}
			} else {
				throw new NonRetriableInternalException("BIGQUERY_ERROR", "Retries exhausted. Cannot insert rows into BQ table " + tableId, "BIGQUERY_ERROR");
			}
		}
	}
	private void doInsert(InsertAllRequest request, boolean initial) {
		InsertAllResponse response = bigQuery.insertAll(request);
		if (response.hasErrors()) {
            if (initial) {
                Entry<Long, List<BigQueryError>> firstErr = response.getInsertErrors().entrySet().iterator().next();
                if (firstErr.getValue().size() > 0) {
                    BigQueryError firstBQErr = firstErr.getValue().get(0);
                    if (firstBQErr.getMessage().contains(NO_SUCH_FIELD_MESSAGE_PREFIX)) {
                        String fieldName = firstBQErr.getLocation();
                        addField(SyncariContext.getSyncariId(), request.getTable().getTable(), fieldName, StandardSQLTypeName.STRING, false);
                        // Retry the request after adding the column.
                        doInsert(request, false);
                    }
                }
            }

			StringBuilder errorBody = new StringBuilder();
			response.getInsertErrors().forEach((k, v) -> {
				v.stream().limit(10).forEach(err -> {
					log.error(format("Error : %s", err));
					errorBody.append(err + "\n");
				});
			});
			throw new NonRetriableInternalException("BIGQUERY_ERROR", "BIGQUERY_INSERT_ERROR", errorBody.toString());
		} else {
			log.debug("Inserted {} records for {}", request.getRows().size(), request.getTable().getTable());
		}
	}

	private boolean isPayloadTooLarge(BigQueryException ex) {
		return ex.getCode()== TOO_LARGE || ex.getCode()== BAD_REQUEST;
	}

	public void setDateUtil(DateUtil util) {
		this.dateUtil = util;
	}

	public Page<TransactionLog> constructPage(PageCursor cursor, List<TransactionLog> results) {
		boolean hasMore = results.size() == cursor.getPageSize() + 1;
		boolean hasPrevious = (!StringUtils.isBlank(cursor.getCursor()) && Integer.parseInt(cursor.getCursor()) >= 1);
		if (results.size() > cursor.getPageSize()) {
			results = results.subList(0, cursor.getPageSize());
		}

		Page<TransactionLog> page = new Page<>();
		String pageStart = results.size() > 0 ? results.get(0).getId() : null;
		String pageEnd = results.size() > 0 ? results.get(results.size() - 1).getId() : null;
		page.setPageInfo(new PageInfo(pageStart, pageEnd, hasMore).addSort("Id", true));
		page.setRecords(results);
		page.getPageInfo().setHasMore(hasMore);
		page.getPageInfo().setHasPrevious(hasPrevious);
		if (StringUtils.isBlank(cursor.getCursor()) || Integer.parseInt(cursor.getCursor()) < 1) {
			page.getPageInfo().setPageNumber(0);
		} else if (cursor.isForward()) {
			page.getPageInfo().setPageNumber(Integer.parseInt(cursor.getCursor()));
		} else {
			page.getPageInfo().setPageNumber(Math.max(Integer.parseInt(cursor.getCursor()), 0));
		}
		assert page.getRecords().size() <= cursor.getPageSize();
		return page;
	}

	public String getLimitClause(PageCursor cursor) {
		String limitClause = "";
		if (cursor.getPageSize() > 0) {
			limitClause = " LIMIT " + (cursor.getPageSize()+1);
		}
		return limitClause;
	}

	public String getOffSetClause(PageCursor cursor) {
		String offsetClause = "";
		if (!StringUtils.isBlank(cursor.getCursor())) {
			int offset = cursor.getPageSize() * Integer.parseInt(cursor.getCursor());
			offsetClause = " OFFSET " + (Math.max(offset, 0));
		}
		return offsetClause;
	}

	public void deleteTableIfExists(TableId tableId) {
		try {
			Table table = bigQuery.getTable(tableId);
			if(table != null) {
				boolean deleted = bigQuery.delete(tableId);
				if (!deleted) {
					throw new RuntimeException(format("Could not delete Bigquery table %s", tableId));
				}
				log.info(format("Bigquery Table %s deleted successfully", tableId));
			}else{
				log.info(format("Bigquery Table %s doesn't exist for deletion", tableId));
			}
		} catch (BigQueryException e) {
			throw new UnknownException(e.getMessage());
		}
	}
	public void deleteDatasetIfExists(String datasetName) {
		try {
			DatasetId datasetId = DatasetId.of(appConfig.getGcpProjectId(), datasetName);
			Dataset dataset = bigQuery.getDataset(datasetId);
			if(dataset != null) {
				boolean deleted = bigQuery.delete(datasetId);
				if (!deleted) {
					throw new RuntimeException(format("Could not delete Bigquery dataset %s", datasetId));
				}
				log.info(format("Bigquery Dataset %s deleted successfully", datasetName));
			}else{
				log.info(format("Bigquery Dataset %s doesn't exist for deletion", datasetName));
			}
		} catch (BigQueryException e) {
			throw new UnknownException(e.getMessage());
		}
	}
}
