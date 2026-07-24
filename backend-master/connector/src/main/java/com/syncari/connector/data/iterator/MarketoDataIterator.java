package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.MarketoEntityPage;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.function.Function2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class MarketoDataIterator implements EntityDataBatchIterator {

    List<EntityData> data = new ArrayList<>();
    WatermarkInfo baseWatermark;
    protected boolean hasMore = true;
    String pageToken;
    private Stats stats = new Stats();
    Function2<WatermarkInfo, String, MarketoEntityPage> generator;
    long lastWatermark = -1l;
    long offset = 0L;
    String changeStream = "";
    int totalRecordsFetched = 0;

    public MarketoDataIterator(WatermarkInfo baseWatermark,
                               Function2<WatermarkInfo, String, MarketoEntityPage> generator) {
        this.baseWatermark = baseWatermark;
        this.generator = generator;
    }

    @Override
    public boolean hasNext() {
        if (!data.isEmpty()) {
            return true;
        }
        // TODO: check if its possible to stop iteration if any of records are beyond watermark end date
        if (!hasMore || totalRecordsFetched >= 2000) {
            return false;
        }

        long now = System.currentTimeMillis();
        // reset lastWm before fetching the next set of records in iterator
        lastWatermark = -1l;
        var results = generator.apply(baseWatermark, pageToken);
        long done = System.currentTimeMillis();

        data = results.getData();
        totalRecordsFetched += data.size();
        hasMore = hasMore(results);
        lastWatermark = hasMore ? results.getOffset() : baseWatermark.getEnd();
        // reset the offset once offset reaches the endTime of watermark
        offset = lastWatermark >= baseWatermark.getEnd() || !hasMore ? 0L : results.getOffset();
        stats.addLatencyCount((done - now), data.size());
        pageToken = results.getNextPage();

        changeStream = pageToken;

        if (data.isEmpty() && hasMore) {
            // Note: Marketo API intermittently give empty result for some requests
            // We need to skip such responses and not terminate the iterator
            return true;
        }
        // Do not filter records for initial sync as endDate is set as -1 when running initial sync.
        if (baseWatermark.hasEnd()) {
            transformFilterAndSortData();
        }
        return !data.isEmpty();
    }

    @Override
    public List<EntityData> next() {
        // reset data to mark it as consumed
        var temp = data;

        // set watermark to last record only if its not set externally
        if (!data.isEmpty() && lastWatermark == -1) {
            EntityData entityData = data.get(data.size() - 1);
            lastWatermark = getWatermarkValue(entityData);
        }

        // check if watermark limit is specified for pipeline test
        if(baseWatermark.getLimit() > 0){
            if(temp.size() >= baseWatermark.getLimit()) {
                temp = temp.stream().limit(baseWatermark.getLimit()).collect(Collectors.toList());
                pageToken = null;
            } else {
                baseWatermark.setLimit(baseWatermark.getLimit() - temp.size());
            }
        }
        data = new ArrayList<>();
        // check lastWatermark of iterator to not go beyond sync endWm - safeGuard
        lastWatermark = Math.min(lastWatermark, baseWatermark.getEnd());
        log.info("MarketoDataIterator -> lastWatermark: {}", lastWatermark);
        if (!temp.isEmpty()) {
            log.info("MarketoDataIterator -> nth record -> lastModifiedTime : {}", temp.get(temp.size() - 1).getLastModified());
        }
        return temp;
    }

    protected long getWatermarkValue(EntityData entityData) {
        return entityData.getLastModified();
    }

    @Override
    public long getLastWatermark() {
        return lastWatermark;
    }

    @Override
    public Stats getStats() {
        return stats;
    }

    public void setPageToken(String pageToken) {
        this.pageToken = pageToken;
    }

    protected boolean hasMore(MarketoEntityPage results) {
        return results.isHasMore();
    }

    private void transformFilterAndSortData() {
        // if createdAt timestamp is within wm don't filter else remove anything beyond watermark's end date
        data = data.stream()
                .filter(entity ->
                        entity.getLastModified() <= baseWatermark.getEnd() ||
                                (entity.getCreatedAt() > baseWatermark.getStart() && entity.getCreatedAt() <= baseWatermark.getEnd()) // this is needed to make sure we always process the new created lead
                ).collect(Collectors.toList());
        // make sure to cap the lastModifiedDate to the endWm to avoid any jumping in the watermark between sync consecutive cycle
        data.forEach(d -> {
            // this will only happen for new records with createdAt within wm window but updatedAt is beyond the endWm so we make lastModified same as createdAt
            if(d.getLastModified() > baseWatermark.getEnd()){
                log.info("Lead with id {}: lastModifiedTime:{}, createdAt:{}, baseWatermark endTime {}", d.getId(), d.getLastModified(), d.getCreatedAt(), baseWatermark.getEnd());
                log.info("Lead with id {} changing lastModifiedTime to {}", d.getId(), baseWatermark.getEnd());
                d.setLastModified(baseWatermark.getEnd());
            }
        });
        // sort the result by lastModified time
        data.sort(Comparator.comparingLong(EntityData::getLastModified));
    }

    @Override
    public Offset getOffsetInfo() {
        // TODO, when we enable this, we need to ensure the pagesize is properly set.
        return new Offset(Offset.OffsetType.NONE, 100);
    }

    @Override
    public String getChangeStream() {
        return changeStream;
    }
}
