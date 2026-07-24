package com.syncari.core.utils;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;
import static com.syncari.core.validation.DBPredicateValidator.FIELD_OUTPUT_PATTERN;
import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mongodb.client.model.*;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoCommandException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.result.DeleteResult;
import com.syncari.core.Index;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class MongoUtils {
    private static final String _ID = "_id";
    public static final int MAX_INDEXES = 64;
    public static final int MAX_INDEX_NAME_LENGTH = 127;

    public static final String EN_LANGUAGE = "en_US";

    protected abstract String getDB();

    protected abstract MongoDbFactory getMongoDBFactory();

    protected abstract MongoTemplate getMongoTemplate();

    private static final Set<Character> IGNORE_CHARACTERS = Set.of(' ', '"', '!', '@', '#', '$', '%', '^', '&', '*',
            '(', ')', '-', '_', '=', '+', '/', '?', ',', '.', '<', '>', '[', ']', '{', '}', '\\', '|', '`', '~', '\'',
            ';', ':');

    private Set<String> caseInsensitiveIndexedFields = new HashSet<>();

    public void createCollection(String collectionName, List<String> indexedFields) {
        if (StringUtils.isBlank(collectionName))
            throw new RuntimeException("Collection name is required");
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        if (!existsCollection(collectionName)) {
            db.createCollection(collectionName);
            log.debug(format("Successfully created collection %s in db %s", collectionName, getDB()));
        }

        MongoCollection<Document> collection = db.getCollection(collectionName);
        if (indexedFields != null && !indexedFields.isEmpty()) {
            List<Document> existingIndexes = new ArrayList<>();
            collection.listIndexes().into(existingIndexes);
            Set<String> existingIndexNames = existingIndexes.stream().map(e -> e.get("name").toString()).collect(Collectors.toSet());
            String textIndexName = collectionName + "_text_index";
            if (!existingIndexNames.contains(textIndexName)) {
                Map<String, String> textIndexMap = new HashMap<>();
                for (String i : indexedFields) {
                    textIndexMap.put(i, "text");
                }
                collection.createIndex(new BasicDBObject(textIndexMap), new IndexOptions().name(textIndexName).background(true));
            }
        }
    }

    public boolean existsCollection(String name) {
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        List<String> existing = new ArrayList<>();
        db.listCollectionNames().into(existing);
        return existing.contains(name);
    }

    private List<Document> findIndexesOnField(String collectionName, String field) {
        if (StringUtils.isBlank(collectionName))
            throw new RuntimeException("Collection name is required");
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        MongoCollection<Document> collection = db.getCollection(collectionName);
        if (collection == null) {
            throw new RuntimeException("Collection " + collectionName + " not found");
        }
        List<Document> existingIndexes = new ArrayList<>();
        collection.listIndexes().into(existingIndexes);
        return existingIndexes.stream()
            .filter(e -> ((Set<String>) ((Document) e.get("key")).keySet()).contains(field)).collect(Collectors.toList());
    }

    public boolean hasIndexOnField(String collectionName, String field) {
        return findIndexesOnField(collectionName, field).size() > 0;
    }

    private boolean isSupportedCaseInsensitiveIndex(Document index) {
        if (index.containsKey("collation") && EN_LANGUAGE.equalsIgnoreCase(((Document) index.get("collation")).get("locale").toString()) 
            && List.of("1","2").contains(((Document) index.get("collation")).get("strength").toString())) {
            return true;
        }
        return false;
    }

    public boolean hasCaseInsensitiveIndexOnField(String collectionName, String field) {
        List<Document> indexes = findIndexesOnField(collectionName, field);
        return indexes.stream().filter(x -> isSupportedCaseInsensitiveIndex(x)).findAny().isPresent();
        /* TODO: we could cache this to speed up performance but the cache has to be shortlived to pick db changes.
        String key = collectionName+"_"+field;
        if (!caseInsensitiveIndexedFields.contains(key)) {
            List<Document> indexes = findIndexesOnField(collectionName, field);
            if (indexes.stream().filter(x -> isSupportedCaseInsensitiveIndex(x)).findAny().isPresent()) {
                caseInsensitiveIndexedFields.add(key);
            }
        } 
        return caseInsensitiveIndexedFields.contains(key);
        */
    }
    

    public void createFieldIndexes(String collectionName, List<String> indexedFields) {
        if (StringUtils.isBlank(collectionName))
            throw new RuntimeException("Collection name is required");
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        List<String> existing = new ArrayList<>();
        db.listCollectionNames().into(existing);
        if (!existing.contains(collectionName)) {
            db.createCollection(collectionName);
        }
        MongoCollection<Document> collection = db.getCollection(collectionName);
        if (indexedFields != null) {
            List<Document> existingIndexes = new ArrayList<>();
            collection.listIndexes().into(existingIndexes);
            Set<String> existingIndexNames = existingIndexes.stream().map(e -> e.get("name").toString()).collect(Collectors.toSet());
            var newIndexes = indexedFields.stream()
                    .map(f -> Pair.of(getUniqueIndexName(db.getName(), collectionName, String.format("idx_%s_%s", collectionName, f)), f))//pair of index name & field name
                    .filter(idx -> !existingIndexNames.contains(idx.x))//pick only unindexed fields
                    .map(idx -> Pair.of(new BasicDBObject(Map.of(idx.y, 1)), new IndexOptions().name(idx.x).background(true))) //pair of index definition and index options
                    .collect(Collectors.toList());
            if (existingIndexes.size() + newIndexes.size() > MAX_INDEXES) {
                throw new RuntimeException("Cannot create more than 64 indexes");
            }

            try {
                newIndexes.forEach(f -> {
                    collection.createIndex(f.x, f.y);
                });
                log.debug(format("Successfully created indexes for collection %s in db %s", collectionName, getDB()));
            } catch (MongoCommandException e) {
                if ("IndexOptionsConflict".equalsIgnoreCase(e.getErrorCodeName())) {
                    // do nothing. we are good, its already there.
                    log.warn("There is an existing index with the same fields options. So ignoring index creation", e);
                } else {
                    throw e;
                }
            }
        }
    }

    public static String getUniqueIndexName(String dbName, String collectionName, String indexName) {
        String potentialFullName = String.format("%s.%s.%s", dbName, collectionName, indexName);
        if (StringUtils.length(potentialFullName) < MAX_INDEX_NAME_LENGTH) {
            return indexName;
        }
        return String.format("idx_%s", String.valueOf(potentialFullName.hashCode()));
    }

    public void dropCollection(String collectionName) {
        log.info(format("Dropping collection %s from db %s", collectionName, getDB()));
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        MongoCollection<Document> myCollection = db.getCollection(collectionName);
        myCollection.drop();
        log.info(format("Successfully dropped collection %s", collectionName));
    }
    
    public void dropDb(String dbName) {
        log.info(format("Dropping db %s", dbName));
        MongoDatabase db = getMongoDBFactory().getDb(dbName);
        db.drop();
        log.info(format("Successfully dropped db %s", dbName));
    }

    public List<String> insertMany(String collectionName, List<Map<String, Object>> values) {
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        MongoCollection<Document> collection = db.getCollection(collectionName);
        List<Document> docs = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> row : values) {
            Document doc = new Document();
            row.forEach(doc::append);
            docs.add(doc);
            i++;
            if (i >= 100) {
                collection.insertMany(docs);
                docs = new ArrayList<>();
                i = 0;
            }
        }
        if (!docs.isEmpty()) {
            collection.insertMany(docs);
        }
        return docs.stream().map(d -> ((ObjectId)d.get("_id")).toHexString()).collect(Collectors.toList());
    }
    
    public int updateMany(String collectionName, Map<String, Map<String, Object>> rows) {
        if(rows == null || rows.isEmpty()) {
            return 0;
        }
    	List<org.springframework.data.util.Pair<Query,Update>> updates = new ArrayList<>();
    	for (Entry<String, Map<String, Object>> entry : rows.entrySet()) {
    		Update update = new Update();
    		entry.getValue().forEach((k, v) -> {
    			update.set(k, v);
            });
    		updates.add(org.springframework.data.util.Pair.of(
                    new Query().addCriteria(where("_id").is(new ObjectId(entry.getKey()))),
                    update
            ));
		}
        final BulkWriteResult results = getMongoTemplate().bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                .updateMulti(updates)
                .execute();
        log.info("Updated {} records with results {}", rows.size(), results);
        return results.getModifiedCount();
    }
    
    public long deleteMany(String collectionName, List<String> ids) {
    	if(ids == null || ids.isEmpty()) return 0;
    	MongoDatabase db = getMongoDBFactory().getDb(getDB());
    	MongoCollection<Document> collection = db.getCollection(collectionName);
		DeleteResult deleteResult = collection
				.deleteMany(Filters.in("_id", ids.stream().map(e -> new ObjectId(e)).collect(Collectors.toList())));
    	return deleteResult.getDeletedCount();
    }

    public long deleteAll(String collectionName) {
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        MongoCollection<Document> collection = db.getCollection(collectionName);
        DeleteResult deleteResult = collection.deleteMany(new Document());
        return deleteResult.getDeletedCount();
    }

    public List<Map<String, String>> readMany(String collectionName, int limit, Optional<String> cursor) {
        List<Map<String, String>> values = new ArrayList<>();
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        MongoCollection<Document> collection = db.getCollection(collectionName);
        limit = (limit == 0 || limit > 1000) ? 1000 : limit;
		FindIterable<Document> iterable = cursor.isEmpty() ? collection.find().sort(new BasicDBObject(_ID, 1)).limit(limit)
				: collection.find(Filters.gt(_ID, new ObjectId(cursor.get()))).sort(new BasicDBObject(_ID, 1)).limit(limit);
        for (Document document : iterable) {
            Map<String, String> row = new HashMap<>();
            for (Entry<String, Object> entry : document.entrySet()) {
                row.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
            }
            values.add(row);
        }
        return values;
    }

    /**
     * Search a collection based on given field-value pairs. ANDs all of them, uses case insenitive regexes, or exact matches or delimiter insensitve regex
     * depending on flags in SearchCriteria.
     * Excludes null or empty fields
     *
     * @param collectionName
     * @param criteria
     * @param page
     * @param converter
     * @param <T>
     * @return
     */
    public <T> Slice<T> search(String collectionName, SearchCriteria criteria, Pageable page, Function<Document, T> converter) {
        MongoCollection<Document> collection = getMongoTemplate().getCollection(collectionName);
        // TODO: Honor SearchCriteria#matchAll and other flags
        var metaPredicate = criteria.getMetaFilters().entrySet().stream().filter(entry -> entry.getValue() != null &&
                !StringUtils.isBlank(entry.getValue().toString().trim())).map(entry -> eq(entry.getKey(), entry.getValue()))
                .reduce((p1, p2) -> and(p1, p2));

        boolean hasCaseInsensitiveIndexField = criteria.isCaseSensitive() ? false :
            criteria.getSearchFieldNameValues().entrySet().stream().anyMatch(entry -> hasCaseInsensitiveIndexOnField(collectionName, entry.getKey()));
        var predicate = criteria.getSearchFieldNameValues().entrySet().stream().filter(entry -> entry.getValue() != null && !StringUtils.isBlank(entry.getValue().toString().trim())).map(entry -> {

            // If the filter is on case insensitiveindex, we need to handle it differently.
            if (!criteria.isCaseSensitive() && hasCaseInsensitiveIndexOnField(collectionName, entry.getKey())) {
                return eq(entry.getKey(), entry.getValue());
            }

            if (criteria.isCaseSensitive() || entry.getValue() == null) {
                return eq(entry.getKey(), entry.getValue());
            }

            String options = "i";
            String value = entry.getValue() == null ? null : entry.getValue().toString().trim();
            boolean quote = true;
            if (criteria.isIgnoreDelimiters()) {
                value = replaceSpecialCharsWithIgnorePattern(value);
                quote = false;
            }
            // TODO: Handle date/time formatting instead of direct toString
            return regex(entry.getKey(), "^" + (quote ? Pattern.quote(value) : value) + "$", options);
        }).reduce((p1, p2) -> and(p1, p2)).map(p -> metaPredicate.isPresent() ? and(p, metaPredicate.get()) : p);

        return predicate.map(p -> {
            FindIterable<Document> iterable = null;
            if (hasCaseInsensitiveIndexField) {
                Collation collation = Collation.builder().locale(EN_LANGUAGE).collationStrength(CollationStrength.SECONDARY).build();
                iterable = (page == Pageable.unpaged()) ? collection.find(p).collation(collation)
                        : collection.find(p).collation(collation).limit(page.getPageSize()).skip((int) page.getOffset());
            } else {
                iterable = (page == Pageable.unpaged()) ? collection.find(p)
                        : collection.find(p).limit(page.getPageSize()).skip((int) page.getOffset());
            }
            MongoIterable<T> entities = iterable.map(converter::apply);

            List<T> target = new ArrayList<>();
            entities.into(target);
            return new SliceImpl<>(target, page, page.isPaged() && target.size() == page.getPageSize());
        }).orElse(new SliceImpl<>(Collections.emptyList(), page, false));
    }

    public long count(String collectionName, SearchCriteria criteria) {
        MongoCollection<Document> collection = getMongoTemplate().getCollection(collectionName);
        // TODO: Honor SearchCriteria#matchAll and other flags
        var predicate = criteria.getSearchFieldNameValues().entrySet().stream().map(entry -> {
            if (criteria.isCaseSensitive() || entry.getValue() == null) {
                return eq(entry.getKey(), entry.getValue());
            } else {
                String options = "i";
                String value = entry.getValue() == null ? null : entry.getValue().toString().trim();
                return regex(entry.getKey(), "^" + value + "$", options);
            }
        }).reduce((p1, p2) -> and(p1, p2));

        if (predicate.isPresent()) {
            log.debug("Counting {} with condition {}", collectionName, predicate.get());
            return collection.countDocuments(predicate.get());
        } else {
            log.debug("Counting without condition on {}", collectionName);
            return collection.estimatedDocumentCount();
        }
    }

    private String replaceSpecialCharsWithIgnorePattern(String value) {
        if (value == null)
            return null;
        StringBuilder withReplacement = new StringBuilder("^");
        // This flag is used to skip continuous non-word match patterns. we need only
        // one replacement for
        // a sequence of ignorable chars
        boolean encounteredSpecial = false;
        for (int i = 0; i < value.length(); i++) {
            if (IGNORE_CHARACTERS.contains(value.charAt(i))) {
                if (!encounteredSpecial) {
                    encounteredSpecial = true;
                    // match all nonword and underscore
                    withReplacement.append("(\\W*|_*)");
                }
            } else {
                encounteredSpecial = false;
                withReplacement.append(value.charAt(i));
            }
        }
        return withReplacement.toString();
    }

    public List<String> getCollectionNamesStartWith(String prefix) {
        MongoDatabase db = getMongoDBFactory().getDb(getDB());
        Iterator<String> collections = db.listCollectionNames().iterator();

        List<String> customerCollections = new ArrayList<>();
        while (collections.hasNext()) {
            String c = collections.next();
            if (c.startsWith(prefix)) customerCollections.add(c);
        }

        return customerCollections;
    }

    public <T> List<T> searchPaged(String collectionName, Optional<Bson> criteria, Bson sort, Function<Document, T> converter, int pageSize) {
        return searchPaged(collectionName, criteria, sort, converter, pageSize, false);
    }

    public <T> List<T> searchPaged(String collectionName, Optional<Bson> criteria, Bson sort, Function<Document, T> converter, int pageSize,
            boolean hasCaseInsensitiveIndexField) {
        new PageCursor(null, null, pageSize).validate();
        MongoCollection<Document> collection = getMongoTemplate().getCollection(collectionName);
        FindIterable<Document> iterable = criteria.map(c -> {
            if (hasCaseInsensitiveIndexField) {
                Collation collation = Collation.builder().locale(EN_LANGUAGE).collationStrength(CollationStrength.SECONDARY).build();
                return collection.find(c).collation(collation);
            }
            return collection.find(c);
        }).orElseGet(() -> collection.find())
                .sort(sort).limit(pageSize);
        MongoIterable<T> entities = iterable.map(converter::apply);
        List<T> target = new ArrayList<>();
        entities.into(target);
        return target;
    }

    public <T> List<T> searchPagedWithOffset(String collectionName, Optional<Bson> criteria, Bson sort, Function<Document, T> converter, int offset, int pageSize) {
        new PageCursor(null, null, pageSize).validate();
        MongoCollection<Document> collection = getMongoTemplate().getCollection(collectionName);
        FindIterable<Document> iterable = criteria
                .map(c -> collection.find(c))
                .orElseGet(() -> collection.find())
                .sort(sort)
                .skip(offset)
                .limit(pageSize);
        MongoIterable<T> entities = iterable.map(converter::apply);
        List<T> target = new ArrayList<>();
        entities.into(target);
        return target;
    }

    public long count(String collectionName, Optional<Bson> criteria) {
        return count(collectionName, criteria, false);
    }

    public long count(String collectionName, Optional<Bson> criteria, boolean hasCaseInsensitiveIndexField) {
        MongoCollection<Document> collection = getMongoTemplate().getCollection(collectionName);
        if (hasCaseInsensitiveIndexField) {
            Collation collation = Collation.builder().locale(EN_LANGUAGE).collationStrength(CollationStrength.SECONDARY).build();
            return criteria.map(c -> collection.countDocuments(c, new CountOptions().collation(collation))).orElseGet(collection::estimatedDocumentCount);
        }
        return criteria.map(collection::countDocuments).orElseGet(collection::estimatedDocumentCount);
    }
    
    public <T extends UUIDAuditModel> Page<T> searchPagedById(String collectionName, Optional<Bson> criteria, Function<Document, T> converter, PageCursor cursor) {
        cursor.validate();
        if (!StringUtils.isBlank(cursor.getCursor())) {
            ObjectId id = new ObjectId(cursor.getCursor());
            Bson condition = cursor.isForward() ? Filters.lt(_ID, id) : Filters.gt(_ID, id);
            criteria = criteria.map(c -> Filters.and(c, condition)).or(() -> Optional.of(condition));
        }
            
        MongoCollection<Document> collection = getMongoTemplate().getCollection(collectionName);
        FindIterable<Document> iterable = criteria.map(c -> collection.find(c)).orElseGet(() -> collection.find())
                .sort(getImplictSort(cursor)).limit(cursor.getPageSize()+1);
        MongoIterable<T> entities = iterable.map(converter::apply);
        List<T> results = new ArrayList<>();
        entities.into(results);
        
        return constructPage(cursor, results);
    }

    public <T extends UUIDAuditModel> Page<T> searchPagedById(Criteria criteria, Class<T> resultClass, PageCursor cursor, Boolean reverseResultOrder) {
        cursor.validate();
        // Implicit sorting on id desc
        Sort sort = cursor.isForward() ? Sort.by(_ID).descending() : Sort.by(_ID).ascending();

        if (!StringUtils.isBlank(cursor.getCursor())) {
            ObjectId id = new ObjectId(cursor.getCursor());
            criteria = cursor.isForward() ? criteria.and(_ID).lt(id) : criteria.and(_ID).gt(id);
        }
        Query q = Query.query(criteria).limit(cursor.getPageSize() + 1).with(sort);
        log.debug("Executing query to find data in searchPagedById method is {}", q);
        List<T> results = getMongoTemplate().find(q, resultClass);
        if (reverseResultOrder)
            Collections.reverse(results);
        log.debug("Results size of searchPagedById method is {}", results.size());
        return constructPage(cursor, results);
    }

    public <T extends UUIDAuditModel> Page<T> searchPagedById(Criteria criteria, Class<T> resultClass, PageCursor cursor) {
       return searchPagedById(criteria, resultClass, cursor, false);
    }

    public <T extends UUIDAuditModel>  List<T> searchCursorById(Criteria criteria, Class<T> resultClass, String objectId, int limit) {
        // Implicit sorting on id asc
        Sort sort = Sort.by(_ID).descending();

        if(objectId!=null){
           criteria = criteria.and(_ID).lt(new ObjectId(objectId));
        }

        List<T> results = getMongoTemplate().find(Query.query(criteria).limit(limit).with(sort), resultClass);

        return results;
    }

    public String toCollectionName(String entityName) {
        return "syncari_" + entityName.toLowerCase();
    }
    
    public static void createIndexes(MongoTemplate db, Map<String, List<Index>> indexMap) {
        indexMap.forEach((collectionName, indexes) -> createIndexes(db, collectionName, indexes));
    }
    
    public void createIndexes(String collectionName, List<Index> indexes, boolean dropExisting) {
    	createIndexes(getMongoTemplate(), collectionName, indexes, dropExisting);
    }

    public static void createIndexes(MongoTemplate db, String collectionName, List<Index> indexes) {
    	createIndexes(db, collectionName, indexes, true);
    }

    public static void createIndexes(MongoTemplate db, String collectionName, List<Index> indexes, boolean dropExisting) {
		MongoCollection<Document> collection = db.getCollection(collectionName);
        indexes.forEach(index -> {
            IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
            if (!index.isCaseSensitive()) {
                // assumes that fields/collections that need this case insensitive behavior are alphanumeric
                Collation c = Collation.builder().locale(EN_LANGUAGE).collationStrength(CollationStrength.SECONDARY).build();
                keyOpts = keyOpts.collation(c);
                keyOpts.name(index.getName());
            }

            String indxName = getUniqueIndexName(db.getDb().getName(), collectionName, index.getName());
            if(dropExisting && !StringUtils.isBlank(indxName)) {
                try{
                    collection.dropIndex(indxName);
                } catch (Exception e){
                    // Do nothing - suppress the error
                }
                keyOpts.name(indxName);
            }
            Map<String, Integer> orderMap = index.getFieldsOrderMap();
            BasicDBObject dbObj = new BasicDBObject();
            index.getFields().stream().forEach(f ->{
                        if (MapUtils.isNotEmpty(orderMap) && (null != orderMap.get(f))){
                            dbObj.append(f, orderMap.get(f));
                        }else{
                            dbObj.append(f, index.getAscending());
                        }
                    }
            );
            keyOpts.background(true);
            String indexName = collection.createIndex(dbObj, keyOpts);
            log.debug("Created index {} successfully for {}", indexName, collectionName);
        });
	}

    public static void dropIndexes(MongoTemplate db, String collectionName, List<Index> indexes) {
        MongoCollection<Document> collection = db.getCollection(collectionName);
        indexes.forEach(index -> {
            if(!StringUtils.isBlank(index.getName())) {
                try{
                    collection.dropIndex(index.getName());
                } catch (Exception e){
                    // Do nothing - suppress the error
                }
            }
        });
    }
    
    private Bson getImplictSort(PageCursor cursor) {
        // Implicit sorting on id desc
        Bson sort = new BasicDBObject(_ID, -1);
        
        if (!StringUtils.isBlank(cursor.getCursor())) {
            if(!cursor.isForward()) {
                sort = new BasicDBObject(_ID, 1);
            }
        }
        return sort;
    }
    
    private <T extends UUIDAuditModel> Page<T> constructPage(PageCursor cursor, List<T> results) {
        // When viewing a previous page we always return true for hasMore
        boolean hasMore = cursor.isForward() ? results.size() == cursor.getPageSize() + 1 : true;
        boolean hasPrevious = StringUtils.isBlank(cursor.getCursor()) ? false
                : (results.size() == cursor.getPageSize() + 1) ? true : !hasMore;

        if (results.size() > cursor.getPageSize()) {
            results = results.subList(0, results.size() - 1);
        }

        Page<T> page = new Page<>();
        String pageStart = results.size() > 0 ? results.get(0).getId() : null;
        String pageEnd = results.size() > 0 ? results.get(results.size() - 1).getId() : null;
        page.setPageInfo(new PageInfo(pageStart, pageEnd, hasMore).addSort("Id", true).setHasPrevious(hasPrevious));
        page.setRecords(results);
        assert page.getRecords().size() <= cursor.getPageSize();
        return page;
    }

    public void constructIndexes(String variableName, boolean isCaseSensitive, Optional<EntityDefinition> entityDefinition){

        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : variableName;

        entityDefinition.ifPresent(e->{
            Optional<AttributeDefinition> attr = e.getAttributes().stream()
                    .filter(a -> a.getId().equals(attributeId)).findFirst();
            attr.ifPresent(a->{
                // Create index for every lookup attribute
                String collectionName = "syncari_" + e.getApiName().toLowerCase();
                String caseInsensitiveIndexName = isCaseSensitive ? null : "case_insensitive_idx_"+a.getApiName();
                try {
                    log.debug("Creating index for db: {}, collection: {}, field: {}",
                            SyncariContext.getDatabase(), collectionName, a.getApiName());
                    createIndexes(collectionName,
                            List.of(new Index(caseInsensitiveIndexName,false,isCaseSensitive,a.getApiName())), false);
                } catch (Exception e2) {
                    if (!StringUtils.isBlank(e2.getMessage()) && !e2.getMessage().contains("Index already exists")) {
                        log.error("Error creating index {}", e2.getMessage());
                    }
                    log.debug("Error creating index {}", e2.getMessage());
                }
            });
        });

    }
    
    public static boolean isIndexExist(MongoTemplate db, String collectionName, String indexName) {
    	if(StringUtils.isAnyBlank(collectionName, indexName)) {
    		throw new RuntimeException("Invalid collection or index");
    	}
    	MongoCollection<Document> collection = db.getCollection(collectionName);
    	List<Document> existingIndexes = new ArrayList<>();
        collection.listIndexes().into(existingIndexes);
        return existingIndexes.stream().filter(e -> e.get("name").toString().equals(indexName)).count() != 0;
    }

    /**
     * Execute a MongoDB query with a limit
     * Simple MongoDB query execution for data fix operations
     *
     * @param mongoTemplate The MongoTemplate to use for execution
     * @param queryText The query text (currently simplified - in production use proper query parser)
     * @param collection The collection name to query
     * @param limit Maximum number of documents to return
     * @return List of documents matching the query
     */
    public static List<Document> executeMongoQuery(MongoTemplate mongoTemplate, String queryText, String collection, int limit) {
        List<Document> results = new ArrayList<>();

        try {
            // Parse MongoDB query to extract filter
            // Expected format: db.collection.find({filter})
            Pattern pattern = Pattern.compile("^db\\.[a-zA-Z0-9_]+\\.([a-zA-Z0-9_]+)\\((.*)\\)$", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(queryText.trim().replaceAll(";$", ""));

            Document filter = new Document();
            if (matcher.matches()) {
                String argsString = matcher.group(2).trim();
                if (!argsString.isEmpty()) {
                    // Extract first JSON object as filter
                    int firstBraceIndex = argsString.indexOf('{');
                    if (firstBraceIndex != -1) {
                        int braceCount = 0;
                        int endIndex = firstBraceIndex;
                        for (int i = firstBraceIndex; i < argsString.length(); i++) {
                            char c = argsString.charAt(i);
                            if (c == '{') braceCount++;
                            else if (c == '}') {
                                braceCount--;
                                if (braceCount == 0) {
                                    endIndex = i + 1;
                                    break;
                                }
                            }
                        }
                        String filterString = argsString.substring(firstBraceIndex, endIndex);
                        filter = Document.parse(filterString);
                    }
                }
            }

            log.info("Executing MongoDB find with filter: {}", filter);

            // Execute query with filter
            org.springframework.data.mongodb.core.query.BasicQuery mongoQuery =
                    new org.springframework.data.mongodb.core.query.BasicQuery(filter);
            mongoQuery.limit(limit);

            List<Document> rawResults = mongoTemplate.find(mongoQuery, Document.class, collection);

            // Convert ObjectId to String for _id field to ensure proper JSON serialization
            for (Document doc : rawResults) {
                Document processedDoc = new Document();
                for (Map.Entry<String, Object> entry : doc.entrySet()) {
                    if ("_id".equals(entry.getKey()) && entry.getValue() instanceof ObjectId) {
                        processedDoc.put("_id", entry.getValue().toString());
                    } else {
                        processedDoc.put(entry.getKey(), entry.getValue());
                    }
                }
                results.add(processedDoc);
            }
        } catch (Exception e) {
            log.error("Error executing MongoDB query: {}", e.getMessage(), e);
            throw e;
        }

        return results;
    }
}

