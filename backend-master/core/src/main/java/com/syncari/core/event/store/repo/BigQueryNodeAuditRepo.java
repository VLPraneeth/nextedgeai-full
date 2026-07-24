package com.syncari.core.event.store.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.InsertAllRequest.RowToInsert;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.event.store.model.BatchMode;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.event.store.model.RunMode;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
public class BigQueryNodeAuditRepo {
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
	private static final String findById = "SELECT * FROM `%s` WHERE id=@id";
	private static final String count = "SELECT COUNT(*) as total FROM `%s` WHERE occurredTime >= @occurredTime";

	private static final String queryByDate = "SELECT * FROM `%s` WHERE %s ORDER BY occurredTime %s %s %s";

	public List<NodeAudit> insertNodeAudit(List<NodeAudit> data) {
		if (data == null || data.isEmpty()) {
			return data;
		}
		helper.insertRowsWithException(toTableId(StoreSchema.NODE_AUDIT_TABLE_NAME), toRows(data));
		return data;
	}

	long count() {
		String fullTableName = helper.getFullTableName(StoreSchema.NODE_AUDIT_TABLE_NAME);
		String formatted = format(count, fullTableName);
		QueryParameterValue occurredTime = QueryParameterValue.timestamp(dateUtil.formatDate(Instant.EPOCH, DateUtil.dateTimeFormatMicro));
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
		QueryJobConfiguration config = QueryJobConfiguration.newBuilder(formatted).build();
		helper.runQuery(config);
		log.info("All transactions deleted from {}", fullTableName);
	}

	private TableId toTableId(String tableName) {
		return TableId.of(SyncariContext.getSyncariId(), tableName);
	}

	private List<RowToInsert> toRows(List<NodeAudit> logs) {
		List<RowToInsert> rows = new ArrayList<RowToInsert>();
		logs.stream().forEach(e -> {
			Map<String, Object> content = new HashMap<String, Object>();
			content.put("id", !StringUtils.isBlank(e.getId()) ? e.getId() : ObjectId.get().toHexString());
			content.put("entityId", e.getEntityId());
			content.put("entityPipelineId", e.getEntityPipelineId());
			content.put("pipelineId", e.getPipelineId());
			content.put("pipelineName", e.getPipelineName());
			content.put("syncariAttributeId", e.getSyncariAttributeId());
			content.put("batchMode", e.getBatchMode().name());
			content.put("runMode", e.getRunMode().name());
			content.put("nodeId", e.getNodeId());
			content.put("nodeName", e.getNodeName());
			content.put("nodeType", e.getNodeType());
			content.put("batchId", e.getBatchId());
			content.put("syncariRecordId", e.getSyncariRecordId());
			content.put("scope", e.getScope());
			content.put("error", e.getError());
			content.put("errorDetails", e.getErrorDetails());
			//json
			try {
				content.put("input", mapper.writeValueAsString(e.getInput()));
				content.put("output", mapper.writeValueAsString(e.getOutput()));
				content.put("externalRecordIds", mapper.writeValueAsString(e.getExternalRecordIds()));
			} catch (JsonProcessingException ex) {
				throw new RuntimeException(ex);
			}
			Instant occrredTime = e.getOccurredTime() == null ? Instant.now() : e.getOccurredTime();
			content.put("occurredTime", occrredTime.toEpochMilli() / 1000.0d);
			content.put("startTime", e.getStartTime());
			content.put("endTime", e.getEndTime());
			rows.add(RowToInsert.of(content));
		});
		return rows;
	}

	private List<NodeAudit> toNodeAudits(TableResult rows) {
		var mapper = new ObjectMapper();
		List<NodeAudit> data = new ArrayList<>();
		for (FieldValueList row : rows.iterateAll()) {
			NodeAudit nodeAudit = new NodeAudit();
			long occurredTime = row.get("occurredTime").getTimestampValue() / 1000;
			nodeAudit.setOccurredTime(Instant.ofEpochMilli(occurredTime));
			nodeAudit.setStartTime(helper.getLong(row.get("startTime")));
			nodeAudit.setEndTime(helper.getLong(row.get("endTime")));
			nodeAudit.setOccurredTime(Instant.ofEpochMilli(occurredTime));
			nodeAudit.setId(helper.getStringOrEmpty(row.get("id")));
			nodeAudit.setBatchId(helper.getStringOrEmpty(row.get("batchId")));
			nodeAudit.setEntityId(helper.getStringOrEmpty(row.get("entityId")));
			nodeAudit.setEntityPipelineId(helper.getStringOrEmpty(row.get("entityPipelineId")));
			nodeAudit.setPipelineId(helper.getStringOrEmpty(row.get("pipelineId")));
			nodeAudit.setSyncariRecordId(helper.getStringOrEmpty(row.get("syncariRecordId")));
			nodeAudit.setPipelineName(helper.getStringOrEmpty(row.get("pipelineName")));
			nodeAudit.setNodeId(helper.getStringOrEmpty(row.get("nodeId")));
			nodeAudit.setSyncariAttributeId(helper.getStringOrEmpty(row.get("syncariAttributeId")));
			nodeAudit.setNodeName(helper.getStringOrEmpty(row.get("nodeName")));
			final String batchMode = helper.getStringOrEmpty(row.get("batchMode"));
			nodeAudit.setBatchMode(StringUtils.isBlank(batchMode) ? BatchMode.UNBATCHED : BatchMode.valueOf(batchMode));
			final String runMode = helper.getStringOrEmpty(row.get("runMode"));
			nodeAudit.setRunMode(StringUtils.isBlank(runMode) ? RunMode.LIVE : RunMode.valueOf(runMode));
			nodeAudit.setError(helper.getStringOrEmpty(row.get("error")));
			nodeAudit.setErrorDetails(helper.getStringOrEmpty(row.get("errorDetails")));
			String scope = helper.getStringOrEmpty(row.get("scope"));
			nodeAudit.setScope(StringUtils.isBlank(scope) ? null : scope);
			try {
				String input = helper.getStringOrEmpty(row.get("input"));
				if (!StringUtils.isBlank(input)) {
					nodeAudit.setInput(mapper.readValue(input, Map.class));
				}
				String output = helper.getStringOrEmpty(row.get("output"));
				if(!StringUtils.isBlank(output)) {
					nodeAudit.setOutput(mapper.readValue(output, Map.class));
				}
				String externalRecordIds = helper.getStringOrEmpty(row.get("externalRecordIds"));
				if(!StringUtils.isBlank(externalRecordIds)) {
					nodeAudit.setExternalRecordIds(mapper.readValue(externalRecordIds, Map.class));
				}
			} catch (Exception e) {
				log.error("{}", ExceptionUtils.getStackTrace(e));
				throw new RuntimeException(e);
			}
			data.add(nodeAudit);
		}
		return data;
	}

	public Page<NodeAudit> query(String entityId, String syncariRecordId, Instant startDate, Instant endDate, PageCursor cursor) {
        if (cursor == null) throw new RuntimeException("Cursor is required");
        cursor.validate();
        String sort = "desc"; // Always use descending order (newest first) for consistent user experience
        String limitClause = helper.getLimitClause(cursor);
        String offSetClause = helper.getOffSetClause(cursor);
        List<String> clauses = new ArrayList<>();
        clauses.add(" entityId = @entityId ");
        if (StringUtils.isNotBlank(syncariRecordId)) {
            clauses.add(" syncariRecordId = @syncariRecordId ");
        }
        clauses.add(" occurredTime > @startDate ");
        clauses.add(" occurredTime < @endDate");
        String mainClause = StringUtils.join(clauses, " AND ");
        String formatted = format(queryByDate, helper.getFullTableName(StoreSchema.NODE_AUDIT_TABLE_NAME), mainClause, sort, limitClause, offSetClause);
        QueryJobConfiguration.Builder builder = QueryJobConfiguration.newBuilder(formatted);
        QueryParameterValue startDateT = QueryParameterValue.timestamp(dateUtil.formatDate(startDate, DateUtil.dateTimeFormatMicro));
        if (StringUtils.isNotBlank(syncariRecordId)) {
            builder.addNamedParameter("syncariRecordId", QueryParameterValue.string(syncariRecordId));
        }
        builder.addNamedParameter("startDate", startDateT);
        log.info("startDate {} ", startDateT);
        QueryParameterValue endDateT = QueryParameterValue.timestamp(dateUtil.formatDate(endDate, DateUtil.dateTimeFormatMicro));
        builder.addNamedParameter("endDate", endDateT);
        log.info("endDate {} ", endDateT);
        builder.addNamedParameter("entityId", QueryParameterValue.string(entityId));
        QueryJobConfiguration config = builder.build();
        log.info(config.getQuery());
        TableResult r = helper.runQuery(config);
        List<NodeAudit> results = toNodeAudits(r);

		return constructPage(cursor, results);
    }

	public Page<NodeAudit> constructPage(PageCursor cursor, List<NodeAudit> results) {
		boolean hasMore = results.size() == cursor.getPageSize() + 1;
		boolean hasPrevious = (!StringUtils.isBlank(cursor.getCursor()) && Integer.parseInt(cursor.getCursor()) >= 1);
		if (results.size() > cursor.getPageSize()) {
			results = results.subList(0, results.size() - 1);
		}

		Page<NodeAudit> page = new Page<>();
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
}
