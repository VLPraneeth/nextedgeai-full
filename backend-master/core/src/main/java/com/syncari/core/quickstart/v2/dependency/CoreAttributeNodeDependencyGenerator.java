package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.DatAuthorityStrategy;
import com.syncari.core.model.DataAuthority;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableCoreAttributeNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
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
public class CoreAttributeNodeDependencyGenerator implements DependencyService {

    @Autowired
    ConnectorService connectorService;

    @Override
    public void extract(QuickStartContext context) {
        // Dependency list
        // 1: Referenced Attribute
        // 2: Data authority

        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        SharableCoreAttributeNodeConfig nodeConfig = node.getTypedConfiguration();
        QSDependency attribDep = DependencyUtil.getAttributeDependency(nodeConfig.getAttributeDefinition());
        qsConfig.addDependency(attribDep);

        DataAuthority dataAuthority = nodeConfig.getDataAuthority();
        if(DatAuthorityStrategy.SELECTED_CONNECTOR.equals(dataAuthority.getDatAuthorityStrategy())){
            String connectorId = dataAuthority.getDataAuthorityConfiguration().get("connectorId").toString();
            Connector conn = context.getConnector(connectorId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getConnectorDependency(conn));
        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode node = context.getCurrentNode();
        SharableCoreAttributeNodeConfig nodeConfig = node.getTypedConfiguration();
        var srcAttribRef = nodeConfig.getAttributeDefinition();
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        AttributeDefinition destAttribRef = (AttributeDefinition) qsConfig.getResolvedValueByType(srcAttribRef.getId(), QSDependency.Type.Attribute);

        DataAuthority dataAuthority = nodeConfig.getDataAuthority();
        if(DatAuthorityStrategy.SELECTED_CONNECTOR.equals(dataAuthority.getDatAuthorityStrategy())){
            String srcConnectorId = dataAuthority.getDataAuthorityConfiguration().get("connectorId").toString();
            Connector destConnRef = (Connector) qsConfig.getResolvedValueByType(srcConnectorId, QSDependency.Type.Connector);
            if(destConnRef != null) {
                dataAuthority = DataAuthority.selectedConnector(destConnRef.getId());
            }
        } else if(DatAuthorityStrategy.NONE.equals(dataAuthority.getDatAuthorityStrategy())){
            dataAuthority = DataAuthority.none(); // This is done to make sure config is blank when strategy selected as NONE
        }

        CoreAttributeNodeConfig coreNodeConfig = new CoreAttributeNodeConfig()
                .setAttributeDefinition(destAttribRef)
                .setDataAuthority(dataAuthority);

        return new MappingNode()
                .setConfiguration(coreNodeConfig)
                .setApiName(destAttribRef.getApiName())
                .setName(destAttribRef.getDisplayName())
                .setScope(Scope.ATTRIBUTE);
    }
}
