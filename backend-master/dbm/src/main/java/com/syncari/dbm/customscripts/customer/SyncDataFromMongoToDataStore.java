package com.syncari.dbm.customscripts.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.core.MigrationContext;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
public class SyncDataFromMongoToDataStore {
    private static final String INSERT = "INSERT";
    private static final String UPDATE = "UPDATE";
    private static final String DELETE = "DELETE";

    ObjectMapper objectMapper = new ObjectMapper();

    @ChangeSet(order = "001", id = "syncDataFromMongoToDataStore", author = "durga", runAlways = true)
    public void syncDataFromMongoToDataStore(MongoTemplate db) {

        ConnectorService connectorService = MigrationContext.getConnectorService();
        DatastoreService datastoreService = MigrationContext.getDatastoreService();
        SchemaService schemaService = MigrationContext.getSchemaService();
        EntityRepo entityRepo = MigrationContext.getEntityRepo();

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        Connector datastore = connectorService.getSyncariDatastore()
                .orElseThrow(() -> new RuntimeException("Datastore connector missing"));

        long startTime = Long.parseLong(System.getProperty("startTime", Long.toString(0L)));

        long endTime = Long.parseLong(System.getProperty("endTime", Long.toString(Instant.now().toEpochMilli())));

        Integer pageSize = Integer.parseInt(System.getProperty("pageSize", Integer.toString(1000)));

        Integer offset = Integer.parseInt(System.getProperty("offset", Integer.toString(0)));

        String syncariEntityName = System.getProperty("entityName");
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(syncariEntityName)
                .orElseThrow(() -> new RuntimeException(String.format("Syncari entity for apiName %s not found", syncariEntityName)));

        PageRequest page = PageRequest.of(offset, pageSize);

        log.info("Datastore sync request received for entityName: {} from startTime: {} to endTime: {} with pageSize: {} and offset: {}",
                syncariEntityName, startTime, endTime, pageSize, offset);

        ConnectorInfo info = datastoreService.toConnectorInfo(Optional.of(datastore));

        long iteratorStart = startTime;

        long lastDate = iteratorStart;

        long totalRecordsMigrated = 0l;

        Slice<EntityData> entityDataList = entityRepo.find(syncariEntity, Instant.ofEpochMilli(iteratorStart), page);

        while (!entityDataList.isEmpty() && lastDate <= endTime) {
            log.info("Obtained {} results for entityName: {} from startTime: {} to endTime: {} with pageSize: {} and pageOffset: {}",
                    entityDataList.getNumberOfElements(), syncariEntityName, startTime, endTime, pageSize, page.getOffset());
            Map<String, List<EntityData>> toBeProcessed = new HashMap<>();
            toBeProcessed.putIfAbsent(DELETE, new ArrayList<>());
            toBeProcessed.putIfAbsent(INSERT, new ArrayList<>());
            toBeProcessed.putIfAbsent(UPDATE, new ArrayList<>());
            Set<String> update = new HashSet<>();
            Set<String> delete = new HashSet<>();
            List<EntityData> toUpdate = new ArrayList<>();
            Map<String, EntityData> lookup = new HashMap<>();
            EntityData lastEntity = null;
            for (EntityData d : entityDataList) {
                EntityData converted = convert(d, syncariEntity);
                lookup.put(converted.getId(), converted);
                lastDate = Math.max(lastDate, converted.getSyncariTimestamp());
                if (converted.getSyncariTimestamp() > endTime){
                    break;
                }
                lastEntity = d;
                ++totalRecordsMigrated;
                if (converted.isDeleted()) {
                    toBeProcessed.get(DELETE).add(converted);
                    delete.add(converted.getId());
                } else {
                    toUpdate.add(converted);
                }
            }

            EntitySchema entitySchema = datastoreService.toEntitySchema(syncariEntity, datastore);
            if(!toUpdate.isEmpty()) {
                SyncRequest byIds = new SyncRequest();
                byIds.setConnector(info);
                entitySchema = changeIdField(entitySchema);
                byIds.setEntitySchema(entitySchema);
                byIds.setData(Map.of(datastore.getId(), toUpdate));
                try {
                    List<EntityData> existing = datastoreService.getService(info).getByIds(byIds);
                    existing.forEach(q -> {
                        update.add(q.getId());
                        EntityData lookedUp = lookup.get(q.getId());
                        if (lookedUp != null) {
                            toBeProcessed.get(UPDATE).add(lookedUp);
                        }
                    });
                } catch (NotSupportedException e){
                    log.error(e.getMessage(), e);
                }
            }



            if (!dryRunMode) {
                // Process Deletes
                log.debug("Processing deletion and updates");
                datastoreService.getService(info).delete(constructRequest(info, datastore, entitySchema, toBeProcessed, DELETE));

                // Process Updates
                datastoreService.getService(info).update(constructRequest(info, datastore, entitySchema, toBeProcessed, UPDATE));
            }

            entityDataList.forEach(d -> {
                if(!delete.contains(d.getId()) && !update.contains(d.getId()) && d.getSyncariTimestamp() <= endTime) {
                    toBeProcessed.get(INSERT).add(lookup.get(d.getId()));
                }
            });

            // Process Inserts
            if (!dryRunMode) {
                log.debug("Processing inserts ");
                datastoreService.getService(info).create(constructRequest(info, datastore, entitySchema, toBeProcessed, INSERT));
            }
            if (lastEntity !=  null){
                log.info("Completed {} records in this batch with startTime {} and offset {}. Last record processed syncariId: {}, timestamp {}", totalRecordsMigrated, iteratorStart, offset, lastEntity.getSyncariEntityId(), lastEntity.getSyncariTimestamp());
            }

            if (entityDataList.getNumberOfElements() == pageSize && lastDate == iteratorStart) {
                offset ++;
            } else if (entityDataList.getNumberOfElements() < pageSize) {
                break;
            } else {
                iteratorStart = lastDate;
                offset = 0;
            }

            page = PageRequest.of(offset, pageSize);
            entityDataList = entityRepo.find(syncariEntity, Instant.ofEpochMilli(iteratorStart), page);
        }
        log.info("Completed Syncing all records for entity {}, totalRecords {}", syncariEntityName, totalRecordsMigrated);
    }

    private EntityData convert(EntityData entityData, EntityDefinition schema) {
        Map<String, Object> convertedValues = new HashMap<>();
        // Replace altered names if any
        schema.setApiName(schema.getDataStoreName());
        schema.getAttributes().forEach(a ->{
            if(entityData.has(a.getApiName())) {
                Object originalValue = entityData.getValue(a.getApiName());
                Object converted = transformValue(a, originalValue);
                if(converted!=null && a.getDataType().equals(DatetimeType.VALUE) && !a.isMultiValueField()){
                    ZonedDateTime convertedDateTime = (ZonedDateTime)converted;
                    converted = new Timestamp(convertedDateTime.toInstant().toEpochMilli());
                }
                convertedValues.put(a.getDataStoreName(), converted);
            }
        });
        return entityData.withValues(convertedValues);
    }

    private Object transformValue(AttributeDefinition a, Object originalValue) {
        if(a.isMultiValueField() && originalValue instanceof List){
            List listValue = List.class.cast(originalValue);
            if(listValue.isEmpty()){
                return null;
            }else {
                //Make a JSON Array of strings
                try {
                    return objectMapper.writeValueAsString(listValue);
                } catch (JsonProcessingException e) {
                    log.warn("Could not convert {} to string",originalValue);
                }

            }
        }
        return  a.convert(originalValue);
    }

    private SyncRequest constructRequest(ConnectorInfo info, Connector datastore, EntitySchema schema, Map<String, List<EntityData>> toBeProcessed, String op) {
        SyncRequest request = new SyncRequest();
        request.setConnector(info);
        schema = changeIdField(schema);
        request.setEntitySchema(schema);
        request.setData(Map.of(datastore.getId(), toBeProcessed.get(op)));
        return request;
    }

    private EntitySchema changeIdField(EntitySchema schema) {
        // Change the id field to syncariid
        if(schema.hasIdField()) {
            schema.getIdField().setIdField(false);
        }
        if(!schema.hasField(Constants.SYNCARI_ID)) {
            schema.addField(getSyncariId());
        }
        schema.getField(Constants.SYNCARI_ID).get().setIdField(true);
        schema.getField(Constants.SYNCARI_ID).get().setNillable(false);
        return schema;
    }

    private AttributeSchema getSyncariId() {
        AttributeSchema field = new AttributeSchema(Constants.SYNCARI_ID, "string");
        field.setIdField(true);
        field.setNillable(false);
        field.setUnique(true);
        field.setSystem(true);
        return field;
    }

}
