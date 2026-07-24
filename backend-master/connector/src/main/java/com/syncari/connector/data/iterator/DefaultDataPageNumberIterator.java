package com.syncari.connector.data.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.function.Function3;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DataWithPageNumber;
import com.syncari.connector.data.WatermarkInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultDataPageNumberIterator extends AbstractEntityDataBatchIterator {
    protected List<EntityData> data = new ArrayList<>();
    protected WatermarkInfo baseWatermark;
    protected int pageNumber = 1;
    boolean isLastPage = false;
    protected Function3<WatermarkInfo, Integer, Integer, DataWithPageNumber> generator;
    protected AttributeSchema watermarkField;
    IteratorHelper helper = new IteratorHelper();

    int prevPageNumber;
    int nextPageNumber;
    
    public DefaultDataPageNumberIterator(WatermarkInfo baseWatermark, int pageNumber, Function3<WatermarkInfo, Integer, Integer, DataWithPageNumber> generator, 
            List<EntityData> data, AttributeSchema watermarkField, int pageSize, int  maxRecords) {
        this.baseWatermark = baseWatermark;
		this.pageNumber = pageNumber;
		this.generator = generator;
		this.data = data;
		this.watermarkField = watermarkField;
        this.pageSize = pageSize;
		this.maxRecords = maxRecords;
		this.nextPageNumber = pageNumber;
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
        DataWithPageNumber results = generator.apply(baseWatermark, getEffectivePageSize(), nextPageNumber);
        long done = System.currentTimeMillis();

        Stream<EntityData> entityDataStream = results.getData().stream();
        // if a synapse doesn't give sorted results - remove records beyond wm window for all batches
        entityDataStream = entityDataStream.filter(e -> getWatermarkValue(e) <= baseWatermark.getEnd()
                || (baseWatermark.isInitial() && !baseWatermark.hasEnd()));
        int recordsToConsume = (int)(maxRecords > 0 ? maxRecords - totalRecordsFetched : pageSize);
        data = entityDataStream.limit(recordsToConsume).collect(Collectors.toList());
        stats.addLatencyCount((done-now),data.size());
        nextPageNumber = nextPageNumber(results.getNextPageNumber(), data);
        prevPageNumber = results.getPrevPageNumber();
        isLastPage = isLastPage();
        if (data.isEmpty()) {
            log.info("Iterator has been drained. datasize is 0. nextPageNumber/isLastPage/pageSize: {}/{}/{} ", nextPageNumber, isLastPage, pageSize);
        }
        return data.size() > 0;
    }

    protected boolean isLastPage() {
        return data.size() < getEffectivePageSize();
    }

    private boolean isConsumed() {
        return data.isEmpty();
    }

    protected int nextPageNumber(int nextPageNumber, List<EntityData> data) {
        // if no data is retrieved meaning the window is exhausted - reset the offset
        if(data.isEmpty() || isLastPage()) return 0;
        return nextPageNumber;
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


}