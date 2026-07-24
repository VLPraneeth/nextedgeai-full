package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;

import java.util.Comparator;
import java.util.List;

public class NetsuiteListBasedIterator implements EntityDataBatchIterator  {
    List<EntityData> filteredRecords;
    boolean consumed = false;
    private WatermarkInfo watermark;

    public NetsuiteListBasedIterator(List<EntityData> records, WatermarkInfo watermark) {
        this.watermark = watermark;
        filteredRecords = records;
    }

    @Override
    public long getLastWatermark() {
        return filteredRecords.stream().max(Comparator.comparingLong(EntityData::getLastModified)).map(e -> e.getLastModified()).orElse(watermark.getStart());
    }

    @Override
    public Stats getStats() {
        return new Stats();
    }

    @Override
    public boolean hasNext() {
        return filteredRecords.size() > 0 && !consumed;
    }

    @Override
    public List<EntityData> next() {
        consumed = true;
        return filteredRecords;
    }
}
