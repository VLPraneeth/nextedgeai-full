package com.syncari.core.repositories.customer;

import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.connector.FieldScore;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.*;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.model.cache.CacheLoadJob;
import com.syncari.core.model.cache.CacheLoadStatus;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.cache.CacheLoaderService;
import com.syncari.core.utils.Criteria;
import com.syncari.core.utils.RedisFindDedupeCriteriaVisitor;
import com.syncari.core.utils.RedisUtils;
import com.syncari.core.utils.SchemaHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.providers.PooledConnectionProvider;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
public class EntityCacheDiffTest {


    @Ignore
    @Test
    public void findEntity() {

        String[] fieldsToCompare = {"isDeleted", "Name", "Phone"};

        String cacheHost = "localhost";
        int cachePort = 6379;

        JedisClientConfig config = DefaultJedisClientConfig.builder().user("default").blockingSocketTimeoutMillis(60000).build();

        JedisPooled redisClient  = new JedisPooled(new PooledConnectionProvider(new HostAndPort(cacheHost, cachePort), config, new GenericObjectPoolConfig<>()));

        Set<String> matchingKeys = new HashSet<>();
        ScanParams params = new ScanParams();
        String keyPrefix = "AIQQGL:e:Account__c:*";
        params.match(keyPrefix);
        params.count(1000);
        String nextCursor = "0";

        do {
            ScanResult<String> scanResult = redisClient.scan(nextCursor, params);

            List<String> keys = scanResult.getResult();

            var docs = redisClient.jsonMGet(keys.toArray(String[]::new))
                                .stream().filter(json -> json != null)
                    .map(d -> new Document(d.getJSONObject(0).toMap())).collect(Collectors.toList());

            log.info("Retreived Documents from Redis {}", docs.size());
            // for each get the id
            var _idList = docs.stream().map(d -> d.get("_id").toString()).collect(Collectors.toList());
            //var docMap = docs.stream().collect(Collectors.toMap(d-> d.get("_id").toString(), d -> d));

            String idString = String.join("|", _idList);

            Query query = new Query(String.format("@_id:{%s}", idString)).limit(0, _idList.size());

            SearchResult results = redisClient.ftSearch("syncari_AIQQGL_Account__c_idx", query);

            List<Document> searchDocs = results.getDocuments().stream().map(d -> Document.parse((String)d.get("$"))).collect(Collectors.toList());
            var docMap = searchDocs.stream().collect(Collectors.toMap(d-> d.get("_id").toString(), d -> d));

            log.info("Retrieved Documents from Search Index {}", searchDocs.size());

            for (Document doc : docs) {
                var _id= doc.get("_id").toString();
                if (docMap.containsKey(_id)) {
                    var indexDoc = docMap.get(_id);

                    var indexValue = (Long)doc.get("syncariTimestamp");
                    var redisValue = (Long)indexDoc.get("syncariTimestamp");

                    if (indexValue.longValue() != redisValue.longValue()) {
                        log.info("Value of syncari timestamp different for id {} Index value {} Redis Value {}", _id, indexValue, redisValue);
                        return;
                    }

                    boolean isEqual = true;
                    for (String field : fieldsToCompare) {
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
                        log.info("Document between redis and index is different for the id {}", _id);
                        return;
                    }

                } else {
                    log.info("Document with _id {} not found in index", _id);
                }
            }
            nextCursor = scanResult.getCursor();
        } while(!nextCursor.equals("0"));
    }
}
