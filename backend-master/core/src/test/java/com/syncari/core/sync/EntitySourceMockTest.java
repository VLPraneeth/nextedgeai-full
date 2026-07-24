package com.syncari.core.sync;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.utils.GraphHelper;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.google.common.collect.Lists;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.UnresolvedReference;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.UnresolvedReferenceService;

public class EntitySourceMockTest extends AbstractSyncariTest {
	@Autowired
	private StagedBatchRecordRepo stagedBatchRecordRepo;
    @Autowired
	private EntitySource entitySource;
    @MockBean
	private UnresolvedReferenceService unresolvedReferenceService;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    UnresolvedReferenceRepo unresolvedReferenceRepo;
    @Autowired
    ConnectorService connectorService;


    @Before
    public void setUp() {
        super.setUp();
    }

    @Test
    public void pullUnresolvedReferencesWithZeroRecords() {
        DataService mock = mock(DataService.class);

        String nextId = "";
        when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(List.of());
        Connector c = new Connector("conn", "metaId", "endpoint");
        c.setId("connid");
        var entityDef = new EntityDefinition("externalEntity", "External Entity");
        entityDef.setConnectorId("connid");
        var entity = entityProxyRepo.save(entityDef);
        EntityFetchResult entityFetchResult = new EntityFetchResult(entity, new SyncRequest(), 
            new FetchResponse(new WatermarkInfo(),mock(EntityDataBatchIterator.class)), c, new EntitySchema(), new Watermark(), false);
        Set<String> unresolvedExternalRecordIds = entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                Map.of(), mock,  new HashMap<>(), "syncCycleId", Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), new MappingGraph(), new HashMap<>());
        assertTrue(unresolvedExternalRecordIds.isEmpty());
        assertTrue(stagedBatchRecordRepo.findAll().isEmpty());
        verify(unresolvedReferenceService, only()).getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000);
    }

    @Test
    public void closeSourceSucceedsOnEmptyBatch(){
        try {
            entitySource.closeSource(new GraphContext().setCurrentBatch(new CurrentBatch(stagedBatchRecordRepo).setSyncariEntityName("account")));
        }catch (Exception e){
            fail(e.getMessage());
        }
    }

    @Test
    public void pullUnresolvedReferencesWithOnePage() {
        DataService mock = mock(DataService.class);
        when(mock.getByIds(any())).thenReturn(List.of(
                new EntityData("externalEntity").setId("externalId0"),
                new EntityData("externalEntity").setId("externalId1")
        ));
        String nextId = "";
        when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(
                createUnresolvedReferences(2, "externalEntity", "connid", "account"));
        Connector c = new Connector("conn", "metaId", "endpoint");
        c.setId("connid");
        var entityDef = new EntityDefinition("externalEntity", "External Entity");
        entityDef.setConnectorId("connid");
        var entity = entityProxyRepo.save(entityDef);
        EntityFetchResult entityFetchResult = new EntityFetchResult(entity, new SyncRequest(), new FetchResponse(new WatermarkInfo(),
                mock(EntityDataBatchIterator.class)), c, new EntitySchema(), new Watermark(), false);
        Set<String> unresolvedExternalRecordIds = entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                Map.of(), mock, new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), new MappingGraph(), new HashMap<>());
        assertEquals(Set.of("externalId0", "externalId1"), unresolvedExternalRecordIds);
        assertEquals(2, stagedBatchRecordRepo.findAll().size());
        ArgumentCaptor<SyncRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(mock, only()).getByIds(requestArgumentCaptor.capture());
        verify(unresolvedReferenceService, times(1)).getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000);
        // Updating this since we're clearing SyncRequest
        assertTrue(requestArgumentCaptor.getValue().getData().isEmpty());
    }

    @Test
    public void pullUnresolvedReferencesWithDeletedExternalRecords() {
        DataService mock = mock(DataService.class);
        when(mock.getByIds(any())).thenReturn(List.of(
                new EntityData("externalEntity").setId("externalId0"),
                new EntityData("externalEntity").setId("externalId1")
        ));
        String nextId = "";
        unresolvedReferenceRepo.upsertUnResolved(createUnresolvedReferences(3, "externalEntity", "connid", "account"));
        when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(
            unresolvedReferenceRepo.findUnResolvedReferencesBy(nextId, "connid", "externalEntity", 1000));
        Connector c = new Connector("conn", "metaId", "endpoint");
        c.setId("connid");
        var entityDef = new EntityDefinition("externalEntity", "External Entity");
        entityDef.setConnectorId("connid");
        var entity = entityProxyRepo.save(entityDef);
        EntityFetchResult entityFetchResult = new EntityFetchResult(entity, new SyncRequest(), new FetchResponse(new WatermarkInfo(),
                mock(EntityDataBatchIterator.class)), c, new EntitySchema(), new Watermark(), false);
        Set<String> unresolvedExternalRecordIds = entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                Map.of(), mock, new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), new MappingGraph(), new HashMap<>());
        assertEquals(Set.of("externalId0", "externalId1"), unresolvedExternalRecordIds);
        assertEquals(2, stagedBatchRecordRepo.findAll().size());
        ArgumentCaptor<SyncRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(mock, only()).getByIds(requestArgumentCaptor.capture());
        verify(unresolvedReferenceService, times(1)).getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000);
        var unresolvedReferences = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000);
        assertEquals(3, unresolvedReferences.size());
        assertFalse(unresolvedReferences.stream().filter(record -> record.getRetries() == 1 && record.getUnresolvable() == false).collect(Collectors.toSet()).isEmpty());
        IntStream.range(0, 3).forEach(x ->{
            Set<String> unresolved = entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                        Map.of(), mock, new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), new MappingGraph(), new HashMap<>());
        });
        unresolvedReferences = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000);
        assertFalse(unresolvedReferences.stream().filter(record -> record.getRetries() == 4 && record.getUnresolvable() == true).collect(Collectors.toSet()).isEmpty());
    }

    @Test
    public void pullMaxUnresolvedReferences() {
        DataService mock = mock(DataService.class);
        List<UnresolvedReference> unresolvedReferences = createUnresolvedReferences(15000, "externalEntity", "connid", "account");
        List<List<UnresolvedReference>> partitions = Lists.partition(unresolvedReferences, 1000);
        doAnswer((Answer<List<EntityData>>) m -> {
            SyncRequest request = m.getArgument(0);
            return request.getData().get("connid").stream().map(e -> e.setValues(Map.of("NewFieldValue", "fieldValue" + e.getId()))).collect(Collectors.toList());
        }).when(mock).getByIds(any(SyncRequest.class));

        Connector c = new Connector("conn", "metaId", "endpoint");
        c.setId("connid");
        var entityDef = new EntityDefinition("externalEntity", "External Entity");
        entityDef.setConnectorId("connid");
        var entity = entityProxyRepo.save(entityDef);
        String nextId = "";
        for (int i = 0; i < 10; i++) {
            when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(partitions.get(i));
            nextId = partitions.get(i).get(partitions.get(i).size() - 1).getId();
        }
        when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(
                createUnresolvedReferences(15000, "externalEntity", "connid", "account")
        );
        EntityFetchResult entityFetchResult = new EntityFetchResult(entity, new SyncRequest(), new FetchResponse(new WatermarkInfo(),
                mock(EntityDataBatchIterator.class)), c, new EntitySchema(), new Watermark(), false);
        Set<String> unresolvedExternalRecordIds = entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                Map.of(), mock,  new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), new MappingGraph(), new HashMap<>());
        assertEquals(10000, unresolvedExternalRecordIds.size());
        assertEquals(10000, stagedBatchRecordRepo.findAll().size());
        ArgumentCaptor<SyncRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(mock, times(10)).getByIds(any());
        verify(unresolvedReferenceService, times(10)).getUnresolvedReferencesFor(anyString(), anyString(), anyString(), anyInt());

    }

    @Test
    public void pullUnresolvedReferencesSyncariEntity() {
        DataService mock = mock(DataService.class);
        List<UnresolvedReference> unresolvedReferences = createUnresolvedReferences(15000, "externalEntity", "connid", "account");
        List<List<UnresolvedReference>> partitions = Lists.partition(unresolvedReferences, 1000);
        doAnswer((Answer<List<EntityData>>) m -> {
            SyncRequest request = m.getArgument(0);
            return request.getData().get("connid").stream().map(e -> e.setValues(Map.of("NewFieldValue", "fieldValue" + e.getId()))).collect(Collectors.toList());
        }).when(mock).getByIds(any(SyncRequest.class));

        Connector c = new Connector("conn", "metaId", "endpoint");
        c.setId("connid");
        var entityDef = new EntityDefinition("externalEntity", "External Entity");
        entityDef.setConnectorId("connid");
        var entity = entityProxyRepo.save(entityDef);
        String nextId = "";
        for (int i = 0; i < 10; i++) {
            when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(partitions.get(i));
            nextId = partitions.get(i).get(partitions.get(i).size() - 1).getId();
        }
        when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(
                createUnresolvedReferences(15000, "externalEntity", "connid", "account")
        );
        EntityFetchResult entityFetchResult = new EntityFetchResult(entity, new SyncRequest(), new FetchResponse(new WatermarkInfo(),
                mock(EntityDataBatchIterator.class)), c, new EntitySchema(), new Watermark(), false);
        Set<String> unresolvedExternalRecordIds = entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                Map.of(), mock,  new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), new MappingGraph(), new HashMap<>());
        assertEquals(10000, unresolvedExternalRecordIds.size());
        assertEquals(10000, stagedBatchRecordRepo.findAll().size());
        ArgumentCaptor<SyncRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(mock, times(10)).getByIds(any());
        verify(unresolvedReferenceService, times(10)).getUnresolvedReferencesFor(anyString(), anyString(), anyString(), anyInt());


        doAnswer((Answer<List<EntityData>>) m -> {
            SyncRequest request = m.getArgument(0);
            return request.getData().get("connid").stream().map(e -> e.setValues(Map.of("NewFieldValue", "fieldValue" + e.getId()))).collect(Collectors.toList());
        }).when(mock).getByIds(any(SyncRequest.class));


        when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(
                createUnresolvedReferences(15000, "externalEntity", "connid", "account")
        );

        //entityProxyRepo.findByN
        var syncariEntityDefinition = new EntityDefinition("account__c", "Account Entity");
        syncariEntityDefinition.setConnectorId(connectorService.getSyncariConnector().getId());
        syncariEntityDefinition.setDraftStatus(DraftStatus.APPROVED);
        entityProxyRepo.save(syncariEntityDefinition);

        stagedBatchRecordRepo.reset();
        entityFetchResult = new EntityFetchResult(entity, new SyncRequest(), new FetchResponse(new WatermarkInfo(),
                mock(EntityDataBatchIterator.class)), c, new EntitySchema(), new Watermark(), false);
        unresolvedExternalRecordIds = entitySource.pullUnresolvedReferences(entityFetchResult,"account__c",
                Map.of(), mock,  new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), new MappingGraph(), new HashMap<>());
        assertEquals(0, unresolvedExternalRecordIds.size());
        assertEquals(0, stagedBatchRecordRepo.findAll().size());
    }

    @Test
    public void pullUnresolvedReferencesError() {
        DataService mock = mock(DataService.class);
        List<UnresolvedReference> unresolvedReferences = createUnresolvedReferences(15000, "externalEntity", "connid", "account");
        List<List<UnresolvedReference>> partitions = Lists.partition(unresolvedReferences, 1000);
        doAnswer((Answer<List<EntityData>>) m -> {
            throw new NotSupportedException("Unsupported Operation");
            //throw new NonRetriableException("NOT FOUND", "NOT FOUND", "NOT FOUND");
        }).when(mock).getByIds(any(SyncRequest.class));

        Connector c = new Connector("conn", "metaId", "endpoint");
        c.setId("connid");
        var entityDef = new EntityDefinition("externalEntity", "External Entity");
        entityDef.setConnectorId("connid");
        var entity = entityProxyRepo.save(entityDef);
        String nextId = "";
        for (int i = 0; i < 10; i++) {
            when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(partitions.get(i));
            nextId = partitions.get(i).get(partitions.get(i).size() - 1).getId();
        }
        when(unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "connid", "externalEntity", 1000)).thenReturn(
                createUnresolvedReferences(15000, "externalEntity", "connid", "account")
        );

        MappingGraph entityGraph = GraphHelper.newGraph(new EntityDefinition("account__c", "Account Entity")).src(entityDef).connect(entityDef.getApiName(), "account__c").getGraph();

        EntityFetchResult entityFetchResult = new EntityFetchResult(entity, new SyncRequest(), new FetchResponse(new WatermarkInfo(),
                mock(EntityDataBatchIterator.class)), c, new EntitySchema(), new Watermark(), false);
        entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                Map.of(), mock,  new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), entityGraph, new HashMap<>());

        doAnswer((Answer<List<EntityData>>) m -> {
            throw new NonRetriableException("NOT FOUND", "NOT FOUND", "NOT FOUND");
        }).when(mock).getByIds(any(SyncRequest.class));

        try {
            entitySource.pullUnresolvedReferences(entityFetchResult,"account",
                    Map.of(), mock,  new HashMap<>(), "syncCycleId",Instant.now().toEpochMilli(), new HashMap<>(), new HashMap<>(), entityGraph, new HashMap<>());
            fail();
        } catch (PipelineException e) {
            assertEquals("NOT FOUND", e.getCause().getMessage());
            assertEquals(entityGraph.getId(), e.getGraphId());
            assertEquals(entityGraph.getSources().collect(Collectors.toList()).get(0).getId(), e.getNodeId());
        }
    }


    private List<UnresolvedReference> createUnresolvedReferences(int num, String externalEntityDefName, String connectorId, String referredSyncariEntity) {
        List<UnresolvedReference> references = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            UnresolvedReference reference = new UnresolvedReference()
                .setExternalRefRecordId("externalId" + i).setExternalRefEntityName(externalEntityDefName).setConnectorId(connectorId).setReferredSyncariEntity(referredSyncariEntity);
            reference.setId((new ObjectId()).toString());
            references.add(reference);
        }
        return references;
    }


}
