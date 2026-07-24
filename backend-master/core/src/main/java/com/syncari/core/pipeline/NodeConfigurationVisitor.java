package com.syncari.core.pipeline;

import com.syncari.core.model.*;
import com.syncari.core.model.misc.fragment.CoreAttributeFragmentNodeConfig;
import com.syncari.core.model.misc.fragment.CoreEntityFragmentNodeConfig;

public interface NodeConfigurationVisitor {

        void visit(SimpleFunctionNodeConfig simpleFunctionNodeConfig, MappingNode node);

        void visit(GenericActionConfig actionConfig, MappingNode node);

        void visit(AttributeSinkNodeConfig attributeSinkNodeConfig, MappingNode node);

        void visit(AttributeSourceNodeConfig attributeSourceNodeConfig, MappingNode node);

        void visit(EntitySinkNodeConfig entitySinkNodeConfig, MappingNode node);

        void visit(EntitySourceNodeConfig entitySourceNodeConfig, MappingNode node);

        void visit(CoreAttributeNodeConfig coreAttributeNodeConfig, MappingNode node);

        void visit(CoreEntityNodeConfig coreEntityNodeConfig, MappingNode node);

        void visit(SendEmailActionConfig sendEmailActionConfig, MappingNode node);


        void visit(SimpleFunctionNodeConfig simpleFunctionNodeConfig);

        void visit(AttributeSinkNodeConfig attributeSinkNodeConfig);

        void visit(AttributeSourceNodeConfig attributeSourceNodeConfig);

        void visit(EntitySinkNodeConfig entitySinkNodeConfig);

        void visit(EntitySourceNodeConfig entitySourceNodeConfig);

        void visit(CoreAttributeNodeConfig coreAttributeNodeConfig);

        void visit(CoreEntityNodeConfig coreEntityNodeConfig);

        void visit(SendEmailActionConfig sendEmailActionConfig);

        void visit(GenericActionConfig actionConfig);

        void visit(CoreAttributeFragmentNodeConfig coreAttributeFragmentNodeConfig);

        void visit(CoreEntityFragmentNodeConfig coreAttributeFragmentNodeConfig);



}
