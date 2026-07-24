package com.syncari.core.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncResponse;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.repositories.customer.IdMappingRepo;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

public class IdMappingServiceMockTest extends AbstractSyncariTest {

    @Autowired
    IdMappingService idmapService;

    @Test
    public void saveAll_IgnoreDuplicateKeyException() {
        IdMappingRepo idMappingRepo = Mockito.mock(IdMappingRepo.class);
        IdMappingService idMappingService = new IdMappingService(idMappingRepo);
        IdMapping idMapping = new IdMapping().setSyncariId("firstSyncariId").setEntityName("account")
            .addMapping("connector", "externalId", "sinkId");
        IdMapping idMapping2 = new IdMapping().setSyncariId("firstSyncariId2").setEntityName("account")
            .addMapping("connector", "externalId2", "sinkId");
        when(idMappingRepo.saveAll(List.of(idMapping, idMapping2))).thenThrow(new DuplicateKeyException("Dummy dupe exception"));
        when(idMappingRepo.save(idMapping)).thenThrow(new DuplicateKeyException("Dummy dupe exception"));
        List<IdMapping> idMappings = idMappingService.saveAll(List.of(idMapping, idMapping2));
        // Even if there is an exception for one of the idMappings, we still end up saving the other one and logging an exception for the failed one,
        // however both gets returned by the saveAll api.
        assertTrue(idMappings.size() == 2);
        verify(idMappingService.mappingRepo, times(1)).saveAll(List.of(idMapping, idMapping2));
        verify(idMappingService.mappingRepo, times(1)).save(idMapping);
        verify(idMappingService.mappingRepo, times(1)).save(idMapping2);
    }

    @Test
    public void saveIdMappingWith1Mapping(){
        EntityDefinition syncariEntity = new EntityDefinition().setApiName("syncariEntity").setConnectorId("connectorSyncari");
        syncariEntity.setId("syncariEntity1");
        EntityDefinition sinkEntity = new EntityDefinition().setApiName("sinkEntity").setConnectorId("connectorSink");
        sinkEntity.setId("sinkId1");
        SyncResponse response = new SyncResponse();
        Result result1 = new Result(true, "test1", "testsyncariid");
        response.setResults(List.of(result1));
        idmapService.saveIdMapping(syncariEntity,"connectorSyncari",response,sinkEntity);
        assertTrue(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).isPresent());
        assertEquals(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).get().getMappings().size(), 1);
    }

    @Test
    public void saveIdMappingWith2Mapping(){
        EntityDefinition syncariEntity = new EntityDefinition().setApiName("syncariEntity").setConnectorId("connectorSyncari");
        syncariEntity.setId("syncariEntity1");
        EntityDefinition sinkEntity = new EntityDefinition().setApiName("sinkEntity").setConnectorId("connectorSink");
        sinkEntity.setId("sinkId1");
        SyncResponse response = new SyncResponse();
        Result result1 = new Result(true, "test1", "testsyncariid");
        response.setResults(List.of(result1));
        idmapService.saveIdMapping(syncariEntity,"connectorSyncari",response,sinkEntity);
        assertTrue(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).isPresent());
        assertEquals(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).get().getMappings().size(), 1);

        Result result2 = new Result(true, "test2", "testsyncariid");
        response.setResults(List.of(result2));

        EntityDefinition sinkEntity2 = new EntityDefinition().setApiName("sinkEntity1").setConnectorId("connectorSink1");
        sinkEntity2.setId("sinkId2");
        idmapService.saveIdMapping(syncariEntity,"connectorSyncari1",response,sinkEntity2);
        assertTrue(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).isPresent());
        assertEquals(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).get().getMappings().size(), 2);

    }

    // Two with same syncari id, only 1 should be added as mapping
    @Test
    public void saveIdMappingWithTwoMappingOneCall(){
        EntityDefinition syncariEntity = new EntityDefinition().setApiName("syncariEntity").setConnectorId("connectorSyncari");
        syncariEntity.setId("syncariEntity1");
        EntityDefinition sinkEntity = new EntityDefinition().setApiName("sinkEntity").setConnectorId("connectorSink");
        sinkEntity.setId("sinkId1");
        SyncResponse response = new SyncResponse();
        Result result1 = new Result(true, "test1", "testsyncariid");
        Result result2 = new Result(true, "test2", "testsyncariid");
        response.setResults(List.of(result1,result2));

        EntityDefinition sinkEntity2 = new EntityDefinition().setApiName("sinkEntity1").setConnectorId("connectorSink1");
        sinkEntity2.setId("sinkId2");
        idmapService.saveIdMapping(syncariEntity,"connectorSyncari1",response,sinkEntity2);
        assertTrue(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).isPresent());
        assertEquals(1,idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).get().getMappings().size());
    }


    // Two with different syncari id, only 1 should be added as mapping
    @Test
    public void saveIdMappingWithTwoMappingTwoCallDifferentSyncariId(){
        EntityDefinition syncariEntity = new EntityDefinition().setApiName("syncariEntity").setConnectorId("connectorSyncari");
        syncariEntity.setId("syncariEntity1");
        EntityDefinition sinkEntity = new EntityDefinition().setApiName("sinkEntity").setConnectorId("connectorSink");
        sinkEntity.setId("sinkId1");
        SyncResponse response = new SyncResponse();
        Result result1 = new Result(true, "test1", "testsyncariid1");
        Result result2 = new Result(true, "test2", "testsyncariid2");
        response.setResults(List.of(result1));

        EntityDefinition sinkEntity2 = new EntityDefinition().setApiName("sinkEntity1").setConnectorId("connectorSink1");
        sinkEntity2.setId("sinkId2");
        idmapService.saveIdMapping(syncariEntity,"connectorSyncari1",response,sinkEntity);
        response.setResults(List.of(result2));
        idmapService.saveIdMapping(syncariEntity,"connectorSyncari1",response,sinkEntity2);
        assertTrue(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).isPresent());
        assertEquals(idmapService.findBySyncariId(syncariEntity.getApiName(), result1.getSyncariId()).get().getMappings().size(), 1);
        assertEquals(idmapService.findBySyncariId(syncariEntity.getApiName(), result2.getSyncariId()).get().getMappings().size(), 1);
    }
}
