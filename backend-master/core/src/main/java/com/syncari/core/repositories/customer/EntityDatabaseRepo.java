package com.syncari.core.repositories.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.connector.ExternalId;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.ZonedDateTimeWriteConverter;
import com.syncari.core.datatype.ChildType;
import com.syncari.core.datatype.DoubleType;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.*;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.core.utils.DataCriteriaVisitor;
import com.syncari.core.utils.MongoCriteria;
import com.syncari.utils.CollectionUtils;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@Slf4j
public class EntityDatabaseRepo {
    private static final String IS_DELETED = "isDeleted";
    private static final String REPARENTED = "reparented";
    private static final String SYNCARI_TIMESTAMP = "syncariTimestamp";
    private static final String SYNCARI_ID = "_id";
    public static final long COUNT_THRESHOLD = 500000L;
    @Autowired
    IdMappingService mappingService;
    @Autowired
    NotificationService notifyService;

    MongoTemplate customerMongoTemplate;
    CustomerMongoUtils customerMongoUtils;
    @Autowired
    DatastoreService datastoreService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    FeatureService featureService;

    private final BiFunction<EntityDefinition, Document, EntityData> entityCreate;

    @Autowired
    public EntityDatabaseRepo(MongoTemplate customerMongoTemplate, CustomerMongoUtils customerMongoUtils, BiFunction<EntityDefinition, Document, EntityData> entityCreate){
        this.customerMongoTemplate = customerMongoTemplate;
        this.customerMongoUtils = customerMongoUtils;
        this.entityCreate = entityCreate;
    }

    public void createCollection(EntityDefinition entityDefinition){
        String collectionName = toCollectionName(entityDefinition.getApiName());
        customerMongoUtils.createCollection(collectionName, List.of());
        customerMongoUtils.createFieldIndexes(collectionName, List.of(IS_DELETED, SYNCARI_TIMESTAMP));
    }

    public boolean collectionExists(EntityDefinition entityDefinition){
        return customerMongoTemplate.collectionExists(toCollectionName(entityDefinition.getApiName()));
    }

    public long count(String entity, boolean deletedOnly, boolean applyDeleteCriteria) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCaseSensitive(true);
        if(deletedOnly || applyDeleteCriteria) {
            criteria.and(IS_DELETED, deletedOnly);
        }
        return customerMongoUtils.count(toCollectionName(entity), criteria);
    }
    
    public void createIndexes(EntityDefinition entityDefinition, List<AttributeDefinition> attributes){
        customerMongoUtils.createFieldIndexes(toCollectionName(entityDefinition.getApiName()),
                attributes.stream().map(a->a.getApiName()).collect(Collectors.toList()));
    }

    public Optional<EntityData> findById(EntityDefinition entityDefinition, String id) {
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityDefinition.getApiName()));
        FindIterable<Document> iterable = collection.find(eq("_id", new ObjectId(id)));
        MongoIterable<EntityData> entities = iterable.map(doc -> entityCreate.apply(entityDefinition, doc));
        return Optional.ofNullable(entities.first());
    }

    /**
     * @deprecated use #findByIds(EntityDefinition syncariEntityDefinition, Set<String> ids)
     * @param entityName
     * @param ids
     * @return
     */
    public Iterable<EntityData> findByIds(String entityName, Set<String> ids) {
        EntityDefinition def = schemaService.getSyncariEntityByName(entityName).orElseThrow(() -> new RuntimeException("Not Found Entity " + entityName));
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));
        FindIterable<Document> iterable = collection.find(in("_id", ids.stream().map(id->new ObjectId(id)).collect(Collectors.toList())));
        MongoIterable<EntityData> entities = iterable.map(doc -> entityCreate.apply(def, doc));
        return entities;
    }

    public Iterable<EntityData> findByIds(EntityDefinition syncariEntityDefinition, Set<String> ids) {
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(syncariEntityDefinition.getApiName()));
        FindIterable<Document> iterable = collection.find(in("_id", ids.stream().map(id->new ObjectId(id)).collect(Collectors.toList())));
        MongoIterable<EntityData> entities = iterable.map(doc -> entityCreate.apply(syncariEntityDefinition, doc));
        return entities;
    }

    public Slice<EntityData> find(String entityName, Instant start, Pageable page) {
        Criteria rangeQuery =  Criteria.where(SYNCARI_TIMESTAMP).gt(start.toEpochMilli());
        Query pagedQuery = page.isUnpaged()? Query.query(rangeQuery): Query.query(rangeQuery).with(Sort.by(SYNCARI_TIMESTAMP)).limit(page.getPageSize()).skip(page.getOffset());
        List<Map> records = customerMongoTemplate.find(pagedQuery, Map.class,toCollectionName(entityName));
        var entities = records.stream().map(document -> createEntity(entityName, new Document(document))).collect(Collectors.toList());
        return page.isUnpaged()? new SliceImpl<>(entities) : new SliceImpl<>(entities, page, records.size() == page.getPageSize());
    }
    public Slice<EntityData> find(EntityDefinition entityDefinition, Instant start, Pageable page) {
        String entityName = entityDefinition.getApiName();
        Criteria rangeQuery =  Criteria.where(SYNCARI_TIMESTAMP).gte(start.toEpochMilli());
        Query pagedQuery = page.isUnpaged()? Query.query(rangeQuery): Query.query(rangeQuery).with(Sort.by(SYNCARI_TIMESTAMP)).limit(page.getPageSize()).skip(page.getOffset());
        log.debug("Paged Query is {} and collectionName used is {}", pagedQuery,toCollectionName(entityName));
        List<Map> records = customerMongoTemplate.find(pagedQuery, Map.class,toCollectionName(entityName));
        var entities = records.stream().map(document -> this.entityCreate.apply(entityDefinition, new Document(document))).collect(Collectors.toList());
        return page.isUnpaged()? new SliceImpl<>(entities) : new SliceImpl<>(entities, page, records.size() == page.getPageSize());
    }

    public List<EntityData> find(EntityDefinition entityDefinition, Instant start, PageCursor page) {
        String entityName = entityDefinition.getApiName();
        Criteria rangeQuery = Criteria.where(SYNCARI_TIMESTAMP).gt(start.toEpochMilli());
        if(StringUtils.isNotBlank(page.getCursor())) {
            String[] cursorSplit = page.getCursor().split("_");
            var cursorTimestamp = Long.parseLong(cursorSplit[0]);
            Criteria greaterThanRangeQuery = Criteria.where(SYNCARI_TIMESTAMP).gt(cursorTimestamp);
            Criteria equalsRangeQuery =  Criteria.where(SYNCARI_TIMESTAMP).is(cursorTimestamp);
            ObjectId objID = new ObjectId(cursorSplit[1]);
            Criteria idQuery =  Criteria.where(SYNCARI_ID).gt(objID);
            rangeQuery = new Criteria().orOperator(greaterThanRangeQuery, new Criteria().andOperator(equalsRangeQuery, idQuery));
        }
        Query pagedQuery = Query.query(rangeQuery)
                    .with(Sort.by(SYNCARI_TIMESTAMP, SYNCARI_ID))
                    .limit(page.getPageSize());

        List<Map> records = customerMongoTemplate.find(pagedQuery, Map.class,toCollectionName(entityName));
        var entities = records.stream().map(document -> this.entityCreate.apply(entityDefinition, new Document(document))).collect(Collectors.toList());
        return entities;
    }

    public List<EntityData> findMaxEntityData(EntityDefinition entityDefinition,int limit) {
        String entityName = entityDefinition.getApiName();
        Criteria rangeQuery = Criteria.where(IS_DELETED).is(Boolean.valueOf("false"));
        Query query = Query.query(rangeQuery)
                .with(Sort.by(Sort.Direction.DESC, SYNCARI_TIMESTAMP))
                .limit(limit);

        List<Map> records = customerMongoTemplate.find(query, Map.class,toCollectionName(entityName));
        var entities = records.stream().map(document -> this.entityCreate.apply(entityDefinition, new Document(document))).collect(Collectors.toList());
        return entities;
    }

    public Slice<EntityData> search(String entityName, SearchCriteria criteria, Pageable page) {
        var excludeDeleted = criteria.addMetaFilter(IS_DELETED,false);
        log.debug("Executing search on '{}' using {}",entityName,excludeDeleted);
        Function<Document, EntityData> converter = document -> createEntity(entityName, document);
        return customerMongoUtils.search(toCollectionName(entityName), excludeDeleted, page, converter);
    }

    public Slice<EntityData> search(EntityDefinition entityDefinition, SearchCriteria criteria, Pageable page) {
        return search(entityDefinition.getApiName(), criteria, page);
    }

    public List<EntityData> search(EntityDefinition def,
                                                                     Optional<? extends MongoCriteria> visitor, int pageSize) {
        Optional<Bson> searchCriteria = visitor.map(v -> v.createCriteria());
        // For now the sorting is implicit, this will be exposed later
        Bson sort = visitor.flatMap(v->v.sort()).orElse(new BasicDBObject("_id", 1));
        Function<Document, EntityData> converter = document -> entityCreate.apply(def, document);
        String collectionName = toCollectionName(def.getApiName());
        boolean hasCaseInsensitiveIndexField = false;
        if (visitor.isPresent()) {
            hasCaseInsensitiveIndexField = (visitor.get()).hasCaseInsensitiveIndexField();
        }
        log.debug("Search criteria - {}, Sort - {}, hasCaseInsensitiveIndex - {}", searchCriteria, sort, hasCaseInsensitiveIndexField);
        List<EntityData> results = customerMongoUtils.searchPaged(collectionName, searchCriteria, sort,
                converter, pageSize + 1, hasCaseInsensitiveIndexField);
        return results;
    }

    public long count(EntityDefinition def,Optional<? extends MongoCriteria> visitor) {
        Optional<Bson> searchCriteria = visitor.map(v -> v.createCriteria());
        String collectionName = toCollectionName(def.getApiName());
        boolean hasCaseInsensitiveIndexField = false;
        if (visitor.isPresent()) {
            hasCaseInsensitiveIndexField = (visitor.get()).hasCaseInsensitiveIndexField();
        }
        return customerMongoUtils.count(collectionName, searchCriteria, hasCaseInsensitiveIndexField);
    }

    public boolean hasCaseInsensitiveIndexOnField(EntityDefinition def, String fieldName) {
        return customerMongoUtils.hasCaseInsensitiveIndexOnField(toCollectionName(def.getApiName()), fieldName);
    }

    /**
     * This always sorts by id and will not honor the incoming sorts
     * @param def
     * @param visitor
     * @param cursor
     * @return
     */
    public com.syncari.core.model.pagination.Page<EntityData> search(EntityDefinition def,
                                                                     Optional<? extends MongoCriteria> visitor, PageCursor cursor) {
        Optional<Bson> searchCriteria = visitor.map(v -> v.createCriteria());
        final Boolean hasCaseInsensitiveIndexField = visitor.map(v -> v.hasCaseInsensitiveIndexField()).orElse(false);
        Optional<Bson> searchWithSort = searchCriteria.map(s ->
                StringUtils.isBlank(cursor.getCursor()) ? s : and(s, gt("_id", new ObjectId(cursor.getCursor())))
        ).or(() -> StringUtils.isBlank(cursor.getCursor()) ? Optional.empty() : Optional.of(gt("_id", new ObjectId(cursor.getCursor()))));
        // For now the sorting is implicit, this will be exposed later
        Bson sort = new BasicDBObject("_id", 1);
        Function<Document, EntityData> converter = document -> entityCreate.apply(def, document);
        String collectionName = toCollectionName(def.getApiName());
        int pageSize = cursor.getPageSize();
        List<EntityData> results = customerMongoUtils.searchPaged(collectionName, searchWithSort, sort,
                converter, pageSize + 1, hasCaseInsensitiveIndexField);


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

    public com.syncari.core.model.pagination.Page<EntityData> searchWithCount(EntityDefinition def,
            Optional<Expression> filter, PageCursor pageInfo, Optional<String> syncariId,boolean withCount) {

        if (pageInfo.isOffsetPagination()) {
            return searchWithOffset(def, filter, pageInfo, syncariId, withCount);
        } else {
            return searchWithCursor(def, filter, pageInfo, syncariId, withCount);
        }
    }

    private com.syncari.core.model.pagination.Page<EntityData> searchWithCursor(EntityDefinition def,
            Optional<Expression> filter, PageCursor pageInfo, Optional<String> syncariId,boolean withCount) {
        Optional<Expression> cursorExp = StringUtils.isBlank(pageInfo.getCursor()) ? Optional.empty()
                : Optional.of(getPageFilter(pageInfo));
        Optional<Expression> finalExpression = filter.map(i -> cursorExp.map(c -> Expression.and(i, c)).orElse(i))
                .or(() -> cursorExp);
        Optional<DataCriteriaVisitor> criteriaVisitor = finalExpression
                .map(i -> new DataCriteriaVisitor(i, def.getIdToAttributes(), syncariId));
        Optional<Bson> searchCriteria = criteriaVisitor.map(v -> v.createCriteria());
        log.debug("Search criteria - {}", searchCriteria);
        Bson sort = buildSortCriteria(pageInfo);
        Function<Document, EntityData> converter = document -> this.entityCreate.apply(def, document);
        String collectionName = toCollectionName(def.getApiName());
        List<EntityData> results = customerMongoUtils.searchPaged(collectionName, searchCriteria, sort,
                converter, pageInfo.getPageSize() + 1);

        boolean hasMore = pageInfo.isForward() ? results.size() == pageInfo.getPageSize() + 1 : true;
		boolean hasPrevious = cursorExp.isEmpty() ? false
				: (results.size() == pageInfo.getPageSize() + 1) ? true : !hasMore;

        if (results.size() > pageInfo.getPageSize()) {
            results = results.subList(0, results.size() - 1);
        }

        if (!pageInfo.isForward()) {
            Collections.reverse(results);
        }

        String pageStart = results.size() > 0 ? results.get(0).getId() : null;
        String pageEnd = results.size() > 0 ? results.get(results.size() - 1).getId() : null;
        com.syncari.core.model.pagination.Page<EntityData> page = new com.syncari.core.model.pagination.Page<EntityData>();
        page.setPageInfo(new PageInfo(pageStart, pageEnd, hasMore).addSort("Id", true).setHasPrevious(hasPrevious));
        page.setRecords(results);

        Optional<Bson> noPageFilter = filter.map(i -> new DataCriteriaVisitor(i, def.getIdToAttributes(), syncariId)).map(v -> v.createCriteria());
        if(withCount){
            long totalCount = customerMongoUtils.count(collectionName, Optional.empty());
            // run the filtered count only if total documents are within threshold
            // this is needed as request timesout with current mongo infrastructure if there is large amount of data
            if(noPageFilter.isPresent() && totalCount > COUNT_THRESHOLD) {
                page.getPageInfo().setMessage(I18n.i18n("data_studio_filtered_count_unavailable"));
            } else {
                page.getPageInfo().setFilteredCount(customerMongoUtils.count(collectionName, noPageFilter));
            }
            page.getPageInfo().setTotalCount(totalCount);
        }
        assert page.getRecords().size() <= pageInfo.getPageSize();
        return page;
    }

    private com.syncari.core.model.pagination.Page<EntityData> searchWithOffset(EntityDefinition def,
            Optional<Expression> filter, PageCursor pageInfo, Optional<String> syncariId,boolean withCount) {

        Optional<DataCriteriaVisitor> criteriaVisitor = filter
                .map(i -> new DataCriteriaVisitor(i, def.getIdToAttributes(), syncariId));
        Optional<Bson> searchCriteria = criteriaVisitor.map(v -> v.createCriteria());
        log.debug("Offset pagination - Search criteria: {}, offset: {}", searchCriteria, pageInfo.getOffsetValue());

        Bson sort = buildSortCriteria(pageInfo);
        Function<Document, EntityData> converter = document -> this.entityCreate.apply(def, document);
        String collectionName = toCollectionName(def.getApiName());

        List<EntityData> results = customerMongoUtils.searchPagedWithOffset(
                collectionName,
                searchCriteria,
                sort,
                converter,
                pageInfo.getOffsetValue(),
                pageInfo.getPageSize() + 1);

        boolean hasMore = results.size() == pageInfo.getPageSize() + 1;
        boolean hasPrevious = pageInfo.getPageNumber() > 1;

        if (results.size() > pageInfo.getPageSize()) {
            results = results.subList(0, pageInfo.getPageSize());
        }

        com.syncari.core.model.pagination.Page<EntityData> page = new com.syncari.core.model.pagination.Page<EntityData>();
        PageInfo pageInfoResult = new PageInfo();
        pageInfoResult.setPageNumber(pageInfo.getPageNumber());
        pageInfoResult.setHasMore(hasMore);
        pageInfoResult.setHasPrevious(hasPrevious);
        page.setPageInfo(pageInfoResult);
        page.setRecords(results);

        if(withCount){
            long totalCount = customerMongoUtils.count(collectionName, Optional.empty());
            if(filter.isPresent() && totalCount > COUNT_THRESHOLD) {
                page.getPageInfo().setMessage(I18n.i18n("data_studio_filtered_count_unavailable"));
            } else {
                Optional<Bson> noPageFilter = filter.map(i -> new DataCriteriaVisitor(i, def.getIdToAttributes(), syncariId)).map(v -> v.createCriteria());
                page.getPageInfo().setFilteredCount(customerMongoUtils.count(collectionName, noPageFilter));
            }
            page.getPageInfo().setTotalCount(totalCount);
        }

        assert page.getRecords().size() <= pageInfo.getPageSize();
        return page;
    }
    
    private Bson buildSortCriteria(PageCursor pageInfo) {
        Document sortDoc = new Document();

        if (pageInfo.hasCustomOrdering()) {
            int direction;

            if (pageInfo.isOffsetPagination()) {
                // Offset pagination: sort based on ascending parameter only (direction ignored)
                direction = Boolean.TRUE.equals(pageInfo.getAscending()) ? 1 : -1;
            } else {
                // Cursor pagination: reverse sort for previous direction
                direction = pageInfo.isForward()
                    ? (Boolean.TRUE.equals(pageInfo.getAscending()) ? 1 : -1)
                    : (Boolean.TRUE.equals(pageInfo.getAscending()) ? -1 : 1);
            }

            sortDoc.put(pageInfo.getOrderByField(), direction);
            sortDoc.put("_id", direction);
        } else {
            int direction = pageInfo.isOffsetPagination()
                ? 1
                : (pageInfo.isForward() ? 1 : -1);
            sortDoc.put("_id", direction);
        }

        return sortDoc;
    }

    private Expression getPageFilter(PageCursor pageInfo) {
        Expression lhs = Expression.var("_id");
        Expression rhs = Expression.lit(new ObjectId(pageInfo.getCursor()));
        return pageInfo.isForward() ? Expression.gt(lhs, rhs) : Expression.lt(lhs, rhs);
    }

    public List<EntityData> findByAttribute(String entityName, String attributeName, List<Object> values) {
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));
        FindIterable<Document> iterable = collection.find(in(attributeName, values));
        MongoIterable<EntityData> entities = createEntities(entityName, iterable);
        List<EntityData> target = new ArrayList<>();
        return entities.into(target);
    }

    public int countByAttributeWithMaxLimit(String entityName, String attributeName, Object value, int limit) {
        //This is in assumption that limit will always be <= 2. not optimised for larger limits.
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));
        Bson filter = Filters.eq(attributeName, value);
        FindIterable<Document> iterable = limit > 0 ? collection.find(filter).limit(limit) : collection.find(filter);
        MongoIterable<EntityData> entities = createEntities(entityName, iterable);
        List<EntityData> target = new ArrayList<>();
        return entities.into(target).size();
    }

    public List<EntityData> findByAttribute(String entityName, String attributeName, List<Object> values, PageCursor cursor) {
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));

        var query = in(attributeName, values);
        query = StringUtils.isBlank(cursor.getCursor()) ? query : and(query, gt("_id", new ObjectId(cursor.getCursor())));

        FindIterable<Document> iterable = collection.find(query).limit(cursor.getPageSize() + 1);
        MongoIterable<EntityData> entities = createEntities(entityName, iterable);
        List<EntityData> target = new ArrayList<>();
        entities.into(target);

        if (!target.isEmpty()) {
            cursor.setCursor(target.get(target.size() - 1).getId());
        } else {
            cursor.setCursor("");
        }
        return target;
    }


    public void delete(String entityName) {
        log.info("Deleting entity {} by {}", entityName, SyncariContext.getUser().getName());
        mappingService.delete(entityName);
        if(featureService.isEnabled(Features.Datastore)) {
            datastoreService.findActiveDatastore().ifPresent(datastore -> {
                datastoreService.deleteEntity(schemaService.getEntity(connectorService.getSyncariConnector().getId(), entityName), datastore);
            });
        }
        customerMongoTemplate.dropCollection(toCollectionName(entityName));
        String subject = String.format(I18n.i18n("data_deleted"), StringUtils.capitalize(entityName));
        String body = String.format(I18n.i18n("data_deleted_subject"), StringUtils.capitalize(entityName),
                SyncariContext.getUser().getName());
        notifyService.broadcast(subject,  body, NotificationType.INFO);
        log.info("Successfully deleted entity {}", entityName);
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
        entity.setLastTransactionTimestamp(document.containsKey("lastTransactionTimestamp") ? document.getLong("lastTransactionTimestamp") : 0);
        entity.setSyncariTimestamp(document.getLong(SYNCARI_TIMESTAMP));
        entity.setReparented(document.getBoolean(REPARENTED, false));
        setCreatedAt(document, entity);
        entity.setSyncariScore(getScore(document.get("syncariScore")));
        return entity;
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
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityDefinition.getApiName()));
        FindIterable<Document> iterable = collection.find(in("_id", CollectionUtils.map(ids, id->new ObjectId(id))));
        MongoIterable<EntityData> entities = createEntities(entityDefinition, iterable);
        List<EntityData> target = new ArrayList<>();
        return entities.into(target);
    }

    public Page<EntityData> findEntities(String entityName, Pageable page) {
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));
        FindIterable<Document> iterable = page == Pageable.unpaged()? collection.find(): collection.find().limit(page.getPageSize()).skip((int) page.getOffset());
        long total = collection.estimatedDocumentCount();
        MongoIterable<EntityData> entities = createEntities(entityName, iterable);
        List<EntityData> pagedEntities = new ArrayList<>();
        entities.into(pagedEntities);
        return new PageImpl<>(pagedEntities, page, total);
    }

    public void updateAll(EntityDefinition entityDefinition, List<EntityData> entities) {
        entities.forEach(entity -> update(entityDefinition, entity));
    }

    public void update(EntityDefinition entityDefinition, EntityData entity) {
        String collectionName = toCollectionName(entity.getName());
        Document values =getDocument(entity,true);
        if(entityDefinition!=null) {
            entityDefinition.getActiveAttributes().stream().filter(a -> a.isChild()).forEach(attributeDefinition -> {
                String apiName = attributeDefinition.getApiName();
                Object value = entity.getValue(attributeDefinition.getApiName());
                if (attributeDefinition.isMultiValueField()) {
                    List<EntityData> childRecords = List.class.cast(value);
                    if (childRecords != null) {
                        List<Document> childDocs = childRecords.stream().map(r -> toChildDocument(r)).collect(Collectors.toList());
                        values.append(apiName, childDocs);
                    }
                } else {
                    values.append(apiName, toChildDocument(EntityData.class.cast(value)));
                }
            });
        }
        values.put("_id", new ObjectId(entity.getSyncariEntityId()));
        Query id = Query.query(Criteria.where("_id").is(new ObjectId(entity.getSyncariEntityId())));
        Update update = new Update();
        values.forEach((key,value)-> update.set(key, value));
        customerMongoTemplate.findAndModify(id, update, FindAndModifyOptions.options().upsert(false).returnNew(false),Document.class, collectionName);
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData entity, boolean changeTimestamp) {
        String collectionName = toCollectionName(entity.getName());
        Document values = getDocument(entity,changeTimestamp);
        if(entityDefinition!=null) {
            entityDefinition.getActiveAttributes().stream().filter(a -> a.isChild()).forEach(attributeDefinition -> {
                String apiName = attributeDefinition.getApiName();
                Object value = entity.getValue(attributeDefinition.getApiName());
                if (attributeDefinition.isMultiValueField()) {
                    List<EntityData> childRecords = List.class.cast(value);
                    if (childRecords != null) {
                        List<Document> childDocs = childRecords.stream().map(r -> toChildDocument(r)).collect(Collectors.toList());
                        values.append(apiName, childDocs);
                    }
                } else {
                    values.append(apiName, toChildDocument(EntityData.class.cast(value)));
                }
            });
        }
        Document saved;
        if (entity.getSyncariEntityId() != null) {
            values.put("_id", new ObjectId(entity.getSyncariEntityId()));
            Query id = Query.query(Criteria.where("_id").is(new ObjectId(entity.getSyncariEntityId())));
            Update update = new Update();
            values.forEach((key,value)-> update.set(key, value));
            saved = customerMongoTemplate.findAndModify(id, update, FindAndModifyOptions.options().upsert(true).returnNew(true),Document.class, collectionName);
        } else {
            values.put("_id", ObjectId.get());
            saved =customerMongoTemplate.insert(values, collectionName);
        }
        entity.setSyncariEntityId(((ObjectId)values.get("_id")).toHexString());
        entity.setId(((ObjectId)values.get("_id")).toHexString());
        entity.setLastTransactionLogId(saved.getString("lastTransactionLogId"));
        entity.setLastTransactionTimestamp(saved.containsKey("lastTransactionTimestamp") ? saved.getLong("lastTransactionTimestamp") : 0);
        if(entityDefinition!=null) {
            entityDefinition.getActiveAttributes().stream().filter(a -> a.isChild()).forEach(attributeDefinition -> {
                String apiName = attributeDefinition.getApiName();
                Object value = saved.get(attributeDefinition.getApiName());
                if (value instanceof List) {
                    List<Document> childRecords = List.class.cast(value);
                    if (childRecords != null) {
                        List<EntityData> childRecordEDs = childRecords.stream().map(r -> toEntityData(r)).collect(Collectors.toList());
                        saved.append(apiName, childRecordEDs);
                    }
                } else if(value instanceof Document){
                    saved.append(apiName, toEntityData(Document.class.cast(value)));
                }
            });
        }
        entity.getValues().putAll(saved);
        return entity;
    }

    public List<EntityData> saveAll(EntityDefinition entityDefinition, List<EntityData> entities, boolean changeTimestamp) {

        if (entities.isEmpty())
            return entities;

        String collectionName = toCollectionName(entities.get(0).getName());
        entities = entities.stream().map(e -> {
            return e.getSyncariEntityId() == null ? e.setSyncariEntityId(ObjectId.get().toHexString()) : e;
        }).collect(Collectors.toList());

        var valuesList = entities.stream().map(entity -> {
            Document values = getDocument(entity,changeTimestamp);
            values.put("_id", new ObjectId(entity.getSyncariEntityId()));
            if(entityDefinition!=null) {
                entityDefinition.getActiveAttributes().stream().filter(a -> a.isChild()).forEach(attributeDefinition -> {
                    String apiName = attributeDefinition.getApiName();
                    Object value = entity.getValue(attributeDefinition.getApiName());
                    if (attributeDefinition.isMultiValueField()) {
                        List<EntityData> childRecords = List.class.cast(value);
                        if (childRecords != null) {
                            List<Document> childDocs = childRecords.stream().map(r -> toChildDocument(r)).collect(Collectors.toList());
                            values.append(apiName, childDocs);
                        }
                    } else {
                        values.append(apiName, toChildDocument(EntityData.class.cast(value)));
                    }
                });
            }
            return values;
        }).collect(Collectors.toList());

        var updates = valuesList.stream().map(values -> {
            Query id = Query.query(Criteria.where("_id").is(values.getObjectId("_id")));
            Update update = new Update();
            values.forEach((key,value)-> update.set(key, value));
            return Pair.of(id, update);
        }).collect(Collectors.toList());

        var saved = customerMongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, collectionName).upsert(updates).execute();
        if (saved.wasAcknowledged()) {
            for (int index = 0; index < valuesList.size(); index++) {
                entities.get(index).getValues().putAll(valuesList.get(index));
            }
        }
        return entities;
    }

    private EntityData toEntityData(Document doc) {
        if(doc == null){
            return null;
        }
        return ChildType.CONVERTERS.get(Map.class).apply(doc);
    }

    private Document toChildDocument(EntityData r) {
        if(r==null){
            return null;
        }

        r.getValues().entrySet().stream().forEach(entry -> {
            if (entry.getValue() != null && entry.getValue() instanceof ZonedDateTime) {
                entry.setValue(new ZonedDateTimeWriteConverter().convert((ZonedDateTime) entry.getValue()));
            }
        });

        return getDocument(r,false)
                .append("syncariId",r.getSyncariEntityId())
                .append("syncariEntityName",r.getName());
    }

    public EntityData save(EntityDefinition entityDefinition, EntityData entity) {
        return save(entityDefinition, entity,true);
    }

    private Document getDocument(EntityData entity, boolean changeTimestamp) {
        Document values =new Document(entity.getValues());
        values.put("lastModified",entity.getLastModified());
        if (entity.getCreatedAt() > 0) {
            values.put("createdAt", entity.getCreatedAt());
        } else {
            var createdDate = entity.getValue("CreatedDate");
            if (createdDate != null && Date.class.isAssignableFrom(createdDate.getClass())) {
                values.put("createdAt", ((Date) createdDate).toInstant().toEpochMilli());
            } else if (createdDate != null && ZonedDateTime.class.isAssignableFrom(createdDate.getClass())) {
                values.put("createdAt", ((ZonedDateTime) createdDate).toInstant().toEpochMilli());
            }
        }
        if (changeTimestamp) {
            values.put(SYNCARI_TIMESTAMP, Instant.now().toEpochMilli());
        }
        values.put(IS_DELETED, entity.isDeleted());
        values.put(REPARENTED, entity.isReparented());
        values.put("syncariScore", entity.getSyncariScore());
        if(!StringUtils.isBlank(entity.getLastTransactionLogId())) {
            values.put("lastTransactionLogId", entity.getLastTransactionLogId());
        }
        if (entity.getLastTransactionTimestamp() > 0) {
            values.put("lastTransactionTimestamp", entity.getLastTransactionTimestamp());
        }
        if (entity.getSyncariCreatedAt() > 0) {
            values.put("syncariCreatedAt", entity.getSyncariCreatedAt());
        }
        if(entity.getOriginatingConnectorId()!=null) {
            values.put("originatingConnectorId", entity.getOriginatingConnectorId());
        }
        values.put("lastModified",entity.getLastModified());
        values.put("dedupeHash",entity.getDedupeHash());
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

    /*public List<EntityData> saveAll(EntityDefinition entityDefinition, List<EntityData> entities, boolean changeTimestamp) {
        return CollectionUtils.map(entities, entity -> save(entityDefinition,entity, changeTimestamp));
    }
*/
    /**
     * Updates existing syncari records with updatedValues. The records are matched by syncariEntityId. Deleted records are not updated.
     * @param entityDefinition - the entity definition
     * @param updatedValues - only the values from actual attributess in getValues are considered . syncariEntityId must be set. Rest of the system fields are ignored
     */
    public void updateValues(EntityDefinition entityDefinition, List<EntityData> updatedValues) {
        if(updatedValues.isEmpty()) {
            return;
        }
        String collectionName = toCollectionName(entityDefinition.getApiName());
        List<Pair<Query,Update>> updates= updatedValues.stream().map(entity -> {
            Update update =new Update();
            entity.getValues().forEach((attributeName, value) -> {
                if (entityDefinition.hasField(attributeName)) {
                    update.set(attributeName,value);
                }
            });
            if(!update.getUpdateObject().isEmpty()){
                long timestamp = Instant.now().toEpochMilli();
                update.set(SYNCARI_TIMESTAMP, timestamp);
                entity.setSyncariTimestamp(timestamp);
            }
            update.set("lastModified", entity.getLastModified());
           return Pair.of(
                    new Query().addCriteria(where("_id").is(new ObjectId(entity.getSyncariEntityId())).and("isDeleted").is(false)),
                    update
            );
        }).filter(update->!update.getSecond().getUpdateObject().isEmpty()).collect(Collectors.toList());
        final BulkWriteResult results = customerMongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                .updateMulti(updates)
                .execute();
        log.debug("Updated {} {} records with results {}",updates.size(), entityDefinition.getApiName(), results);
    }

    public void updateLastTransaction(EntityDefinition entityDefinition, List<EntityData> entities) {

        if (entities.isEmpty()) {
            return;
        }

        String collectionName = toCollectionName(entityDefinition.getApiName());
        List<Pair<Query,Update>> updates = entities.stream().map(entity -> {
            Update update = new Update();
            update.set("lastTransactionLogId", entity.getLastTransactionLogId());
            update.set("lastTransactionTimestamp", entity.getLastTransactionTimestamp());
            return Pair.of(
                    new Query().addCriteria(where("_id").is(new ObjectId(entity.getSyncariEntityId())).and("isDeleted").is(false)),
                    update
            );
        }).collect(Collectors.toList());
        final BulkWriteResult results = customerMongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                .updateMulti(updates)
                .execute();
        log.debug("Updated Last Transaction ID for {} {} records with results {}",updates.size(), entityDefinition.getApiName(), results);
    }

    private MongoIterable<EntityData> createEntities(String entityName, FindIterable<Document> documents) {
        return documents.map(document -> createEntity(entityName, document));
    }

    private MongoIterable<EntityData> createEntities(EntityDefinition entityDefinition, FindIterable<Document> documents) {
        return documents.map(document -> this.entityCreate.apply(entityDefinition, document));
    }

    public String toCollectionName(String entityName) {
        return "syncari_" + entityName.toLowerCase();
    }

    public void deleteAll(String entityName) {
        if(featureService.isEnabled(Features.Datastore)) {
          datastoreService.findActiveDatastore().ifPresent(datastore -> {
              datastoreService.deleteEntity(schemaService.getEntity(connectorService.getSyncariConnector().getId(), entityName), datastore);
          });
        }
        customerMongoTemplate.getCollection(toCollectionName(entityName)).drop();
    }

    public void deleteAll(List<EntityData> entityDataList) {
        Map<String, List<EntityData>> byEntityName = entityDataList.stream().collect(Collectors.groupingBy(e->e.getName()));
        byEntityName.forEach((entityName, entities) -> {
            MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));
            collection.deleteMany(Filters.in("_id",entities.stream().map(e->new ObjectId(e.getSyncariEntityId())).collect(Collectors.toList())));
            if(featureService.isEnabled(Features.Datastore)) {
                datastoreService.findActiveDatastore().ifPresent(datastore -> {
                    datastoreService.deleteAll(schemaService.getEntity(connectorService.getSyncariConnector().getId(), entityName), datastore, entities);
                });
            }
        });
    }
    public void delete(List<String> recordIds, String entityName) {
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));
        collection.deleteMany(Filters.in("_id",recordIds.stream().map(e->new ObjectId(e)).collect(Collectors.toList())));
    }

    public void markDeleted(List<String> recordIds, String entityName) {
        MongoCollection<Document> collection = customerMongoTemplate.getCollection(toCollectionName(entityName));
        collection.updateMany(Filters.in("_id",recordIds.stream().map(e->new ObjectId(e)).collect(Collectors.toList())),new Document("$set",new Document("isDeleted",true)));
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
        AggregateIterable<Document> aggregate = customerMongoTemplate.getCollection(toCollectionName(entity)).aggregate(
                List.of(Aggregates.match( filter), Aggregates.group(null,accumulator))
        );
        if(aggregate.iterator().hasNext()){
            final Document aggregateResult = aggregate.iterator().next();
            final Double doubleValue = DoubleType.VALUE.convert(aggregateResult.get("aggregate"));
            return  doubleValue == null ? 0.0d : doubleValue;
        }
        return 0.0d;
    }

	public void setDatastoreService(DatastoreService datastoreService) {
		this.datastoreService = datastoreService;
	}
	
	public void removeExternalIdFields(String entityApiName, Set<String> externalIds) {
		if(org.apache.commons.collections4.CollectionUtils.isEmpty(externalIds)) {
			return;
		}
		Query query = new Query();
		Update update = new Update();
		externalIds.forEach(extId -> {
			update.unset(extId);
		});
		customerMongoTemplate.updateMulti(query, update, toCollectionName(entityApiName));
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
		try {
			String collectionName = toCollectionName(entityName);

			Query query = new Query();
			query.addCriteria(Criteria.where("fromObjectId").is(fromObjectId));
			query.addCriteria(Criteria.where("toObjectId").is(toObjectId));
			query.addCriteria(Criteria.where("toObjectType").is(toObjectType));
			query.addCriteria(Criteria.where("isDeleted").ne(true));

			List<Map> results = customerMongoTemplate.find(query, Map.class, collectionName);
			log.debug("Found {} matching associations", results.size());

			return (List) results;

		} catch (Exception e) {
			log.error("Error querying matching associations: {}", e.getMessage(), e);
			return List.of();
		}
	}
}
