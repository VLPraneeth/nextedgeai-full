package com.syncari.connector;

import com.syncari.connector.data.Stats;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ListBasedIterator implements EntityDataBatchIterator {
    protected List<EntityData> filteredRecords;
    protected boolean consumed = false;
    protected WatermarkInfo watermark;

    public ListBasedIterator(List<EntityData> records, WatermarkInfo watermark) {
        this.watermark = watermark;
        filterRecords(records, watermark);
    }

    protected void filterRecords(List<EntityData> records, WatermarkInfo watermark) {
        filteredRecords = records.stream().filter(r -> r.getLastModified() >= watermark.getStart() && (!!watermark.hasEnd() || r.getLastModified() < watermark.getEnd()))
                .collect(Collectors.toList());
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
