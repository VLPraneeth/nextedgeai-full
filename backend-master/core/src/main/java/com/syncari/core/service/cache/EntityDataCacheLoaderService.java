package com.syncari.core.service.cache;

import com.syncari.core.datatype.BooleanType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.cache.CacheLoadJob;
import com.syncari.core.model.cache.CacheLoadStatus;
import com.syncari.core.repositories.customer.cache.CacheLoadJobRepo;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.JsonSetParams;

import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
public class EntityDataCacheLoaderService implements CacheLoaderService {
    private static final int ID_MAPPING_BATCH_SIZE = 500;
    public static final String SYNCARI_ID_MAPPING_KEY = "__sim";
    @Autowired
    private JedisPooled redisClient;
    @Autowired
    private MongoTemplate secondaryReaderCustomerMongoTemplate;
    private static final int CACHE_FLUSH_SIZE = 10000;
    @Autowired
    private CacheLoadJobRepo cacheLoadJobRepo;
    @Autowired
    private SchemaService schemaService;

    private CacheDataTypeConverter converter = new CacheDataTypeConverter();

    public void load(CacheLoadJob cacheLoadJob) {
        if (cacheLoadJob == null) {
            log.debug("No CacheLoad Job to run");
            return;
        }
        final Iterator<Document> records = getRecordIterator(secondaryReaderCustomerMongoTemplate, cacheLoadJob);
        try(Pipeline pipeline = redisClient.pipelined()) {
            int cachedRecordCounter = 0;
            Document record = null;
            EntityDefinition entityDefinition = schemaService.getSyncariEntityByName(cacheLoadJob.getEntityName()).orElseThrow();
            final Map<String, Document> recordMap = new HashMap<>();
            while (records.hasNext()) {
                record = transform(records.next(), entityDefinition);
                //record id is stringified
                String recordId = record.getString("_id");
                recordMap.put(recordId, record);
                if (recordMap.size() % ID_MAPPING_BATCH_SIZE == 0) {
                    cachedRecordCounter += cacheRecordsAndIdMappings(cacheLoadJob, pipeline, recordMap);
                    recordMap.clear();
                }
                if (cachedRecordCounter > 0 && cachedRecordCounter % CACHE_FLUSH_SIZE == 0) {
                    pipeline.sync();
                    updateCacheLoadStatus(cachedRecordCounter, cacheLoadJob.setStatus(CacheLoadStatus.IN_PROGRESS), record, System.currentTimeMillis());
                }

            }
            cachedRecordCounter += cacheRecordsAndIdMappings(cacheLoadJob, pipeline, recordMap);
            pipeline.sync();
            updateCacheLoadStatus(cachedRecordCounter, cacheLoadJob.setStatus(CacheLoadStatus.COMPLETED), record, System.currentTimeMillis());
        }

    }

    private Document transform(Document record, EntityDefinition entityDefinition) {
        List<String> nullFields = new ArrayList<>();
        entityDefinition.getAttributes().forEach(attribute -> {
            String attributeName = attribute.getApiName();
            if (record.containsKey(attributeName)) {
                final Object convertedValue = converter.convertFrom(attribute.getDataType(), record.get(attributeName));
                if (convertedValue != null) {
                    record.put(attributeName, convertedValue);
                } else {
                    nullFields.add(attributeName);
                }
            } else {
                nullFields.add(attributeName);
            }
        });
        record.put("syncariTimestamp", record.getLong("syncariTimestamp"));
        record.put("lastModified", record.getLong("lastModified"));
        record.computeIfPresent("createdAt", (k,v) -> record.getLong("createdAt"));
        record.put("_id", record.getObjectId("_id").toHexString());
        record.put("isDeleted", converter.convertFrom(BooleanType.VALUE, record.getBoolean("isDeleted")));
        if (!nullFields.isEmpty()) {
            record.put("__nf", String.join(",", nullFields));
        }
        return record;
    }

    @Override
    public void runAvailableJob() {
        load(cacheLoadJobRepo.findAndReserveJob());
    }

    private int cacheRecordsAndIdMappings(CacheLoadJob cacheLoadJob, Pipeline pipeline, Map<String, Document> recordMap) {
        final Map<String, Document> idMappings = getIdMappings(secondaryReaderCustomerMongoTemplate, recordMap.keySet());
        recordMap.forEach((syncariId, r) ->
                pipeline.jsonSet(createKey(cacheLoadJob, syncariId), updateWithIdMapping(r, idMappings.get(syncariId)).toJson(JsonWriterSettings.builder()
                        .dateTimeConverter((aLong, strictJsonWriter) -> strictJsonWriter.writeNumber(aLong==null? "null": aLong.toString())).build()
                        ),

                        JsonSetParams.jsonSetParams().nx()));
        return recordMap.size();
    }

    private Document updateWithIdMapping(Document record, Document idMapping) {
        return record.append(SYNCARI_ID_MAPPING_KEY, extractIdMapping(idMapping));
    }

    private Object extractIdMapping(Document idMapping) {
        //only need mappings and nothing else
        if (idMapping != null) {
            List<Document> mappings = idMapping.get("mappings", List.class);
            mappings.forEach(mapping -> {
                mapping.put("disconnected", converter.convertFrom(BooleanType.VALUE, mapping.getBoolean("disconnected", false)));
            });
            return mappings;
        }
        return List.of();
    }

    protected Map<String, Document> getIdMappings(MongoTemplate template, Set<String> recordIds) {
        Map<String, Document> idMap = new HashMap<>();

        template.getCollection("idMapping").find(new Document("syncariId", new Document("$in", recordIds))).forEach((Consumer<? super Document>) document -> {
            idMap.put(document.getString("syncariId"), document);
        });
        return idMap;
    }

    protected void updateCacheLoadStatus(int cachedRecordCounter, CacheLoadJob cacheLoadJob, Document record, long lastCacheWriteTimestamp) {
        cacheLoadJobRepo.save(cacheLoadJob);
        log.info("Loaded {} records into cache , entity name {}. Last recordId {}", cachedRecordCounter, cacheLoadJob.getEntityName(), record == null ? "N/A" : record.getString("_id"));
    }

    @Override
    public CacheLoadJob status(String entityName) {
        return cacheLoadJobRepo.findByEntityName(entityName);
    }

    @Override
    public CacheLoadJob queueCacheLoadJob(CacheLoadJob cacheLoadJob) {
        return cacheLoadJobRepo.save(cacheLoadJob);
    }

    protected String createKey(CacheLoadJob cacheLoadConfig, String recordId) {
        return cacheLoadConfig.getInstanceId() + ":e:" + cacheLoadConfig.getEntityName() + ":" + recordId;
    }

    protected Iterator<Document> getRecordIterator(MongoTemplate template, CacheLoadJob cacheLoadJob) {
        return template.getCollection(toCollectionName(cacheLoadJob.getEntityName())).find(getFilters(cacheLoadJob)).sort(new Document("_id", 1)).iterator();

    }

    protected Document getFilters(CacheLoadJob cacheLoadJob) {
        if (!StringUtils.isBlank(cacheLoadJob.getLastCacheWriteWatermark())) {
            return new Document("_id", new Document("$gte", new ObjectId(cacheLoadJob.getLastCacheWriteWatermark())));
        } else {
            return new Document();
        }
    }

    protected String toCollectionName(String entityName) {
        return "syncari_" + entityName.toLowerCase();
    }


}

