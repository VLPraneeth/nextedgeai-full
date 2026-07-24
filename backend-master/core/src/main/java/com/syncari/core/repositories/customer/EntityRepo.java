package com.syncari.core.repositories.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.AttachRecordData;
import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.core.Features;
import com.syncari.core.model.*;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.*;
import com.syncari.core.utils.Criteria;
import com.syncari.core.utils.MongoCriteria;
import com.syncari.core.utils.RedisCriteria;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class EntityRepo {

    @Autowired
    EntityCacheRepo entityCacheRepo;

    @Autowired
    EntityDatabaseRepo entityDatabaseRepo;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    IdMappingRepo idMappingRepo;

    @Autowired
    IdMappingService mappingService;
    @Autowired
    NotificationService notifyService;

    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    FeatureService featureService;

    private static final String IS_DELETED = "isDeleted";
    private static final String REPARENTED = "reparented";
    private static final String SYNCARI_TIMESTAMP = "syncariTimestamp";
    private static final String SYNCARI_CREATED_AT = "syncariCreatedAt";
    private static final String SYNCARI_ID = "_id";

    @Bean
    public BiFunction<EntityDefinition, Document, EntityData> entityCreate() {
        return this::createEntity;
    }

    public boolean useCache(EntityDefinition entityDefinition) {
        if (entityDefinition != null) {
            return useCache(entityDefinition.getApiName());
        }
        return false;
    }

    public boolean useCache(String apiName) {
        if (featureService != null && featureService.isEnabled(Features.EntityCaching, true)) {
            Feature f = featureService.getFeatureByName(Features.EntityCaching, true);
            if (!StringUtils.isEmpty(f.getParams())) {
                List<String> enabledEntities = Stream.of(f.getParams().split(",")).collect(Collectors.toList());
                boolean useCache = enabledEntities.contains(apiName);
                log.debug("Use cache {} for entity {} Params {}", useCache, apiName, f.getParams());
                return useCache;
            }
        }
        return false;
    }


    public void createCollection(EntityDefinition entityDefinition){
        entityDatabaseRepo.createCollection(entityDefinition);
    }

    public boolean collectionExists(EntityDefinition entityDefinition){
        return entityDatabaseRepo.collectionExists(entityDefinition);
    }

    public long count(String entity, boolean deletedOnly) {
        return entityDatabaseRepo.count(entity, deletedOnly,deletedOnly);
    }

    public long countWithDeleteCriteria(String entity, boolean deletedOnly) {
        return entityDatabaseRepo.count(entity, deletedOnly,true);
    }

    public void createIndexes(EntityDefinition entityDefinition, List<AttributeDefinition> attributes){
        entityDatabaseRepo.createIndexes(entityDefinition, attributes);
    }

    public Optional<EntityData> findById(EntityDefinition entityDefinition, String id) {
        return useCache(entityDefinition) ? entityCacheRepo.findById(entityDefinition, id) : entityDatabaseRepo.findById(entityDefinition, id);
    }

    /**
     * @deprecated use #findByIds(EntityDefinition syncariEntityDefinition, Set<String> ids)
     * @param entityName
     * @param ids
     * @return
     */
    public Iterable<EntityData> findByIds(String entityName, Set<String> ids) {
        return entityDatabaseRepo.findByIds(entityName, ids);
    }

    public Iterable<EntityData> findByIds(EntityDefinition syncariEntityDefinition, Set<String> ids) {
        return useCache(syncariEntityDefinition.getApiName()) ? entityCacheRepo.findByIds(syncariEntityDefinition, ids) : entityDatabaseRepo.findByIds(syncariEntityDefinition, ids);
    }

    /**
     * @deprecated Not used anywhere outside of tests. Use #search
     * @param entityName
     * @param start
     * @param page
     * @return
     */
    public Slice<EntityData> find(String entityName, Instant start, Pageable page) {
        return entityDatabaseRepo.find(entityName, start, page);
    }

    // This method is used by Datastore, need cache implementation
    public Slice<EntityData> find(EntityDefinition entityDefinition, Instant start, Pageable page) {
        return useCache(entityDefinition) ? entityCacheRepo.find(entityDefinition, start, page) : entityDatabaseRepo.find(entityDefinition, start, page);
    }

    // This method used by SaveToSink, need cache implementation
    public List<EntityData> find(EntityDefinition entityDefinition, Instant start, PageCursor page) {
        return useCache(entityDefinition) ? entityCacheRepo.find(entityDefinition, start, page) : entityDatabaseRepo.find(entityDefinition, start, page);
    }

    public List<EntityData> findRecent(EntityDefinition entityDefinition,int limit) {
        return entityDatabaseRepo.findMaxEntityData(entityDefinition, limit);
    }

    // Needs cache implementation, consider consolidation
    public Slice<EntityData> search(String entityName, SearchCriteria criteria, Pageable page) {
        return useCache(entityName) ? entityCacheRepo.search(entityName, criteria, page) : entityDatabaseRepo.search(entityName, criteria, page);
    }

    /**
     * @Deprecated Function used in old lookup/dedupe, may not need cache implementation
     */
    public Slice<EntityData> search(EntityDefinition entityDefinition, SearchCriteria criteria, Pageable page) {
        return search(entityDefinition.getApiName(), criteria, page);
    }

    public com.syncari.core.model.pagination.Page<EntityData> search(EntityDefinition def,
                                                                     Optional<? extends Criteria> visitor, int pageSize) {

        List<EntityData>  results = useCache(def.getApiName()) && visitor.map(v -> RedisCriteria.class.isAssignableFrom(v.getClass())).orElse(false)
                ? entityCacheRepo.search(def, (Optional<? extends RedisCriteria>)visitor, pageSize)
                : entityDatabaseRepo.search(def, (Optional<? extends MongoCriteria>)visitor, pageSize);

        boolean hasMore = results.size() == pageSize + 1;
        if (results.size() > pageSize) {
            results = results.subList(0, results.size() - 1);
        }
        String pageStart = results.size() > 0 ? results.get(0).getId() : null;
        String pageEnd = results.size() > 0 ? results.get(results.size() - 1).getId() : null;
        com.syncari.core.model.pagination.Page<EntityData> page = new com.syncari.core.model.pagination.Page<EntityData>();
        page.setPageInfo(new PageInfo(pageStart, pageEnd, hasMore).addSort("Id", true));
        page.setRecords(results);
        assert page.getRecords().size() <= pageSize;
        return page;
    }


    public com.syncari.core.model.pagination.Page<EntityData> searchWithFallback(EntityDefinition def,
                                                                     Optional<? extends Criteria> redisCriteria, Optional<? extends Criteria> mongoCriteria, boolean useCache, int pageSize) {

        // check
        if (useCache) {
            try {
                return search(def, redisCriteria, pageSize);
            } catch(Exception e) {
                log.error("Failed searching with Redis for entity " + def.getApiName(), e);
                return search(def, mongoCriteria, pageSize);
            }
        }
        return search(def, mongoCriteria, pageSize);
    }

    public com.syncari.core.model.pagination.Page<EntityData> searchWithFallback(EntityDefinition def,
                                                                                 Optional<? extends Criteria> redisCriteria, Optional<? extends Criteria> mongoCriteria, boolean useCache, PageCursor cursor) {

        // check
        if (useCache) {
            try {
                return search(def, redisCriteria, cursor);
            } catch(Exception e) {
                log.error("Failed searching with Redis for entity " + def.getApiName(), e);
                return search(def, mongoCriteria, cursor);
            }
        }
        return search(def, mongoCriteria, cursor);
    }


    public long count(EntityDefinition def,Optional<? extends Criteria> visitor) {
        return useCache(def.getApiName()) && visitor.map(v -> RedisCriteria.class.isAssignableFrom(v.getClass())).orElse(false) ?
                entityCacheRepo.count(def, (Optional<? extends RedisCriteria>)visitor)
                : entityDatabaseRepo.count(def, (Optional<? extends MongoCriteria>)visitor);
    }

    public long countWithFallback(EntityDefinition def, Optional<? extends Criteria> redisCriteria, Optional<? extends Criteria> mongoCriteria, boolean useCache) {
        if (useCache(def.getApiName()) && useCache) {
            try {
                return count(def, redisCriteria);
            } catch(Exception e) {
                log.error("Failed searching with Redis for entity " + def.getApiName(), e);
                return count(def, mongoCriteria);
            }
        }
        return count(def, mongoCriteria);

    }

    // May not need cache implementation
    public boolean hasCaseInsensitiveIndexOnField(EntityDefinition def, String fieldName) {
        return entityDatabaseRepo.hasCaseInsensitiveIndexOnField(def, fieldName);
    }

    /**
     * Needs changes for handling other criteria
     * This always sorts by id and will not honor the incoming sorts
     * @param def
     * @param visitor
     * @param cursor
     * @return
     */
    public com.syncari.core.model.pagination.Page<EntityData> search(EntityDefinition def,
                                                                     Optional<? extends Criteria> visitor, PageCursor cursor) {


        return useCache(def.getApiName()) && visitor.map(v -> RedisCriteria.class.isAssignableFrom(v.getClass())).orElse(false) ? entityCacheRepo.search(def, (Optional<? extends RedisCriteria>)visitor, cursor) :
                entityDatabaseRepo.search(def, (Optional<? extends MongoCriteria>)visitor, cursor);
    }

    public Iterator<EntityData> search(EntityDefinition def, Optional<MongoCriteria> criteria) {
        return new RecordIterator(def, criteria);
    }

    class RecordIterator implements Iterator<EntityData> {
        private final Optional<MongoCriteria> criteria;
        private final EntityDefinition def;
        private PageCursor currentCursor;
        private Iterator<EntityData> currentPage = null;

        private PageCursor getPageCursor(PageInfo pageInfo) {
            if (pageInfo == null) {
                return new PageCursor(null, PageDirection.next, 200);
            }
            return new PageCursor(pageInfo.getEnd(), PageDirection.next, 200);
        }

        public RecordIterator(EntityDefinition def, Optional<MongoCriteria> criteria) {
            this.def = def;
            this.currentCursor = getPageCursor(null);
            this.criteria = criteria;
        }

        @Override
        public boolean hasNext() {
            if (currentPage == null || !currentPage.hasNext()) {
                com.syncari.core.model.pagination.Page<EntityData> page = search(def, criteria, currentCursor);
                if (page == null || page.getRecords().isEmpty()) {
                    return false;
                }
                currentPage = page.getRecords().iterator();
                currentCursor = getPageCursor(page.getPageInfo());
            }
            return currentPage.hasNext();
        }

        @Override
        public EntityData next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentPage.next();
        }
    }

    /**
     * Check the count implementation
     * @param def
     * @param filter
     * @param pageInfo
     * @param syncariId
     * @return
     */
    public com.syncari.core.model.pagination.Page<EntityData> searchWithCount(EntityDefinition def,
            Optional<Expression> filter, PageCursor pageInfo, Optional<String> syncariId, boolean withCount) {
        return useCache(def.getApiName()) ? entityCacheRepo.searchWithCount(def, filter, pageInfo, syncariId,withCount) :
                entityDatabaseRepo.searchWithCount(def, filter, pageInfo, syncariId,withCount);
    }
    
    public List<EntityData> findByAttribute(String entityName, String attributeName, List<Object> values) {
        return useCache(entityName) ? entityCacheRepo.findByAttribute(entityName, attributeName, values) : entityDatabaseRepo.findByAttribute(entityName, attributeName, values);
    }

    public int countByAttributeWithMaxLimit(String entityName, String attributeName, Object value, int limit) {
        return entityDatabaseRepo.countByAttributeWithMaxLimit(entityName, attributeName, value, limit);
    }

    public List<EntityData> findByAttribute(String entityName, String attributeName, List<Object> values, PageCursor cursor) {
        return useCache(entityName) ? entityCacheRepo.findByAttribute(entityName, attributeName, values, cursor) : entityDatabaseRepo.findByAttribute(entityName, attributeName, values, cursor);
    }

    public void delete(String entityName) {
        if (useCache(entityName)) {
            entityCacheRepo.delete(entityName);
        } else {
            entityDatabaseRepo.delete(entityName);
        }
    }

    protected EntityScore getScore(Object object) {
        EntityScore score = new EntityScore();
        if(object == null) return score;
        if(object instanceof EntityScore) return (EntityScore) object;
        if(object instanceof Document) {
            Document document = (Document) object;
            String json = document.toJson();
            ObjectMapper mapper = new ObjectMapper();
            try {
                score = mapper.readValue(json, EntityScore.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize entityscore due to {}", e.getMessage(), e);
                throw new RuntimeException("Failed to deserialize entityscore.", e);
            }
        }
        return score;
    }

    protected Map getAttachRecordData(Object object) {
        Map<String, AttachRecordData> attachRecordData = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        if(object == null) return new HashMap();
        //Map map = new HashMap();
        if(object instanceof Document) {
            Document document = (Document) object;
            document.entrySet().forEach(entry -> {
                String nodeId = entry.getKey();
                Document attachDataDocument = (Document) entry.getValue();
                String json = attachDataDocument.toJson();
                try {
                    var attachData = mapper.readValue(json, AttachRecordData.class);
                    attachRecordData.put(nodeId, attachData);
                } catch (JsonProcessingException e) {
                    // log error and move on
                    log.error("Failed to deserialize attachRecordData due to {}", e.getMessage(), e);
                }

            });
        }
        return attachRecordData;
    }

    /**
     * @deprecated
     */
    public List<EntityData> findByIdsIn(String entityName, List<String> ids) {
        return entityDatabaseRepo.findByIdsIn(entityName, ids);
    }

    /**TestControllerTest
     * Deprecated
     * @param entityName
     * @param page
     * @return
     */
    public Page<EntityData> findEntities(String entityName, Pageable page) {
        return entityDatabaseRepo.findEntities(entityName, page);
    }

    public void updateAll(EntityDefinition entityDefinition, List<EntityData> entities) {
        entities.forEach(entity -> update(entityDefinition, entity));
    }

    public void update(EntityDefinition entityDefinition, EntityData entity) {
        if (useCache(entityDefinition)) {
            entityCacheRepo.update(entityDefinition, entity);
        } else {
            entityDatabaseRepo.update(entityDefinition, entity);
        }
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData entity, boolean changeTimestamp) {
        return useCache(entityDefinition) ? entityCacheRepo.save(entityDefinition, entity, changeTimestamp) : entityDatabaseRepo.save(entityDefinition, entity, changeTimestamp);
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData entity) {
        return save(entityDefinition, entity,true);
    }

    /**
     * @deprecated
     * Deprecated. Use save(EntityDefinition entityDefinition, EntityData entity)
     * @param entity
     * @return
     */
    public EntityData save(EntityData entity) {
        return save(null, entity, true);
    }

    /**
     * @deprecated
     * Deprecated. Use saveAll(EntityDefinition entityDefinition, List<EntityData> entities)
     * @param entities
     * @return
     */
    public List<EntityData> saveAll(List<EntityData> entities) {
        return saveAll(null,entities, true);
    }
    public List<EntityData> saveAll(EntityDefinition entityDefinition, List<EntityData> entities) {
        return saveAll(entityDefinition, entities, true);
    }

    public List<EntityData> saveAll(EntityDefinition entityDefinition, List<EntityData> entities, boolean changeTimestamp) {
        return useCache(entityDefinition) ? entityCacheRepo.saveAll(entityDefinition, entities, changeTimestamp) : entityDatabaseRepo.saveAll(entityDefinition, entities, changeTimestamp);
    }

    /**
     * Updates existing syncari records with updatedValues. The records are matched by syncariEntityId. Deleted records are not updated.
     * @param entityDefinition - the entity definition
     * @param updatedValues - only the values from actual attributess in getValues are considered . syncariEntityId must be set. Rest of the system fields are ignored
     */
    public void updateValues(EntityDefinition entityDefinition, List<EntityData> updatedValues) {
        if (useCache(entityDefinition)) {
            entityCacheRepo.updateValues(entityDefinition, updatedValues);
        } else {
            entityDatabaseRepo.updateValues(entityDefinition, updatedValues);
        }
    }

    public void updateLastTransaction(EntityDefinition entityDefinition, List<EntityData> entities) {
        if (useCache(entityDefinition)) {
            entityCacheRepo.updateLastTransaction(entityDefinition, entities);
        } else {
            entityDatabaseRepo.updateLastTransaction(entityDefinition, entities);
        }
    }

    public String toCollectionName(String entityName) {
        return entityDatabaseRepo.toCollectionName(entityName);
    }

    public void deleteAll(String entityName) {
        if (useCache(entityName)) {
            entityCacheRepo.deleteAll(entityName);
        } else {
            entityDatabaseRepo.deleteAll(entityName);
        }
    }

    public void deleteAll(EntityDefinition syncariEntityDefinition, List<EntityData> entityDataList) {
        if (useCache(syncariEntityDefinition)) {
            entityCacheRepo.deleteAll(entityDataList);
        } else {
            entityDatabaseRepo.deleteAll(entityDataList);
        }
    }

    // Do no use this method, use EntityReporservice deleteRecords method to delete records
    public void delete(List<String> recordIds, String entityName) {
        if (useCache(entityName)) {
            entityCacheRepo.delete(recordIds, entityName);
        } else {
            entityDatabaseRepo.delete(recordIds, entityName);
        }
    }

    public void markDeleted(List<String> recordIds, String entityName) {

        if (useCache(entityName)) {
            entityCacheRepo.markDeleted(recordIds, entityName);
        } else {
            entityDatabaseRepo.markDeleted(recordIds, entityName);
        }
    }

    /*
    Aggregate fucntions
     */
    public double sum(String entity,String sumField, Bson filter) {
        return entityDatabaseRepo.sum(entity, sumField, filter);
    }

    public double avg(String entity,String sumField, Bson filter) {
        return entityDatabaseRepo.avg(entity, sumField, filter);
    }

    public double stdDev(String entity,String sumField, Bson filter) {
        return entityDatabaseRepo.stdDev(entity, sumField, filter);
    }


	public void setDatastoreService(DatastoreService datastoreService) {
		this.datastoreService = datastoreService;
	}

    @Transactional("customerTransactionManager")
    public void saveEntityBatch(EntityDefinition entityDefinition, List<EntityData> entityBatch, List<IdMapping> idMappings) {
        Map<String, IdMapping> syncariIdToIdMapping = idMappings.stream().collect(Collectors.toMap(idMapping -> idMapping.getSyncariId(), idMapping -> idMapping,
                (first, second) -> second));
        log.debug("Syncari id to external id mapping - {}", syncariIdToIdMapping);
        List<AttributeDefinition> externalIdFields = entityDefinition.getExternalIdFields();
        log.debug("External Id Fields - {}", externalIdFields);
        // set all the external ids on the record
        entityBatch.forEach(record -> {
            IdMapping idMapping = syncariIdToIdMapping.get(record.getSyncariEntityId());
            log.debug("Id mapping - {}", idMapping);
            if(idMapping != null){
                externalIdFields.forEach(externalIdField -> {
                    log.debug("External id field - {}", externalIdField);
                    Optional<IdMapping.Mapping> mapping = idMapping.getMapping(externalIdField.getReferenceTo());
                    mapping.ifPresent(m -> {
                        log.debug("Mapping - {}", m);
                        record.addValue(externalIdField.getApiName(), m.getEntityId());
                    });
                });
            }
        });
        saveAll(entityDefinition, entityBatch);
        log.debug("Saved entity batch");
        idMappingRepo.upsert(idMappings);
    }

    private EntityData createEntity(EntityDefinition entityDefinition, Document document) {
        var entity = new EntityData(entityDefinition.getApiName());
        entity.setSyncariEntityId(document.getObjectId("_id").toHexString());
//        //Id is the same as syncariEntityId
        entity.setId(document.getObjectId("_id").toHexString());
        document.forEach((key, value)-> {
            if(!"_id".equals(key)) {
                entityDefinition.getField(key).ifPresent(field-> {
                    Object newValue = checkForEmptyString(value) ? value : field.convert(value);
                    Object updatedValue = newValue instanceof Document ? new HashMap<>((Document)newValue) : newValue;
                    entity.addValue(key, updatedValue);
                });
            };
        });
        Boolean deletedFlag = document.getBoolean(IS_DELETED);
        entity.setDeleted(deletedFlag == null ? false : deletedFlag);
        entity.setLastModified(document.getLong("lastModified"));
        //Set createdAt
        setCreatedAt(document, entity);

        entity.setOriginatingConnectorId(document.getString("originatingConnectorId"));
        entity.setLastTransactionLogId(document.getString("lastTransactionLogId"));
        entity.setLastTransactionTimestamp(document.containsKey("lastTransactionTimestamp") ? document.getLong("lastTransactionTimestamp") : 0);
        entity.setSyncariCreatedAt(document.containsKey(SYNCARI_CREATED_AT) ? document.getLong(SYNCARI_CREATED_AT) : 0);
        entity.setSyncariTimestamp(document.getLong(SYNCARI_TIMESTAMP));
        entity.setReparented(document.getBoolean(REPARENTED,false));
        entity.setSyncariScore(getScore(document.get("syncariScore")));
        entity.setDedupeHash(document.getString("dedupeHash"));
        entity.setAttachRecordData(getAttachRecordData(document.get("attachRecordData")));
        return entity;
    }

    private boolean checkForEmptyString(Object result) {
        if(result != null && result instanceof String && StringUtils.isBlank(result.toString())) {
            return true;
        }
        return false;
    }

    private void setCreatedAt(Document document, EntityData entity) {
        if(document.containsKey("createdAt") && document.get("createdAt") != null){
            if ( (document.get("createdAt") instanceof DateTime) || (document.get("createdAt") instanceof Date)){
                entity.setCreatedAt(document.getDate("createdAt").toInstant().toEpochMilli());
            }else{
                entity.setCreatedAt(document.getLong("createdAt"));
            }
        }else if(document.get("CreatedDate")!=null && Date.class.isAssignableFrom(document.get("CreatedDate").getClass())){
            entity.setCreatedAt(document.getDate("CreatedDate").toInstant().toEpochMilli());
        }else{
            entity.setCreatedAt(0l);
        }
    }
    
    @Transactional("customerTransactionManager")
    public void removeExternalIdFields(String entityApiName, Set<String> externalIds) {
    	entityDatabaseRepo.removeExternalIdFields(entityApiName, externalIds);
    }

    /**
     * Query associations by matching field criteria.
     * Used for finding associations when only partial identifying information is available.
     *
     * @param entityName The association entity name (e.g., "contact_association")
     * @param fromObjectId The source object ID
     * @param toObjectId The target object ID
     * @param toObjectType The target object type
     * @return List of matching association documents
     */
    public List<Map<String, Object>> findAssociationsByFields(String entityName, String fromObjectId,
                                                                String toObjectId, String toObjectType) {
        return entityDatabaseRepo.findAssociationsByFields(entityName, fromObjectId, toObjectId, toObjectType);
    }
}
