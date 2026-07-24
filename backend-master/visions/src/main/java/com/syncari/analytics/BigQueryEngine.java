package com.syncari.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.*;
import com.syncari.analytics.cache.QueryCache;
import com.syncari.analytics.service.data.ApiUsage;
import com.syncari.analytics.service.data.DataMetrics;
import com.syncari.analytics.service.data.Direction;
import com.syncari.analytics.service.data.MetricOverTime;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.event.store.repo.BigQueryTransactionLogRepo;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.misc.PageRequest;
import com.syncari.core.model.misc.Source;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component
public class BigQueryEngine implements QueryEngine {
    @Autowired
    AppConfig appConfig;
    @Autowired
    QueryCache queryCache;
    @Autowired
    BigQuery bigQueryService;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    BigQueryHelper helper;

    @Autowired
    BigQueryTransactionLogRepo bigQueryTransactionLogRepo;


    public String mostActiveEntity(Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.mostActiveEntity,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));
        Map<String, Long> mostActive = transactionCountGroupsByDateRange(startDate, endDate, formatted, "entityName");
        return mostActive.entrySet().stream().findFirst().map(e->e.getKey()).orElse(null);
    }

    public Map<String, Long> topActiveEntitiesWithCount(Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.topActiveEntitiesWithCount,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));
        return transactionCountGroupsByDateRange(startDate, endDate, formatted, "entityName");
    }

    public Long syncErrorCountByRange(Instant startDate, Instant endDate, String connectorName, String operation, String syncariEntityName, String syncariRecordId) {
    	String connectorString = getConnectorFilter(connectorName);
        String operationString = getOperationFilter(operation);
        String entityNameString = getSyncariEntityNameFilter(syncariEntityName);
        String syncariRecordIdString = getSyncariRecordIdFilter(syncariRecordId);

        String formatted = format(QueryConstant.syncErrorCount, helper.getFullTableName(StoreSchema.ERROR_LOG_TABLE_NAME),
                connectorString, operationString, entityNameString, syncariRecordIdString);
        return syncErrorCountByDateRange(startDate, endDate, formatted);
    }

    private Long syncErrorCountByDateRange(Instant startDate, Instant endDate, String q) {
        return withCache(getCacheKey(startDate, endDate, q), () -> {
            TableResult query = helper.runQuery(helper.getQueryConfigSyncErrors(startDate, endDate, q));
            long count = 0L;
            for (FieldValueList row : query.iterateAll()) {
                count = row.get("errorCount").getLongValue();
            }
            return count;
        });
    }

    private Long syncErrorCountBySyncIdAndError(String syncCycleId, String connectorId, String externalEntityName, String error) {
        String formatted = format(QueryConstant.syncErrorCountBySyncCycleIdAndEntity,
                helper.getFullTableName(StoreSchema.ERROR_LOG_TABLE_NAME), syncCycleId, connectorId, externalEntityName, error);

        return withCache(formatted, () -> {
            TableResult query = helper.runQuery(QueryJobConfiguration.newBuilder(formatted).build());
            long count = 0L;
            for (FieldValueList row : query.iterateAll()) {
                count = row.get("errorCount").getLongValue();
            }
            return count;
        });
    }

    public Long transactionCountByRange(Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.transactionCountByDateRange,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));
        return transactionCountByDateRange(startDate, endDate, formatted);
    }

    public Long transactionCountByEntityNameAndRange(String entityName,Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.transactionCountByEntityNameAndDateRange,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME),entityName);
        return transactionCountByDateRange(startDate, endDate, formatted);
    }

    public Long transactionCountNewByRange(Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.transactionCountNewByRange,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));
        return transactionCountByDateRange(startDate, endDate, formatted);
    }

    public Long transactionCountNewByEntityNameAndRange(String entityName,Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.transactionCountNewByEntityNameAndRange,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME),entityName);
        return transactionCountByDateRange(startDate, endDate, formatted);
    }

    public Long transactionCountUpdateByRange(Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.transactionCountUpdateByRange,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));
        return transactionCountByDateRange(startDate, endDate, formatted);
    }
    public Long transactionCountUpdateByEntityNameAndRange(String entityName,Instant startDate, Instant endDate){
        String formatted =
                format( QueryConstant.transactionCountUpdateByEntityNameAndRange,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME),entityName);
        return transactionCountByDateRange(startDate, endDate, formatted);
    }

    int getMaxPageNumber(Long totalRecords, int pageSize) {
        int maxPage = 0;
        if (totalRecords > 0 && pageSize > 0) {
            maxPage = (int) (totalRecords / pageSize);
        }
        return maxPage;
    }

    private void validatePageNumber(int pageNumber, int maxPage) {
        if (pageNumber < 0 || pageNumber > maxPage) {
            throw new SyncariValidationException(i18n("page_number_invalid"));
        }
    }

    public Optional<String> mostActiveSynapse(Instant startDate, Instant endDate){
        String formatted =
                format(BigQueryTransactionLogRepo.mostActiveSynapse,
                        helper.getFullTableName(StoreSchema.TXNS_LOG_TABLE_NAME));
        return Optional.ofNullable(withCache(getCacheKey(startDate, endDate, formatted), () -> bigQueryTransactionLogRepo.mostActiveSynapse(formatted,startDate, endDate)));
    }

    private Long transactionCountByDateRange(Instant startDate, Instant endDate, String q) {
        return withCache(getCacheKey(startDate, endDate, q), () -> {
            TableResult query = helper.runQuery(helper.getQueryConfigForTimestamp(startDate, endDate, q));
            long count = 0l;
            for (FieldValueList row : query.iterateAll()) {
                count = row.get("transactionCount").getLongValue();
            }
            return count;
        });
    }

    private Map<String, Long> transactionCountGroupsByDateRange(Instant startDate, Instant endDate, String q,String groupName) {
        return withCache(getCacheKey(startDate, endDate, q), () -> {
            TableResult query = helper.runQuery(helper.getQueryConfigForTimestamp(startDate, endDate, q));
            Map<String, Long> countGroups = new HashMap<>();
            for (FieldValueList row : query.iterateAll()) {
                countGroups.put(row.get(groupName).getStringValue(),row.get("transactionCount").getLongValue());
            }
            return countGroups;
        });
    }

    @Override
    public List<MetricOverTime> getSyncThroughput(PageRequest page, Instant startDate, Instant endDate,
            Direction direction, String connectorName) {
        boolean byHour = isByHour(startDate, endDate);
        String connectorString = (StringUtils.isBlank(connectorName) || "all".equalsIgnoreCase(connectorName)) ? ""
                : " AND connectorName in ('" + connectorName + "', 'Syncari') ";
        String formatted = format(byHour ? QueryConstant.syncThroughputByHour : QueryConstant.syncThroughputByDay,
                helper.getFullTableName(StoreSchema.SYNC_LOG_TABLE_NAME), getDirectionFilter(direction), connectorString);
        return withCache(getCacheKey(startDate, endDate, formatted), () -> {
            TableResult query = helper.runQuery(helper.getQueryConfig(startDate, endDate, formatted));
            TableResult r = query;
            var results = new ArrayList<MetricOverTime>();
            for (FieldValueList row : r.iterateAll()) {
                long epochMilli = byHour
                        ? Instant.ofEpochMilli(row.get("occuredDateHour").getTimestampValue()).getEpochSecond()
                        : dateUtil.toInstant(row.get("occuredDate").getStringValue()).toEpochMilli();
                results.add(new MetricOverTime(row.get("connectorName").getStringValue(), epochMilli,
                        row.get("recordCountSum").getLongValue(), byHour));
            }
            return results;
        });
    }

    @Override
    public List<MetricOverTime> getSyncLatency(PageRequest page, Instant startDate, Instant endDate) {

        boolean byHour = isByHour(startDate, endDate);
        String formatted = format(byHour ? QueryConstant.syncLatencyByHour : QueryConstant.syncLatencyByDay,
                helper.getFullTableName(StoreSchema.SYNC_LOG_TABLE_NAME));

        return withCache(getCacheKey(startDate, endDate, formatted), () -> {
            List<MetricOverTime> result = new ArrayList<MetricOverTime>();
            TableResult r = helper.runQuery(helper.getQueryConfig(startDate, endDate, formatted));
            for (FieldValueList row : r.iterateAll()) {
                long epochMilli = byHour
                        ? Instant.ofEpochMilli(row.get("occuredDateHour").getTimestampValue()).getEpochSecond()
                        : dateUtil.toInstant(row.get("occuredDate").getStringValue()).toEpochMilli();
                String direction = helper.getStringOrEmpty(row.get("direction"));
                String name = "";
                if (Direction.inbound.name().equalsIgnoreCase(direction)) {
                    name = "Source";
                } else if (Direction.outbound.name().equalsIgnoreCase(direction)) {
                    name = "Sink";
                } else {
                    name = "Syncari";
                }
                result.add(new MetricOverTime(name, epochMilli,
                        Math.round(row.get("timeTaken").getNumericValue().doubleValue()), byHour));
            }
            return result;
        });
    }

    @Override
    public Page<SyncError> getSyncErrors(PageCursor pageCursor, String syncCycleId, String connectorId, String externalEntityName, String error) {
        try {
            String escapedError = escapeBQString(error);
            String formatted = format(QueryConstant.syncErrorsBySyncCycleAndError, helper.getFullTableName(StoreSchema.ERROR_LOG_TABLE_NAME), syncCycleId, connectorId, externalEntityName, escapedError);
            long totalRecords = syncErrorCountBySyncIdAndError(syncCycleId, connectorId, externalEntityName, escapedError);
            int maxPage = getMaxPageNumber(totalRecords, pageCursor.getPageSize());
            return withCache(formatted + pageCursor.getPageNumber() + maxPage + pageCursor.getPageSize(), () -> {
                List<SyncError> result = new ArrayList<>();
                TableResult r = helper.runQuery(helper.getQueryConfig(formatted, pageCursor));
                for (FieldValueList row : r.iterateAll()) {
                    long occuredInMicro = row.get("occuredTime").getTimestampValue();

                    if (occuredInMicro > 0) occuredInMicro = occuredInMicro / 1000;
                    result.add(new SyncError(helper.getStringOrEmpty(row.get("connectorId")), helper.getStringOrEmpty(row.get("connectorName")),
                            helper.getStringOrEmpty(row.get("batchId")),
                            helper.getStringOrEmpty(row.get("syncariEntityName")), helper.getStringOrEmpty(row.get("externalEntityName")),
                            helper.getStringOrEmpty(row.get("operation")), helper.getStringOrEmpty(row.get("errorCode")),
                            helper.getStringOrEmpty(row.get("errorCode")), helper.getStringOrEmpty(row.get("syncariRecordId")),
                            helper.getStringOrEmpty(row.get("externalRecordId")), Instant.ofEpochMilli(occuredInMicro)));
                }

                PageInfo pageInfo = new PageInfo(pageCursor.getPageNumber(), maxPage);
                pageInfo.setTotalCount(totalRecords);

                return new Page<>(pageInfo, result);
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public Page<SyncError> getSyncErrors(PageCursor pageCursor, Instant startDate, Instant endDate,
                                         String connectorName, String operation, String syncariEntityName, String syncariRecordId) {
        try {
            String connectorString = getConnectorFilter(connectorName);
            String operationString = getOperationFilter(operation);
            String entityNameString = getSyncariEntityNameFilter(syncariEntityName);
            String syncariRecordIdString = getSyncariRecordIdFilter(syncariRecordId);

            String formatted = format(QueryConstant.syncErrors, helper.getFullTableName(StoreSchema.ERROR_LOG_TABLE_NAME),
                    connectorString, operationString, entityNameString, syncariRecordIdString);
            
            List<SyncError> result = new ArrayList<>();
            TableResult r = helper.runQuery(helper.getQueryConfig(startDate, endDate, formatted, pageCursor));
            log.info("Query Executed, process table result now");
            for (FieldValueList row : r.iterateAll()) {
                long occuredInMicro = row.get("occuredTime").getTimestampValue();
                if (occuredInMicro > 0) occuredInMicro = occuredInMicro / 1000;
                result.add(new SyncError(helper.getStringOrEmpty(row.get("connectorId")), helper.getStringOrEmpty(row.get("connectorName")),
                        helper.getStringOrEmpty(row.get("batchId")),
                        helper.getStringOrEmpty(row.get("syncariEntityName")), helper.getStringOrEmpty(row.get("externalEntityName")),
                        helper.getStringOrEmpty(row.get("operation")), helper.getStringOrEmpty(row.get("errorCode")),
                        helper.getStringOrEmpty(row.get("errorDetails")), helper.getStringOrEmpty(row.get("syncariRecordId")),
                        helper.getStringOrEmpty(row.get("externalRecordId")), Instant.ofEpochMilli(occuredInMicro)));
            }
			int maxPage = getMaxPageNumber(syncErrorCountByRange(startDate, endDate, connectorName, operation,
					syncariEntityName, syncariRecordId), pageCursor.getPageSize());
            PageInfo pageInfo = new PageInfo(pageCursor.getPageNumber(), maxPage);
            pageInfo.setTotalCount(result.size());
            return new Page<>(pageInfo, result);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ApiUsage> getSynapseUsage(PageRequest page, Instant startDate, Instant endDate, String connectorName,
            String operation) {

        try {
            String connectorString = getConnectorFilter(connectorName);
            String operationString = (StringUtils.isBlank(operation) || "all".equalsIgnoreCase(operation)) ? ""
                    : " AND operation = '" + operation + "' ";
            String formatted = format(QueryConstant.synapseUsage, helper.getFullTableName(StoreSchema.SYNC_LOG_TABLE_NAME),
                    connectorString, operationString);
            return withCache(getCacheKey(startDate, endDate, formatted), () -> {
                List<ApiUsage> result = new ArrayList<>();
                TableResult r = helper.runQuery(helper.getQueryConfig(startDate, endDate, formatted));
                for (FieldValueList row : r.iterateAll()) {
                    result.add(new ApiUsage(helper.getStringOrEmpty(row.get("connectorName")),
                            helper.getStringOrEmpty(row.get("operation")), helper.getStringOrEmpty(row.get("syncariEntityApiName")),
                            row.get("latency").getLongValue(), getFormattedDate(row, "occuredDateHour")));
                }
                return result;
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<MetricOverTime> getSynapseLatency(PageRequest page, Instant startDate, Instant endDate,
            String connectorName) {

        try {
            boolean byHour = isByHour(startDate, endDate);
            String connectorString = getConnectorFilter(connectorName);
            String formatted = format(byHour ? QueryConstant.synapseLatencyByHour : QueryConstant.synapseLatencyByDay,
                    helper.getFullTableName(StoreSchema.SYNC_LOG_TABLE_NAME), connectorString);
            return withCache(getCacheKey(startDate, endDate, formatted), () -> {
                TableResult r = helper.runQuery(helper.getQueryConfig(startDate, endDate, formatted));
                List<MetricOverTime> result = new ArrayList<MetricOverTime>();
                for (FieldValueList row : r.iterateAll()) {
                    long epochMilli = byHour
                            ? Instant.ofEpochMilli(row.get("occuredDateHour").getTimestampValue()).getEpochSecond()
                            : dateUtil.toInstant(row.get("occuredDate").getStringValue()).toEpochMilli();
                    result.add(new MetricOverTime(row.get("connectorName").getStringValue(), epochMilli,
                            Math.round(row.get("timeTaken").getNumericValue().doubleValue()), byHour));
                }
                return result;
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DataMetrics> getDataMetrics(PageRequest page, String entityName) {
        return null;
    }
    
    @Override
    public Map<String, Long> getTopActiveSynapses(Instant startDate, Instant endDate) {
        try {
            String formatted = format(QueryConstant.topSynapsesByWeek, helper.getFullTableName(StoreSchema.SYNC_LOG_TABLE_NAME));
            return withCache(getCacheKey(startDate, endDate, formatted), () -> {
                TableResult r = helper.runQuery(helper.getQueryConfig(startDate, endDate, formatted));
                Map<String, Long> result = new HashMap<String, Long>();
                for (FieldValueList row : r.iterateAll()) {
                    FieldValue recordCountSum = row.get("recordCountSum");
                    result.put(row.get("connectorName").getStringValue(), recordCountSum == null ? 0 : recordCountSum.getNumericValue().longValue());
                }
                return result;
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private String getConnectorFilter(String connectorName) {
        return (StringUtils.isBlank(connectorName) || "all".equalsIgnoreCase(connectorName)) ? ""
                : " AND connectorName = '" + connectorName + "' ";
    }

    private String getOperationFilter(String operation) {
        return (StringUtils.isBlank(operation) || "all".equalsIgnoreCase(operation)) ? ""
                : " AND operation = '" + operation + "' ";
    }

    private String getSyncariEntityNameFilter(String syncariEntityName) {
        return (StringUtils.isBlank(syncariEntityName) || "all".equalsIgnoreCase(syncariEntityName)) ? ""
                : " AND syncariEntityName = '" + syncariEntityName + "' ";
    }

    private String getSyncariRecordIdFilter(String syncariRecordId) {
        return (StringUtils.isBlank(syncariRecordId) || "all".equalsIgnoreCase(syncariRecordId)) ? ""
                : " AND syncariRecordId = '" + syncariRecordId + "' ";
    }

    @Override
    /**
     * This API powers a realtime widget. Do not cache
     */
    public long getApiCalls(Instant startDate, Instant endDate) {
        try {
            String formatted = format(QueryConstant.totalApiCallToday, helper.getFullTableName(StoreSchema.SYNC_LOG_TABLE_NAME));

            TableResult r = helper.runQuery(helper.getQueryConfig(startDate, endDate, formatted));
            for (FieldValueList row : r.iterateAll()) {
                FieldValue fieldValue = row.get("totalApiCallToday");
                if(fieldValue == null || fieldValue.getValue() == null || StringUtils.isBlank(fieldValue.getStringValue())) continue;
                else return fieldValue.getLongValue();
            }
            return 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    /**
     * This API powers a realtime widget. Do not cache
     */
    public List<MetricOverTime> getDedupeCount(Instant startDate, Instant endDate, String entityName) {
        return List.of();
    }


    /**
     * This API powers a realtime widget. Do not cache
     */
    @Override
    public List<MetricOverTime> getEnrichCount(Instant startDate, Instant endDate, String functionName) {
        return List.of();
    }

    private String getCacheKey(Instant startDate, Instant endDate, String formatted) {
        return formatted + startDate.getEpochSecond() + endDate.getEpochSecond();
    }

    private <T> T withCache(String cacheKey, Supplier<T> supplier){
        T cached =queryCache.getCached(cacheKey);
        if(cached == null) {
            cached = supplier.get();
            if(cached!=null) {
                queryCache.put(cacheKey, cached);
            }
        }
        return cached;

    }

    private boolean isByHour(Instant startDate, Instant endDate) {
        boolean byHour = false;
        if (startDate.until(endDate, ChronoUnit.DAYS) <= 1)
            byHour = true;
        return byHour;
    }

    private String getDirectionFilter(Direction direction) {
        return (direction == null || direction == Direction.all) ? "" : " AND direction = '" + direction.name() + "' ";
    }

    private String getFormattedDate(FieldValueList row, String fieldName) {
        FieldValue fieldValue = row.get(fieldName);
        if (fieldValue == null)
            return StringUtils.EMPTY;
        return dateUtil.formatDate(Instant.ofEpochSecond(fieldValue.getTimestampValue() / 1000000), DateUtil.dateFormat)
                .replace("T", " ");
    }

	@Override
    public List<SyncError> getLatestSyncErrorsForEntityPipeline(String syncariEntityName) {
        try {
            String tableName = helper.getFullTableName(StoreSchema.ERROR_LOG_TABLE_NAME);
            String formatted = format(QueryConstant.syncStreamErrors, tableName, syncariEntityName, tableName, syncariEntityName);

            List<SyncError> result = new ArrayList<SyncError>();
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formatted).build();
            TableResult r = helper.runQuery(queryConfig);
            for (FieldValueList row : r.iterateAll()) {
                long occuredInMicro = row.get("occuredTime").getTimestampValue();
                if (occuredInMicro > 0) occuredInMicro = occuredInMicro / 1000;
                result.add(new SyncError(helper.getStringOrEmpty(row.get("connectorId")), helper.getStringOrEmpty(row.get("connectorName")),
                        helper.getStringOrEmpty(row.get("batchId")),
                        helper.getStringOrEmpty(row.get("syncariEntityName")), helper.getStringOrEmpty(row.get("externalEntityName")),
                        helper.getStringOrEmpty(row.get("operation")), helper.getStringOrEmpty(row.get("errorCode")),
                        helper.getStringOrEmpty(row.get("errorCode")), helper.getStringOrEmpty(row.get("syncariRecordId")),
                        helper.getStringOrEmpty(row.get("externalRecordId")), Instant.ofEpochMilli(occuredInMicro)));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String escapeBQString(String s) {
        return s.replace("\\", "\\\\")
                .replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r")
                .replaceAll("\t", "\\\\t").replaceAll("\b", "\\\\b")
                .replaceAll("\"", "\\\"").replaceAll("'", "\\\\'");
    }
}
