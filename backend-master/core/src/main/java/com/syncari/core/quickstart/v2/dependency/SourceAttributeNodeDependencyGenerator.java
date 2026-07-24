package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.misc.sharable.SharableSourceAttributeNodeConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceAttributeNodeDependencyGenerator implements DependencyService {
    @Override
    public void extract(QuickStartContext context) {
        // Dependency list
        // 1: Referenced Attribute

        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        SharableSourceAttributeNodeConfig nodeConfig = node.getTypedConfiguration();
        QSDependency attribDep = DependencyUtil.getAttributeDependency(nodeConfig.getAttributeDefinition());
        qsConfig.addDependency(attribDep);
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode node = context.getCurrentNode();
        SharableSourceAttributeNodeConfig nodeConfig = node.getTypedConfiguration();
        var srcAttribRef = nodeConfig.getAttributeDefinition();
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(srcAttribRef.getId(), QSDependency.Type.Attribute);

        AttributeSourceNodeConfig srcNodeConfig = new AttributeSourceNodeConfig()
                .setAttributeDefinition(resolvedAttrib);

        return new MappingNode()
                .setConfiguration(srcNodeConfig)
                .setApiName(resolvedAttrib.getApiName())
                .setName(resolvedAttrib.getDisplayName())
                .setScope(Scope.ATTRIBUTE);
    }
}
