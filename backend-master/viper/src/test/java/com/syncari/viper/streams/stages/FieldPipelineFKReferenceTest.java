package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.IdType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.service.*;
import com.syncari.utils.Pair;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;


public class FieldPipelineFKReferenceTest extends AbstractSyncariTest {

    @MockBean
    IdMappingRepo idMappingRepo;
    @Autowired
    FunctionService functionService;
    @Mock
    SchemaService schemaService;
    @Mock
    EntityRepo entityRepo;
    @Mock
    ConnectorService connectorService;
    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    FieldPipelineTestHelper helper;
    @Autowired
    ReferenceDataService refService;
    @MockBean
    MappingGraphService graphServicefService;
    @Autowired
    PipelineEvaluator evaluator;
    @Autowired
    SyncDetailMetricService syncDetailMetricService;

    @Autowired
    FeatureService featureService;

    @Autowired
    NotificationService notificationService;
    @Autowired
    PipelineUtil pipelineUtil;

    @Before
    public void init() {
        doNothing().when(eventService).log(any());
        executeFieldPipeline = new ExecuteFieldPipeline(connectorService,entityRepo,graphServicefService,evaluator
                ,schemaService,executeFieldPipeline.attributeProxyRepo,executeFieldPipeline.eventStore,
                executeFieldPipeline.recordMergeService,executeFieldPipeline.idMappingRepo,
                executeFieldPipeline.unresolvedReferenceRepo,executeFieldPipeline.datastoreService,executeFieldPipeline.repoService,executeFieldPipeline.requeueService,executeFieldPipeline.transactionLogService,syncDetailMetricService, featureService, pipelineUtil,notificationService);
        helper = new FieldPipelineTestHelper(functionService, schemaService,entityRepo, connectorService,executeFieldPipeline);

    }

    @Test
    public void resolveSingleValuedAttributeReferences() {

        String accountIdField = "AccountID";
        String id = "Id";
        Connector connector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = helper.getEntityDef("contact", null, List.of(Pair.of(accountIdField, new ReferenceType())));
        EntityDefinition sourceEntityDef = helper.getEntityDef("contact", null, List.of(Pair.of(accountIdField, new ReferenceType())));
        coreEntityDef.getFieldByName(accountIdField).setReferenceTo("account");
        sourceEntityDef.getFieldByName(accountIdField).setReferenceTo("account");

        EntityDefinition fkEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(id, new IdType())));

        System.out.println("Connector " + fkEntityDef.getConnectorId() + " api name" + fkEntityDef.getApiName());

        when(schemaService.findEntity(fkEntityDef.getConnectorId(), fkEntityDef.getApiName())).thenReturn(Optional.of(fkEntityDef));
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(fkEntityDef));
        when(entityRepo.count(fkEntityDef, Optional.empty())).thenReturn(1l);
        when(graphServicefService.retrieveApprovedEntityGraph(fkEntityDef.getId())).thenReturn(Optional.of(new MappingGraph()));

        // add a single reference
        EntityData entityData = new EntityData("contact").setSyncariEntityId(ObjectId.get().toHexString()).addValue(accountIdField, "accountId1");

        IdMapping existingMapping = new IdMapping();
        existingMapping.setEntityName("account").setSyncariId("syncariId1").addMapping(connector.getId(),"accountId1", fkEntityDef.getId());
        existingMapping.setId("mappingId1");
        when(idMappingRepo.findByExternalIds("account", connector.getId(), fkEntityDef.getId(), List.of("accountId1"))).thenReturn(List.of(existingMapping));

        Change change = helper.executeFunction(coreEntityDef, sourceEntityDef, "accountId", "accountId", "trim", Map.of(), entityData);
        assertTrue(change.getChanges().has("AccountID"));
        assertEquals(change.getChanges().getValueAsString("AccountID"), "syncariId1");
        assertEquals(executeFieldPipeline.unresolvedReferenceRepo.findResolvedReferenceBy(coreEntityDef.getId()).size(), 0);

        EntityData entityData2 = new EntityData("contact").setSyncariEntityId(ObjectId.get().toHexString()).addValue(accountIdField, "accountNotReference").setReparented(false);
        change = helper.executeFunction(coreEntityDef, sourceEntityDef, "accountId", "accountId", "trim", Map.of(), entityData2);
        assertNull(change.getChanges().getValue("AccountID"));
    }

    @Test
    public void resolveMultiValuedAttributeReferences() {

        String accountIdField = "AccountID";
        String id = "Id";
        Connector connector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        EntityDefinition coreEntityDef = helper.getEntityDef("contact", null, List.of(Pair.of(accountIdField, new ReferenceType())));
        EntityDefinition sourceEntityDef = helper.getEntityDef("contact", null, List.of(Pair.of(accountIdField, new ReferenceType())));
        coreEntityDef.getFieldByName(accountIdField).setReferenceTo("account");
        sourceEntityDef.getFieldByName(accountIdField).setReferenceTo("account");

        EntityDefinition fkEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(id, new IdType())));

        System.out.println("Connector " + fkEntityDef.getConnectorId() + " api name" + fkEntityDef.getApiName());

        when(schemaService.findEntity(fkEntityDef.getConnectorId(), fkEntityDef.getApiName())).thenReturn(Optional.of(fkEntityDef));
        when(schemaService.getSyncariEntityByName("account")).thenReturn(Optional.of(fkEntityDef));
        when(entityRepo.count(fkEntityDef, Optional.empty())).thenReturn(1l);
        when(graphServicefService.retrieveApprovedEntityGraph(any())).thenReturn(Optional.of(new MappingGraph()));

        // add a single reference
        EntityData entityData = new EntityData("contact").setSyncariEntityId(ObjectId.get().toHexString()).addValue(accountIdField, List.of("accountId1", "accountId2"));

        IdMapping existingMapping = new IdMapping();
        existingMapping.setEntityName("account").setSyncariId("syncariId1").addMapping(connector.getId(),"accountId1", fkEntityDef.getId());
        existingMapping.setId("mappingId1");
        when(idMappingRepo.findByExternalIds("account", connector.getId(), fkEntityDef.getId(), List.of("accountId1"))).thenReturn(List.of(existingMapping));

        Change change = helper.executeFunction(coreEntityDef, sourceEntityDef, "accountId", "accountId", "first", Map.of(), entityData);
        assertTrue(change.getChanges().has("AccountID"));
        assertEquals(change.getChanges().getValueAsString("AccountID"), "syncariId1");
        assertEquals(executeFieldPipeline.unresolvedReferenceRepo.findResolvedReferenceBy(coreEntityDef.getId()).size(), 0);

        change = helper.executeFunction(coreEntityDef, sourceEntityDef, "accountId", "accountId", "last", Map.of(), entityData);
        assertNull(change.getChanges().getValue("AccountID"));
    }
}
