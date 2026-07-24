package com.syncari.core.service;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.core.DataTransformer;
import com.syncari.core.model.Connector;
import com.syncari.core.model.DatastoreWatermark;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.SyncDetail;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.repositories.customer.DatastoreWatermarkRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.utils.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WatermarkService {
	public static final String INITIAL_SYNC_START_DATE = "1999-01-01T00:00:00.00Z";
	@Autowired
	SyncDetailRepo syncRepo;
	@Autowired
	DateUtil dateUtil;
	@Autowired
	DataServiceFactory factory;
	@Autowired
	DataTransformer transformer;
	@Autowired
	DatastoreWatermarkRepo dsWatermarkRepo;
	@Autowired
	SchemaService schemaService;
	@Autowired
	MappingGraphService graphService;

	@Autowired
    private MongoTemplate customerMongoTemplate;

	public SyncDetail getOrCreateDownstreamWatermark(String syncariEntityName, EntityDefinition externalEntity) {
		Optional<SyncDetail> watermark = syncRepo.findWatermark(externalEntity.getId(), syncariEntityName, SyncDirection.OUTBOUND);
		return watermark.orElseGet(() -> syncRepo.save(new SyncDetail(externalEntity.getId(), syncariEntityName,
				new Watermark(0, 0,true,0).setDirection(SyncDirection.OUTBOUND))));
	}

	public Optional<SyncDetail> getDownstreamWatermark(String syncariEntityName, EntityDefinition externalEntity) {
		return syncRepo.findWatermark(externalEntity.getId(), syncariEntityName, SyncDirection.OUTBOUND);
	}
	
	public Optional<DatastoreWatermark> getDatastoreWatermark(String syncariEntityId) {
	    return dsWatermarkRepo.findByEntityId(syncariEntityId);
	}

	public List<DatastoreWatermark> findAllEntitiesDatastoreWatermark(){
		return dsWatermarkRepo.findAll();
	}

    public List<DatastoreWatermark> getDatastoreWatermarks() {
        return dsWatermarkRepo.findAll();
    }
	
	public void deleteDatastoreWatermark() {
	    dsWatermarkRepo.deleteAll();
	}
	
	public Optional<DatastoreWatermark> saveDatastoreWatermark(EntityDefinition syncariEntityDef, Watermark wm) {
	    Optional<DatastoreWatermark> existing = dsWatermarkRepo.findByEntityId(syncariEntityDef.getId());
	    DatastoreWatermark watermark = new DatastoreWatermark();
	    watermark.setEntityId(syncariEntityDef.getId());
        if(existing.isPresent()) {
            watermark = existing.get();
        }
        watermark.setEntityName(syncariEntityDef.getApiName());
	    watermark.setWatermark(wm);
	    return Optional.of(dsWatermarkRepo.save(watermark));
	}

	public DatastoreWatermark saveDatastoreWatermark(DatastoreWatermark datastoreWatermark){
		return dsWatermarkRepo.save(datastoreWatermark);
	}

	public List<Watermark> getWatermarks(String syncariEntityName, List<EntityDefinition> externalEntities) {
		List<SyncDetail> existing = syncRepo.findWatermarks(externalEntities.stream().map(e->e.getId()).collect(Collectors.toList()), syncariEntityName);
		return existing.stream().map(s->s.getWatermark()).collect(Collectors.toList());
	}

	public List<SyncDetail> getUpstreamWatermarks(String syncariEntityName, List<String> sourceEntityDefinitionIds) {
		return syncRepo.findUpstreamWatermarks(syncariEntityName, sourceEntityDefinitionIds);
	}
	
	public Optional<SyncDetail> getDownstreamWatermarks(String syncariEntityName, String sinkEntityDefinitionId) {
		return syncRepo.findDownstreamWatermarks(sinkEntityDefinitionId, syncariEntityName);
	}

	public Optional<SyncDetail> findUpstreamWatermark(String syncariEntityName, String sourceEntityDefinitionId) {
		return syncRepo.findWatermark(sourceEntityDefinitionId, syncariEntityName);
	}

	public Optional<SyncDetail> findDownstreamWatermark(String syncariEntityName, String sourceEntityDefinitionId) {
		return syncRepo.findWatermark(sourceEntityDefinitionId, syncariEntityName, SyncDirection.OUTBOUND);
	}

	public void deleteWatermarksForSyncariEntity(String syncariEntity){
		syncRepo.deleteWatermarksForSyncariEntity(syncariEntity);
	}

	public List<SyncDetail> save(List<SyncDetail> syncDetails) {
		return syncRepo.saveAll(syncDetails);
	}

	public SyncDetail save(SyncDetail syncDetail) {
		return syncRepo.save(syncDetail);
	}

	public Watermark getOrCreateWatermark(Connector c, String syncariEntityName, EntityDefinition externalEntity) {
		Optional<SyncDetail> existing = syncRepo.findWatermark(externalEntity.getId(), syncariEntityName);
		if (!existing.isPresent()) {
			// Initial sync, start from a long time ago
			log.info(format("No watermark found for %s, setting to initial sync", c.getName()));

			// Compute the new watermark by looking at the first record in the end system
			long start = getSynapseFirstCreatedTime(c, externalEntity);
			Watermark watermark = new Watermark(start, start, true, 0);
			SyncDetail syncDetail = new SyncDetail(externalEntity.getId(), syncariEntityName, watermark);
			syncDetail.setCreatedAt(new Date());
			syncRepo.save(syncDetail);
			return watermark;
		} else {
			SyncDetail syncDetail = existing.get();
			return syncDetail.getWatermark();
		}
	}

	public Watermark updateWatermark(EntityDefinition externalEntity, String entityName, Watermark watermark) {
		log.info("Updating watermark for externalEntity {} , syncari entity {} and watermark {}",
				externalEntity.getDisplayName(), entityName, watermark);
		validateWatermark(watermark);
		Optional<SyncDetail> existing = syncRepo.findWatermark(externalEntity.getId(), entityName, watermark.getDirection());
		SyncDetail updated;
		if(existing.isPresent()) {
			var e = existing.get();
			if(e.getWatermark().isInitial() && !watermark.isInitial()){
				log.info("Switching to incremental for externalEntity {} , syncari entity {} and watermark {}",
					externalEntity.getDisplayName(), entityName, watermark);
			}
			if(watermark.getOffset() != 0 && watermark.getOffset() > e.getWatermark().getOffset()){
				log.info("Updating offset for entity {} {}", externalEntity.getApiName(), watermark);
				updated = updateOffset(e, watermark.getOffset(), watermark.getStart(), watermark.getEnd());
			} else if(StringUtils.isNotEmpty(watermark.getChangeStream())) {
				log.info("Updating changeStream for entity {} {}", externalEntity.getApiName(), watermark);
				updated = updateChangeStream(e, watermark.getChangeStream(), watermark.getStart(), watermark.getEnd());
			} else {
				log.info("Updating watermark for entity {} {}", externalEntity.getApiName(), watermark);
				updated = updateWatermark(e, watermark);
			}
			updatePruneState(e, watermark);
		}else{
			throw new RuntimeException(
					String.format("Sync details for entity %s and connector %s not found", entityName, externalEntity.getConnectorId()));
		}
		return updated.getWatermark();
	}

	public SyncDetail updateWatermark(SyncDetail syncDetail, Watermark watermark) {
		Query query = new Query().addCriteria(where("_id").is(new ObjectId(syncDetail.getId())));
        Update update = new Update().set("watermark", watermark).set("updatedAt", new Date());
        return customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false).upsert(false), SyncDetail.class);
	}

	public SyncDetail updateOffset(SyncDetail syncDetail, long offset, long start, long end) {
		Query query = new Query().addCriteria(where("_id").is(new ObjectId(syncDetail.getId())));
		Update update = new Update().set("watermark.offset", offset).set("watermark.start", start).set("watermark.end", end).set("updatedAt", new Date());
		return customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false).upsert(false), SyncDetail.class);
	}

	public SyncDetail updateChangeStream(SyncDetail syncDetail, String changeStream, long start, long end) {
		Query query = new Query().addCriteria(where("_id").is(new ObjectId(syncDetail.getId())));
		Update update = new Update().set("watermark.changeStream", changeStream).set("watermark.start", start).set("watermark.end", end).set("updatedAt", new Date());
		return customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false).upsert(false), SyncDetail.class);
	}

	public SyncDetail updatePruneState(SyncDetail syncDetail, Watermark watermark) {
		log.info("Updating prune state from {} to {}", syncDetail.getWatermark().getPruneState(), watermark.getPruneState());
		Query query = new Query().addCriteria(where("_id").is(new ObjectId(syncDetail.getId())));
		Update update = new Update().set("watermark.pruneState", watermark.getPruneState()).set("updatedAt", new Date());
		return customerMongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false).upsert(false), SyncDetail.class);
	}

	public void resetToOriginalWatermark(Map<String, Watermark> originalStreamWatermark) {
		originalStreamWatermark.forEach((syncDetailId, syncDetailWatermark) -> {
			Optional<SyncDetail> syncDetail = syncRepo.findById(syncDetailId);
			syncDetail.ifPresent(v -> {
				log.info("Reset watermark for entity {} to {}", v.getEntityName(), syncDetailWatermark.toString());
				updateWatermark(v, syncDetailWatermark);
			});
		});
	}

	/**
	 * Create source watermark with given watermark only if startDate is after firstCreatedDate
	 * else set watermark start to firstCreatedDate
	 */
	public SyncDetail createSourceWatermark(Connector c, EntityDefinition externalEntity, String entityName, Watermark watermark) {
		validateWatermark(watermark);
		long computedStart = Math.max(watermark.getStart(), getSynapseFirstCreatedTime(c, externalEntity));
		watermark.setStart(computedStart);
		watermark.setEnd(computedStart);
		watermark.setDirection(SyncDirection.INBOUND);
		log.info("Create watermark for Source Entity {} in Connector {} with watermark {}",
				externalEntity.getId(), c.getName(), watermark.toString());
		SyncDetail syncDetail = new SyncDetail(externalEntity.getId(), entityName, watermark);
		return syncRepo.save(syncDetail);
	}

	/**
	 * Reset source watermark with given watermark only if startDate is after firstCreatedDate
	 * else set watermark start to firstCreatedDate
	 */
	public void resetSourceWatermark(Connector c, EntityDefinition externalEntity, String syncariEntityName, Watermark watermark) {
		validateWatermark(watermark);
		if(!SyncDirection.INBOUND.equals(watermark.getDirection())){
			throw new RuntimeException(format("Cannot reset %s watermark", watermark.getDirection().name()));
		}
		long computedStart = Math.max(watermark.getStart(), getSynapseFirstCreatedTime(c, externalEntity));
		watermark.setStart(computedStart);
		watermark.setEnd(computedStart);
		log.info("Reset watermark for Source Entity {} in Connector {} to {}",
				externalEntity.getId(), c.getName(), watermark.toString());
		updateWatermark(externalEntity, syncariEntityName, watermark);
	}

	/**
	 * Updates nextSyncAt for sync details of mapped entities in a connector
	 * @param connectorId - id of connector for which sync run of entities will be updated
	 * @param nextSyncAt - epoch time in seconds for next sync run
	 */
	public void updateNextSyncAtForAllEntitiesOfConnector(String connectorId, long nextSyncAt, boolean forceSchedule){
		log.info("Updating nextSyncAt for syncDetails of all mapped entities of connector with id {} to epoch time {}", connectorId, nextSyncAt);
		Map<String, Set<String>> mappedEntities = graphService.getMappedEntities(connectorId);
		List<SyncDetail> syncDetailsForExternalEntity = syncRepo.findAllWatermarksFor(new ArrayList<>(mappedEntities.keySet()));
		syncDetailsForExternalEntity.forEach(syncDetail -> {
			log.info("Updating nextSyncAt of syncDetail with id {} to epoch time {}", syncDetail.getId(), nextSyncAt);
			syncDetail.setNextSyncAt(nextSyncAt);
			syncDetail.setForceSchedule(forceSchedule);
		});
		syncRepo.saveAll(syncDetailsForExternalEntity);
	}

	/**
	 * Return created time (epoch) for first record in the end system
	 */
	private long getSynapseFirstCreatedTime(Connector c, EntityDefinition externalEntity){
		EntitySchema entitySchema = transformer.toEntitySchema(externalEntity, c);
		SyncRequest request = new SyncRequest().Builder(transformer.toConnectorInfo(c), entitySchema)
				.setWatermark(new WatermarkInfo(-1, -1, true, 0))
				.setEntitySchemaWithMappedFields(transformer.toEntitySchema(schemaService.getEntityWithSystemFields(externalEntity.getId()), c));
		long start = getInitialSyncStart();
		try {
			long firstEntity = factory.getDataService(c.getMetadata()).getFirstCreatedTime(request);
			start = firstEntity < 0 ? start : firstEntity;
		} catch (Exception e) {
			log.error(e.getMessage());
		}
		return start;
	}

	private static long getInitialSyncStart() {
		return Instant.parse(INITIAL_SYNC_START_DATE).toEpochMilli();
	}

	private void validateWatermark(Watermark watermark){
		if (watermark == null) {
			throw new RuntimeException("Watermark cannot be null");
		}
		if (watermark.hasEnd() && watermark.getEnd() < watermark.getStart()) {
			throw new RuntimeException("End cannot be less than start for watermark");
		}
		if (!watermark.hasStart()) {
			throw new RuntimeException("Watermark should have start");
		}
	}

	public boolean isSyncDone(SyncDetail syncDetail, Watermark watermark) {
		if(!syncDetail.isOnGoingSync()) return false;
		if(watermark.getOffset() > 0) return false;
		if(StringUtils.isNotEmpty(watermark.getChangeStream())) return false;
		return watermark.getEnd() >= syncDetail.getEndTime();
	}
}