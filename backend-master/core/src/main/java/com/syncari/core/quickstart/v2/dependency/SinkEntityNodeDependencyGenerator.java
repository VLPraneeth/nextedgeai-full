package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.misc.sharable.SharableSinkEntityNodeConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.ConnectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SinkEntityNodeDependencyGenerator implements DependencyService {

    @Autowired
    ConnectorService connectorService;

    @Override
    public void extract(QuickStartContext context) {
        // Dependency list
        // 1: Referenced Entity

        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        SharableSinkEntityNodeConfig nodeConfig = node.getTypedConfiguration();
        EntityDefinition entityDef = nodeConfig.getEntityDefinition();
        QSDependency entityDep = DependencyUtil.getEntityDependency(entityDef);
        qsConfig.addDependency(entityDep);
        context.getConnector(entityDef.getConnectorId()).ifPresent(c -> qsConfig.addDependency(DependencyUtil.getConnectorDependency(c)));
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode node = context.getCurrentNode();
        SharableSinkEntityNodeConfig nodeConfig = node.getTypedConfiguration();
        var srcEntityRef = nodeConfig.getEntityDefinition();
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        EntityDefinition resolvedEntityRef = (EntityDefinition) qsConfig.getResolvedValueByType(srcEntityRef.getId(), QSDependency.Type.Entity);

        EntitySinkNodeConfig sinkNodeConfig = new EntitySinkNodeConfig()
                .setEntityDefinition(resolvedEntityRef);

        return new MappingNode()
                .setConfiguration(sinkNodeConfig)
                .setApiName(resolvedEntityRef.getApiName())
                .setName(resolvedEntityRef.getDisplayName())
                .setScope(Scope.ENTITY);
    }
}
