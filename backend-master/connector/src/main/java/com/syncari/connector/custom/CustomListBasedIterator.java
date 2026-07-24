package com.syncari.connector.custom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.iterator.AbstractEntityDataBatchIterator;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.data.iterator.Offset.OffsetType;

import org.jooq.lambda.function.Function1;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomListBasedIterator extends AbstractEntityDataBatchIterator implements EntityDataBatchIterator {

    private ReadResponse readResponse;
    private int pageSize;
    private boolean isLastPage;
    Function1<WatermarkInfo, ReadResponse> generator;


    public CustomListBasedIterator(WatermarkInfo watermark, Function1<WatermarkInfo, ReadResponse> generator, 
            ReadResponse readResponse, int pageSize) {
        this.generator = generator;
        this.readResponse = readResponse;
        this.maxRecords = pageSize;
    }

    @Override
    public long getLastOffset() {
        return readResponse.getWatermarkInfo().getOffset();
    }

    @Override
    public String getChangeStream() {
        return readResponse.getWatermarkInfo().getChangeStream();
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
        if (!isConsumed()) {
            return true;
        }

        var results = generator.apply(readResponse.getWatermarkInfo());
        readResponse = results;
        
        isLastPage = isLastPage();
        // TODO: solidify this for other type of offsets.
        return readResponse.getData().size() > 0;
    }

    private boolean isConsumed() {
        return readResponse.getData().isEmpty();
    }

    protected boolean isLastPage() {
        // TODO: Is this true for all offset types?
        if (List.of(OffsetType.PAGE_NUMBER, OffsetType.RECORD_COUNT).contains(readResponse.getOffsetType())) {
            // Indicate that the pagination is done.
            if (getLastOffset() == 0) {
                return true;
            }
        }
        return readResponse.getData().size() < getEffectivePageSize();
    }

    @Override
    public List<EntityData> next() {
        // reset data to mark it as consumed
        var temp = readResponse.getData();
        if (!readResponse.getData().isEmpty()) {
            EntityData entityData = readResponse.getData().get(readResponse.getData().size() - 1);
            lastWatermark = getWatermarkValue(entityData);
            totalRecordsFetched+=readResponse.getData().size();
        }
        readResponse.setData(new ArrayList<>());

        return temp;
    }

    protected long getWatermarkValue(EntityData entityData) {
        return entityData.getLastModified();
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(readResponse.getOffsetType(), pageSize);
    }

    @Override
    public long getLastWatermark() {
        return readResponse.getData().stream().max(Comparator.comparingLong(EntityData::getLastModified))
            .map(e -> e.getLastModified()).orElse(readResponse.getWatermarkInfo().getStart());
    }

    @Override
    public Stats getStats() {
        return new Stats();
    }
    
}
