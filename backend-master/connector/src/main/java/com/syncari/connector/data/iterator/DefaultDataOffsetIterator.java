package com.syncari.connector.data.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.utils.Pair;

import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultDataOffsetIterator extends AbstractEntityDataBatchIterator {
    protected List<EntityData> data = new ArrayList<>();
    protected WatermarkInfo baseWatermark;
    protected long offset = 0;
    boolean isLastPage = false;
    protected Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator;
    protected AttributeSchema watermarkField;
    IteratorHelper helper = new IteratorHelper();

    long prevOffset;
    long nextOffset;
    
    public DefaultDataOffsetIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator, 
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

    @Override
    public boolean hasNext() {
        // We have already consumed last page. Nothing more here
        if (isLastPage && isConsumed() || hasFetchedMaxRecords()) {
            log.info("Iterator has been drained. Either this is the last page or this cycle has reached max records. " +
                "isLastPage/isConsumed/hasFetchedMaxRecords:{}/{}/{}", isLastPage, isConsumed(), hasFetchedMaxRecords());
            return false;
        }
        // retrieved data is not yet consumed.
        if (!isConsumed()) return true;

        long now = System.currentTimeMillis();
        DataWithOffset results = generator.apply(baseWatermark, getEffectivePageSize(), nextOffset);
        long done = System.currentTimeMillis();

        Stream<EntityData> entityDataStream = results.getData().stream();
        if (results.getNextOffset() == 0l) {
            log.info("Processing lastpage. nextOffset/baseWatermark: {}/{}", nextOffset, baseWatermark);
        }
        // if a synapse doesn't give sorted results - remove records beyond wm window for all batches
        entityDataStream = entityDataStream.filter(e -> getWatermarkValue(e) <= baseWatermark.getEnd()
                || (baseWatermark.isInitial() && !baseWatermark.hasEnd()));
        int recordsToConsume = (int)(maxRecords > 0 ? maxRecords - totalRecordsFetched : pageSize);
        data = entityDataStream.limit(recordsToConsume).collect(Collectors.toList());
        stats.addLatencyCount((done-now),data.size());
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
        if(data.isEmpty() || isLastPage()) return 0;
        return nextOffset;
    }

    @Override
    public long getLastOffset() {
        return nextOffset;
    }

    @Override
    public List<EntityData> next() {
        // reset data to mark it as consumed
        var temp = data;
        if (!data.isEmpty()) {
            EntityData entityData = data.get(data.size() - 1);
            lastWatermark = getWatermarkValue(entityData);
            totalRecordsFetched+=data.size();
        }
        data = new ArrayList<>();

        return temp;
    }

    protected long getWatermarkValue(EntityData entityData) {
        return entityData.getLastModified();
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(OffsetType.RECORD_COUNT, pageSize);
    }

}