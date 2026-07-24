package com.syncari.core.repositories.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.BsonField;
import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.connector.ExternalId;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.*;
import com.syncari.core.service.cache.CacheDataTypeConverter;
import com.syncari.core.utils.RedisCriteria;
import com.syncari.core.utils.RedisUtils;
import com.syncari.utils.CollectionUtils;
import com.syncari.utils.Timers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.JsonSetParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.search.querybuilder.Node;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EntityCacheRepo {
    private static final String IS_DELETED = "isDeleted";
    private static final String REPARENTED = "reparented";
    private static final String SYNCARI_TIMESTAMP = "syncariTimestamp";
    private static final String SYNCARI_ID = "_id";
    public static final long COUNT_THRESHOLD = 500000L;
    @Autowired
    IdMappingService mappingService;
    @Autowired
    NotificationService notifyService;

    @Autowired
    RedisUtils redisUtils;

    @Autowired
    private JedisPooled redisClient;

    @Autowired
    DatastoreService datastoreService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;

    @Autowired
    EntityDatabaseRepo entityDatabaseRepo;

    private static final String ENTITY_KEY_FORMAT = "%s:e:%s:%s";
    private static final String ENTITY_PREFIX_FORMAT = "%s:e:%s:*";

    private CacheDataTypeConverter cacheDataTypeConverter = new CacheDataTypeConverter();

    private BiFunction<EntityDefinition, Document, EntityData> entityCreate;

    @Autowired
    public EntityCacheRepo(RedisUtils redisUtils, BiFunction<EntityDefinition, Document, EntityData> entityCreate){
        this.redisUtils = redisUtils;
        this.entityCreate = entityCreate;
    }

    public long count(String entity, boolean deletedOnly) {

        SearchCriteria criteria = new SearchCriteria();
        criteria.setCaseSensitive(true);
        if(deletedOnly) {
            criteria.and(IS_DELETED, deletedOnly);
        }
        return redisUtils.count(SyncariContext.getSyncariId(), entity, criteria);
    }
    
    public void createIndexes(EntityDefinition entityDefinition, List<AttributeDefinition> attributes){
    }

    private String key(String entityName, String id) {
        return String.format(ENTITY_KEY_FORMAT, SyncariContext.getSyncariId(), entityName, id);
    }

    private String entityPrefix(String entityName, String id) {
        return String.format(ENTITY_KEY_FORMAT, SyncariContext.getSyncariId(), entityName, id);
    }

    public Document convertFrom(Document record, EntityDefinition entityDefinition) {
        List<String> nullFields = new ArrayList<>();
        entityDefinition.getAttributes().forEach(attribute -> {
            String attributeName = attribute.getApiName();
            if (record.containsKey(attributeName)) {
                final Object convertedValue = cacheDataTypeConverter.convertTo(attribute.getDataType(), record.get(attributeName));
                if (convertedValue != null) {
                    record.put(attributeName, convertedValue);
                } else {
                    nullFields.add(attributeName);
                }
            }
        });
        // converts to long if input is either long or double
        record.computeIfPresent("syncariTimestamp", (k, v) -> IntegerType.CONVERTERS.get(Object.class).apply(v));
        record.computeIfPresent("lastModified", (k, v) -> IntegerType.CONVERTERS.get(Object.class).apply(v));
        record.computeIfPresent("createdAt", (k, v) -> IntegerType.CONVERTERS.get(Object.class).apply(v));
        record.put("_id", new ObjectId(record.get("_id").toString()));
        record.put("isDeleted", cacheDataTypeConverter.convertTo(BooleanType.VALUE, record.get("isDeleted")));
        if (!nullFields.isEmpty()) {
            record.put("__nf", String.join(",", nullFields));
        }
        return record;
    }


    private Document convertTo(Document record, EntityDefinition entityDefinition) {
        List<String> nullFields = new ArrayList<>();
        entityDefinition.getAttributes().forEach(attribute -> {
            String attributeName = attribute.getApiName();
            if (record.containsKey(attributeName)) {
                final Object convertedValue = cacheDataTypeConverter.convertFrom(attribute.getDataType(), record.get(attributeName));
                if (convertedValue != null) {
                    record.put(attributeName, convertedValue);
                } else {
                    record.remove(attributeName);
                    nullFields.add(attributeName);
                }
            } else {
                // add it to the field
                nullFields.add(attributeName);
            }
        });
        // converts to long if input is either long or double
        record.computeIfPresent("syncariTimestamp", (k, v) -> cacheDataTypeConverter.convertFrom(IntegerType.VALUE, v));
        record.computeIfPresent("lastModified", (k, v) -> cacheDataTypeConverter.convertFrom(IntegerType.VALUE, v));
        record.computeIfPresent("createdAt", (k, v) -> cacheDataTypeConverter.convertFrom(IntegerType.VALUE, v));
        record.put("isDeleted", cacheDataTypeConverter.convertFrom(BooleanType.VALUE, record.get("isDeleted")));
        if (!nullFields.isEmpty()) {
            record.put("__nf", String.join(",", nullFields));
        }
        return record;
    }

    public Optional<EntityData> findById(EntityDefinition entityDefinition, String id) {

        return Optional.ofNullable(redisClient.jsonGet(key(entityDefinition.getApiName(), id)))
                .map(m -> new Document((Map)m))
                .map(doc -> entityCreate.apply(entityDefinition, convertFrom(doc, entityDefinition)));
    }

    /**
     * @deprecated use #findByIds(EntityDefinition syncariEntityDefinition, Set<String> ids)
     * @param entityName
     * @param ids
     * @return
     */
    public Iterable<EntityData> findByIds(String entityName, Set<String> ids) {
        return List.of();
    }

    public Iterable<EntityData> findByIds(EntityDefinition syncariEntityDefinition, Set<String> ids) {

        // Due to bug in Redis Mget implementation default to using Mongo for now
        return entityDatabaseRepo.findByIds(syncariEntityDefinition, ids);
    }

    public Slice<EntityData> find(String entityName, Instant start, Pageable page) {

        return entityDatabaseRepo.find(entityName, start, page);

/*        Criteria rangeQuery =  Criteria.where(SYNCARI_TIMESTAMP).gt(start.toEpochMilli());
        Query pagedQuery = page.isUnpaged()? Query.query(rangeQuery): Query.query(rangeQuery).with(Sort.by(SYNCARI_TIMESTAMP)).limit(page.getPageSize()).skip(page.getOffset());
        List<Map> records = customerMongoTemplate.find(pagedQuery, Map.class,toCollectionName(entityName));
        var entities = records.stream().map(document -> createEntity(entityName, new Document(document))).collect(Collectors.toList());
        return page.isUnpaged()? new SliceImpl<>(entities) : new SliceImpl<>(entities, page, records.size() == page.getPageSize());*/
        //return new SliceImpl<EntityData>(List.of());
    }
    public Slice<EntityData> find(EntityDefinition entityDefinition, Instant start, Pageable page) {

        return entityDatabaseRepo.find(entityDefinition, start, page);
/*
        String entityName = entityDefinition.getApiName();
        Criteria rangeQuery =  Criteria.where(SYNCARI_TIMESTAMP).gte(start.toEpochMilli());
        Query pagedQuery = page.isUnpaged()? Query.query(rangeQuery): Query.query(rangeQuery).with(Sort.by(SYNCARI_TIMESTAMP)).limit(page.getPageSize()).skip(page.getOffset());
        List<Map> records = customerMongoTemplate.find(pagedQuery, Map.class,toCollectionName(entityName));
        var entities = records.stream().map(document -> createEntity(entityDefinition, new Document(document))).collect(Collectors.toList());
        return page.isUnpaged()? new SliceImpl<>(entities) : new SliceImpl<>(entities, page, records.size() == page.getPageSize());
*/
        //return new SliceImpl<EntityData>(List.of());
    }

    public List<EntityData> find(EntityDefinition entityDefinition, Instant start, PageCursor page) {
        return entityDatabaseRepo.find(entityDefinition, start, page);
    }

    // Needs cache implementation, consider consolidation
    public Slice<EntityData> search(String entityName, SearchCriteria criteria, Pageable page) {
        return entityDatabaseRepo.search(entityName, criteria, page);
    }

    public Slice<EntityData> search(EntityDefinition entityDefinition, SearchCriteria criteria, Pageable page) {
        return search(entityDefinition.getApiName(), criteria, page);
    }

    private String index(String entityName) {
        return redisUtils.getEntityIndex(SyncariContext.getSyncariId(), entityName);
    }

    public List<EntityData> search(EntityDefinition def,
                                                                     Optional<? extends RedisCriteria> visitor, int pageSize) {
        Timers timer = new Timers(log);
        Optional<Node> criteria = timer.time("entitycacherepo:createCriteria", () -> visitor.map(v -> v.createCriteria()));
        var sortBy = visitor.map(v -> v.sort()).orElse(List.of());

        List<Document> documents = timer.time("entitycacherepo:searchPaged", () -> redisUtils.searchPaged(def.getApiName(), criteria, sortBy, pageSize + 1));

        var entities = timer.time("entitycacherepo:converEntity", () -> documents.stream().map(d -> entityCreate.apply(def, convertFrom(d, def))).collect(Collectors.toList()));
        timer.logDebug();
        return entities;
    }

    public long count(EntityDefinition def,Optional<? extends RedisCriteria> visitor) {
        Optional<Node> criteria = visitor.map(v -> v.createCriteria());
        return redisUtils.count(def.getApiName(), criteria);
    }

    public boolean hasCaseInsensitiveIndexOnField(EntityDefinition def, String fieldName) {
/*        return customerMongoUtils.hasCaseInsensitiveIndexOnField(toCollectionName(def.getApiName()), fieldName);*/
        return false;
    }

    /**
     * This always sorts by id and will not honor the incoming sorts
     * @param def
     * @param visitor
     * @param cursor
     * @return
     */
    public com.syncari.core.model.pagination.Page<EntityData> search(EntityDefinition def,
                                                                     Optional<? extends RedisCriteria> visitor, PageCursor cursor) {
        Optional<Node> criteria = visitor.map(v -> v.createCriteria());
        var sortBy = visitor.map(v -> v.sort()).orElse(List.of());

        // get one more
        var documents = redisUtils.searchWithCursor(def.getApiName(), criteria, sortBy, cursor);

        List<EntityData> results = documents.stream().map(d -> entityCreate.apply(def, convertFrom(d, def))).collect(Collectors.toList());

        int pageSize = cursor.getPageSize();

        boolean hasMore = !(results.size() < pageSize);

        String pageStart = results.size() > 0 ? results.get(0).getId() : null;
        String pageEnd = results.size() > 0 ? cursor.getCursor() : null;
        com.syncari.core.model.pagination.Page<EntityData> page = new com.syncari.core.model.pagination.Page<EntityData>();
        page.setPageInfo(new PageInfo(pageStart, pageEnd, hasMore).addSort("Id", true));
        page.setRecords(results);
        assert page.getRecords().size() <= pageSize;
        return page;
    }

    public com.syncari.core.model.pagination.Page<EntityData> searchWithCount(EntityDefinition def,
            Optional<Expression> filter, PageCursor pageInfo, Optional<String> syncariId,boolean withCount) {
        return entityDatabaseRepo.searchWithCount(def, filter, pageInfo, syncariId,withCount);
    }
    
    private Expression getPageFilter(PageCursor pageInfo) {
        Expression lhs = Expression.var("_id");
        Expression rhs = Expression.lit(new ObjectId(pageInfo.getCursor()));
        return pageInfo.isForward() ? Expression.gt(lhs, rhs) : Expression.lt(lhs, rhs);
    }

    public List<EntityData> findByAttribute(String entityName, String attributeName, List<Object> values) {
        return entityDatabaseRepo.findByAttribute(entityName, attributeName, values);
    }

    public List<EntityData> findByAttribute(String entityName, String attributeName, List<Object> values, PageCursor pageCursor) {
        return entityDatabaseRepo.findByAttribute(entityName, attributeName, values, pageCursor);
    }

    public void delete(String entityName) {
        entityDatabaseRepo.delete(entityName);

        Set<String> matchingKeys = new HashSet<>();
        ScanParams params = new ScanParams();
        String keyPrefix = String.format(ENTITY_PREFIX_FORMAT, SyncariContext.getSyncariId(), entityName);
        params.match(keyPrefix);
        String nextCursor = "0";

        List<String> keys = List.of();
        do {
            ScanResult<String> scanResult = redisClient.scan(nextCursor, params);
            keys = scanResult.getResult();
            nextCursor = scanResult.getCursor();
            matchingKeys.addAll(keys);
        } while(!nextCursor.equals("0"));

        if (matchingKeys.size() == 0) {
            return;
        }

        redisClient.del(matchingKeys.toArray(new String[matchingKeys.size()]));
    }

    private EntityData createEntity(String entityName, Document document) {
        var entity = new EntityData(entityName);
        entity.setSyncariEntityId(document.getObjectId("_id").toHexString());
//        //Id is the same as syncariEntityId
        entity.setId(document.getObjectId("_id").toHexString());
        document.forEach((key, value)-> {if(!"_id".equals(key)) entity.addValue(key, value);});
        Boolean deletedFlag = document.getBoolean(IS_DELETED);
        entity.setDeleted(deletedFlag == null ? false : deletedFlag);
        entity.setLastModified(document.getLong("lastModified"));
        entity.setOriginatingConnectorId(document.getString("originatingConnectorId"));
        entity.setLastTransactionLogId(document.getString("lastTransactionLogId"));
        entity.setSyncariTimestamp(document.getLong(SYNCARI_TIMESTAMP));
        entity.setReparented(document.getBoolean(REPARENTED, false));
        setCreatedAt(document, entity);
        entity.setSyncariScore(getScore(document.get("syncariScore")));
        return entity;
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
        entity.setSyncariTimestamp(document.getLong(SYNCARI_TIMESTAMP));
        entity.setReparented(document.getBoolean(REPARENTED,false));
        entity.setSyncariScore(getScore(document.get("syncariScore")));
        return entity;
    }

    private boolean checkForEmptyString(Object result) {
        if(result != null && result instanceof String && StringUtils.isBlank(result.toString())) {
            return true;
        }
        return false;
    }

    private void setCreatedAt(Document document, EntityData entity) {
        if(document.containsKey("createdAt")){
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

    protected Document getScore(EntityScore score) {
        if (score != null) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                return Document.parse(mapper.writeValueAsString(score));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize entityscore due to {}", e.getMessage(), e);
                throw new RuntimeException("Failed to serialize entityscore.", e);
            }
        }
        return null;
    }

    /**
     * @deprecated
     */
    public List<EntityData> findByIdsIn(String entityName, List<String> ids) {
        Optional<EntityDefinition> syncariEntityByName = schemaService.getSyncariEntityByName(entityName);
        return syncariEntityByName.map(e-> {
            return findByIdsIn(e,ids);
        }).orElse(List.of());
    }

    public List<EntityData> findByIdsIn(EntityDefinition entityDefinition, List<String> ids) {
        return entityDatabaseRepo.findByIdsIn(entityDefinition, ids);
    }

    public Page<EntityData> findEntities(String entityName, Pageable page) {
        return entityDatabaseRepo.findEntities(entityName, page);
    }

    public void updateAll(EntityDefinition entityDefinition, List<EntityData> entities) {
        entities.forEach(entity -> update(entityDefinition, entity));
    }

    public void update(EntityDefinition entityDefinition, EntityData entity) {
        entityDatabaseRepo.update(entityDefinition, entity);
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData entity, boolean changeTimestamp) {

        EntityData savedData = entityDatabaseRepo.save(entityDefinition, entity, changeTimestamp);

        log.debug("Saved to Mongo DB {} {}", entityDefinition.getApiName(), entity.getSyncariEntityId());
        Document doc = convertTo(getDocument(savedData, false), entityDefinition);
        String key = key(entityDefinition.getApiName(), savedData.getId());

        log.debug("Saving to Redis {} {}", entityDefinition.getApiName(), entity.getSyncariEntityId());
        //int oldFailureCount = redisUtils.indexFailures(entityDefinition.getApiName());
        var jsonString = doc.toJson(JsonWriterSettings.builder()
                .dateTimeConverter((aLong, strictJsonWriter) -> strictJsonWriter.writeNumber(aLong==null? "null": aLong.toString())).build());

        String result = redisClient.jsonSet(key, jsonString);

        log.debug("Saved to Redis {} {} Result {}", entityDefinition.getApiName(), entity.getSyncariEntityId(), result);

        return savedData;
    }

    private Document toChildDocument(EntityData r) {
        if(r==null){
            return null;
        }
        return getDocument(r,false)
                .append("syncariId",r.getSyncariEntityId())
                .append("syncariEntityName",r.getName());
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData entity) {
        return save(entityDefinition, entity,true);
    }

    private Document getDocument(EntityData entity, boolean changeTimestamp) {
        Document values =new Document(entity.getValues());

        if (values.containsKey("_id") && values.get("_id") instanceof ObjectId) {
            values.put("_id", ((ObjectId)values.get("_id")).toHexString());
        }

        values.put("lastModified",entity.getLastModified());
        if(entity.getCreatedAt() > 0) {
            values.put("createdAt", entity.getCreatedAt());
        }else if(entity.getValue("CreatedDate")!=null && Date.class.isAssignableFrom(entity.getValue("CreatedDate").getClass())){
            Date createdDate = entity.getTypedValue("CreatedDate");
            values.put("createdAt", createdDate.toInstant().toEpochMilli());
        }else if(entity.getValue("CreatedDate")!=null && ZonedDateTime.class.isAssignableFrom(entity.getValue("CreatedDate").getClass())){
            ZonedDateTime createdDate = entity.getTypedValue("CreatedDate");
            values.put("createdAt", createdDate.toInstant().toEpochMilli());
        }

        if(changeTimestamp) {
            values.put(SYNCARI_TIMESTAMP, Instant.now().toEpochMilli());
        }
        values.put(IS_DELETED, entity.isDeleted());
        values.put(REPARENTED, entity.isReparented());

        values.put("syncariScore", getScore(entity.getSyncariScore()));
        if(!StringUtils.isBlank(entity.getLastTransactionLogId())) {
            values.put("lastTransactionLogId", entity.getLastTransactionLogId());
        }
        if(entity.getOriginatingConnectorId()!=null) {
            values.put("originatingConnectorId", entity.getOriginatingConnectorId());
        }
        values.put("lastModified",entity.getLastModified());
        return values;
    }

    private List<Document> toDocuments(Set<ExternalId> externalIds) {
        return externalIds.stream().map(e->new Document(e.toMap())).collect(Collectors.toList());
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

        List<EntityData> savedEntities = entityDatabaseRepo.saveAll(entityDefinition, entities, changeTimestamp);

        try(Pipeline pipeline = redisClient.pipelined()) {
            savedEntities.forEach(savedData -> {
                    Document doc = convertTo(getDocument(savedData, false), entityDefinition);
                    String key = key(entityDefinition.getApiName(), savedData.getId());

                    var jsonString = doc.toJson(JsonWriterSettings.builder()
                            .dateTimeConverter((aLong, strictJsonWriter) -> strictJsonWriter.writeNumber(aLong==null? "null": aLong.toString())).build());

                    pipeline.jsonSet(key, jsonString);
            });
            pipeline.sync();
        }
        return savedEntities;
    }

    /**
     * Updates existing syncari records with updatedValues. The records are matched by syncariEntityId. Deleted records are not updated.
     * @param entityDefinition - the entity definition
     * @param updatedValues - only the values from actual attributess in getValues are considered . syncariEntityId must be set. Rest of the system fields are ignored
     */
    public void updateValues(EntityDefinition entityDefinition, List<EntityData> updatedValues) {

        if(updatedValues.isEmpty()) {
            return;
        }
        entityDatabaseRepo.updateValues(entityDefinition, updatedValues);

        Map<String, EntityData> valuesMap = updatedValues.stream().collect(Collectors.toMap(EntityData::getId, Function.identity()));

        try(Pipeline pipeline = redisClient.pipelined()) {
            // Retrieve values from cache and update them.
            String[] keys = updatedValues.stream().map(e -> key(entityDefinition.getApiName(), e.getId())).toArray(String[]::new);

            // TODO: fix this - this version of mget can be in redis utils
            List<JSONArray> arr = redisClient.jsonMGet(keys).stream().filter(json -> json != null).collect(Collectors.toList());

            int oldFailureCount = redisUtils.indexFailures(entityDefinition.getApiName());

            for (JSONArray updateDoc : arr) {
                Map obj = updateDoc.getJSONObject(0).toMap();
                if (obj.containsKey("_id") && valuesMap.containsKey((String)obj.get("_id"))) {
                    final EntityData entity = valuesMap.get((String) obj.get("_id"));
                    updateCacheRecord(entityDefinition, entity, obj);
                    pipeline.jsonSet(key(entityDefinition.getApiName(), entity.getId()), new Document(obj).toJson(JsonWriterSettings.builder()
                                    .dateTimeConverter((aLong, strictJsonWriter) -> strictJsonWriter.writeNumber(aLong == null ? "null" : aLong.toString())).build()),
                            JsonSetParams.jsonSetParams().xx());
                }
            }
            pipeline.sync();
            int newFailureCount = redisUtils.indexFailures(entityDefinition.getApiName());
            if (newFailureCount > oldFailureCount) {
                log.error("Record IDs {} have indexing failure for entity {} New count {}", String.join(",", keys), entityDefinition.getApiName(), newFailureCount);
            }
        }
    }

    protected void updateCacheRecord(EntityDefinition entityDefinition, EntityData entity, Map existingCacheRecord) {
        Set<String> nullFields = new HashSet<>();
        Set<String> nonNullFields = new HashSet<>();

        entity.getValues().forEach((attributeName, value) -> {
            if (entityDefinition.hasField(attributeName)) {
                var attribute = entityDefinition.getField(attributeName).get();
                log.debug("Converting {} to attribute type {} Attribute name {}", value, attribute.getDataType(), attributeName);
                final Object convertedValue = cacheDataTypeConverter.convertFrom(attribute.getDataType(), value);
                if (convertedValue != null) {
                    existingCacheRecord.put(attributeName, convertedValue);
                    nonNullFields.add(attributeName);
                } else {
                    existingCacheRecord.remove(attributeName);
                    nullFields.add(attributeName);
                }
            }
        });
        Set<String> existingNullFields = getExistingNullFields(existingCacheRecord);
        existingNullFields.removeAll(nonNullFields);
        existingNullFields.addAll(nullFields);
        final Optional<String> newNullFieldString = existingNullFields.stream()
                .filter(v -> StringUtils.isNotBlank(v))
                .reduce((first, second) -> first + "," + second);

        newNullFieldString.ifPresentOrElse(
                n -> existingCacheRecord.put("__nf", n), //put the new string there is a value
                () -> existingCacheRecord.remove("__nf") //otherwise remove the existing key as well
        );
        existingCacheRecord.put("syncariTimestamp", cacheDataTypeConverter.convertFrom(IntegerType.VALUE, entity.getSyncariTimestamp()));
        existingCacheRecord.put("lastModified", cacheDataTypeConverter.convertFrom(IntegerType.VALUE, entity.getLastModified()));
    }

    private Set<String> getExistingNullFields(Map obj) {
        if (obj.containsKey("__nf")) {
            var commaSeparatedNullFieldList = (String) obj.get("__nf");
            return new HashSet<>(List.of(commaSeparatedNullFieldList.split(",")));
        } else {
            return new HashSet<>();
        }
    }

    public void updateLastTransaction(EntityDefinition entityDefinition, List<EntityData> entities) {
        entityDatabaseRepo.updateLastTransaction(entityDefinition, entities);
    }

    private MongoIterable<EntityData> createEntities(String entityName, FindIterable<Document> documents) {
        return documents.map(document -> createEntity(entityName, document));
    }

    private MongoIterable<EntityData> createEntities(EntityDefinition entityDefinition, FindIterable<Document> documents) {
        return documents.map(document -> createEntity(entityDefinition, document));
    }

    public String toCollectionName(String entityName) {
        return "syncari_" + entityName.toLowerCase();
    }

    public void deleteAll(String entityName) {
        entityDatabaseRepo.deleteAll(entityName);
    }

    public void deleteAll(List<EntityData> entityDataList) {

        entityDatabaseRepo.deleteAll(entityDataList);

        Map<String, List<EntityData>> byEntityName = entityDataList.stream().collect(Collectors.groupingBy(e->e.getName()));

        try(Pipeline pipeline = redisClient.pipelined()) {
            byEntityName.forEach((entityName, entities) -> {
                entities.forEach(e -> {
                    pipeline.jsonDel(key(entityName, e.getId()));
                });
            });
            pipeline.sync();
        }
    }

    public void delete(List<String> recordIds, String entityName) {

        log.debug("Deleting records for entity {}", entityName);
        entityDatabaseRepo.delete(recordIds, entityName);

        try(Pipeline pipeline = redisClient.pipelined()) {
            recordIds.forEach(r -> pipeline.jsonDel(key(entityName, r)));
            pipeline.sync();
        }
    }

    public void markDeleted(List<String> recordIds, String entityName) {

        entityDatabaseRepo.markDeleted(recordIds, entityName);

        try(Pipeline pipeline = redisClient.pipelined()) {
            recordIds.forEach(r -> pipeline.jsonDel(key(entityName, r)));
            pipeline.sync();
        }
    }

    /*
    Aggregate fucntions
     */
    public double sum(String entity,String sumField, Bson filter) {
        return aggregate(entity, filter, Accumulators.sum("aggregate","$"+sumField));
    }

    public double avg(String entity,String sumField, Bson filter) {
        return aggregate(entity, filter, Accumulators.avg("aggregate","$"+sumField));
    }
    public double stdDev(String entity,String sumField, Bson filter) {
        return aggregate(entity, filter, Accumulators.stdDevPop("aggregate","$"+sumField));
    }

    private double aggregate(String entity, Bson filter, BsonField accumulator) {
/*        AggregateIterable<Document> aggregate = customerMongoTemplate.getCollection(toCollectionName(entity)).aggregate(
                List.of(Aggregates.match( filter), Aggregates.group(null,accumulator))
        );
        if(aggregate.iterator().hasNext()){
            final Document aggregateResult = aggregate.iterator().next();
            final Double doubleValue = DoubleType.VALUE.convert(aggregateResult.get("aggregate"));
            return  doubleValue == null ? 0.0d : doubleValue;
        }*/
        return 0.0d;
    }

	public void setDatastoreService(DatastoreService datastoreService) {
		this.datastoreService = datastoreService;
	}
}
