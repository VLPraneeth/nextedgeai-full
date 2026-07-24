package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.simulation.SimulationCurrentBatch;
import com.syncari.core.utils.SchemaHelper;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FieldPipelineDeleteTest extends AbstractSyncariTest {

    @MockBean
    SchemaService schemaService;
    @MockBean
    EntityRepo entityRepo;

    ConnectorService connectorService;

    @MockBean
    MappingGraphService graphService;

    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;

    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    @Autowired
    FunctionService functionService;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @MockBean
    Actions actions;
    private Connector syncariConnector;

    private Connector zendeskConnector;

    @Autowired
    RecordMergeService recordMergeService;

    @Autowired
    TransactionLogService transactionLogService;

    @Before
    public void init() {

        doNothing().when(eventService).log(any());
    }

    @Override
    public void setUp() {
        connectorService = mock(ConnectorService.class);
        executeFieldPipeline.connectorService = connectorService;
        if(syncariConnector == null){
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }

        if (zendeskConnector == null) {
            zendeskConnector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        }

        when(schemaService.getSyncariSchema()).thenReturn(new Schema());
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(connectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());
        super.setUp();
    }

    @Test
    public void deleteTest() {
        EntityDefinition coreEntityDef = new EntityDefinition();
        coreEntityDef.setApiName("account");
        coreEntityDef.setDisplayName("Account");
        coreEntityDef.setStatus(Status.ACTIVE);
        coreEntityDef.setId(ObjectId.get().toHexString());

        EntityDefinition srcEntityDef = new EntityDefinition();
        srcEntityDef.setConnectorId(zendeskConnector.getId());
        srcEntityDef.setApiName("Organization");
        srcEntityDef.setDisplayName("Organization");
        srcEntityDef.setStatus(Status.ACTIVE);
        srcEntityDef.setId(ObjectId.get().toHexString());

        AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("Name", new StringType(), srcEntityDef.getId());


        AttributeDefinition coreRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), coreEntityDef.getId());

        AttributeDefinition coreQualityAttribute = SchemaHelper.createAttribute("Quality", new StringType(), coreEntityDef.getId());

        AttributeDefinition srcRevenueAttribute = SchemaHelper.createAttribute("Revenue", new DoubleType(), srcEntityDef.getId());

        AttributeDefinition srcQualityAttribute = SchemaHelper.createAttribute("Sink Quality", new StringType(), srcEntityDef.getId());

        coreEntityDef.addField(coreNameAttr);
        coreEntityDef.addField(coreRevenueAttribute);
        coreEntityDef.addField(coreQualityAttribute);

        srcEntityDef.addField(srcNameAttr);
        srcEntityDef.addField(srcRevenueAttribute);
        srcEntityDef.addField(srcQualityAttribute);

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);

        Edge srcToCore = edge(srcNode, coreNode, entityGraph);
        srcToCore.setId(ObjectId.get().toHexString());

        MappingGraph nameAttrGraph = createGraph(coreNameAttr.getId(), Scope.ATTRIBUTE);
        MappingGraph revAttrGraph = createGraph(coreRevenueAttribute.getId(), Scope.ATTRIBUTE);
        MappingGraph qualityAttrGraph = createGraph(coreQualityAttribute.getId(), Scope.ATTRIBUTE);

        MappingNode coreNameAttrNode = coreAttributeNode(coreNameAttr, nameAttrGraph);
        MappingNode srcNameAttrNode = srcAttributeNode(srcNameAttr, nameAttrGraph);
        edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

        MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualityAttrGraph);
        MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualityAttrGraph);
        edge(srcQAttrNode, coreQAttrNode, qualityAttrGraph);

        MappingNode coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
        MappingNode srcRevAttrNode = srcAttributeNode(srcRevenueAttribute, revAttrGraph);
        edge(srcRevAttrNode, coreRevAttrNode, revAttrGraph);

        String syncariId = ObjectId.get().toHexString();
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(syncariId)
                .setConnectorId(zendeskConnector.getId())
                .setLastModified(Instant.now().toEpochMilli())
                .setId("deleteTestId")
                .addValue("Name", "Account Name1")
                .addValue("Revenue", 200.0)
                .addValue("_source","test")
                .addValue("Sink Quality", "VERY GOOD");
        List<EntityData> t = List.of(entityData);

        when(graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId())).thenReturn(List.of(nameAttrGraph,revAttrGraph,qualityAttrGraph));

        AttributeRepo mockAttributeRepo = mock(AttributeRepo.class);
        when(mockAttributeRepo.findAllById(List.of(coreEntityDef.getFieldByName("Name").getId(),coreEntityDef.getFieldByName("Revenue").getId(),coreEntityDef.getFieldByName("Quality").getId())))
                .thenReturn(List.of(coreEntityDef.getFieldByName("Name"),coreEntityDef.getFieldByName("Revenue"),coreEntityDef.getFieldByName("Quality")));
        when(mockAttributeRepo.findById(coreEntityDef.getFieldByName("Name").getId()))
                .thenReturn(Optional.of(coreEntityDef.getFieldByName("Name")));
        when(mockAttributeRepo.findById(coreEntityDef.getFieldByName("Revenue").getId()))
                .thenReturn(Optional.of(coreEntityDef.getFieldByName("Revenue")));
        when(mockAttributeRepo.findById(coreEntityDef.getFieldByName("Quality").getId()))
                .thenReturn(Optional.of(coreEntityDef.getFieldByName("Quality")));

        UnresolvedRecordService mockUnresolvedRecordService = mock(UnresolvedRecordService.class);
        when(mockUnresolvedRecordService.getUnresolvedRecords(anyString())).thenReturn(List.of());
        when(mockUnresolvedRecordService.getUnresolvedEntities(anyString(),anyString())).thenReturn(List.of());

        UnresolvedReferenceRepo mockUnresolvedReferenceRepo = mock(UnresolvedReferenceRepo.class);
        doNothing().when(mockUnresolvedReferenceRepo).updateSyncariValues(anyList());
        doNothing().when(mockUnresolvedReferenceRepo).upsertUnResolved(anyList());
        doNothing().when(mockUnresolvedReferenceRepo).deleteAllById(anyList());
        when(mockUnresolvedReferenceRepo.findUnresolvedReferenceBy(coreEntityDef.getId())).thenReturn(List.of());

        EntityRepoService mockEntityRepoService = mock(EntityRepoService.class);
        doNothing().when(mockEntityRepoService).computeScore(anyList(), anyString());

        executeFieldPipeline.attributeProxyRepo = mockAttributeRepo;
        executeFieldPipeline.unresolvedReferenceRepo = mockUnresolvedReferenceRepo;
        executeFieldPipeline.repoService = mockEntityRepoService;

        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
        when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
        when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);

        when(connectorService.get(eq(srcEntityDef.getConnectorId()))).thenReturn(zendeskConnector);
        when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
        when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
        when(entityRepo.save(any(),any())).thenReturn(entityData);

        GraphContext currentContext = new GraphContext();
        currentContext.set("field_"+srcQualityAttribute.getId(),"GOOD");
        currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
        currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
        currentContext.setGraph(entityGraph);

        SimulationCurrentBatch currentBatch = getCurrentBatch(SerializationUtils.clone(entityData), srcEntityDef, coreEntityDef, syncariId, true);
        currentBatch.setExistingRecords(List.of(entityData));

        currentContext.setCurrentBatch(currentBatch);

        executeFieldPipeline.execute(viperContext,currentContext);

        var transactions = transactionLogService.findLatestTransactions(currentBatch.getCurrentBatchId(), Date.from(Instant.EPOCH), List.of(syncariId)).get(syncariId);
        assertTrue(transactions.size() > 0);

        assertEquals(Operation.delete, transactions.get(0).getOperation());
        assertEquals(zendeskConnector.getName(), transactions.get(0).getSources().get(0).getConnectorName());
        assertEquals(syncariId, transactions.get(0).getSyncariId());
    }

    private SimulationCurrentBatch getCurrentBatch(EntityData entityData, EntityDefinition srcEntity, EntityDefinition syncariEntity, String syncariId, boolean isDeleted){

        entityData.setDeleted(isDeleted);
        entityData.setSyncariEntityId(syncariId);
        StagedBatch staged = new StagedBatch(syncariEntity.getApiName()).setConnectorId(srcEntity.getConnectorId())
                .setCurrentBatchId(UUID.randomUUID().toString()).setSourceEntityName(srcEntity.getApiName())
                .setSourceEntityDefinitionId(srcEntity.getId());
        staged.setId(UUID.randomUUID().toString());
        StagedBatchRecord record = new StagedBatchRecord()
                .setStagedBatchId(staged.getId())
                .setEntityData(entityData)
                .setExternalRecordId(entityData.getId())
                .setExternalEntityDefinitionId(srcEntity.getId());
        record.setId(UUID.randomUUID().toString());
        record.setSyncariId(syncariId);
        SimulationCurrentBatch currentBatch = new SimulationCurrentBatch();
        currentBatch.setBatchRecords(List.of(record));
        currentBatch.setEntityBatch(srcEntity, staged);
        currentBatch.setSyncariEntityName(syncariEntity.getApiName());
        currentBatch.setCurrentBatchId(ObjectId.get().toHexString());
        return currentBatch;
    }
}
