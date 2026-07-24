package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.core.Features;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.StagedBatch;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.model.StagedExternalRecord;
import com.syncari.core.model.misc.SyncLog;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.repositories.customer.StagedExternalRecordRepo;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.IdMappingService;
import com.syncari.utils.Pair;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Data
@Accessors(chain = true)
/**
 * A Class that represents the current batch of a syncari entity that is staged
 * in a temporary durable storage area It is like a cursor that can be used to
 * fetch all end system entities mapped to the specificied syncari entity in
 * batches The cursors can be reset. Staged records can be read in pages either
 * across all mapped systems, or for a single mapped system THis is primarily
 * the source for downstream processing pipelines
 */
public class CurrentBatch {
	protected String syncariEntityName;
	protected EntityDefinition syncariEntity;
	protected String currentBatchId;
	protected boolean isSuccess;
	protected Map<EntityDefinition, StagedBatch> entityBatches = new HashMap<>();

	protected Map<EntityDefinition, Watermark> currentWatermark = new HashMap<>();

	protected Map<String, EntityDefinition> stagedBatchIdEntityDefinitionMap = new HashMap<>();
	protected List<String> errors = new ArrayList<>();

	protected Map<EntityDefinition, StagedBatch> connectedEntityBatches = new HashMap<>();
	protected StagedBatchRecordRepo recordRepo;
	protected StagedExternalRecordRepo externalRecordRepo;
	protected StagedBatchRepo stagedBatchRepo;
	private IdMappingService idMappingService;
	private EntityRepoService entityRepoService;
	private FeatureService featureService;
	protected int pageSize = 500;

	protected List<SyncLog> syncLogs = new ArrayList<>();

	private Map<Pair<String,String>, String> externalRecordToSyncariIdMap = new HashMap<>();

	public CurrentBatch(StagedBatchRecordRepo recordRepo) {
		this(recordRepo,null, null, null, null, null);
	}

	public CurrentBatch(StagedBatchRecordRepo recordRepo, StagedBatchRepo stagedBatchRepo, IdMappingService idMappingService,EntityRepoService entityRepoService,
						FeatureService featureService, StagedExternalRecordRepo externalRecordRepo) {
		this.recordRepo = recordRepo;
		this.stagedBatchRepo = stagedBatchRepo;
		this.idMappingService = idMappingService;
		this.entityRepoService = entityRepoService;
		this.featureService = featureService;
		this.externalRecordRepo = externalRecordRepo;
	}

	/**
	 * Set a connector batch. If this object was already in use, and
	 * fetch(conenctorName) was called, the pagination details will be reset and the
	 * cursor will start at the beginning
	 *
	 * @param entityDefinition
	 * @param stagedBatch   - the batch details for the connected system
	 * @return
	 */
	public CurrentBatch setEntityBatch(EntityDefinition entityDefinition, StagedBatch stagedBatch) {
		entityBatches.put(entityDefinition, stagedBatch);
		stagedBatchIdEntityDefinitionMap.put(stagedBatch.getId(), entityDefinition);
		return this;
	}

	public StagedBatch getEntityBatch(EntityDefinition entityDefinition) {
		return entityBatches.get(entityDefinition);
	}

	public void setCurrentWatermark(EntityDefinition entityDefinition, Watermark watermark) {
		currentWatermark.put(entityDefinition, watermark);
	}

	public void removeCurrentWatermark(EntityDefinition entityDefinition) {
		if (currentWatermark.containsKey(entityDefinition)) {
			currentWatermark.remove(entityDefinition);
		}
	}

	public String getSyncariId(String externalRecordId,String externalEntityDefId){
		return externalRecordToSyncariIdMap.get(Pair.of(externalRecordId,externalEntityDefId));
	}

	public void setSyncariId(String externalRecordId, String externaEntityDefId, String syncariId){
		externalRecordToSyncariIdMap.put(Pair.of(externalRecordId,externaEntityDefId),syncariId);
	}

	public List<StagedBatchRecord> update(List<StagedBatchRecord> records) {
		return recordRepo.updateMany(records);
	}

	public void delete(List<StagedBatchRecord> records) {
		recordRepo.deleteAll(records);
	}

	public Iterator<List<StagedBatchRecord>> iterator(EntityDefinition entityDefinition) {
		return new RecordIterator(List.of(entityBatches.get(entityDefinition).getId()), recordRepo, pageSize);
	}

	public Iterator<List<StagedBatchRecord>> iterator(StagedBatch stagedBatch) {
		return new RecordIterator(List.of(stagedBatch.getId()), recordRepo, pageSize);
	}

	public Iterator<Page<StagedBatchRecord>> newRecordsIterator() {
		List<String> stagedBatchIds = entityBatches.values().stream().map(StagedBatch::getId)
				.collect(Collectors.toList());
		Sort sort = Sort.by(Sort.Order.asc("_id"));
		Function<Pageable, Page<StagedBatchRecord>> pageGenerator = (Pageable page) -> recordRepo
				.findByStagedBatchIdInAndIsNew(stagedBatchIds, true, page);
		return new StagedRecordIterator(pageGenerator, pageSize, sort);
	}

	public Optional<StagedBatchRecord> findExternalRecord(EntityDefinition externalEntity, String externalId){
		return toStagedBatchRecord(externalRecordRepo.findByExternalRecordIdAndExternalEntityDefinitionId(externalEntity.getId(), externalId));
	}

	public EntityDefinition lookupConnectorIdByBatchId(String stagedBatchId) {
		if(stagedBatchIdEntityDefinitionMap.containsKey(stagedBatchId)) {
			return stagedBatchIdEntityDefinitionMap.get(stagedBatchId);
		}
		return connectedEntityBatches.entrySet().stream().filter(e->e.getValue().getId().equals(stagedBatchId)).map(e->e.getKey()).findFirst().orElse(null);
	}

	public Iterator<RecordsBySyncariId> recordsBySyncariIdIterator() {
		return new RecordsBySyncariIdIterator(this, recordRepo, pageSize,idMappingService, entityRepoService);
	}

    public Iterator<RecordsBySyncariId> recordsBySyncariIdLiveTestIterator() {
        return new RecordsBySyncariIdIterator(this, recordRepo, pageSize, true, idMappingService,entityRepoService);
    }

	public Optional<StagedBatchRecord> toStagedBatchRecord(Optional<StagedExternalRecord> record) {
		return record.isEmpty() ? Optional.empty() : Optional.of(new StagedBatchRecord()
				.setEntityData(record.get().getEntityData()).setExternalRecordId(record.get().getExternalRecordId())
				.setDeleted(record.get().isDeleted()).setExternalEntityDefinitionId(record.get().getExternalEntityDefinitionId()));
	}

	//Expects records from the same connector and entitydefinition
	public void addNewRecords(EntityDefinition externalEntityDefinition, List<EntityData> connectedRecords) {
		if(connectedRecords.isEmpty()){
			return;
		}
		EntityData first = connectedRecords.get(0);
		StagedBatch stagedBatch =connectedEntityBatches.containsKey(externalEntityDefinition) ? connectedEntityBatches.get(externalEntityDefinition) :
				stagedBatchRepo.save(new StagedBatch(syncariEntityName)
						.setConnectorId(first.getConnectorId())
						.setCurrentBatchId(getCurrentBatchId())
						.setSourceEntityName(first.getName())
						.setSourceEntityDefinitionId(externalEntityDefinition.getId()));

		List<StagedBatchRecord> newRecords =connectedRecords.stream().map(c ->
			 new StagedBatchRecord().setStagedBatchId(stagedBatch.getId()).setExternalRecordId(c.getId())
					.setExternalEntityDefinitionId(externalEntityDefinition.getId())
					.setNew(false)
					.setEntityData(c)
					.setSyncariId(c.getSyncariEntityId())
		).collect(Collectors.toList());
		connectedEntityBatches.put(externalEntityDefinition, stagedBatch);
		update(newRecords);

	}
}

class RecordIterator implements Iterator<List<StagedBatchRecord>>{

	private List<String> stagedBatchIds;
	private final StagedBatchRecordRepo repo;
	private final int pageSize;
	private String pageMarker;
	private List<StagedBatchRecord> currentPage;

	public RecordIterator(List<String> stagedBatchIds, StagedBatchRecordRepo repo, int pageSize){
		this.stagedBatchIds = stagedBatchIds;
		this.repo = repo;
		this.pageSize = pageSize;
	}
	@Override
	public boolean hasNext() {
		if(currentPage==null) {
			currentPage = repo.findByStagedBatchIdIn(stagedBatchIds, pageMarker, pageSize);
			pageMarker = currentPage.isEmpty() ? null : currentPage.get(currentPage.size()-1).getId();
		}
		return !currentPage.isEmpty();
	}

	@Override
	public List<StagedBatchRecord> next() {
		var tmp = currentPage;
		currentPage=null;
		return tmp;
	}
}