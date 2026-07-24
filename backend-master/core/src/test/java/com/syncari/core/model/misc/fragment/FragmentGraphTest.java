package com.syncari.core.model.misc.fragment;

import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.Scope;
import org.junit.Test;

import static org.junit.Assert.*;

public class FragmentGraphTest {

    @Test
    public void validate_DuplicateEdge(){
        // Case: node1 --> node2
        //       node1 --> node2
        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = new FunctionDefinition();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        FragmentNode node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge2 = new FragmentEdge().setTemplateId("edge2").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);
        graph.getEdges().add(fragmentEdge2);

        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("More than 1 edge is connecting two fragment nodes funcNode1 and funcNode2", e.getMessage());
        }
    }

    @Test
    public void validateCyclesinFragmentGraph_NoSources(){
        // Case: node1 --> node2
        //       node2 <-- node1
        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = new FunctionDefinition();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        FragmentNode node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge2 = new FragmentEdge().setTemplateId("edge2").setDestinationStage(node1)
                .setSourceStage(node2).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);
        graph.getEdges().add(fragmentEdge2);

        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("There are cyclic references in fragment graph.", e.getMessage());
        }
    }

    @Test
    public void validateCyclesinFragmentGraph(){
        /* Case:
            node1 --> node2 --> node3
                      node2 <-- node3
         */
        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = new FunctionDefinition();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        FragmentNode node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        FragmentNode node3 = new FragmentNode();
        node3.setName("funcNode3").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node3.setTemplateId("node3").setId("node3");
        graph.getNodes().add(node3);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge2 = new FragmentEdge().setTemplateId("edge2").setDestinationStage(node3)
                .setSourceStage(node2).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge3 = new FragmentEdge().setTemplateId("edge3").setDestinationStage(node2)
                .setSourceStage(node3).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);
        graph.getEdges().add(fragmentEdge2);
        graph.getEdges().add(fragmentEdge3);

        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("There are cyclic references in fragment graph.", e.getMessage());
        }
    }

    @Test
    public void validateGraph_LinearConnection(){
        /* Case:
            node1 --> node2 --> node3
         */
        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = new FunctionDefinition();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        FragmentNode node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        FragmentNode node3 = new FragmentNode();
        node3.setName("funcNode3").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node3.setTemplateId("node3").setId("node3");
        graph.getNodes().add(node3);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge2 = new FragmentEdge().setTemplateId("edge2").setDestinationStage(node3)
                .setSourceStage(node2).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);
        graph.getEdges().add(fragmentEdge2);

        graph.validate();

    }

    @Test
    public void validateGraph_OnlyCoreEntityNode(){
        FragmentGraph graph = new FragmentGraph();
        var funcConfig = new CoreEntityNodeConfig();
        FragmentNode node1 = new FragmentNode();
        node1.setName("coreNode").setApiName("core").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Fragment with only core node is not allowed", e.getMessage());
        }
    }

    @Test
    public void validateGraph_OnlyCoreAttributeNode(){
        FragmentGraph graph = new FragmentGraph();
        var funcConfig = new CoreAttributeNodeConfig();
        FragmentNode node1 = new FragmentNode();
        node1.setName("coreNode").setApiName("core").setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Fragment with only core node is not allowed", e.getMessage());
        }
    }

    @Test
    public void validateGraph_NotConnected(){
        /* Case:
            node1 --> node2
            node3 --> node4
         */
        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = new FunctionDefinition();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        FragmentNode node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        FragmentNode node3 = new FragmentNode();
        node3.setName("funcNode3").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node3.setTemplateId("node3").setId("node3");
        graph.getNodes().add(node3);

        FragmentNode node4 = new FragmentNode();
        node4.setName("funcNode4").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node4.setTemplateId("node4").setId("node4");
        graph.getNodes().add(node4);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge2 = new FragmentEdge().setTemplateId("edge2").setDestinationStage(node4)
                .setSourceStage(node3).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);
        graph.getEdges().add(fragmentEdge2);

        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Invalid fragment. All nodes in the fragment graph are not connected.", e.getMessage());
        }
    }

    @Test
    public void validateGraph_DanglingNode(){
        /* Case:
            node1 --> node2
            node3
         */
        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = new FunctionDefinition();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        FragmentNode node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        FragmentNode node3 = new FragmentNode();
        node3.setName("funcNode3").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node3.setTemplateId("node3").setId("node3");
        graph.getNodes().add(node3);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);

        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Fragment has dangling node funcNode3", e.getMessage());
        }
    }

    @Test
    public void validateGraph_NoNodes() {
        /* Case:
            node1 --> node2
            node3
         */
        FragmentGraph graph = new FragmentGraph();
        try {
            graph.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Fragment should contain at least one node", e.getMessage());
        }
    }

}
