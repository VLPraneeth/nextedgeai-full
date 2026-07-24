package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.EntityPage;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.WatermarkInfo;
import org.jooq.lambda.function.Function3;

import java.util.List;
import java.util.stream.Stream;

public class HubspotContactIncrementalIterator extends HubspotIterator {

    public HubspotContactIncrementalIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long, EntityPage> generator, List<EntityData> data, AttributeSchema watermarkField, int maxRecords) {
        super(baseWatermark, offset, generator, data, watermarkField, maxRecords);
    }

    @Override
    protected Stream<EntityData> transformAndFilterDataStream(Stream<EntityData> entityDataStream) {
        //discard all records that are older than the current cycle's start watermark
        return entityDataStream.filter(entity -> entity.getLastModified() >= baseWatermark.getStart());
    }

    @Override
    protected boolean hasMore(EntityPage results) {
        //Contacts are returned in reverse chronology. Stop when we are past the start of baseWatermark
        //or we have exhausted all results
        return results.isHasMore() && baseWatermark.getStart() > results.getOffset();
    }
}
