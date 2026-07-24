package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SlackIterator extends AbstractEntityDataBatchIterator {
    protected List<EntityData> data = new ArrayList<>();
    protected Function2<Integer, String, DataWithCursor> generator;
    protected int pageSize;
    protected boolean isLastPage = false;
    protected int maxRecords;
    boolean ignorePageSize = false;

    String prevPageURL;
    String nextPageURL;

    List<Pair<String, Integer>> prevPagesSizes = new ArrayList<>();

    public SlackIterator(Function2<Integer, String, DataWithCursor> generator, int pageSize, int maxRecords, String nextPageURL) {
        this.generator = generator;
        this.pageSize = pageSize;
        this.nextPageURL = nextPageURL;
        this.maxRecords = maxRecords;
    }

    public SlackIterator(boolean isLastPage) {
        this.isLastPage = true;
    }

    @Override
    public boolean hasNext() {
        if(isLastPage && isConsumed() || hasFetchedMaxRecords()) {
            return false;
        }
        if (!isConsumed()) return true;
        long now = System.currentTimeMillis();
        var results = generator.apply(getEffectivePageSize(), nextPageURL);
        long done = System.currentTimeMillis();

        Stream<EntityData> entityDataStream = results.getData().stream();
        int returnedPageSize = ignorePageSize ? results.getData().size() : pageSize;
        if (StringUtils.isEmpty(results.getNextPageURL())) {
            log.debug("Processing lastpage. nextPageURL: {}", nextPageURL);
        }

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
        return data.size() > 0;
    }

    private boolean isLastPage() {
        return ignorePageSize ? StringUtils.isEmpty(nextPageURL)  : data.size() < getEffectivePageSize();
    }

    private boolean isConsumed() {
        return data.isEmpty();
    }

    @Override
    public List<EntityData> next() {
        // reset data to mark it as consumed
        var temp = data;
        if (!data.isEmpty()) {
            totalRecordsFetched+=data.size();
        }
        data = new ArrayList<>();

        return temp;
    }
}
