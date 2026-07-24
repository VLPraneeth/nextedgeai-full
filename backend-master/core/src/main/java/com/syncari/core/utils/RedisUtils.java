package com.syncari.core.utils;



import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.model.pagination.PageCursor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.opensaml.xmlsec.signature.Q;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.*;

import static redis.clients.jedis.search.querybuilder.QueryBuilders.*;

import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.AggregationResult;
import redis.clients.jedis.search.querybuilder.Node;
import redis.clients.jedis.search.querybuilder.QueryNode;
import redis.clients.jedis.search.querybuilder.Values;
//import redis.clients.jedis.search.schemafields.TagField;

import javax.mail.search.SearchTerm;
import javax.print.Doc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.syncari.core.validation.DBPredicateValidator.FIELD_OUTPUT_PATTERN;

@Slf4j
@Component
public class RedisUtils {

    @Autowired
    protected JedisPooled redisClient;

    protected static final String INDEX_FORMAT = "syncari_%s_%s_idx";
    private static String NULL_INDEX_FIELD = "nullFields";
    private static String PATH_PREFIX = "$.";
    private static String NULL_FIELD_SEPARATOR = ",";
    public static final String NULL_FIELDS = "__nf";


    public String getEntityIndex(String instanceId, String entity) {
        return String.format(INDEX_FORMAT, instanceId, entity);
    }

    public void constructPipelineIndex(String instanceId,  EntityDefinition entityDefinition,  List<CacheIndexAttribute> indexFields){
        String indexName = getEntityIndex(instanceId, entityDefinition.getApiName());
        String[] indexPrefix = {instanceId + ":" + "e:" + entityDefinition.getApiName()};
        this.constructIndex(indexName, indexPrefix, indexFields);
    }

    public void constructIndex(String indexName, String[] indexPrefix, List<CacheIndexAttribute> indexFields){
        IndexDefinition def = new IndexDefinition(IndexDefinition.Type.JSON).setPrefixes(indexPrefix);
        IndexOptions options = IndexOptions.defaultOptions().setDefinition(def);
        Schema schema = new Schema();
        Map<String, Schema.Field> schemaFields = new HashMap<>();

        for (CacheIndexAttribute field :indexFields) {

            Optional<Schema.Field> schemaField = getNewFieldForCacheAttribute(field, schemaFields);
            if (schemaField.isPresent()) {
                schema = schema.addField(schemaField.get());
            }
        }
        try{
            redisClient.ftCreate(indexName, options, schema);
        }catch (JedisDataException jedisDataException){
            log.info("Could not create the index {} with schema {}, exception occurred {}",indexName,schema, ExceptionUtils.getStackTrace(jedisDataException));
            throw jedisDataException;
        }
    }

    public boolean indexExists(String indexName){
        return MapUtils.isNotEmpty(this.getIndexInfo(indexName));
    }

    public boolean indexStatus(String entityName) {
        Map<String, Object> indexInfo = this.getIndexInfo(getEntityIndex(SyncariContext.getSyncariId(), entityName));

        if (!indexInfo.containsKey("num_docs")) {
            return false;
        }

        int numDocs = Integer.parseInt(indexInfo.get("num_docs").toString());
        int hashIndexingFailures = indexInfo.containsKey("hash_indexing_failures") ? Integer.parseInt(indexInfo.get("hash_indexing_failures").toString()) : 0;

        double failureRate = numDocs == 0 ? 0 : hashIndexingFailures / (double)numDocs;

        return failureRate > 0.1d ? false : true;
    }

    public int indexFailures(String entityName) {
        Map<String, Object> indexInfo = this.getIndexInfo(getEntityIndex(SyncariContext.getSyncariId(), entityName));
        return indexInfo != null && indexInfo.containsKey("hash_indexing_failures") ? Integer.parseInt(indexInfo.get("hash_indexing_failures").toString()) : 0;
    }


    public Map<String, Object> getIndexInfo(String indexName){
        Map<String, Object> indexInfo = null;
        try{
            indexInfo = redisClient.ftInfo(indexName);
        }catch (JedisDataException jedisDataException){
            log.info("Could not find the index info {} exception occurred {}", indexName, ExceptionUtils.getStackTrace(jedisDataException));
        }
        return indexInfo;
    }

    public void dropIndex(String indexName){
        if (indexExists(indexName)){
            redisClient.ftDropIndex(indexName);
        }
    }

    public void alterIndex(String indexName,List<CacheIndexAttribute> newFieldsToAdd){
        Schema schema = new Schema();
        Map<String, Schema.Field> schemaFields = new HashMap<>();

        for (CacheIndexAttribute field :newFieldsToAdd) {
            Optional<Schema.Field> schemaField = getNewFieldForCacheAttribute(field, schemaFields);
            if (schemaField.isPresent()) {
                schema = schema.addField(schemaField.get());
            }
        }
        try{
            redisClient.ftAlter(indexName, schema);
        }catch (JedisDataException jedisDataException){
            log.info("Could not alter the index {},with schema {}, exception occurred {}", indexName,schema,ExceptionUtils.getStackTrace(jedisDataException));
            throw jedisDataException;
        }
    }

    public void constructOrAlterIndex(String instanceId, EntityDefinition entityDefinition, List<CacheIndexAttribute> cacheIndexAttributes){
        String indexName = getEntityIndex(instanceId, entityDefinition.getApiName());
        if (!indexExists(indexName)){
            constructPipelineIndex(instanceId, entityDefinition, cacheIndexAttributes);
        }else{
            Map<String, Object> indexInfo = getIndexInfo(indexName);
            List<Object> allAttributesList = (List)(indexInfo.getOrDefault("attributes", List.of()));
            List<String> attributesUsed = new ArrayList<>();
            // this is based on existing api and return of index info
            allAttributesList.forEach(a -> {
                attributesUsed.add(((List)a).get(3).toString());
            });
            // Just add those attributes for which cache keys does not exists
            List<CacheIndexAttribute> cacheIndexAttributesToBeUsed = new ArrayList<>();
            cacheIndexAttributes.forEach(ci -> {
                String aliasToCheck = ci.isCaseInSensitive() ? ci.getAlias() + "_i" : ci.getAlias();
                if (!attributesUsed.contains(aliasToCheck)){
                    cacheIndexAttributesToBeUsed.add(ci);
                }
            });
            if (CollectionUtils.isNotEmpty(cacheIndexAttributesToBeUsed)){
                alterIndex(indexName, cacheIndexAttributesToBeUsed);
                log.info("Index altered with new attributes {}", cacheIndexAttributesToBeUsed);
            }else{
                log.info("Index not altered as attributes are same which already exists in index, Existing index map is {} and attributes to be added is {}", indexInfo, cacheIndexAttributes);
            }
        }
    }

    public CacheIndexAttribute createCacheIndexAttribute(String variableName, boolean isCaseInSensitive, Optional<EntityDefinition> entityDefinition) {
        return createCacheIndexAttribute(variableName, isCaseInSensitive, false, entityDefinition);
    }

    public CacheIndexAttribute createCacheIndexAttribute(String variableName, boolean isCaseInSensitive, boolean sortable, Optional<EntityDefinition> entityDefinition) {
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : variableName;
        CacheIndexAttribute cacheIndexAttribute = new CacheIndexAttribute();
        cacheIndexAttribute.setSortable(sortable);
        cacheIndexAttribute.setCaseInSensitive(isCaseInSensitive);
        entityDefinition.ifPresent(e -> {
            Optional<AttributeDefinition> attr = e.getAttributes().stream()
                    .filter(a -> a.getId().equals(attributeId)).findFirst();
            attr.ifPresent(a->{
                cacheIndexAttribute.setPath(a.getApiName());
                cacheIndexAttribute.setAlias(a.getApiName());
                cacheIndexAttribute.setDataType(a.getDataType());
            });
        });
        return  cacheIndexAttribute;
    }

    public CacheIndexAttribute createSystemIndexAttribute(String schema, Datatype dataType, boolean isCaseInSensitive) {
        CacheIndexAttribute cacheIndexAttribute = new CacheIndexAttribute();
        cacheIndexAttribute.setCaseInSensitive(isCaseInSensitive);
        cacheIndexAttribute.setSortable(false);
        cacheIndexAttribute.setPath(schema);
        cacheIndexAttribute.setAlias(schema);
        cacheIndexAttribute.setDataType(dataType);
        return  cacheIndexAttribute;
    }

    public CacheIndexAttribute createNullField() {
        CacheIndexAttribute cacheIndexAttribute = new CacheIndexAttribute();
        cacheIndexAttribute.setCaseInSensitive(false);
        cacheIndexAttribute.setPath(NULL_FIELDS);
        cacheIndexAttribute.setAlias(NULL_FIELDS);
        cacheIndexAttribute.setDataType(StringType.VALUE);
        cacheIndexAttribute.setSeparator(NULL_FIELD_SEPARATOR);
        return  cacheIndexAttribute;
    }



    public long count(String instanceName, String entityName, SearchCriteria criteria) {

        var predicate = criteria.getSearchFieldNameValues().entrySet().stream().map(entry -> {
            if (entry.getValue() != null) {
                //TODO: fix this Use cache value convertor
                return intersect(entry.getKey(), Values.tags(entry.getValue().toString()));
            } else {
                // TODO: This nul
                return intersect(NULL_INDEX_FIELD, Values.tags(entry.getKey()));
            }
        }).reduce((p1, p2) -> intersect(p1, p2)).map(QueryNode::toString);

        return predicate.map(p -> redisClient.ftSearch(getEntityIndex(instanceName, entityName), predicate.get()).getTotalResults()).orElseThrow(() -> new RuntimeException("Invalid criteria"));
    }

    // String entityName, Optional<Node> criteria,
    public long count(String entityName, Optional<Node> criteria) {
        String indexName = getEntityIndex(SyncariContext.getSyncariId(), entityName);

        return criteria.map(c -> {
            Query query = constructQuery(c.toString(Node.Parenthesize.ALWAYS), List.of(), 1);
            log.debug("Criteria for the query is {}" + c.toString(Node.Parenthesize.ALWAYS));
            return redisClient.ftSearch(indexName, query).getTotalResults();
        }).orElseGet(() -> {
            return 0L;
        });
    }

    public List<Document> searchPaged(String entityName, Optional<Node> criteria, List<LookupCriteriaVisitor.Sort> sortBy, int pageSize) {

        String indexName = getEntityIndex(SyncariContext.getSyncariId(), entityName);

        var searchResult = criteria.map(c -> {
            Query query = constructQuery(c.toString(), sortBy, pageSize);
            log.debug("Criteria for the query is {}" + c.toString());
            return redisClient.ftSearch(indexName, query);
        }).orElseGet(() -> {
            return redisClient.ftSearch(indexName, constructQuery("", sortBy, pageSize));
        });

        return searchResult.getDocuments().stream().map(d -> Document.parse((String)d.get("$"))).collect(Collectors.toList());
    }

    public List<Document> searchWithCursor(String entityName, Optional<Node> criteria, List<LookupCriteriaVisitor.Sort> sortBy, PageCursor cursor) {

        String indexName = getEntityIndex(SyncariContext.getSyncariId(), entityName);
        String query = criteria.map(c -> c.toString()).orElse("");

        SearchResult searchResult = null;
        int offset = cursor.getCursor() == null ? 0 : Integer.parseInt(cursor.getCursor());
        if (cursor.getCursor() == null) {
            log.debug("Criteria for the query is {}" + query);
            searchResult = redisClient.ftSearch(indexName, constructQuery(query, sortBy, cursor.getPageSize()));
        } else {
            // cursor read
            searchResult = redisClient.ftSearch(indexName, constructQuery(query, sortBy, offset, cursor.getPageSize()));
        }

        var results = searchResult.getDocuments().stream().map(d -> Document.parse((String)d.get("$"))).collect(Collectors.toList());
        cursor.setCursor(Integer.toString(results.size() + offset));
        return results;
    }

    private Schema.FieldType getFieldTypeForDataType(String dataType){
        if (StringUtils.isEmpty(dataType)){
            throw new SyncariValidationException("Cache Index Attribute data type is required field.");
        }
        switch (dataType){
            case "integer":
            case "boolean":
            case "datetime":
            case "date":
            case "double":
            case "long":
            case "timestamp":
                return Schema.FieldType.NUMERIC;
            default:
                return Schema.FieldType.TAG;
        }
    }

    private Optional<Schema.Field> getNewFieldForCacheAttribute(CacheIndexAttribute cacheIndexAttribute, Map<String, Schema.Field> schemaFields) {

        Datatype type = cacheIndexAttribute.getDataType();
        Schema.FieldType fieldType = getFieldTypeForDataType(type.getName());
        String fieldAlias = cacheIndexAttribute.getAlias();
        Schema.Field schemaField = null;
        if (fieldType.equals(Schema.FieldType.NUMERIC)) {
            schemaField = new Schema.Field(FieldName.of(PATH_PREFIX + cacheIndexAttribute.getPath()).as(cacheIndexAttribute.getAlias()), fieldType, cacheIndexAttribute.isSortable(), false);
        } else if (fieldType.equals(Schema.FieldType.TAG)) {
            fieldAlias = cacheIndexAttribute.isCaseInSensitive() ? cacheIndexAttribute.getAlias() + "_i" : cacheIndexAttribute.getAlias();
            schemaField = new Schema.TagField(FieldName.of(PATH_PREFIX + cacheIndexAttribute.getPath()).as(fieldAlias),
                    cacheIndexAttribute.getSeparator(), !cacheIndexAttribute.isCaseInSensitive(), cacheIndexAttribute.isSortable());
        }

        if (schemaFields.containsKey(fieldAlias)) {
            return Optional.empty();
        }
        schemaFields.put(fieldAlias, schemaField);
        return Optional.of(schemaField);
    }

    private Query constructQuery(String criteria, List<LookupCriteriaVisitor.Sort> sortBy, int pageSize) {
        return constructQuery(criteria, sortBy, 0, pageSize);
    }

    private Query constructQuery(String criteria, List<LookupCriteriaVisitor.Sort> sortBy, int limit, int pageSize) {

        Query query = StringUtils.isEmpty(criteria) ? new Query() : new Query(criteria);

        for (LookupCriteriaVisitor.Sort sort : sortBy) {
            query = query.setSortBy(sort.sortField, sort.sortDirection.equalsIgnoreCase("asc"));
        }
        return query.limit(limit, pageSize).dialect(2);
    }

}
