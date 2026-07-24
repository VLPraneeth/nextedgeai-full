package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.IdType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.pipeline.expression.dedupe.HighestValueExpression;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.pipeline.jtwig.TokenEnvironmentConfig;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class RecordMergeServiceMockTest {

    @Test
    public void migrateRetainedLosersSingleSynapse() {
        RecordMergeService recordMergeService = new RecordMergeService();
        IdMappingService mockIdMappingService = mock(IdMappingService.class);
        recordMergeService.idMappingService = mockIdMappingService;
        IdMapping loserIdMapping = new IdMapping().setSyncariId("loser1").addMapping("connector", "loserexternalid", "externalentitydefid");
        IdMapping winnerIdMapping = new IdMapping().setSyncariId("winner1").addMapping("connector", "winnerexternalid", "externalentitydefid");
        //loser id mapping present, but no winner id mapping present
        when(mockIdMappingService.findBySyncariIds(eq("account"), eq(List.of("loser1", "winner1")))).thenReturn(List.of(loserIdMapping, winnerIdMapping));
        IdMapping savedWinnerIdMapping = recordMergeService.migrateRetainedLosers(new MergeOperation().setEntity(new EntityDefinition("account", "Account"))
                .setWinningRecord(new EntityData("account").setSyncariEntityId("winner1"))
                .setLosingRecords(List.of(new EntityData("account").setSyncariEntityId("loser1")))
        );
        //Id Mappnig has winner id set
        assertEquals("winner1", winnerIdMapping.getSyncariId());
        //since there were no multi-ssynapse losers, loser should NOT be remapped
        assertTrue(savedWinnerIdMapping.findMapping("connector", "externalentitydefid", "loserexternalid").isEmpty());
    }

    @Test
    public void migrateRetainedLosersMultipleSynapse() {
        RecordMergeService recordMergeService = new RecordMergeService();
        IdMappingService mockIdMappingService = mock(IdMappingService.class);
        recordMergeService.idMappingService = mockIdMappingService;
        IdMapping loserIdMapping = new IdMapping().setSyncariId("loser1")
                .addMapping("connector", "loserexternalid", "externalentitydefid")
                .addMapping("connector2", "loserexternalid2", "externalentitydefid2");
        ;
        IdMapping winnerIdMapping = new IdMapping().setSyncariId("winner1").addMapping("connector", "winnerexternalid", "externalentitydefid");
        //loser id mapping present, but no winner id mapping present
        when(mockIdMappingService.findBySyncariIds(eq("account"), eq(List.of("loser1", "winner1")))).thenReturn(List.of(loserIdMapping, winnerIdMapping));
        IdMapping savedWinnerIdMapping = recordMergeService.migrateRetainedLosers(new MergeOperation().setEntity(new EntityDefinition("account", "Account"))
                .setWinningRecord(new EntityData("account").setSyncariEntityId("winner1"))
                .setLosingRecords(List.of(new EntityData("account").setSyncariEntityId("loser1")))
        );
        //Id Mappnig has winner id set
        assertEquals("winner1", winnerIdMapping.getSyncariId());
        //The losing record from the same synapse is NOT remapped
        assertTrue(savedWinnerIdMapping.findMapping("connector", "externalentitydefid", "loserexternalid").isEmpty());
        //Old idmapping has the losing record
        assertTrue(loserIdMapping.findMapping("connector", "externalentitydefid", "loserexternalid").isPresent());
        //But, the losing record from synapse 2 is mapped to the winner
        assertTrue(savedWinnerIdMapping.findMapping("connector2", "externalentitydefid2", "loserexternalid2").isPresent());
        //and removed from old idMapping
        assertFalse(loserIdMapping.findMapping("connector2", "externalentitydefid2", "loserexternalid2").isPresent());
        verify(mockIdMappingService, times(1)).findBySyncariIds(eq("account"), eq(List.of("loser1", "winner1")));
        verify(mockIdMappingService, times(1)).upsert(savedWinnerIdMapping);
        verify(mockIdMappingService, times(1)).save(loserIdMapping);
    }

    @Test
    public void migrateRetainedLosersMultipleSynapseMultipleLosers() {
        RecordMergeService recordMergeService = new RecordMergeService();
        IdMappingService mockIdMappingService = mock(IdMappingService.class);
        recordMergeService.idMappingService = mockIdMappingService;
        IdMapping loserIdMapping1 = new IdMapping().setSyncariId("loser1")
                .addMapping("connector1", "loserexternalid1_connector1", "externalentitydefid1")
                .addMapping("connector2", "loserexternalid1_connector2", "externalentitydefid2");
        ;
        IdMapping loserIdMapping2 = new IdMapping().setSyncariId("loser2")
                .addMapping("connector1", "loserexternalid2_connector1", "externalentitydefid1")
                .addMapping("connector2", "loserexternalid2_connector2", "externalentitydefid2");
        ;
        IdMapping winnerIdMapping = new IdMapping().setSyncariId("winner1").addMapping("connector1", "winnerexternalid", "externalentitydefid1");

        when(mockIdMappingService.findBySyncariIds(eq("account"), eq(List.of("loser1", "loser2", "winner1")))).thenReturn(List.of(loserIdMapping1, loserIdMapping2, winnerIdMapping));
        //loser 2 of connector2 needs to be retained. Rest deleted
        IdMapping savedWinnerIdMapping = recordMergeService.migrateRetainedLosers(new MergeOperation().setEntity(new EntityDefinition("account", "Account"))
                .setWinningRecord(new EntityData("account").setSyncariEntityId("winner1"))
                .setLosingRecords(List.of(new EntityData("account").setSyncariEntityId("loser1").setLastModified(100),
                        new EntityData("account").setSyncariEntityId("loser2").setLastModified(200)))
        );
        //Id Mappnig has winner id set
        assertEquals("winner1", winnerIdMapping.getSyncariId());
        //The losing record from the same synapse is NOT remapped
        assertTrue(savedWinnerIdMapping.findMapping("connector1", "externalentitydefid1", "loserexternalid1_connector1").isEmpty());
        //The oldest losing record from the second synapse synapse is NOT remapped either
        assertTrue(savedWinnerIdMapping.findMapping("connector2", "externalentitydefid2", "loserexternalid1_connector2").isEmpty());
        //But, the latest losing record from synapse 2 is mapped to the winner
        assertFalse(savedWinnerIdMapping.findMapping("connector2", "externalentitydefid2", "loserexternalid2_connector2").isEmpty());
        verify(mockIdMappingService, times(1)).findBySyncariIds(eq("account"), eq(List.of("loser1", "loser2", "winner1")));
        verify(mockIdMappingService, times(1)).upsert(any(IdMapping.class));
    }

    @Test
    public void generateReferenceUpdateHandlesInvalidReferenceFields() {
        RecordMergeService recordMergeService = new RecordMergeService();
        EventStore mockEventStore = mock(EventStore.class);
        recordMergeService.eventStore = mockEventStore;
        doNothing().when(mockEventStore).insertErrorLogs(any());
        final EntityDefinition opptyObject = SchemaHelper.createEntityDefinition("oppty").getEntityDefinition();
        final EntityDefinition contactObject = SchemaHelper.createEntityDefinition("contact").getEntityDefinition();
        final AttributeDefinition fromAttribute = SchemaHelper.createAttribute("primaryContactId", new ReferenceType(), opptyObject.getId());
        final AttributeDefinition toAttribute = SchemaHelper.createAttribute("Id", new IdType(), contactObject.getId()).setIdField(true);
        Reference reference = new Reference().setFromAttribute(fromAttribute).setFromEntity(opptyObject).setToEntity(contactObject).setToAttribute(null);
        final List<EntityData> records = List.of(new EntityData("oppty").setSyncariEntityId("oppty1").addValue("primaryContactId","c1"),new EntityData("oppty").setSyncariEntityId("oppty2").addValue("primaryContactId","c2"));
        final List<ReferencedRecords> referencedRecords = List.of(new ReferencedRecords().setReference(reference).setReferencedRecords(records));
        final MergeOperation mergeOperation = new MergeOperation().setLoserReferencedEntities(referencedRecords);
        mergeOperation.setEntity(contactObject);
        mergeOperation.setWinningRecord(new EntityData("contact").setId("winner").setSyncariEntityId("winner"));
        ArgumentCaptor<List<SyncError>> errorCaptor = ArgumentCaptor.forClass(List.class);

        final Map<String, List<EntityData>> referenceUpdates = recordMergeService.generateReferenceUpdates(mergeOperation);
        assertTrue(referenceUpdates.isEmpty());
        verify(mockEventStore).insertErrorLogs(errorCaptor.capture());
        final List<SyncError> errorLogs = errorCaptor.getValue();
        assertEquals(1, errorLogs.size());
        assertEquals("Syncari",errorLogs.get(0).getConnectorName());
        assertEquals("The reference field 'primaryContactId' in entity 'oppty' is referring to 'contact', but the referring field is not defined.",errorLogs.get(0).getErrorDetails());
        //successful case
        reference.setToAttribute(toAttribute);
        final Map<String, List<EntityData>> validReferenceUpdates = recordMergeService.generateReferenceUpdates(mergeOperation);
        assertFalse(validReferenceUpdates.isEmpty());
        List<EntityData> updatedReferences = validReferenceUpdates.get(reference.getFromEntity().getId());
        assertEquals(2,updatedReferences.size());
        assertEquals("oppty1",updatedReferences.get(0).getSyncariEntityId());
        assertEquals("winner",updatedReferences.get(0).getValue("primaryContactId"));
        assertEquals("oppty2",updatedReferences.get(1).getSyncariEntityId());
        assertEquals("winner",updatedReferences.get(1).getValue("primaryContactId"));

    }

    @Test
    public void selectWinnerSequencing() {

        TokenEnvironment environment =new TokenEnvironmentConfig().tokenEnvironment();
        TokenHelper tokenHelper = new TokenHelper(environment);

        RecordMergeService recordMergeService = new RecordMergeService();
        recordMergeService.tokenHelper = tokenHelper;
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("city", StringType.VALUE, entityDef.getId());
        AttributeDefinition state = SchemaHelper.createAttribute("state", StringType.VALUE, entityDef.getId());
        entityDef.addField(city);
        entityDef.addField(state);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setLastModified(1).addValue("city", "SFO").addValue("state", "");
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setLastModified(2).addValue("city", "SM").addValue("state", "");
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setLastModified(3).addValue("city", "SJ").addValue("state", "");
        EntityData candidate4 = new EntityData("testentity").setSyncariEntityId("record4").setLastModified(4).addValue("city", "DN").addValue("state", "");
        EntityData candidate5 = new EntityData("testentity").setSyncariEntityId("record5").setLastModified(5).addValue("city", "NY").addValue("state", "");
        EntityData candidate6 = new EntityData("testentity").setSyncariEntityId("record6").setLastModified(6).addValue("city", "FR").addValue("state", "");
        MergeInfo mergeInfo = new MergeInfo();
        var selectWinnerMap = DedupeTestHelper.toSelectWinnerMap(
                //latest updated record with a value in address
                new HighestValueExpression(Expression.var(state.getId())),
                new FirstMatchingValueExpression(Expression.var(city.getId()),Expression.lit(List.of("SF","NM","SJ")))
        );
        Optional<EntityData> winner = recordMergeService.selectWinner(
                        new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                candidate1, List.of(candidate2,candidate3, candidate4,candidate5, candidate6),entityDef, mergeInfo);

        assertEquals(candidate3,winner.get());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(1).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());
    }

    @Test
    public void selecWinnerSortTest() {

        TokenEnvironment environment =new TokenEnvironmentConfig().tokenEnvironment();
        TokenHelper tokenHelper = new TokenHelper(environment);

        RecordMergeService recordMergeService = new RecordMergeService();
        recordMergeService.tokenHelper = tokenHelper;
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("city", StringType.VALUE, entityDef.getId());
        AttributeDefinition state = SchemaHelper.createAttribute("state", StringType.VALUE, entityDef.getId());
        entityDef.addField(city);
        entityDef.addField(state);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setLastModified(1).addValue("city", "SJ").addValue("state", "");
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setLastModified(2).addValue("city", "SM").addValue("state", "");
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setLastModified(3).addValue("city", "SJ").addValue("state", "");
        EntityData candidate4 = new EntityData("testentity").setSyncariEntityId("record4").setLastModified(4).addValue("city", "DN").addValue("state", "");
        EntityData candidate5 = new EntityData("testentity").setSyncariEntityId("record5").setLastModified(5).addValue("city", "NY").addValue("state", "");
        EntityData candidate6 = new EntityData("testentity").setSyncariEntityId("record6").setLastModified(6).addValue("city", "SJ").addValue("state", "");
        MergeInfo mergeInfo = new MergeInfo();
        var selectWinnerMap = DedupeTestHelper.toSelectWinnerMap(
                //latest updated record with a value in address
                new HighestValueExpression(Expression.var(state.getId())),
                new FirstMatchingValueExpression(Expression.var(city.getId()),Expression.lit(List.of("SF","NM","SJ")))
        );
        Optional<EntityData> winner = recordMergeService.selectWinner(
                new AdvancedDedupeConfig()
                        .setSelectWinner(selectWinnerMap),
                candidate1, List.of(candidate2,candidate3, candidate4,candidate5, candidate6),entityDef, mergeInfo);

        assertEquals(candidate6,winner.get());
        assertEquals(((Map<String, Object>)((List<Map<String,Object>>)selectWinnerMap.get("compositeValues")).get(1).get("winnerSelectionPredicate")).get("value"), mergeInfo.getWinnerSelectorPredicate());
    }
}