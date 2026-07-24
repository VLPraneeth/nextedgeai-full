package com.syncari.core.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AuthType;
import com.syncari.core.Features;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.StreamInfo;
import com.syncari.core.repositories.customer.DatastoreWatermarkRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.schema.Schema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DatastoreLagService {

    @Autowired
    EntityRepoService entityRepoService;

    @Autowired
    EntityRepo entityRepo;

    @Autowired
    DatastoreWatermarkRepo datastoreWatermarkRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    WatermarkService watermarkService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SyncStatusService syncStatusService;

    @Autowired
    FeatureService featureService;


    public List<DatastoreLag> lagForAllEntities(){
        if (!featureService.isEnabled(Features.Datastore)){
            throw new SyncariValidationException("Datastore is not enabled to check for the lag");
        }
        List<DatastoreLag> datastoreLags= new ArrayList<>();
        // Get all active entities
        // get datastore lag for each entity and return that.
        Schema schema = schemaService.getSyncariSchema(true);
        //List<StreamInfo> streamInfos = syncStatusService.getAllPipelineStreamStatus();
        List<String>entityIds = schema.getEntities().stream().map(e -> e.getId()).collect(Collectors.toList());
        entityIds.forEach(eId -> {
            datastoreLags.add(lagForSyncariEntity(eId));
        });
        return datastoreLags;
    }

    public List<DatastoreLag> lagForInitialLoadApprovedRunningEntities(){
        if (!featureService.isEnabled(Features.Datastore)){
            throw new SyncariValidationException("Datastore is not enabled to check for the lag");
        }
        List<DatastoreLag> datastoreLags= new ArrayList<>();
        // Get all active entities
        // get datastore lag for each entity and return that.
        //Schema schema = schemaService.getSyncariSchema(true);
        List<StreamInfo> streamInfos = syncStatusService.getAllPipelineStreamStatus();
        //List<String> entityIds = schema.getEntities().stream().map(e -> e.getId()).collect(Collectors.toList());
        List<String> entityIds = streamInfos.stream().filter(streamInfo ->  streamInfo.getStatus().equals(StreamInfo.Status.RUNNING)).map(a -> a.getSyncariEntityId()).collect(Collectors.toList());
        List<String> entityIdsWithIntialLoad = entityIds.stream().filter(eId -> {
            Optional<DatastoreWatermark> datastoreWm = watermarkService.getDatastoreWatermark(eId);
           return datastoreWm.isPresent() && datastoreWm.get().isDatastoreInitial();
        }).collect(Collectors.toList());

        entityIdsWithIntialLoad.forEach(eId -> {;
            datastoreLags.add(lagForSyncariEntity(eId));
        });
        return datastoreLags;
    }

    //Lag api checks the watermark between max syncari timestamp and datastorewatermark,
    // if datastore is lagging then calculate pending records to process (number of records in mongodb - number of records in datastore). This is per entity.
    public DatastoreLag lagForSyncariEntity(String entityId){
        DatastoreLag lag = new DatastoreLag();
        EntityDefinition entityDefinition = schemaService.getEntity(entityId, false);
        lag.setEntityName(entityDefinition.getApiName());
        lag.setEntityId(entityDefinition.getId());
        Optional<DatastoreWatermark> datastoreWm = watermarkService.getDatastoreWatermark(entityId);
        datastoreWm.ifPresentOrElse(dw-> {
            // get datastore watermark
            if (null != dw.getWatermark()){
                long datastoreWmTime = dw.getWatermark().getStart();
                // get max timestamp for entity in syncari entity collection
                List<EntityData> entityData = entityRepo.findRecent(entityDefinition, 1);
                entityData.stream().findFirst().ifPresentOrElse(ed -> {
                    Long syncariTimestamp = ed.getSyncariTimestamp();
                    lag.setDataStoreCurrentTimestamp(Instant.ofEpochMilli(datastoreWmTime).toString());
                    //compare difference between maxtimestamp and datastorewmtime
                    if (datastoreWmTime == syncariTimestamp){
                        log.info("No lag exists for this entity {}", entityDefinition.getApiName());
                    }else{
                        try{
                            // if there is a difference then calculate count difference to get pending records, otherwise pending records is 0
                            long entityCount = entityRepoService.getCountWithDeleteCriteria(entityDefinition.getApiName());
                            Connector datastore = datastoreService.findActiveDatastore().orElseThrow(() -> new RuntimeException("Datastore connector missing"));

                            // Refresh OAuth tokens if needed before calling count API
                            if (!datastore.isSyncariDatastore()) {
                                String authType = datastore.getMetaConfig()
                                    .getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();

                                if (authType.equalsIgnoreCase(AuthType.Oauth.toString())) {
                                    connectorService.refreshAuthentication(datastore);
                                }
                            }

                            ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(Optional.of(datastore));
                            long datastoreCount = datastoreService.getService(connectorInfo).count(connectorInfo, entityDefinition.getResolvedDataStoreName());
                            lag.setPendingRecords(Math.max(0,(entityCount-datastoreCount)));
                        }catch (Exception e ){
                            log.error("Exception occurred while fetching lag, stack trace is {}", ExceptionUtils.getStackTrace(e));
                            lag.setError(e.getMessage());
                        }
                    }
                },()-> {
                    log.info("No datastorewatermark exists, this entity {} seems not active anymore", entityDefinition.getApiName());
                });
            }
        },() -> {
            log.info("No datastorewatermark exists, this entity {} never synched to datastore", entityDefinition.getApiName());
        });
        return lag;
    }


}
