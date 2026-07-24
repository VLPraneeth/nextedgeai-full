package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Returns at most 2k records per synapse and runs a single page.
 */
@Slf4j
public class EntityPageIterator {
    private Map<EntityFetchResult, EntityDataBatchIterator> iterators = new LinkedHashMap<>();
    private Map<EntityFetchResult, Long> duplicateWatermarks = new LinkedHashMap<>();
    private boolean isHistoricSync;

    public Optional<EntityDataBatchIterator> getIterator(EntityFetchResult result){
        return Optional.ofNullable(iterators.get(result));
    }
    public EntityPageIterator(List<EntityFetchResult> results, boolean isHistoricSync) {
        this.isHistoricSync = isHistoricSync;
        results.forEach(result -> iterators.put(result, result.getResponse().getIterator()));
    }

    public boolean hasNext() {

        return iterators.keySet().stream().anyMatch(iter -> {
            try {
                return iterators.get(iter).hasNext();
            } catch (Exception e) {
                throw new PipelineException(e).setExternalEntityDefinitionId(iter.getEntityDefinition().getId());
            }
        });
    }

    public List<EntityPage> next() {
        List<EntityPage> entityPages = fetchNextPage();
        List<EntityPage> results = entityPages;
        if(!isHistoricSync) {
            Optional<EntityPage> minWatermarkPage = findMinWatermarkPage(entityPages);
            results = minWatermarkPage.map(target ->
                    entityPages.stream().filter(page -> !isNoWatermarkEntity(page)).map(page ->  prune(page, target.getWatermark())).collect(Collectors.toList())
            ).orElse(entityPages);
        }
        results.forEach(page -> {
            //Remove sources that dont have potential duplicates over next pages
            if (page.getWatermark() != duplicateWatermarks.getOrDefault(page.getResult(),Long.MAX_VALUE) &&  !page.hasJobs()){
                log.info("Finished reading from source {}({}). Page watermark: {} and duplicate watermark: {}",
                        page.getResult().getSchema().getApiName(), page.getResult().getConnector().getName(),
                        page.getWatermark(), duplicateWatermarks.getOrDefault(page.getResult(),Long.MAX_VALUE));
                iterators.remove(page.getResult());
            }
        });
        return results;

    }

    private Optional<EntityPage> findMinWatermarkPage(List<EntityPage> entityPages) {
        return entityPages.stream().filter(p -> !p.hasJobs() && p.size() >= p.getMaxRecordsPerEntitySyncCycle()-1 && !isNoWatermarkEntity(p))
            .min(Comparator.comparingLong(p -> p.getWatermark()));
    }

    private static boolean isNoWatermarkEntity(EntityPage p) {
        return p.getResult() != null && p.getResult().getSchema() != null && p.getResult().getSchema().hasWatermarkField() && p.getResult().getSchema().getWatermarkField().isSyncariDefined();
    }

    private List<EntityPage> fetchNextPage() {
        //TODO: Handle records with same watermarks crossing over pages
        List<EntityPage> currentPages = new ArrayList<>();
        iterators.forEach((schema, iterator) -> {
            try {
                Timer timer = new Timer("EntityPageIterator::fetchNextPage");
                String idFieldName = schema.getSchema().hasIdField() ? schema.getSchema().getIdField().getApiName() :"Id";
                List<EntityData> records = new ArrayList<>(1000);
                long latestWatermark = -1l;
                long targetWatermark = duplicateWatermarks.getOrDefault(schema,Long.MAX_VALUE);
                int maxRecordsPerEntitySyncCycle = (iterator.getMaxRecordsPerEntitySyncCycle() == 0) ?
                        EntityDataBatchIterator.MAX_RECORDS_PER_ENTITY_PER_SYNC_CYCLE : iterator.getMaxRecordsPerEntitySyncCycle();
                log.debug("MaxRecordsPerEntitySyncCycle: {}, Iterator: {}", maxRecordsPerEntitySyncCycle, iterator);
                while (records.size() < maxRecordsPerEntitySyncCycle && iterator.hasNext()) {
                    List<EntityData> next = iterator.next();
                    //set the id value inside values for all synapses
                    List<EntityData> matchingRecords = next.stream().map(r -> !r.has(idFieldName) ? r.addValue(idFieldName, r.getId()) : r)
                            //we consume out of order records as well.
                            .takeWhile(record -> record.isOutlierTimestamp() || record.getLastModified() <= targetWatermark)
                            .collect(Collectors.toList());
                    records.addAll(matchingRecords);
                    if(matchingRecords.size() != next.size()){
                        duplicateWatermarks.remove(schema);
                    }
                    log.info("Found iterator page for {} with total records {} and matching records {} with target watermark {}, iterator watermark {}",
                            schema.getSchema().getApiName(),next.size(),matchingRecords.size(),targetWatermark,iterator.getLastWatermark());
                    latestWatermark = Math.max(latestWatermark,records.isEmpty()?latestWatermark : getLastWatermark(records));
                    if(matchingRecords.size() != next.size()){
                        break;
                    }
                }
                //no records found while looking for same lastupdated values
                if(latestWatermark == -1 && targetWatermark != Long.MAX_VALUE){
                    latestWatermark = targetWatermark;
                }

                boolean potentialRepeatedWatermarks = hasPotentialRepeatedWatermarks(records);
                if(records.size() < maxRecordsPerEntitySyncCycle && targetWatermark == Long.MAX_VALUE){
                    //update watermark to current, if we hhave lesss than a page worth or records, AND we weren't simply
                    //consuming records with duplicate WM. in that case, the iterator's watermark takes precedence, because we haven't really exhausted
                    //the iterator
                    latestWatermark =  Math.max(latestWatermark, schema.getRequest().getWatermark().getEnd());
                    log.info("Updating latest watermark to {} as we got number of records {}", latestWatermark, records.size());
                }
                Long totalTimeTaken = schema.getResponse().getTimeTaken() + timer.getTimeTakenUntilNow();
                if(schema.getWatermark() != null && schema.getWatermark().getPruneState() != null) {
                    schema.getWatermark().getPruneState().setPruned(false);
                }
                EntityPage page = new EntityPage(iterator, schema, records, latestWatermark, iterator.getLastOffset(), iterator.getChangeStream(),totalTimeTaken);
                log.debug("Found page for {}({}) with records {} and latest watermark {}",schema.getSchema().getApiName(), schema.getSchema().getId(), page.size(), latestWatermark);
                //Entities with jobs must exhaust all records every time
                //Otherwise, we've reached the max records and almost done with the results
                if(page.isCompleted() && iterator.getOffsetInfo().getType() == OffsetType.NONE){
                    if(potentialRepeatedWatermarks){
                        log.info("Found duplicate watermarks for the connector/schema {}/{}, setting the latestWatermark as: {}",
                            schema.getConnector().getName(), schema.getSchema().getApiName(), latestWatermark);
                        duplicateWatermarks.put(schema, latestWatermark);
                    }else{
                        //remove the last record to remove ambiguity on if there is a next page with same watermarks
                        page.removeLastRecord().recomputeWatermark();
                        log.info("Recomputed page for {} with records {} and latest watermark {}",schema.getSchema().getApiName(),page.size(),latestWatermark);
                    }
                }
                timer.close();
                currentPages.add(page);
            } catch (Exception e) {
                throw new PipelineException(e).setExternalEntityDefinitionId(schema.getEntityDefinition().getId());
            }
        });
        return currentPages;
    }

    protected long getLastWatermark(List<EntityData> records) {
        return EntityPage.getLastValidWMRecord(records)
                //exclude deleted records
                .map(EntityData::getLastModified)
                .orElse(-1L);
    }

    protected boolean hasPotentialRepeatedWatermarks(List<EntityData> records) {
        if(records.size()< 2) return false;
        //if the last two records have the same watermark, the next page potentially has records
        //with same watermarks

        return getLastWatermark(records) == getNonOutlierFromLast(records, 1).getLastModified();
    }

    protected EntityData getNonOutlierFromLast(List<EntityData> records, int reverseIndex) {
        int nonOutlinerCount = -1;
        int recordIndex = records.size();
        while (nonOutlinerCount < reverseIndex && --recordIndex >= 0) {
            if (!records.get(recordIndex).isOutlierTimestamp()) {
                nonOutlinerCount++;
            }
        }
        return records.get(recordIndex);
    }

    private EntityPage prune(EntityPage value, long targetWatermark) {
        if (needsPruning(value, targetWatermark)) {
            return value.prune(targetWatermark);
        }
        return value;
    }

    private boolean needsPruning(EntityPage value, long targetWatermark) {
        //Don't prune if less than max records
        //Don't prune job based sources because these sources do not understand watermarks and always return back the entire response
        //Don't prune for historic syncs either, because pages doesn't need to be in lock step
        //DOn't prune pages that are already below the target watermark
        return  !value.hasJobs() && !isHistoricSync && value.getWatermark() > targetWatermark;
    }
}
