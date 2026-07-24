package com.syncari.core.model;

import com.syncari.core.model.Fragment;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.fragment.FragmentGraph;
import com.syncari.core.model.misc.fragment.FragmentNode;
import com.syncari.core.model.util.Scope;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FragmentTest {

    @Test
    public void validate_UnsupportedScope(){

        Fragment fragment = new Fragment().setName("fragment").setOwnerUserId("user123")
                .setScope(Scope.SCHEMA).setFragmentGraph(new FragmentGraph());
        try {
            fragment.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Schema Fragments are not supported", e.getMessage());
        }
    }

    @Test
    public void validate_SharedFragment(){

        Fragment fragment = new Fragment().setName("fragment").setOwnerUserId("user123")
                .setScope(Scope.ENTITY).setFragmentGraph(new FragmentGraph()).setShared(true);
        try {
            fragment.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Shared Fragments should have reference to shared item record", e.getMessage());
        }
    }

    @Test
    public void validate_NullGraph(){

        Fragment fragment = new Fragment().setName("fragment").setOwnerUserId("user123")
                .setScope(Scope.ENTITY);
        try {
            fragment.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Fragment should contain valid graph", e.getMessage());
        }
    }

    @Test
    public void validate(){
        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = new FunctionDefinition();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        Fragment fragment = new Fragment().setName("fragment").setOwnerUserId("user123")
                .setScope(Scope.ENTITY).setFragmentGraph(graph);
        fragment.validate();

        fragment.setShared(true).setSharedItemId("item123");
        fragment.validate();
    }
}
