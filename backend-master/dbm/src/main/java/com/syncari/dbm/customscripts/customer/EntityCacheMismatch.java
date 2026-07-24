package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.model.Filters;
import com.syncari.core.MigrationContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.EntityCacheRepo;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.providers.PooledConnectionProvider;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class EntityCacheMismatch {

    private JedisPooled redisClient = MigrationContext.getRedisClient();
    private EntityCacheRepo entityCacheRepo = MigrationContext.getEntityCacheRepo();
    private SchemaService schemaService = MigrationContext.getSchemaService();

    @ChangeSet(order = "001", id = "entityCacheMismatch", author = "venkat", runAlways = true)
    public void entityCacheMismatch(MongoTemplate db) {

        String entity = System.getProperty("entity");
        int batchSize = System.getProperty("batchSize") != null ? Integer.parseInt(System.getProperty("batchSize")) : 500;
        List<String> indexedFields = System.getProperty("indexedFields") != null ?
                Arrays.stream(System.getProperty("indexedFields").split(":")).map(String::trim).collect(Collectors.toList())
                : List.of();
        String instanceId = MigrationContext.getSyncariId();
        String indexName = new RedisUtils().getEntityIndex(instanceId, entity);
        EntityDefinition entityDefinition = schemaService.getSyncariEntityByName(entity).orElseThrow(() -> new RuntimeException("Invalid entity definition id " + entity));

        var coll = db.getCollection("syncari_" + entity.toLowerCase());
        int dbPageSize = batchSize;
        int redisPageSize = batchSize;

        int numberOfDocs = 0;
        var cursor = coll.find().batchSize(dbPageSize).iterator();
        Map<String, Document> mongoDocMap = new HashMap();
        while (cursor.hasNext()) {
            var doc = cursor.next();
            String key = doc.getObjectId("_id").toHexString();
            mongoDocMap.put(key, doc);

            log.debug("Prefix from Mongo {}", key);
            if (mongoDocMap.size() == redisPageSize) {
                checkinRedis(mongoDocMap, indexName, indexedFields, entityDefinition);
                mongoDocMap.clear();
            }

            numberOfDocs++;
            if (numberOfDocs % 500 == 0) {
                log.info("Iterated through docs {}", numberOfDocs);
            }
        }

        if (mongoDocMap.size() > 0) {
            checkinRedis(mongoDocMap, indexName, indexedFields, entityDefinition);
        }
    }

    private void checkinRedis(Map<String, Document> mongoDocMap, String indexName, List<String> indexFields, EntityDefinition entityDefinition) {

        Set<String> keys = mongoDocMap.keySet();
        //  for docs found check if they are present in search index
        String idString = String.join("|", keys);

        Query query = new Query(String.format("@_id:{%s}", idString)).limit(0, keys.size());

        SearchResult results = redisClient.ftSearch(indexName, query);

        List<Document> searchDocs = results.getDocuments().stream().map(d -> Document.parse((String) d.get("$")))
                .map(d -> entityCacheRepo.convertFrom(d, entityDefinition)).collect(Collectors.toList());
        var searchDocMap = searchDocs.stream().collect(Collectors.toMap(d -> d.get("_id").toString(), d -> d));

        for (String key : keys) {
            var doc = mongoDocMap.get(key);
            var indexDoc = searchDocMap.get(key);

            if (indexDoc == null) {
                log.info("Document with key {} not found in Redis index", key);
                continue;
            }

            // for each key found compare values
            var indexValue = (Long)doc.get("syncariTimestamp");
            var mongoValue = (Long)indexDoc.get("syncariTimestamp");

            if (indexValue.longValue() != mongoValue.longValue()) {
                log.info("Value of syncari timestamp different for id {} Index value {} Mongo Value {}", key, indexValue, mongoValue);
                return;
            }

            boolean isEqual = true;
            for (String field : indexFields) {
                var v1 = indexDoc.get(field);
                var v2 = doc.get(field);
                boolean val = false;

                if (v1 == null && v2 == null) {
                    val = true;
                } else if ((v1 != null && v2 == null) || (v1 == null && v2 != null)) {
                    val = false;
                } else {
                    val = v1.equals(v2);
                }
                isEqual &= val;
            }

            if (!isEqual) {
                log.info("Document between Mongo and Redis Index is different for the id {}", key);
                return;
            }
        }

    }
}
