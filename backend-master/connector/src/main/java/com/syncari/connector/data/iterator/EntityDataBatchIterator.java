package com.syncari.connector.data.iterator;

import java.util.Iterator;
import java.util.List;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.Stats;
import com.syncari.connector.data.iterator.Offset.OffsetType;

public interface EntityDataBatchIterator extends Iterator<List<EntityData>> {

    public static final int MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE = 2000;

    long getLastWatermark();

	default long getLastOffset(){
		return 0L;
	}

    default String getChangeStream(){
		return "";
	}

    default Offset getOffsetInfo() { return new Offset(OffsetType.NONE, 0); }
    default void customOffsetReset(int resetRecordCount) {}

    default int getMaxRecordsPerEntitySyncCycle() { return MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE; }

	Stats getStats();

    default long applyPrune(int prunedSize) {
        Offset offsetInfo = getOffsetInfo();
        long offset = getLastOffset();
        // Since the data was underconsumed, we have to reset the offset and change streams accordingly to avoid any data loss.
        if (offsetInfo.getType() == Offset.OffsetType.CUSTOM) {
            // For custom offset types, we let the synapses reset the offsets.
            customOffsetReset(prunedSize);
        } else {
            offset = Offset.recomputeOffset(offsetInfo, offset, prunedSize);
        }
        return offset;
    }

    default boolean fetchByLastBatchWatermark() {
        return false;
    }
}
