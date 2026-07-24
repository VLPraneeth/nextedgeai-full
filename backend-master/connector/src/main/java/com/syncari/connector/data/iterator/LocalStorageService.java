package com.syncari.connector.data.iterator;

import java.util.HashMap;
import java.util.List;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.*;
import com.syncari.connector.database.HsqlService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.connector.EntityData;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LocalStorageService {
    @Autowired
    HsqlService dbService;

    public void provisionIfNotExists(SyncRequest request, String fileName) {
        request.getConnector().getMetaConfig().put("fileName", fileName);
        EntitySchema entitySchema = getSchema(request);
        if(dbService.describe(new DescribeRequest(request.getConnector(),request.getEntityName())).isEmpty()) {
            dbService.createObject(new CreateObjectRequest(request.getConnector(), entitySchema));
        }
    }

    public void fetch(SyncRequest request, AbstractEntityDataBatchIterator iterator) {
        EntitySchema entitySchema = getSchema(request);
        if(dbService.describe(new DescribeRequest(request.getConnector(),request.getEntityName())).isEmpty()) {
            dbService.createObject(new CreateObjectRequest(request.getConnector(), entitySchema));
        }
        while (iterator.hasNext()) {
            SyncRequest req = request.withEntitySchema(entitySchema).withData(new HashMap<String, List<EntityData>>());
            List<EntityData> next = iterator.next();
            log.info("Got {} records", next.size());
            next.forEach(e -> {
                EntityData data = new EntityData(entitySchema.getApiName()).setId(e.getId()).addValue("entity_data", e)
                        .addValue("updated_at", e.getLastModified());
                req.addData(request.getConnector().getId(), data);
            });
            dbService.create(req);
        }
    }
    public long count(ConnectorInfo connectorInfo, String entityName, long startWatermark){
        return dbService.count(connectorInfo, entityName,startWatermark);
    }
    public long maxWatermark(ConnectorInfo connectorInfo, String entityName){
        return dbService.maxWatermark(connectorInfo, entityName);
    }

    private EntitySchema getSchema(SyncRequest request) {
        EntitySchema entitySchema = new EntitySchema(request.getEntitySchema().getApiName());
        entitySchema.addField(new AttributeSchema("updated_at", "number").setWatermarkField(true));
        entitySchema.addField(new AttributeSchema("id", "string").setIdField(true));
        entitySchema.addField(new AttributeSchema("entity_data", "other"));
        return entitySchema;
    }
    public FetchResponse getByWatermark(SyncRequest request) {
        EntitySchema entitySchema = getSchema(request);
        SyncRequest req = request.withEntitySchema(entitySchema);
        return dbService.getByWatermark(req);
    }

    public void cleanupDB(SyncRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        if ((null != connectorInfo.getMetaConfig()) && (null != connectorInfo.getMetaConfig().get("fileName"))) {
            String dbName = dbService.getDbName(request.getConnector());
            log.info("Number of rows deleted {}",dbService.deleteAllData(request.getConnector(),request.getEntityName()));
            dbService.cleanupDB(dbName);
        }
    }

}
