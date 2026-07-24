package com.syncari.core.event.store.repo;

import static java.lang.String.format;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.event.store.model.WebhookReceiverLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebhookReceiverLogRepo {
	@Autowired
	BigQueryHelper helper;
	@Autowired
	DateUtil dateUtil;
	@Autowired
	AppConfig appConfig;
	@Autowired
	BigQuery bigQueryService;
	@Autowired
	ObjectMapper mapper;


	private static final String deleteAll = "DELETE FROM `%s` WHERE true";
	private static final String count = "SELECT COUNT(*) as total FROM `%s` WHERE receivedOn >= @receivedOn";

	private static final String queryByDate = "SELECT * FROM `%s` WHERE %s ORDER BY receivedOn %s %s %s";

	public WebhookReceiverLog insertWebhookReceiverLog(WebhookReceiverLog data) {
		if (data == null) {
			return data;
		}
		helper.insertRowsWithException(toTableId(StoreSchema.WEBHOOK_TXN_LOG_TABLE_NAME), toRows(List.of(data)));
		return data;
	}

	long count() {
		String fullTableName = helper.getFullTableName(StoreSchema.WEBHOOK_TXN_LOG_TABLE_NAME);
		String formatted = format(count, fullTableName);
		QueryParameterValue occurredTime = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.EPOCH, DateUtil.dateTimeFormatMicro));
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("receivedOn", occurredTime).build();
		TableResult tableResult = helper.runQuery(config);
		for (FieldValueList row : tableResult.iterateAll()) {
			return row.get("total").getLongValue();
		}
		return 0;
	}

	public void deleteAll() {
		String fullTableName = helper.getFullTableName(StoreSchema.WEBHOOK_TXN_LOG_TABLE_NAME);
		String formatted = format(deleteAll, fullTableName);
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted).build();
		helper.runQuery(config);
		log.info("All transactions deleted from {}", fullTableName);
	}

	private TableId toTableId(String tableName) {
		return TableId.of(SyncariContext.getSyncariId(), tableName);
	}

	private List<RowToInsert> toRows(List<WebhookReceiverLog> logs) {
		List<RowToInsert> rows = new ArrayList<RowToInsert>();
		logs.stream().forEach(e -> {
			Map<String, Object> content = new HashMap<String, Object>();
			content.put("id", !StringUtils.isBlank(e.getId()) ? e.getId() : ObjectId.get().toHexString());
			content.put("connectorId", e.getConnectorId());
			Instant receivedOn = e.getReceivedOn() == null ? Instant.now() : e.getReceivedOn();
			content.put("receivedOn", receivedOn.toEpochMilli() / 1000.0d);
			content.put("payload", e.getPayload());
			content.put("headers", e.getHeaders());
			content.put("verified", e.getVerified());
			content.put("authenticated", e.getAuthenticated());
			rows.add(RowToInsert.of(content));
		});
		return rows;
	}

	private List<WebhookReceiverLog> toWebhookReceiverLogs(TableResult rows) {
		List<WebhookReceiverLog> data = new ArrayList<>();
		for (FieldValueList row : rows.iterateAll()) {
		  WebhookReceiverLog log = new WebhookReceiverLog();
			long receivedOn = row.get("receivedOn").getTimestampValue() / 1000;
			log.setReceivedOn(Instant.ofEpochMilli(receivedOn));
			log.setId(helper.getStringOrEmpty(row.get("id")));
			log.setConnectorId(helper.getStringOrEmpty(row.get("connectorId")));
			log.setAuthenticated(row.get("authenticated").getBooleanValue());
			log.setVerified(row.get("verified").getBooleanValue());
			log.setPayload(helper.getStringOrEmpty(row.get("payload")));
			log.setHeaders(helper.getStringOrEmpty(row.get("headers")));
			data.add(log);
		}
		return data;
	}

	public Page<WebhookReceiverLog> query(String connectorId, Instant startDate, Instant endDate, PageCursor cursor) {
        if (cursor == null) throw new RuntimeException("Cursor is required");
        cursor.validate();
        String sort = cursor.isForward() ? "desc" : "asc";
        String limitClause = helper.getLimitClause(cursor);
        String offSetClause = helper.getOffSetClause(cursor);
        List<String> clauses = new ArrayList<>();
        clauses.add(" connectorId = @connectorId ");
        clauses.add(" receivedOn > @startDate ");
        clauses.add(" receivedOn < @endDate");
        String mainClause = StringUtils.join(clauses, " AND ");
        String formatted = format(queryByDate, helper.getFullTableName(StoreSchema.WEBHOOK_TXN_LOG_TABLE_NAME), mainClause, sort, limitClause, offSetClause);
        QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(formatted);
        QueryParameterValue startDateT = QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro));
        builder.addNamedParameter("startDate", startDateT);
        log.info("startDate {} ", startDateT);
        QueryParameterValue endDateT = QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro));
        builder.addNamedParameter("endDate", endDateT);
        log.info("endDate {} ", endDateT);
        builder.addNamedParameter("connectorId", QueryParameterValue.string(connectorId));
        QueryJobConfiguration config = builder.build();
        log.info(config.getQuery());
        TableResult r = helper.runQuery(config);
        List<WebhookReceiverLog> results = toWebhookReceiverLogs(r);

		return constructPage(cursor, results);
    }

	public Page<WebhookReceiverLog> constructPage(PageCursor cursor, List<WebhookReceiverLog> results) {
		boolean hasMore = results.size() == cursor.getPageSize() + 1;
		boolean hasPrevious = (!StringUtils.isBlank(cursor.getCursor()) && Integer.parseInt(cursor.getCursor()) >= 1);
		if (results.size() > cursor.getPageSize()) {
			results = results.subList(0, results.size() - 1);
		}

		Page<WebhookReceiverLog> page = new Page<>();
		String pageStart = results.size() > 0 ? results.get(0).getId() : null;
		String pageEnd = results.size() > 0 ? results.get(results.size() - 1).getId() : null;
		page.setPageInfo(new PageInfo(pageStart, pageEnd, hasMore).addSort("Id", true));
		page.setRecords(results);
		page.getPageInfo().setHasMore(hasMore);
		page.getPageInfo().setHasPrevious(hasPrevious);
		page.getPageInfo().setPageNumber(cursor.getPageNumber());
		assert page.getRecords().size() <= cursor.getPageSize();
		return page;
	}
}
