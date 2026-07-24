package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.StagedBatch;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.IdMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.stream.Collectors;
@Slf4j
public class RecordsBySyncariIdIterator implements Iterator<RecordsBySyncariId> {


    private final IdMappingService idMappingService;
    private Page<StagedBatchRecord> current;

    private List<String> stagedBatchIds;
    private boolean includeDeleted;
    private EntityRepoService entityRepoService;
    private StagedBatchRecordRepo recordRepo;

    private int pageSize=1000;

    private Pageable page;

    private RecordsBySyncariId currentRecord;
    private CurrentBatch batch;

    private Iterator<StagedBatchRecord> iterator;
    //syncari Id -> idMapping
    private Map<String, IdMapping> idMappings = new HashMap<>();
    //syncariId -> EntityData
    private Map<String, EntityData> existingRecords = new HashMap<>();


    public RecordsBySyncariIdIterator(CurrentBatch batch, StagedBatchRecordRepo recordRepo, int pageSize, IdMappingService idMappingService, EntityRepoService entityRepoService) {
        this(batch, recordRepo, pageSize, false,idMappingService, entityRepoService);
    }

    public RecordsBySyncariIdIterator(CurrentBatch batch, StagedBatchRecordRepo recordRepo, int pageSize, boolean includeDeleted,IdMappingService idMappingService,EntityRepoService entityRepoService) {
        this.batch = batch;
        stagedBatchIds = new ArrayList<>(batch.getEntityBatches().values().stream().map(StagedBatch::getId).collect(Collectors.toList()));
        this.includeDeleted = includeDeleted;
        this.entityRepoService = entityRepoService;
        stagedBatchIds.addAll(batch.getConnectedEntityBatches().values().stream().map(StagedBatch::getId).collect(Collectors.toList()));
        this.recordRepo = recordRepo;
        this.pageSize = pageSize;
        this.idMappingService = idMappingService;

        page = PageRequest.of(0, this.pageSize, Sort.Direction.ASC, "syncariId","externalEntityDefinitionId","externalRecordId");
        long time = System.currentTimeMillis();
        if (!includeDeleted) {
            current = recordRepo.findByStagedBatchIdUndeleted(stagedBatchIds, page);
        } else {
            current = recordRepo.findByStagedBatchIdIn(stagedBatchIds, page);
        }
        loadIdMappings();
        loadSyncariRecords();

        log.info("Took {} ms to find batch of {} stagedBatchRecords",(System.currentTimeMillis() - time),current.getNumberOfElements());
        iterator = current.iterator();
    }

    private void loadIdMappings(){
        idMappings.clear();
        final List<IdMapping> existingIdMappings = idMappingService.findBySyncariIds(batch.getSyncariEntityName(), current.map(r -> r.getSyncariId()).stream().collect(Collectors.toSet()));
        existingIdMappings.forEach(idMapping -> idMappings.put(idMapping.getSyncariId(), idMapping));
    }
    private void loadSyncariRecords(){
        existingRecords.clear();
        final Iterable<EntityData> records = entityRepoService.findRecordsByIds(batch.getSyncariEntity(),  current.map(r -> r.getSyncariId()).stream().collect(Collectors.toSet()));
        records.forEach(r->existingRecords.put(r.getSyncariEntityId(),r));
    }

    private void fetchNextPage() {
        page = page.next();
        long time = System.currentTimeMillis();
        current = includeDeleted ? recordRepo.findByStagedBatchIdIn(stagedBatchIds, page): recordRepo.findByStagedBatchIdUndeleted(stagedBatchIds, page) ;
        loadIdMappings();
        loadSyncariRecords();
        log.debug("Took {} ms to find batch of {} stagedBatchRecords",(System.currentTimeMillis() - time),current.getNumberOfElements());
    }

    @Override
    public boolean hasNext() {
        //need both iterator to be exhausted and last record consumed
        return iterator.hasNext() || currentRecord!=null;
    }

    @Override
    public RecordsBySyncariId next() {

        while (iterator.hasNext()) {
            var record = iterator.next();
            String tempSyncariId = record.getSyncariId();
            IdMapping idMapping = idMappings.get(tempSyncariId);
            EntityData existinngRecord = existingRecords.get(tempSyncariId);
            if (!iterator.hasNext()) {
                //this will mutate the 'current' ref
                fetchNextPage();
                //set new iterator
                iterator = current.iterator();
            }
            if (currentRecord == null) {
                currentRecord = new RecordsBySyncariId(tempSyncariId);
            }
            //a batch may have multiple updates to the same record, and we need to treat them as if they are separate records
            if (currentRecord.getSyncariId().equals(tempSyncariId) && !currentRecord.exists(record)) {
                currentRecord.addRecord(record);
                currentRecord.setIdMapping(idMapping);
                currentRecord.setExistingRecord(existinngRecord);
            } else {
                var temp = currentRecord;
                currentRecord = new RecordsBySyncariId(tempSyncariId);
                currentRecord.addRecord(record);
                currentRecord.setIdMapping(idMapping);
                currentRecord.setExistingRecord(existinngRecord);
                return temp;
            }
        }
        var lastRecorcd = currentRecord;
        currentRecord= null;
        return lastRecorcd;
    }
}
