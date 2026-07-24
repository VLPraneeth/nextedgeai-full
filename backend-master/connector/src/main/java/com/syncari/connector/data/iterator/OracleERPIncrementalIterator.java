package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.WatermarkInfo;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.function.Function3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom iterator for Oracle ERP that handles the case where all records have the same LastUpdateDate.
 *
 * Oracle bulk imports often result in all records having identical timestamps, which causes
 * watermark-based sync to get stuck in an infinite loop. This iterator:
 *
 * 1. Uses offset-based pagination when records have the same watermark
 * 2. Advances the watermark by 1ms when all records at a timestamp are consumed
 * 3. Properly signals to Viper when sync is complete
 *
 * The key difference from DefaultDataOffsetIterator is the watermark advancement logic
 * in the next() method that ensures progress even with duplicate timestamps.
 */
@Slf4j
public class OracleERPIncrementalIterator extends AbstractEntityDataBatchIterator {

    protected List<EntityData> data = new ArrayList<>();
    protected WatermarkInfo baseWatermark;
    protected long offset = 0;
    boolean isLastPage = false;
    protected Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator;
    protected AttributeSchema watermarkField;
    IteratorHelper helper = new IteratorHelper();

    long prevOffset;
    long nextOffset;

    // Track the initial watermark to detect if we've made progress
    private final long initialWatermarkStart;
    // Track max watermark seen across all pages
    private long maxWatermarkSeen = -1;

    public OracleERPIncrementalIterator(WatermarkInfo baseWatermark, long offset,
            Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator,
            List<EntityData> data, AttributeSchema watermarkField, int pageSize, int maxRecords) {
        this.baseWatermark = baseWatermark;
        this.offset = offset;
        this.generator = generator;
        this.data = data;
        this.watermarkField = watermarkField;
        this.pageSize = pageSize;
        this.maxRecords = maxRecords;
        this.nextOffset = offset;
        this.initialWatermarkStart = baseWatermark.getStart();
        log.info("OracleERPIncrementalIterator created: initialOffset={}, initialWatermarkStart={}, pageSize={}, maxRecords={}",
                offset, initialWatermarkStart, pageSize, maxRecords);
    }

    @Override
    public boolean hasNext() {
        // We have already consumed last page. Nothing more here
        if ((isLastPage && isConsumed()) || hasFetchedMaxRecords()) {
            log.info("Iterator has been drained. Either this is the last page or this cycle has reached max records. " +
                    "isLastPage/isConsumed/hasFetchedMaxRecords:{}/{}/{}", isLastPage, isConsumed(), hasFetchedMaxRecords());
            return false;
        }
        // retrieved data is not yet consumed.
        if (!isConsumed()) return true;

        log.info("Fetching next batch: nextOffset={}, pageSize={}, totalRecordsFetched={}",
                nextOffset, getEffectivePageSize(), totalRecordsFetched);
        long now = System.currentTimeMillis();
        DataWithOffset results = generator.apply(baseWatermark, getEffectivePageSize(), nextOffset);
        long done = System.currentTimeMillis();
        log.info("Fetched {} records in {}ms. prevOffset={}, nextOffset={}",
                results.getData().size(), (done - now), results.getPrevOffset(), results.getNextOffset());

        Stream<EntityData> entityDataStream = results.getData().stream();
        if (results.getNextOffset() == 0L) {
            log.info("Processing lastpage. nextOffset/baseWatermark: {}/{}", nextOffset, baseWatermark);
        }
        // if a synapse doesn't give sorted results - remove records beyond wm window for all batches
        entityDataStream = entityDataStream.filter(e -> getWatermarkValue(e) <= baseWatermark.getEnd()
                || (baseWatermark.isInitial() && !baseWatermark.hasEnd()));
        int recordsToConsume = (int) (maxRecords > 0 ? maxRecords - totalRecordsFetched : pageSize);
        data = entityDataStream.limit(recordsToConsume).collect(Collectors.toList());
        stats.addLatencyCount((done - now), data.size());
        nextOffset = nextOffset(results.getNextOffset(), data);
        prevOffset = results.getPrevOffset();
        isLastPage = isLastPage();

        if (data.isEmpty()) {
            log.info("Iterator has been drained. datasize is 0. nextOffset/isLastPage/pageSize: {}/{}/{} ", nextOffset, isLastPage, pageSize);
        }
        return data.size() > 0;
    }

    protected boolean isLastPage() {
        return data.size() < getEffectivePageSize();
    }

    private boolean isConsumed() {
        return data.isEmpty();
    }

    protected long nextOffset(long nextOffset, List<EntityData> data) {
        // if no data is retrieved meaning the window is exhausted - reset the offset
        if (data.isEmpty() || isLastPage()) return 0;
        return nextOffset;
    }

    @Override
    public long getLastOffset() {
        return nextOffset;
    }

    @Override
    public List<EntityData> next() {
        var temp = data;
        if (!temp.isEmpty()) {
            // Find the maximum watermark in this batch
            long maxTSInBatch = temp.stream()
                    .max(Comparator.comparingLong(EntityData::getLastModified))
                    .map(EntityData::getLastModified)
                    .orElse(-1L);

            // Track the overall max watermark seen
            maxWatermarkSeen = Math.max(maxWatermarkSeen, maxTSInBatch);

            totalRecordsFetched += temp.size();

            // Determine the lastWatermark to return
            if (isLastPage) {
                // This is the last page - check if we need to advance watermark
                if (maxWatermarkSeen == initialWatermarkStart) {
                    // All records have the same timestamp as our starting watermark
                    // Advance by 1ms to prevent infinite loop on next sync cycle
                    lastWatermark = maxWatermarkSeen + 1;
                    log.info("Last page reached with all records at same watermark {}. " +
                            "Advancing lastWatermark to {} to prevent infinite loop.",
                            maxWatermarkSeen, lastWatermark);
                } else {
                    // We saw records with different timestamps, use the max
                    lastWatermark = maxWatermarkSeen;
                    log.info("Last page reached. Setting lastWatermark to max seen: {}", lastWatermark);
                }
            } else {
                // Not the last page - just track the watermark normally
                lastWatermark = maxTSInBatch;
            }

            log.info("Processed {} records. maxTSInBatch={}, maxWatermarkSeen={}, lastWatermark={}, isLastPage={}, totalRecordsFetched={}",
                    temp.size(), maxTSInBatch, maxWatermarkSeen, lastWatermark, isLastPage, totalRecordsFetched);
        }
        data = new ArrayList<>();
        return temp;
    }

    protected long getWatermarkValue(EntityData entityData) {
        return entityData.getLastModified();
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.RECORD_COUNT, pageSize);
    }
}
