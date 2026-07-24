package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.MappingGraphService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MappingGraphRepoTest extends AbstractSyncariTest {

    @Autowired
    private MappingGraphRepo mappingGraphRepo;
    @Autowired
    private MappingGraphService mappingGraphService;
    @Autowired
    private MappingNodeRepo nodeRepo;
    @Autowired
    private EdgeRepo edgeRepo;

    @Autowired
    private FunctionService functionService;

    @Override
    public void tearDown() {
        resetRepos(mappingGraphRepo,mappingGraphRepo,edgeRepo);
    }

    @Test
    public void querySingleNodeEntityMap() {

        FunctionDefinition mask = functionService.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
        FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));
        MappingGraph mappingGraph = mappingGraphRepo.save(new MappingGraph().setName("Account Map")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId"));

        nodeRepo.save(new MappingNode()
                .setName("Save")
                .setApiName("Save")
                .setScope(Scope.ENTITY)
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
                .setMappingGraphId(mappingGraph.getId()));

        List<MappingNode> retrievedStage = mappingGraphService.findNodesByGraphId(mappingGraph.getId());
        assertFalse(retrievedStage.isEmpty());
        SimpleFunctionNodeConfig configuration = (SimpleFunctionNodeConfig) retrievedStage.get(0).getConfiguration();
        assertEquals("mask", configuration.getFunctionCall().getFunctionDefinition().getName());


    }
    @Test
    public void filterFunctionPersistence() {

        FunctionDefinition filter = functionService.findByNameAndScope("filter", Scope.ATTRIBUTE).get();
        FunctionDefinition mask = functionService.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
        FunctionCall maskCall = mask.withParams(ParameterValue.string("a.b", "sfdc"));

        FunctionCall filterNonSFDC = filter.withParams(ParameterValue.string("output_something","input"));
        var predicate = Expression.gt(Expression.renderedLit("output_something.account.revenue"),Expression.lit(500));
        filterNonSFDC.setConfig(Map.of("predicate",predicate));

        MappingGraph mappingGraph = mappingGraphRepo.save(new MappingGraph().setName("Account Map")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId"));

        var stage1 = nodeRepo.save(new MappingNode()
                .setName("Save")
                .setApiName("Save")
                .setScope(Scope.ENTITY)
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(filterNonSFDC))
                .setMappingGraphId(mappingGraph.getId()));

        var stage2 = nodeRepo.save(new MappingNode()
                .setName("Zzzz")
                .setApiName("Zzzz")
                .setScope(Scope.ENTITY)
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(maskCall))
                .setMappingGraphId(mappingGraph.getId()));

        var edge1 = edgeRepo.save(new Edge()
                .setGraphId(mappingGraph.getId())
                .setInput(InputPort.any())
                .setOutput(OutputPort.any())
                .setDestinationStage(stage2)
                .setSourceStage(stage1));


        List<MappingNode> nodes = mappingGraphService.findNodesByGraphId(mappingGraph.getId());;
        List<Edge> edges= mappingGraphService.findEdgesForGraphId(mappingGraph.getId(), nodes);
        assertEquals(List.of(edge1),edges);
        assertEquals(stage1, edges.get(0).getSourceStage());
        assertEquals(stage2, edges.get(0).getDestinationStage());

        assertEquals(List.of(stage1,stage2),nodes);

    }

    @Test
    public void queryMultiNodeEntityMap() {

        FunctionDefinition mask = functionService.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
        FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));
        MappingGraph mappingGraph = mappingGraphRepo.save(new MappingGraph().setName("Account Map")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId"));

        var stage1 = nodeRepo.save(new MappingNode()
                .setName("Save")
                .setApiName("Save")
                .setScope(Scope.ENTITY)
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
                .setMappingGraphId(mappingGraph.getId()));

        var stage2 = nodeRepo.save(new MappingNode()
                .setName("Zzzz")
                .setApiName("Zzzz")
                .setScope(Scope.ENTITY)
                .setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
                .setMappingGraphId(mappingGraph.getId()));

        var edge1 = edgeRepo.save(new Edge()
                .setGraphId(mappingGraph.getId())
                .setInput(InputPort.any())
                .setOutput(OutputPort.any())
                .setDestinationStage(stage2)
                .setSourceStage(stage1));

        List<MappingNode> nodes = mappingGraphService.findNodesByGraphId(mappingGraph.getId());;
        List<Edge> edges= mappingGraphService.findEdgesForGraphId(mappingGraph.getId(), nodes);
        assertEquals(List.of(edge1),edges);
        assertEquals(stage1, edges.get(0).getSourceStage());
        assertEquals(stage2, edges.get(0).getDestinationStage());
    }

    @Test
    public void findRealtimePipelines() {
        MappingGraph standard = mappingGraphRepo.save(new MappingGraph().setName("Account Map")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId"));
        MappingGraph realtime1 = mappingGraphRepo.save(new MappingGraph().setName("Account Map1")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId1")
                .setSettings(new PipelineSettings().setRealtimePipeline(true))
        );
        MappingGraph realtime2 = new MappingGraph().setName("Account Map2")
                .setScope(Scope.ENTITY)
                .setTargetId("entityId2")
                .setSettings(new PipelineSettings().setRealtimePipeline(true).setRealtimeEndpointSuffix("suffix1"));
        realtime2.setDraftStatus(DraftStatus.APPROVED);
        realtime2 = mappingGraphRepo.save(realtime2);

        final List<MappingGraph> realtimePipelinesByIds = mappingGraphService.findRealtimePipelinesByIds(Set.of(standard.getId(), realtime1.getId(), realtime2.getId()));
        assertEquals(2, realtimePipelinesByIds.size());
        assertEquals(Set.of(realtime1.getId(), realtime2.getId()), realtimePipelinesByIds.stream()
                .map(r -> r.getId()).collect(Collectors.toSet()));

        final Optional<MappingGraph> activeRTPipeline = mappingGraphRepo.findActiveGraphByRealTimeEndPoint("suffix1");
        assertTrue(activeRTPipeline.isPresent());
        assertEquals(realtime2.getId(), activeRTPipeline.get().getId());

    }


}
