package com.syncari.connector.data.iterator;

import com.syncari.connector.data.Stats;

public abstract class AbstractEntityDataBatchIterator implements EntityDataBatchIterator {
    protected int pageSize = 100;
    protected int maxRecords;
    protected long totalRecordsFetched;
    protected Stats stats = new Stats();
    protected long lastWatermark = -1l;
    protected int maxRecordsPerEntityPerCycle = 0;

    protected boolean hasFetchedMaxRecords() {
        return maxRecords != 0 && totalRecordsFetched >= maxRecords;
    }

    protected int getEffectivePageSize() {
        return maxRecords == 0 ? pageSize : Math.min(pageSize, maxRecords);
    }
    @Override
    /**
     * Last watermark is updated ONLY after consuming next() record
     */
    public long getLastWatermark() {
        return lastWatermark;
    }

    @Override
    public Stats getStats() {
        return stats;
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.NONE, pageSize);
    }

    @Override
    public int getMaxRecordsPerEntitySyncCycle() {
        return maxRecordsPerEntityPerCycle == 0 ? MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE : maxRecordsPerEntityPerCycle;
    }

    public void setMaxRecordsPerEntitySyncCycle(int maxRecordsPerEntityPerCycle) {
        this.maxRecordsPerEntityPerCycle = maxRecordsPerEntityPerCycle;
    }

    @Override
    public String toString() {
        return String.format("Name: %s; offsetInfo: %s; lastWatermark: %d; offset: %d; changeStream: %s", 
            getClass().getCanonicalName(), getOffsetInfo(), getLastWatermark(), getLastOffset(), getChangeStream());
    }

}