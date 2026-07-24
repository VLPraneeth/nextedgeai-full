package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.actions.Actions;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.core.simulation.SimulationCurrentBatch;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.CoreEntityNodeValidator;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FieldPipelineMergeTest extends AbstractSyncariTest {

    @MockBean
    IdMappingRepo idMappingRepo;
    @MockBean
    SchemaService schemaService;
    @MockBean
    EntityRepo entityRepo;
    @MockBean
    ConnectorService connectorService;
    @MockBean
    BiFunction<EntityDefinition, Document, EntityData> entityCreate;


    @MockBean
    MappingGraphService graphService;

    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    @Autowired
    FunctionService functionService;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @MockBean
    Actions actions;
    private Connector syncariConnector;

    @Autowired
    RecordMergeService recordMergeService;

    @Autowired
    TransactionLogService transactionLogService;

    @MockBean
    CoreEntityNodeValidator mockCoreEntityNodeValidator;


    @Before
    public void init() {

        doNothing().when(eventService).log(any());
    }

    @Override
    public void setUp() {
        if(syncariConnector == null){
            syncariConnector = createConnector("syncari", "syncariConnId", "syncariConnMetaId");
        }
        when(schemaService.getSyncariSchema()).thenReturn(new Schema());
        when(connectorService.getSyncariConnector()).thenReturn(syncariConnector);
        when(connectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());
        super.setUp();
    }

    @Test
    public void mergeTest(){
        {
            EntityDefinition coreEntityDef = new EntityDefinition();
            coreEntityDef.setApiName("account");
            coreEntityDef.setDisplayName("Account");
            coreEntityDef.setStatus(Status.ACTIVE);
            coreEntityDef.setId(ObjectId.get().toHexString());

            EntityDefinition srcEntityDef = new EntityDefinition();
            srcEntityDef.setConnectorId(syncariConnector.getId());
            srcEntityDef.setApiName("Organization");
            srcEntityDef.setDisplayName("Organization");
            srcEntityDef.setStatus(Status.ACTIVE);
            srcEntityDef.setId(ObjectId.get().toHexString());

            AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
            coreNameAttr.setId("Account Name1");

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
            Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

            MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualityAttrGraph);
            MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualityAttrGraph);
            Date currTime = new Date();

            //incoming_change IS update
            Map<String, Object> predicateMap = new HashMap<>();
            var preidcates = List.of(Map.of(
                    "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                    "operator", "eq",
                    "right", Map.of("type", "literal", "value", "update")
            ));
            predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
            MappingNode filterUpdates =
                    new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                            .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                            .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                            .setConfig(predicateMap)
                    )).setName("UpdatesOnly");
            filterUpdates.setId(ObjectId.get().toHexString());
            qualityAttrGraph.getNodes().add(filterUpdates);
            Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualityAttrGraph);
            Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualityAttrGraph);
            var srcRevAttrNode = srcAttributeNode(srcRevenueAttribute, revAttrGraph);
            var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
            Edge srcToCoreRev = edge(srcRevAttrNode, coreRevAttrNode, revAttrGraph);
            String syncariId = ObjectId.get().toHexString();


            EntityData entityData = new EntityData("account")
                    .setSyncariEntityId(syncariId)
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestId")
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

            when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(syncariConnector);
            when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.save(any(),any())).thenReturn(entityData);


            GraphContext currentContext = new GraphContext();
            currentContext.setCurrentBatch(createCurrentBatch());
            currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
            currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
            currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
            currentContext.setGraph(entityGraph);

            CurrentBatch currentBatch = getCurrentBatch(entityData, srcEntityDef, coreEntityDef);
            currentContext.setCurrentBatch(currentBatch);
            mockEntityRepoService.dfiRuleAssignmentService = mock(DfiRuleAssignmentService.class);
            when(mockEntityRepoService.dfiRuleAssignmentService.getRulesForEntityByField(any())).thenReturn(Map.of());

            executeFieldPipeline.execute(viperContext,currentContext);

            com.syncari.core.model.pagination.Page<EntityData> page = new com.syncari.core.model.pagination.Page<EntityData>();
            page.setPageInfo(new PageInfo("0", "2", false));
            page.setRecords(List.of(entityData));

            EntityData entityData1 = new EntityData("account")
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestRO1")
                    .setSyncariEntityId("mergeTestRO1")
                    .addValue("Name", "Account Name1")
                    .addValue("Revenue", 20.0)
                    .addValue("_source","test")
                    .addValue("Sink Quality", "VERY GOOD");

            AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig().setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.LATEST_WITH_VALUE)
                    .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);
            WinnerSelection revWinner = new WinnerSelection().setWinnerSelectionType(revAttrGraph.getId()).
                    setWinnerSelectionValue(FieldLevelWinnerSelection.WITH_LOWEST_VALUE.toString());
            advancedDedupeConfig.setSelectWinner(toSelectWinnerMap(revWinner));

            Map<String, Object> dupeFindCri = toFindDuplicates();
            advancedDedupeConfig.setFindDupes(dupeFindCri);
            advancedDedupeConfig.setMergeAction(MergeAction.REPORT_ONLY);
            advancedDedupeConfig.setMaximumAllowedDupes("");
            ((CoreEntityNodeConfig)coreNode.getConfiguration()).setAdvancedDedupeConfig(advancedDedupeConfig);

            currentBatch = getCurrentBatch(entityData,entityData1, srcEntityDef, coreEntityDef);
            currentContext.setCurrentBatch(currentBatch);

            // We are mocking attributedIds here. Hence we need to skip processing indexes. Force true.
            currentContext.cache("processedIndexes", true);
            com.syncari.core.model.pagination.Page<EntityData> page1 = new com.syncari.core.model.pagination.Page<EntityData>(new PageInfo("0", "2", false),List.of(entityData1));
            when(entityRepo.searchWithFallback(any(),any(), any(), anyBoolean(), anyInt())).thenReturn(page1);
            when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.findById(coreEntityDef,entityData1.getSyncariEntityId())).thenReturn(Optional.of(entityData1));
            when(entityRepo.save(any(),any())).thenReturn(entityData1);
            Optional<MergeOperation> mergeOperation = recordMergeService.advancedDedupeMerge(advancedDedupeConfig,entityData1,coreEntityDef,currentContext, null, Optional.empty());
            assertTrue(mergeOperation.isPresent());
            mergeOperation.ifPresent(x -> {
                assertTrue(x.isReportOnly());
            });
            assertTrue(mergeOperation.get().getWinningRecord().getSyncariEntityId().equalsIgnoreCase(entityData1.getSyncariEntityId()));
            executeFieldPipeline.execute(viperContext,currentContext);
            List<TransactionLog> transactionLogs =  transactionLogService.findByBatchIdAndSyncariIdIn(currentBatch.getCurrentBatchId(), List.of(entityData.getSyncariEntityId()), currTime);
            assertEquals(2,transactionLogs.size());
            assertTrue(transactionLogs.get(0).getOperation().name().equalsIgnoreCase(Operation.merge_report_only.name()));
            assertTrue(transactionLogs.get(0).getAdditionalInfo().size()==1);
            assertEquals(Operation.create, transactionLogs.get(1).getOperation());
        }
    }

    @Test
    public void mergeTest_ReportOnly(){
        {
            EntityDefinition coreEntityDef = new EntityDefinition();
            coreEntityDef.setApiName("account");
            coreEntityDef.setDisplayName("Account");
            coreEntityDef.setStatus(Status.ACTIVE);
            coreEntityDef.setId(ObjectId.get().toHexString());


            EntityDefinition srcEntityDef = new EntityDefinition();
            srcEntityDef.setConnectorId(syncariConnector.getId());
            srcEntityDef.setApiName("Organization");
            srcEntityDef.setDisplayName("Organization");
            srcEntityDef.setStatus(Status.ACTIVE);
            srcEntityDef.setId(ObjectId.get().toHexString());

            AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
            coreNameAttr.setId("Account Name1");

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
            Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

            MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualityAttrGraph);
            MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualityAttrGraph);

            Date currTime = new Date();

            //incoming_change IS update
            Map<String, Object> predicateMap = new HashMap<>();
            var preidcates = List.of(Map.of(
                    "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                    "operator", "eq",
                    "right", Map.of("type", "literal", "value", "update")
            ));
            predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
            MappingNode filterUpdates =
                    new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                            .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                            .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                            .setConfig(predicateMap)
                    )).setName("UpdatesOnly");
            filterUpdates.setId(ObjectId.get().toHexString());
            qualityAttrGraph.getNodes().add(filterUpdates);
            Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualityAttrGraph);
            Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualityAttrGraph);
            var srcRevAttrNode = srcAttributeNode(srcRevenueAttribute, revAttrGraph);
            var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
            Edge srcToCoreRev = edge(srcRevAttrNode, coreRevAttrNode, revAttrGraph);
            String syncariId = ObjectId.get().toHexString();


            EntityData entityData = new EntityData("account")
                    .setSyncariEntityId(syncariId)
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestRO")
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
            when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(syncariConnector);
            when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.save(any(),any())).thenReturn(entityData);
            when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
            when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
            when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);


            GraphContext currentContext = new GraphContext();
            currentContext.setCurrentBatch(createCurrentBatch());
            currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
            currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
            currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
            currentContext.setGraph(entityGraph);

            CurrentBatch currentBatch = getCurrentBatch(entityData, srcEntityDef, coreEntityDef);
            currentContext.setCurrentBatch(currentBatch);
            when(entityRepo.save(any(),any())).thenReturn(entityData);
            mockEntityRepoService.dfiRuleAssignmentService = mock(DfiRuleAssignmentService.class);
            when(mockEntityRepoService.dfiRuleAssignmentService.getRulesForEntityByField(any())).thenReturn(Map.of());
            executeFieldPipeline.execute(viperContext,currentContext);

            com.syncari.core.model.pagination.Page<EntityData> page = new com.syncari.core.model.pagination.Page<EntityData>();
            page.setPageInfo(new PageInfo("0", "2", false));
            page.setRecords(List.of(entityData));

            EntityData entityData1 = new EntityData("account")
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestRO1")
                    .setSyncariEntityId("mergeTestRO1")
                    .addValue("Name", "Account Name1")
                    .addValue("Revenue", 20.0)
                    .addValue("_source","test")
                    .addValue("Sink Quality", "VERY GOOD");

            AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig().setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.LATEST_WITH_VALUE)
                    .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);
            WinnerSelection revWinner = new WinnerSelection().setWinnerSelectionType(revAttrGraph.getId()).
                    setWinnerSelectionValue(FieldLevelWinnerSelection.WITH_LOWEST_VALUE.toString());
            advancedDedupeConfig.setSelectWinner(toSelectWinnerMap(revWinner));

            Map<String, Object> dupeFindCri = toFindDuplicates();
            advancedDedupeConfig.setFindDupes(dupeFindCri);
            advancedDedupeConfig.setMergeAction(MergeAction.REPORT_ONLY);
            advancedDedupeConfig.setMaximumAllowedDupes("2");
            ((CoreEntityNodeConfig)coreNode.getConfiguration()).setAdvancedDedupeConfig(advancedDedupeConfig);

            currentBatch = getCurrentBatch(entityData,entityData1, srcEntityDef, coreEntityDef);
            currentContext.setCurrentBatch(currentBatch);
            
            // We are mocking attributedIds here. Hence we need to skip processing indexes. Force true.
            currentContext.cache("processedIndexes", true);
            com.syncari.core.model.pagination.Page<EntityData> page1 = new com.syncari.core.model.pagination.Page<EntityData>(new PageInfo("0", "2", false),List.of(entityData1));
            when(entityRepo.searchWithFallback(any(),any(), any(), anyBoolean(), anyInt())).thenReturn(page1);
            when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.findById(coreEntityDef,entityData1.getSyncariEntityId())).thenReturn(Optional.of(entityData1));
            when(entityRepo.save(any(),any())).thenReturn(entityData1);
            Optional<MergeOperation> mergeOperation = recordMergeService.advancedDedupeMerge(advancedDedupeConfig,entityData1,coreEntityDef,currentContext, null, Optional.empty());
            assertTrue(mergeOperation.isPresent());
            mergeOperation.ifPresent(x -> {
                assertTrue(x.isReportOnly());
            });
            assertTrue(mergeOperation.get().getWinningRecord().getSyncariEntityId().equalsIgnoreCase(entityData1.getSyncariEntityId()));
            executeFieldPipeline.execute(viperContext,currentContext);
            List<TransactionLog> transactionLogs =  transactionLogService.findByBatchIdAndSyncariIdIn(currentBatch.getCurrentBatchId(), List.of(entityData.getSyncariEntityId()), currTime);
            assertEquals(2,transactionLogs.size());
            assertTrue(transactionLogs.get(0).getOperation().name().equalsIgnoreCase(Operation.merge_report_only.name()));
            assertTrue(transactionLogs.get(0).getAdditionalInfo().size()==1);
            assertEquals(Operation.create, transactionLogs.get(1).getOperation());
        }
    }


    @Test
    public void mergeTest_Maxdupes(){
        {
            EntityDefinition coreEntityDef = new EntityDefinition();
            coreEntityDef.setApiName("account");
            coreEntityDef.setDisplayName("Account");
            coreEntityDef.setStatus(Status.ACTIVE);
            coreEntityDef.setId(ObjectId.get().toHexString());


            EntityDefinition srcEntityDef = new EntityDefinition();
            srcEntityDef.setConnectorId(syncariConnector.getId());
            srcEntityDef.setApiName("Organization");
            srcEntityDef.setDisplayName("Organization");
            srcEntityDef.setStatus(Status.ACTIVE);
            srcEntityDef.setId(ObjectId.get().toHexString());

            AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
            coreNameAttr.setId("Account Name1");

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
            Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

            MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualityAttrGraph);
            MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualityAttrGraph);
            Date currTime = new Date();

            //incoming_change IS update
            Map<String, Object> predicateMap = new HashMap<>();
            var preidcates = List.of(Map.of(
                    "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                    "operator", "eq",
                    "right", Map.of("type", "literal", "value", "update")
            ));
            predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
            MappingNode filterUpdates =
                    new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                            .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                            .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                            .setConfig(predicateMap)
                    )).setName("UpdatesOnly");
            filterUpdates.setId(ObjectId.get().toHexString());
            qualityAttrGraph.getNodes().add(filterUpdates);
            Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualityAttrGraph);
            Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualityAttrGraph);
            var srcRevAttrNode = srcAttributeNode(srcRevenueAttribute, revAttrGraph);
            var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
            Edge srcToCoreRev = edge(srcRevAttrNode, coreRevAttrNode, revAttrGraph);
            String syncariId = ObjectId.get().toHexString();


            EntityData entityData = new EntityData("account")
                    .setSyncariEntityId(syncariId)
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestRO")
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
            when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(syncariConnector);
            when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.save(any(),any())).thenReturn(entityData);
            when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
            when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
            when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);


            GraphContext currentContext = new GraphContext();
            currentContext.setCurrentBatch(createCurrentBatch());
            currentContext.set("field_" + srcQualityAttribute.getId(), "GOOD");
            currentContext.set("field_"+srcRevenueAttribute.getId(),300.0d);
            currentContext.set("field_"+srcNameAttr.getId(),"Account Name");
            currentContext.setGraph(entityGraph);

            CurrentBatch currentBatch = getCurrentBatch(entityData, srcEntityDef, coreEntityDef);
            currentContext.setCurrentBatch(currentBatch);
            when(entityRepo.save(any(),any())).thenReturn(entityData);
            mockEntityRepoService.dfiRuleAssignmentService = mock(DfiRuleAssignmentService.class);
            when(mockEntityRepoService.dfiRuleAssignmentService.getRulesForEntityByField(any())).thenReturn(Map.of());
            executeFieldPipeline.execute(viperContext,currentContext);

            com.syncari.core.model.pagination.Page<EntityData> page = new com.syncari.core.model.pagination.Page<EntityData>();
            page.setPageInfo(new PageInfo("0", "2", false));
            page.setRecords(List.of(entityData));

            EntityData entityData1 = new EntityData("account")
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestRO1")
                    .setSyncariEntityId(ObjectId.get().toHexString())
                    .addValue("Name", "Account Name1")
                    .addValue("Revenue", 20.0)
                    .addValue("_source","test")
                    .addValue("Sink Quality", "VERY GOOD");

            AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig().setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.LATEST_WITH_VALUE)
                    .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);
            WinnerSelection revWinner = new WinnerSelection().setWinnerSelectionType(revAttrGraph.getId()).
                    setWinnerSelectionValue(FieldLevelWinnerSelection.WITH_LOWEST_VALUE.toString());
            advancedDedupeConfig.setSelectWinner(toSelectWinnerMap(revWinner));

            Map<String, Object> dupeFindCri = toFindDuplicates();
            advancedDedupeConfig.setFindDupes(dupeFindCri);
            advancedDedupeConfig.setMaximumAllowedDupes("0");
            ((CoreEntityNodeConfig)coreNode.getConfiguration()).setAdvancedDedupeConfig(advancedDedupeConfig);

            currentBatch = getCurrentBatch(entityData,entityData1, srcEntityDef, coreEntityDef);
            currentContext.setCurrentBatch(currentBatch);

            // We are mocking attributedIds here. Hence we need to skip processing indexes. Force true.
            currentContext.cache("processedIndexes", true);
            com.syncari.core.model.pagination.Page<EntityData> page1 = new com.syncari.core.model.pagination.Page<EntityData>(new PageInfo("0", "2", false),List.of(entityData1));
            when(entityRepo.searchWithFallback(any(),any(), any(), anyBoolean(), anyInt())).thenReturn(page1);
            when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.findById(coreEntityDef,entityData1.getSyncariEntityId())).thenReturn(Optional.of(entityData1));
            when(entityRepo.save(any(),any())).thenReturn(entityData1);
            Optional<MergeOperation> mergeOperation = recordMergeService.advancedDedupeMerge(advancedDedupeConfig,entityData1,coreEntityDef,currentContext, null, Optional.empty());
            assertTrue(mergeOperation.isPresent());
            mergeOperation.ifPresent(x -> {
                assertFalse(x.isReportOnly());
            });
            assertTrue(mergeOperation.get().getWinningRecord().getSyncariEntityId().equalsIgnoreCase(entityData1.getSyncariEntityId()));
            executeFieldPipeline.execute(viperContext,currentContext);
            List<TransactionLog> transactionLogs =  transactionLogService.findByBatchIdAndSyncariIdIn(currentBatch.getCurrentBatchId(), List.of(entityData.getSyncariEntityId()), currTime);
            assertEquals(2,transactionLogs.size());
            assertTrue(transactionLogs.get(0).getOperation().name().equalsIgnoreCase(Operation.merge_report_only.name()));
            assertTrue(transactionLogs.get(0).getAdditionalInfo().size()==1);
            assertEquals(Operation.create, transactionLogs.get(1).getOperation());
        }
    }

    @Test
    public void mergeTest_ReportOnlyWithinBatch(){
        {
            EntityDefinition coreEntityDef = new EntityDefinition();
            coreEntityDef.setApiName("account");
            coreEntityDef.setDisplayName("Account");
            coreEntityDef.setStatus(Status.ACTIVE);
            coreEntityDef.setId(ObjectId.get().toHexString());


            EntityDefinition srcEntityDef = new EntityDefinition();
            srcEntityDef.setConnectorId(syncariConnector.getId());
            srcEntityDef.setApiName("Organization");
            srcEntityDef.setDisplayName("Organization");
            srcEntityDef.setStatus(Status.ACTIVE);
            srcEntityDef.setId(ObjectId.get().toHexString());

            AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
            coreNameAttr.setId("Account Name1");

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
            Edge srcToCoreNameAttr = edge(srcNameAttrNode, coreNameAttrNode, nameAttrGraph);

            MappingNode coreQAttrNode = coreAttributeNode(coreQualityAttribute, qualityAttrGraph);
            MappingNode srcQAttrNode = srcAttributeNode(srcQualityAttribute, qualityAttrGraph);

            //incoming_change IS update
            Map<String, Object> predicateMap = new HashMap<>();
            var preidcates = List.of(Map.of(
                    "left", Map.of("datatype", "exact_match", "type", "variable", "value", "incoming_change"),
                    "operator", "eq",
                    "right", Map.of("type", "literal", "value", "update")
            ));
            predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
            MappingNode filterUpdates =
                    new MappingNode().setScope(Scope.ATTRIBUTE).setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall()
                            .setFunctionDefinition(functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get())
                            .setParams(List.of(ParameterValue.string("output_" + srcQAttrNode.getId()+".x.typedValue", "input")))
                            .setConfig(predicateMap)
                    )).setName("UpdatesOnly");
            filterUpdates.setId(ObjectId.get().toHexString());
            qualityAttrGraph.getNodes().add(filterUpdates);
            Edge srcToFilterQ = edge(srcQAttrNode, filterUpdates, qualityAttrGraph);
            Edge filterToCoreQ = edge(filterUpdates, coreQAttrNode, qualityAttrGraph);
            var srcRevAttrNode = srcAttributeNode(srcRevenueAttribute, revAttrGraph);
            var coreRevAttrNode = coreAttributeNode(coreRevenueAttribute, revAttrGraph);
            Edge srcToCoreRev = edge(srcRevAttrNode, coreRevAttrNode, revAttrGraph);
            String syncariId = ObjectId.get().toHexString();


            EntityData entityData = new EntityData("account")
                    .setSyncariEntityId(syncariId)
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestRO")
                    .addValue("Name", "Account Name1")
                    .addValue("Revenue", 200.0)
                    .addValue("_source","test")
                    .setCreatedAt(System.currentTimeMillis() - 1000);;
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
            when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(syncariConnector);
            when(entityRepo.findById(srcEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.findById(coreEntityDef,syncariId)).thenReturn(Optional.of(entityData));
            when(entityRepo.save(any(),any())).thenReturn(entityData);
            when(schemaService.getAttribute(srcNameAttr.getId())).thenReturn(srcNameAttr);
            when(schemaService.getAttribute(srcQualityAttribute.getId())).thenReturn(srcQualityAttribute);
            when(schemaService.getAttribute(srcRevenueAttribute.getId())).thenReturn(srcRevenueAttribute);


            GraphContext currentContext = new GraphContext();
            currentContext.setCurrentBatch(createCurrentBatch());
            String syncariId2 = ObjectId.get().toHexString();
            EntityData entityData2 = new EntityData("account")
                    .setSyncariEntityId(syncariId2)
                    .setConnectorId(syncariConnector.getId())
                    .setId("mergeTestRO")
                    .addValue("Name", "Account Name1")
                    //.addValue("Revenue", 300.0)
                    .addValue("_source","test").addValue("Sink Quality", "VERY GOOD").setCreatedAt(System.currentTimeMillis());


            StagedBatch staged = new StagedBatch("account").setConnectorId(srcEntityDef.getConnectorId())
                    .setCurrentBatchId(UUID.randomUUID().toString()).setSourceEntityName(srcEntityDef.getApiName())
                    .setSourceEntityDefinitionId(srcEntityDef.getId());
            staged.setId(UUID.randomUUID().toString());
            StagedBatchRecord record1 = new StagedBatchRecord()
                    .setStagedBatchId(staged.getId())
                    .setEntityData(entityData)
                    .setExternalRecordId(entityData.getId())
                    .setExternalEntityDefinitionId(srcEntityDef.getId());
            record1.setId(UUID.randomUUID().toString());
            record1.setSyncariId(syncariId);

            StagedBatchRecord record2 = new StagedBatchRecord()
                    .setStagedBatchId(staged.getId())
                    .setEntityData(entityData2)
                    .setExternalRecordId(entityData2.getId())
                    .setExternalEntityDefinitionId(srcEntityDef.getId());
            record1.setId(UUID.randomUUID().toString());
            record1.setSyncariId(syncariId2);
            currentContext.setGraph(entityGraph);

            CurrentBatch currentBatch = spy(SimulationCurrentBatch.class);
            RecordsBySyncariId recordsBySyncariId1 = new RecordsBySyncariId(syncariId);
            recordsBySyncariId1.addRecord(record1);
            RecordsBySyncariId recordsBySyncariId2 = new RecordsBySyncariId(syncariId2);
            recordsBySyncariId2.addRecord(record2);
            when(currentBatch.recordsBySyncariIdIterator()).thenReturn(List.of(recordsBySyncariId1,recordsBySyncariId2).iterator());
            when(currentBatch.getCurrentBatchId()).thenReturn("batchId");

            AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig().setDefaultWinnerValueSelectionPolicy(WinnerValueSelectionPolicy.LATEST_WITH_VALUE)
                    .setDefaultWinnerOverridePolicy(WinnerOverridePolicy.WHEN_BLANK);
            WinnerSelection revWinner = new WinnerSelection().setWinnerSelectionType("record").
                    setWinnerSelectionValue(RecordLevelWinnerSelection.OLDEST_CREATED.toString());
            advancedDedupeConfig.setSelectWinner(toSelectWinnerMap(revWinner));

            Map<String, Object> dupeFindCri = toFindDuplicates(coreNameAttr.getId());
            advancedDedupeConfig.setFindDupes(dupeFindCri);
            advancedDedupeConfig.setMergeAction(MergeAction.REPORT_ONLY);
            advancedDedupeConfig.setMaximumAllowedDupes("2");
            ((CoreEntityNodeConfig)coreNode.getConfiguration()).setAdvancedDedupeConfig(advancedDedupeConfig);

            com.syncari.core.model.pagination.Page<EntityData> page1 = new com.syncari.core.model.pagination.Page<EntityData>(new PageInfo("0", "2", false),List.of());
            when(entityRepo.searchWithFallback(any(),any(), any(), anyBoolean(), anyInt())).thenReturn(page1);



            // findDuplicatesInBatch

            currentContext.setCurrentBatch(currentBatch);
            when(entityRepo.save(any(),any())).thenReturn(entityData);
            mockEntityRepoService.dfiRuleAssignmentService = mock(DfiRuleAssignmentService.class);
            when(mockEntityRepoService.dfiRuleAssignmentService.getRulesForEntityByField(any())).thenReturn(Map.of());
            executeFieldPipeline.execute(viperContext,currentContext);

            ArgumentCaptor<EntityData> captor = ArgumentCaptor.forClass(EntityData.class);
            verify(entityRepo).save(any(), captor.capture());
            EntityData winner = captor.getValue();
            assertNotNull(winner);
            assertNull(winner.getValue("Sink Quality"));
        }
    }


    private CurrentBatch getCurrentBatch(EntityData entityData, EntityDefinition srcEntity, EntityDefinition syncariEntity) {
        return getCurrentBatch(entityData,entityData,srcEntity, syncariEntity);
    }
    private CurrentBatch getCurrentBatch(EntityData incomingRecord,EntityData existingRecord, EntityDefinition srcEntity, EntityDefinition syncariEntity){
        String syncariId = new ObjectId().toHexString();
        incomingRecord.setSyncariEntityId(syncariId);
        StagedBatch staged = new StagedBatch(syncariEntity.getApiName()).setConnectorId(srcEntity.getConnectorId())
                .setCurrentBatchId(UUID.randomUUID().toString()).setSourceEntityName(srcEntity.getApiName())
                .setSourceEntityDefinitionId(srcEntity.getId());
        staged.setId(UUID.randomUUID().toString());
        StagedBatchRecord record = new StagedBatchRecord()
                .setStagedBatchId(staged.getId())
                .setEntityData(incomingRecord)
                .setExternalRecordId(incomingRecord.getId())
                .setExternalEntityDefinitionId(srcEntity.getId());
        record.setId(UUID.randomUUID().toString());
        record.setSyncariId(syncariId);
        SimulationCurrentBatch currentBatch = new SimulationCurrentBatch();
        currentBatch.setCurrentBatchId(UUID.randomUUID().toString());
        currentBatch.setBatchRecords(List.of(record));
        currentBatch.setExistingRecords(List.of(existingRecord));
        currentBatch.setEntityBatch(srcEntity, staged);
        currentBatch.setSyncariEntityName(syncariEntity.getApiName());
        return currentBatch;
    }

    public Map<String, Object> toSelectWinnerMap(WinnerSelection... winnerSelections){
        List<Map<String, Object>> winnerSelectionMaps = Arrays.asList(winnerSelections).stream().map(w ->
                Map.of("repeatId", ObjectId.get().toHexString(),
                        "winnerSelectionType", Map.of("name", "winnerSelectionType", "value", w.getWinnerSelectionType()),
                        "winnerSelectionValue", Map.of("name", "winnerSelectionValue", "value", w.getWinnerSelectionValue())
                )
        ).collect(Collectors.toList());
        return Map.of("configId",ObjectId.get().toHexString(),"name","selectWinnerValue","compositeValues",winnerSelectionMaps);
    }

    private static Map<String, Object> toSelectWinnerMap(String... nameFieldPairs) {
        List<Map<String, Object>> predicateMaps =new ArrayList<>();
        for(int i=0;i<nameFieldPairs.length-1;i+=2) {

            Map<String, Object> predicateMap = new HashMap<>();
            Map<String, Object> predicate = new HashMap<>();

            predicate.put("operator", "AND");
            predicate.put("predicates", List.of(Map.of("operator", nameFieldPairs[i], "left", Map.of("type","variable","value", nameFieldPairs[i+1]))));
            predicateMap.put("winnerSelectionPredicate",Map.of("value",predicate,"name","winnerSelectionPredicate"));
            predicateMaps.add(predicateMap);
        }
        return Map.of("configId", ObjectId.get().toHexString(), "name", "selectWinner", "compositeValues", predicateMaps);
    }

    private Map<String, Object> toFindDuplicates() {
        var predicates =  List.of(Map.of("left",
                Map.of("datatype", "picklist",
                        "label", "Name",
                        "picklistGroup", "Fields",
                        "type", "variable",
                        "value", "Account Name1"),
                "name", "findDupesPredicate",
                "operator", "eq",
                "predicateId", UUID.randomUUID(),
                "right", Map.of("type", "literal",
                        "value", "Account Name1")
        ));
        var duplicates = Map.of("configId", UUID.randomUUID(),
                "name", "findDupes",
                "compositeValues", List.of(Map.of("findDupesPredicate", Map.of("name", "findDupesPredicate",
                        "value", Map.of("groupPredicateId", UUID.randomUUID(),
                                "operator", "AND",
                                "predicates", predicates)))));
        return duplicates;
    }

    private Map<String, Object> toFindDuplicates(String fieldId) {
        var predicates =  List.of(Map.of("left",
                Map.of("datatype", "picklist",
                        "label", "Name",
                        "picklistGroup", "Fields",
                        "type", "variable",
                        "value", fieldId),
                "name", "findDupesPredicate",
                "operator", "eq",
                "predicateId", UUID.randomUUID(),
                "right", Map.of("type", "literal",
                        "value", "Account Name1")
        ));
        var duplicates = Map.of("configId", UUID.randomUUID(),
                "name", "findDupes",
                "compositeValues", List.of(Map.of("findDupesPredicate", Map.of("name", "findDupesPredicate",
                        "value", Map.of("groupPredicateId", UUID.randomUUID(),
                                "operator", "AND",
                                "predicates", predicates)))));
        return duplicates;
    }

    private static CurrentBatch createCurrentBatch() {
        return new CurrentBatch(null).setCurrentBatchId(UUID.randomUUID().toString());
    }


}
