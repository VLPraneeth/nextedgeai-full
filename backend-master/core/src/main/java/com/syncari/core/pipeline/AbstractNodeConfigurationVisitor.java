package com.syncari.core.pipeline;

import com.syncari.core.model.*;
import com.syncari.core.model.misc.fragment.CoreAttributeFragmentNodeConfig;
import com.syncari.core.model.misc.fragment.CoreEntityFragmentNodeConfig;

public abstract class AbstractNodeConfigurationVisitor implements NodeConfigurationVisitor {

        protected void defaultVisit(NodeConfiguration nodeConfiguration, MappingNode node){

        }
        protected void defaultVisit(NodeConfiguration nodeConfiguration){

        }

        public void visit(SimpleFunctionNodeConfig simpleFunctionNodeConfig, MappingNode node){
                defaultVisit(simpleFunctionNodeConfig,node);
        }

        public void visit(AttributeSinkNodeConfig attributeSinkNodeConfig, MappingNode node) {
                defaultVisit(attributeSinkNodeConfig,node);
        }

        public void visit(AttributeSourceNodeConfig attributeSourceNodeConfig, MappingNode node) {
                defaultVisit(attributeSourceNodeConfig,node);
        }

        public void visit(EntitySinkNodeConfig entitySinkNodeConfig, MappingNode node) {
                defaultVisit(entitySinkNodeConfig,node);
        }

        public void visit(EntitySourceNodeConfig entitySourceNodeConfig, MappingNode node) {
                defaultVisit(entitySourceNodeConfig,node);
        }

        public void visit(CoreAttributeNodeConfig coreAttributeNodeConfig, MappingNode node) {
                defaultVisit(coreAttributeNodeConfig,node);
        }

        public void visit(CoreEntityNodeConfig coreEntityNodeConfig, MappingNode node) {
                defaultVisit(coreEntityNodeConfig,node);
        }

        public void visit(SendEmailActionConfig sendEmailActionConfig, MappingNode node) {
                defaultVisit(sendEmailActionConfig,node);
        }

        public void visit(GenericActionConfig actionConfig, MappingNode node) {
                defaultVisit(actionConfig,node);
        }

        public void visit(SimpleFunctionNodeConfig simpleFunctionNodeConfig){
                defaultVisit(simpleFunctionNodeConfig);
        }

        public void visit(AttributeSinkNodeConfig attributeSinkNodeConfig) {
                defaultVisit(attributeSinkNodeConfig);
        }

        public void visit(AttributeSourceNodeConfig attributeSourceNodeConfig) {
                defaultVisit(attributeSourceNodeConfig);
        }

        public void visit(EntitySinkNodeConfig entitySinkNodeConfig) {
                defaultVisit(entitySinkNodeConfig);
        }

        public void visit(EntitySourceNodeConfig entitySourceNodeConfig) {
                defaultVisit(entitySourceNodeConfig);
        }

        public void visit(CoreAttributeNodeConfig coreAttributeNodeConfig) {
                defaultVisit(coreAttributeNodeConfig);
        }

        public void visit(CoreEntityNodeConfig coreEntityNodeConfig) {
                defaultVisit(coreEntityNodeConfig);
        }

        public void visit(SendEmailActionConfig sendEmailActionConfig) {
                defaultVisit(sendEmailActionConfig);
        }

        public void visit(GenericActionConfig actionConfig) {
                defaultVisit(actionConfig);
        }

        public void visit(CoreAttributeFragmentNodeConfig coreAttributeFragmentNodeConfig) {
                defaultVisit(coreAttributeFragmentNodeConfig);
        }

        public void visit(CoreEntityFragmentNodeConfig coreEntityFragmentNodeConfig) {
                defaultVisit(coreEntityFragmentNodeConfig);
        }
}
