package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.utils.Pair;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.function.Function3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class NetsuiteIncrementalIterator extends AbstractEntityDataBatchIterator {

    protected List<EntityData> data = new ArrayList<>();
    protected WatermarkInfo baseWatermark;
    protected long offset = 0;
    boolean isLastPage = false;
    protected Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> generator;
    protected AttributeSchema watermarkField;
    IteratorHelper helper = new IteratorHelper();
    @Getter
    private boolean ignoreWMMode = false;

    long prevOffset;
    long nextOffset;
    public NetsuiteIncrementalIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> generator,
                                     List<EntityData> data, AttributeSchema watermarkField, int pageSize, int  maxRecords) {
        this.baseWatermark = baseWatermark;
        this.offset = offset;
        this.generator = generator;
        this.data = data;
        this.watermarkField = watermarkField;
        this.pageSize = pageSize;
        this.maxRecords = maxRecords;
        this.nextOffset = offset;
    }

    public NetsuiteIncrementalIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> generator,
                                     List<EntityData> data, AttributeSchema watermarkField, int pageSize, int  maxRecords, boolean ignoreWMMode) {
        this(baseWatermark, offset, generator, data, watermarkField, pageSize, maxRecords);
        this.ignoreWMMode = ignoreWMMode;
        // For NO_WM_ENTITIES (ignoreWMMode = true), initialize lastWatermark to watermark end time
        // instead of relying on record timestamps
        if (ignoreWMMode && baseWatermark != null) {
            this.lastWatermark = baseWatermark.getEnd();
        }
    }

    @Override
    public boolean hasNext() {
        if (isLastPage() && isConsumed()) {
            log.info("Iterator has been drained. This is the last page" +
                    "isLastPage/isConsumed/hasFetchedMaxRecords:{}/{}/{}", isLastPage(), isConsumed(), hasFetchedMaxRecords());
            return false;
        }
        if(totalRecordsFetched >= MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE || (maxRecords > 0 && totalRecordsFetched >= maxRecords)) {
            log.info("Iterator has been drained. This cycle has reached max records. " +
                    "isLastPage/isConsumed/hasFetchedMaxRecords:{}/{}/{}", isLastPage(), isConsumed(), hasFetchedMaxRecords());
            return false;
        }
        // retrieved data is not yet consumed.
        if (!isConsumed()) return true;

        long now = System.currentTimeMillis();
        Pair<Boolean, DataWithOffset> resultPair = generator.apply(baseWatermark, getEffectivePageSize(), nextOffset);
        DataWithOffset results = resultPair.getY();
        isLastPage = !resultPair.getX();
        long done = System.currentTimeMillis();

        Stream<EntityData> entityDataStream = results.getData().stream();
        if (results.getNextOffset() == 0l) {
            log.info("Processing lastpage. nextOffset/baseWatermark: {}/{}", nextOffset, baseWatermark);
        }
        // if a synapse doesn't give sorted results - remove records beyond wm window for all batches
        entityDataStream = entityDataStream.filter(e -> getWatermarkValue(e) <= baseWatermark.getEnd()
                || (baseWatermark.isInitial() && !baseWatermark.hasEnd()));
        int recordsToConsume = results.getData().size();
        data = entityDataStream.limit(recordsToConsume).collect(Collectors.toList());
        long maxTS = data.stream().max(Comparator.comparingLong(EntityData::getLastModified)).map(e -> e.getLastModified()).orElse(-1l);
        // For NO_WM_ENTITIES (ignoreWMMode = true), keep watermark end time
        // instead of updating based on record timestamps
        if (!ignoreWMMode) {
            lastWatermark = Math.max(maxTS, lastWatermark);
        }
        stats.addLatencyCount((done-now),data.size());
        nextOffset = nextOffset(results.getNextOffset(), data);
        prevOffset = results.getPrevOffset();
        if (data.isEmpty()) {
            log.info("Iterator has been drained. datasize is 0. nextOffset/isLastPage/pageSize: {}/{}/{} ", nextOffset, isLastPage(), pageSize);
        }
        return data.size() > 0;
    }

    @Override
    public List<EntityData> next() {
        var temp = data;
        if (!data.isEmpty()) {
            EntityData entityData = data.get(data.size() - 1);
            // For NO_WM_ENTITIES (ignoreWMMode = true), keep watermark end time
            // instead of updating based on record timestamps
            if (!ignoreWMMode) {
                lastWatermark = getWatermarkValue(entityData);
            }
            totalRecordsFetched+=data.size();
        }
        data = new ArrayList<>();

        return temp;
    }

    protected long getWatermarkValue(EntityData entityData) {
        return entityData.getLastModified();
    }

    @Override
    public int getMaxRecordsPerEntitySyncCycle() {
        if (ignoreWMMode) {
            return Integer.MAX_VALUE;
        }
        return super.getMaxRecordsPerEntitySyncCycle();
    }


    private boolean isConsumed() {
        return data.isEmpty();
    }

    protected boolean isLastPage() {
        return isLastPage;
    }

    protected long nextOffset(long nextOffset, List<EntityData> data) {
        if(data.isEmpty() || isLastPage()) return 0;
        return nextOffset;
    }
}
