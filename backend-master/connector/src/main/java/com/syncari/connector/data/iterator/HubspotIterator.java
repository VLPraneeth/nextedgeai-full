package com.syncari.connector.data.iterator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.function.Function3;

import com.syncari.connector.EntityData;
import com.syncari.connector.EntityPage;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HubspotIterator extends AbstractEntityDataBatchIterator {
    protected List<EntityData> data = new ArrayList<>();
    protected boolean hasMore = true;
    protected WatermarkInfo baseWatermark;
    protected long offset = 0;
    protected Function3<WatermarkInfo, Integer, Long, EntityPage> generator;
    protected AttributeSchema watermarkField;
    private Stats stats = new Stats();
    IteratorHelper helper = new IteratorHelper();

    public HubspotIterator(WatermarkInfo baseWatermark, long offset,
            Function3<WatermarkInfo, Integer, Long, EntityPage> generator, List<EntityData> data,
            AttributeSchema watermarkField,int maxRecords) {
        this.baseWatermark = baseWatermark;
        this.offset = offset;
        this.generator = generator;
        this.data = data;
        this.watermarkField = watermarkField;
        this.maxRecords = maxRecords;
        this.pageSize = 100;
    }

    public HubspotIterator(WatermarkInfo baseWatermark, long offset,
                           Function3<WatermarkInfo, Integer, Long, EntityPage> generator, List<EntityData> data,
                           AttributeSchema watermarkField,int maxRecords, int pageSize) {
        this.baseWatermark = baseWatermark;
        this.offset = offset;
        this.generator = generator;
        this.data = data;
        this.watermarkField = watermarkField;
        this.maxRecords = maxRecords;
        this.pageSize = pageSize;
    }

    protected EntityPage generate(
            Function3<WatermarkInfo, Integer, Long, EntityPage> generator) {
        return generator.apply(baseWatermark, getEffectivePageSize(), offset);
    }

    public boolean hasNext() {
        if (!hasMore && isConsumed() || hasFetchedMaxRecords()) {
            return false;
        }
        if(!data.isEmpty()) {
            return true;
        }
        long now = System.currentTimeMillis();
        var results = generate(generator);
        long done = System.currentTimeMillis();

        data = results.getData();
        Stream<EntityData> entityDataStream = results.getData().stream();
//        if (results.x < PAGE_SIZE) {
//            entityDataStream = entityDataStream.filter(e -> getWatermarkValue(e) <= baseWatermark.getEnd()
//                    || (baseWatermark.isInitial() && !baseWatermark.hasEnd()));
//        }
        // set last modified, and also allow more specific implementations to filter data
        //Used for incremental contac sync, where data is returned in reverse-chronological order
        // All records whose lastModifiedDate is less than the base watermark start need to be filtered for that use case
        //See HubspotService.getContactByWatermark()
        data = transformAndFilterDataStream(entityDataStream).collect(Collectors.toList());
        stats.addLatencyCount((done-now),data.size());
        offset = results.getOffset();
        hasMore =hasMore(results);
        return data.size() > 0;
    }
    //protected method to allow subclasses to filter & transform as needed
    protected Stream<EntityData> transformAndFilterDataStream(Stream<EntityData> entityDataStream) {
        return entityDataStream;
    }
    //protected method to allow subclasses to manipulate hasMore flag for more complex cases
    protected boolean hasMore(EntityPage results) {
        return results.isHasMore();
    }

    private boolean isConsumed() {
        return data.isEmpty();
    }

    public List<EntityData> next() {
        // reset data to mark it as consumed
        totalRecordsFetched+=data.size();
        var temp = data;
        if (!data.isEmpty()) {
            EntityData entityData = data.get(data.size() - 1);
            lastWatermark = Math.max(lastWatermark,entityData.getLastModified());
        }
        data = new ArrayList<>();

        return temp;
    }

    /**
     * Last watermark is updated ONLY after consuming next() record
     */
    public long getLastWatermark() {
        return lastWatermark;
    }

    public Stats getStats() {
        return stats;
    }

    @Override
    public long getLastOffset() {
        return offset;
    }

}
