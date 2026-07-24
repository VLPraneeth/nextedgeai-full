package com.syncari.connector.data.iterator;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.Offset.OffsetType;

import com.syncari.utils.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultCursorBasedIterator extends AbstractEntityDataBatchIterator {
    protected List<EntityData> data = new ArrayList<>();
    protected WatermarkInfo baseWatermark;
    protected long offset = 0;
    boolean isLastPage = false;
    protected Function3<WatermarkInfo, Integer, String, DataWithCursor> generator;
    protected AttributeSchema watermarkField;
    IteratorHelper helper = new IteratorHelper();
    boolean ignorePageSize = false;
    boolean fetchByLastBatchWatermark = false;

    String prevPageURL;
    String nextPageURL;
    // Cursor based pagination. Previous pages list is useful here to rewind in case of framework prune logic.
    List<Pair<String, Integer>> prevPagesSizes = new ArrayList<>();
    //List<String> prevPageURLs = new ArrayList<>();

    public DefaultCursorBasedIterator(WatermarkInfo baseWatermark, String nextPageUrl, long offset,
                                      Function3<WatermarkInfo, Integer, String, DataWithCursor> generator,
                                      List<EntityData> data, int pageSize, int maxRecords) {

        this(baseWatermark, nextPageUrl, offset, generator, data, pageSize, maxRecords, false);
    }

    public  DefaultCursorBasedIterator(WatermarkInfo baseWatermark, String nextPageUrl, long offset,
            Function3<WatermarkInfo, Integer, String, DataWithCursor> generator, 
            List<EntityData> data, int pageSize, int maxRecords, boolean ignorePageSize) {
        this.nextPageURL = nextPageUrl;
        this.baseWatermark = baseWatermark;
		this.offset = offset;
		this.generator = generator;
		this.data = data;
        this.pageSize = pageSize;
		this.maxRecords = maxRecords;
		this.ignorePageSize = ignorePageSize;
    }

    public  DefaultCursorBasedIterator(WatermarkInfo baseWatermark, String nextPageUrl, long offset,
                                       Function3<WatermarkInfo, Integer, String, DataWithCursor> generator,
                                       List<EntityData> data, int pageSize, int maxRecords, boolean ignorePageSize,
                                       boolean fetchByLastBatchWatermark) {
        this.nextPageURL = nextPageUrl;
        this.baseWatermark = baseWatermark;
        this.offset = offset;
        this.generator = generator;
        this.data = data;
        this.pageSize = pageSize;
        this.maxRecords = maxRecords;
        this.ignorePageSize = ignorePageSize;
        this.fetchByLastBatchWatermark = fetchByLastBatchWatermark;
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
        var results = generator.apply(baseWatermark, getEffectivePageSize(), nextPageURL);
        long done = System.currentTimeMillis();

        Stream<EntityData> entityDataStream = results.getData().stream();
        int returnedPageSize = ignorePageSize ? results.getData().size() : pageSize;
        if (StringUtils.isEmpty(results.getNextPageURL())) {
            log.info("Processing lastpage. nextPageURL/baseWatermark: {}/{}", nextPageURL, baseWatermark);
        }
        // if a synapse doesn't give sorted results - remove records beyond wm window for all batches
        entityDataStream = entityDataStream.filter(e -> getWatermarkValue(e) <= baseWatermark.getEnd()
                || (baseWatermark.isInitial() && !baseWatermark.hasEnd()));

        int recordsToConsume =  (int)(maxRecords > 0 ? maxRecords - totalRecordsFetched : returnedPageSize);

        data = entityDataStream.limit(recordsToConsume).collect(Collectors.toList());
        stats.addLatencyCount((done-now),data.size());
        nextPageURL = results.getNextPageURL();
        prevPageURL = results.getPrevPageURL();
        prevPagesSizes.add(Pair.of(prevPageURL, data.size()));
        isLastPage = isLastPage();
        if (data.isEmpty()) {
            log.info("Iterator has been drained. datasize is 0. nextPageURL/isLastPage/pageSize: {}/{}/{} ", nextPageURL, isLastPage, pageSize);
        }
        return !isLastPage || data.size() > 0;
    }

    // if no page size passed use empty next page as proxy for last page - Why should we not do this more generally?
    protected boolean isLastPage() {
        return ignorePageSize ? StringUtils.isEmpty(nextPageURL)  : data.size() < getEffectivePageSize();
    }

    private boolean isConsumed() {
        return data.isEmpty();
    }

    @Override
    public String getChangeStream() {
        return nextPageURL;
    }

    @Override
    public void customOffsetReset(int resetRecordCount) {

        if (resetRecordCount == 0) {
            return;
        }
        int rewindIndex = 0;
        for (rewindIndex=prevPagesSizes.size() -1; resetRecordCount > 0 && rewindIndex > -1 ; rewindIndex--) {
            resetRecordCount -= prevPagesSizes.get(rewindIndex).y;
        }
        nextPageURL = prevPagesSizes.get(rewindIndex + 1).x;
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
        return new Offset(OffsetType.CUSTOM, pageSize);
    }

    @Override
    public boolean fetchByLastBatchWatermark() {
        return fetchByLastBatchWatermark;
    }
}
