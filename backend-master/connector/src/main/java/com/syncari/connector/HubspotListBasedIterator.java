package com.syncari.connector;

import com.syncari.connector.data.WatermarkInfo;

import java.util.Comparator;
import java.util.List;

public class HubspotListBasedIterator extends ListBasedIterator{

    String changeStream;
    long lastWatermark;
    List<EntityData> records;
    List<EntityData> entityRecords;

    public HubspotListBasedIterator(List<EntityData> records, List<EntityData> entityRecords, WatermarkInfo watermark, String changeStream) {
        super(records, watermark);
        this.changeStream = changeStream;
        this.records = records;
        this.entityRecords = entityRecords;
    }

    @Override
    public String getChangeStream(){
        return this.changeStream;
    }

    public void setChangeStream(String changeStream) {
        this.changeStream = changeStream;
    }

    @Override
    public long getLastWatermark() {
        return lastWatermark;
    }

    public void setLastWatermark(long lastWatermark) {
        this.lastWatermark = lastWatermark;
    }

    @Override
    public int getMaxRecordsPerEntitySyncCycle() {
        return entityRecords.size() >= 2000 ? records.size() : 2000;
    }
}
