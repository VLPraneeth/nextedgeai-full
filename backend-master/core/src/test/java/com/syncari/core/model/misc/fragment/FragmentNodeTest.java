package com.syncari.core.model.misc.fragment;

import com.syncari.core.model.AttributeSinkNodeConfig;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.Scope;
import org.junit.Test;

import static org.junit.Assert.*;

public class FragmentNodeTest {

    @Test
    public void validate_UnsupportedNodes(){

        var entitySourceConfig = new EntitySourceNodeConfig();
        FragmentNode node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ENTITY)
                .setConfiguration(entitySourceConfig).setMappingGraphId("graph123");
        try{
            node.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("ENTITY_SOURCE nodes are not supported in Fragments.", e.getMessage());
        }

        var attributeSourceConfig = new AttributeSourceNodeConfig();
        node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ATTRIBUTE)
                .setConfiguration(attributeSourceConfig).setMappingGraphId("graph123");
        try{
            node.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("ATTRIBUTE_SOURCE nodes are not supported in Fragments.", e.getMessage());
        }

        var entitySinkConfig = new EntitySinkNodeConfig();
        node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ENTITY)
                .setConfiguration(entitySinkConfig).setMappingGraphId("graph123");
        try{
            node.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("ENTITY_SINK nodes are not supported in Fragments.", e.getMessage());
        }

        var attributeSinkConfig = new AttributeSinkNodeConfig();
        node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ATTRIBUTE)
                .setConfiguration(attributeSinkConfig).setMappingGraphId("graph123");
        try{
            node.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("ATTRIBUTE_SINK nodes are not supported in Fragments.", e.getMessage());
        }

    }

    @Test
    public void validate(){
        var coreEntityConfig = new CoreEntityNodeConfig();
        FragmentNode node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ENTITY)
                .setConfiguration(coreEntityConfig).setMappingGraphId("graph123");
        node.validate();

        var coreAttrConfig = new CoreAttributeNodeConfig();
        node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ATTRIBUTE)
                .setConfiguration(coreAttrConfig).setMappingGraphId("graph123");
        node.validate();

        var functionNodeConfig = new SimpleFunctionNodeConfig();
        node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ENTITY)
                .setConfiguration(functionNodeConfig).setMappingGraphId("graph123");
        node.validate();

        var actionNodeConfig = new GenericActionConfig();
        node = new FragmentNode();
        node.setName("node").setApiName("node").setScope(Scope.ATTRIBUTE)
                .setConfiguration(actionNodeConfig).setMappingGraphId("graph123");
        node.validate();
    }
}
