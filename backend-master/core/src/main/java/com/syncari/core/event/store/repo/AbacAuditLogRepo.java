package com.syncari.core.event.store.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.event.store.model.AbacAudit;
import com.syncari.core.event.store.model.WebhookReceiverLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

@Slf4j
@Component
public class AbacAuditLogRepo {
	@Autowired
	BigQueryHelper helper;
	@Autowired
	DateUtil dateUtil;
	@Autowired
	ObjectMapper mapper;


	private static final String deleteAll = "DELETE FROM `%s` WHERE true";
	private static final String count = "SELECT COUNT(*) as total FROM `%s` WHERE createdAt >= @createdAt";
	private static final String queryByDate = "SELECT * FROM `%s` WHERE %s ORDER BY createdAt %s %s %s";

	public AbacAudit insertAbacAudit(AbacAudit data) {
		if (data == null) {
			return data;
		}
		try {
			helper.insertRowsWithException(toTableId(StoreSchema.ABAC_AUDIT_LOG_TABLE_NAME), toRows(List.of(data)));
			return data;
		} catch (Exception e) {
			log.error("Failed to insert ABAC audit log: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to save ABAC audit log", e);
		}
	}

	long count() {
		String fullTableName = helper.getFullTableName(StoreSchema.ABAC_AUDIT_LOG_TABLE_NAME);
		String formatted = format(count, fullTableName);
		QueryParameterValue occurredTime = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.EPOCH, DateUtil.dateTimeFormatMicro));
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted)
				.addNamedParameter("createdAt", occurredTime).build();
		TableResult tableResult = helper.runQuery(config);
		for (FieldValueList row : tableResult.iterateAll()) {
			return row.get("total").getLongValue();
		}
		return 0;
	}

	public void deleteAll() {
		String fullTableName = helper.getFullTableName(StoreSchema.ABAC_AUDIT_LOG_TABLE_NAME);
		String formatted = format(deleteAll, fullTableName);
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted).build();
		helper.runQuery(config);
		log.info("All transactions deleted from {}", fullTableName);
	}

	private TableId toTableId(String tableName) {
		return TableId.of(SyncariContext.getSyncariId(), tableName);
	}

 private List<RowToInsert> toRows(List<AbacAudit> logs) {
     List<RowToInsert> rows = new ArrayList<RowToInsert>();
     logs.stream().forEach(e -> {
         Map<String, Object> content = new HashMap<String, Object>();
         content.put("id", !StringUtils.isBlank(e.getId()) ? e.getId() : ObjectId.get().toHexString());
         content.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toEpochMilli() / 1000.0d : Instant.now().toEpochMilli() / 1000.0d);
         content.put("resourceType", e.getResourceType());
         content.put("resource", e.getResource());
         content.put("action", e.getAction());
         content.put("user", e.getUser());
         content.put("allowed", e.getAllowed());
         content.put("policy", e.getPolicy());
         rows.add(RowToInsert.of(content));
     });
     return rows;
 }

	private List<AbacAudit> toAbacAudits(TableResult rows) {
        List<AbacAudit> data = new ArrayList<>();
        for (FieldValueList row : rows.iterateAll()) {
          AbacAudit log = new AbacAudit();
            // Fix timestamp conversion to match insertion format
            long createdAtTimestamp = row.get("createdAt").getTimestampValue();
            log.setCreatedAt(Instant.ofEpochMilli(createdAtTimestamp / 1000));
            log.setId(helper.getStringOrEmpty(row.get("id")));
            log.setResourceType(helper.getStringOrEmpty(row.get("resourceType")));
            log.setResource(helper.getStringOrEmpty(row.get("resource")));
            log.setAction(helper.getStringOrEmpty(row.get("action")));
            log.setUser(helper.getStringOrEmpty(row.get("user")));
            log.setAllowed(row.get("allowed").getBooleanValue());
            log.setPolicy(helper.getStringOrEmpty(row.get("policy")));
            data.add(log);
        }
        return data;
	}

	public Page<AbacAudit> query(String resourceType, Instant startDate, Instant endDate, PageCursor cursor) {
        if (cursor == null) throw new RuntimeException("Cursor is required");
        cursor.validate();
        String sort = cursor.isForward() ? "desc" : "asc";
        String limitClause = helper.getLimitClause(cursor);
        String offSetClause = helper.getOffSetClause(cursor);
        List<String> clauses = new ArrayList<>();
        if (StringUtils.isNotBlank(resourceType)) {
            clauses.add(" resourceType = @resourceType ");
        }
        clauses.add(" createdAt > @startDate ");
        clauses.add(" createdAt < @endDate");
        String mainClause = StringUtils.join(clauses, " AND ");
        String formatted = format(queryByDate, helper.getFullTableName(StoreSchema.ABAC_AUDIT_LOG_TABLE_NAME), mainClause, sort, limitClause, offSetClause);
        QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(formatted);
        QueryParameterValue startDateT = QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro));
        builder.addNamedParameter("startDate", startDateT);
        log.info("startDate {} ", startDateT);
        QueryParameterValue endDateT = QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro));
        builder.addNamedParameter("endDate", endDateT);
        log.info("endDate {} ", endDateT);
        if (StringUtils.isNotBlank(resourceType)) {
            builder.addNamedParameter("resourceType", QueryParameterValue.string(resourceType));
        }
        QueryJobConfiguration config = builder.build();
        log.info(config.getQuery());
        TableResult r = helper.runQuery(config);
        List<AbacAudit> results = toAbacAudits(r);

		return constructPage(cursor, results);
    }

	public Page<AbacAudit> constructPage(PageCursor cursor, List<AbacAudit> results) {
		boolean hasMore = results.size() == cursor.getPageSize() + 1;
		boolean hasPrevious = (!StringUtils.isBlank(cursor.getCursor()) && Integer.parseInt(cursor.getCursor()) >= 1);
		if (results.size() > cursor.getPageSize()) {
			results = results.subList(0, results.size() - 1);
		}

		Page<AbacAudit> page = new Page<>();
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