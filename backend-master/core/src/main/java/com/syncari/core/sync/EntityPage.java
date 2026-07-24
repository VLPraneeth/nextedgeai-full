package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.core.model.misc.PruneState;
import com.syncari.utils.Timer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.util.*;

@AllArgsConstructor
@Getter
@Slf4j
public class EntityPage {
    private EntityDataBatchIterator iterator;
    private EntityFetchResult result;
    private List<EntityData> records;
    private long watermark;
    private long offset;
    private String changeStream;
    private Long pageTimeTaken = 0l;

    public EntityPage setWatermark(long watermark){
        this.watermark = watermark;
        return this;
    }

    public int getMaxRecordsPerEntitySyncCycle() {
        // Cap the max records per sync cycle to the maximum possible. Anything more than that overriden in iterators are useless, since
        // the other iterators would prune those anyways. 
        // Note, the first condition below is for unit tests that mock the iterator.
        return (iterator.getMaxRecordsPerEntitySyncCycle() == 0 ||
                iterator.getMaxRecordsPerEntitySyncCycle() > EntityDataBatchIterator.MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE)
            ? EntityDataBatchIterator.MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE : iterator.getMaxRecordsPerEntitySyncCycle();
    }

    public EntityPage prune(long targetWatermark) {
        Timer timer = new Timer("EntityPage::prune");
        int offsetInPage = 0;
        List<EntityData> recordsNotPruned = new ArrayList<>();
        Map<String, Long> discardedById = new HashMap<>();
        final List<EntityData> currentPage = getRecords();
        for (int i = 0; i < currentPage.size(); i++) {
            final EntityData record = currentPage.get(i);
            if (record.getLastModified() <= targetWatermark) {
                offsetInPage++;
                recordsNotPruned.add(record);
            } else if (record.isOutlierTimestamp()) {
                recordsNotPruned.add(record);
                if (discardedById.isEmpty()) {
                    offsetInPage++;
                }
            } else {
                discardedById.put(record.getId(), record.getLastModified());
            }
        }
        long offset = iterator.getLastOffset();
        String changeStream = iterator.getChangeStream();
        if (MapUtils.isNotEmpty(discardedById)) {
            log.info("Pruned entityDatas beyond watermark {} ", targetWatermark);
            log.info("Pruned entitydatas: {} ", discardedById);
            int prunedSizeExcludingOutliers = records.size() - offsetInPage;
            int prunedSize = records.size() - recordsNotPruned.size();
            log.info("Pruned total: {}, Offset In Page, excluding outliers {}", prunedSize, offsetInPage);
            Offset offsetInfo = iterator.getOffsetInfo();
            // Since the data was underconsumed, we have to reset the offset and change streams accordingly to avoid any data loss.
            offset = iterator.applyPrune(prunedSizeExcludingOutliers);
            // TODO, we need to move this logic completely to the iterator.
            if (offsetInfo.getType() == Offset.OffsetType.CUSTOM) {
                changeStream = iterator.getChangeStream();
            }

            PruneState pruneState = new PruneState();
            if(discardedById.size() > 0) {
                pruneState.setPruned(true);
                pruneState.setTimestamp(targetWatermark);
                if(recordsNotPruned.size() > 0) {
                    final Optional<EntityData> lastNonOutlier = getLastValidWMRecord(recordsNotPruned);
                    lastNonOutlier.ifPresent(pruneState::setLastRecordNotPruned);
                    pruneState.setOffset(offsetInPage);
                }
            }
            result.getWatermark().setPruneState(pruneState);
        }
        Long timeTaken = timer.getTimeTakenUntilNow();
        timer.close();
        return new EntityPage(iterator, result, recordsNotPruned, targetWatermark, offset, changeStream,timeTaken);
    }

    public static Optional<EntityData> getLastValidWMRecord(List<EntityData> recordsNotPruned) {
        Optional<EntityData> latestNonoutlier = Optional.empty();
        for (int i = recordsNotPruned.size() - 1; i >= 0; i--) {
            final EntityData record = recordsNotPruned.get(i);
            //Deleted records must
            if (!record.isOutlierTimestamp() && !record.isDeleted()) {
                return Optional.of(record);
            }

            if (latestNonoutlier.isEmpty() && !record.isOutlierTimestamp()) {
                latestNonoutlier = Optional.of(record);
            }
        }

        return recordsNotPruned.stream().allMatch(EntityData::isDeleted) ? latestNonoutlier : Optional.empty();
    }

    public boolean hasJobs() {
        return !result.getRequest().getBatchJobs().isEmpty() || !result.getResponse().getBatchJobs().isEmpty();
    }
    public boolean isCompleted(){
        return (records.size() >= getMaxRecordsPerEntitySyncCycle() && !hasJobs()) || records.isEmpty() ;
    }
    public int size() {
        return records == null ? 0 : records.size();
    }

    public void setRecords(List<EntityData> records) {
        this.records = records;
    }

    public EntityPage removeLastRecord(){
        // if no records or if its offset based retrieval don't remove any records
        if(!records.isEmpty() && offset == 0) {
            records = records.subList(0, records.size() - 1);
        }
        return this;
    }

    public EntityPage recomputeWatermark(){
        if(!records.isEmpty()) {
            watermark = getLastModified();
        }
        return this;
    }

    public long getLastModified() {
        return getLastValidWMRecord(records)
                .map(EntityData::getLastModified)
                .orElse(-1L);
    }

}
