package com.syncari.core.actions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Type;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;

public class PipelineActionTest extends AbstractSyncariTest {

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    private ActionDefinitionRepo actionDefinitionRepo;

    @Override
    public void tearDown() {
        super.tearDown();
        resetRepos(actionDefinitionRepo, entityProxyRepo);
    }

    @Test
    public void testConfigVariables() {

        var syncariConnector =connectorService.getSyncariConnector();

        List<FunctionConfiguration> variables = List.of(
                new FunctionConfiguration().setName("name").setDatatype(StringType.VALUE).setLabel("Name").setRequired(false),
                new FunctionConfiguration().setName("address").setDatatype(StringType.VALUE).setLabel("Address").setRequired(false));

        // create a custom action
        var httpActionDefinition = new CustomActionDefinition().setApiName("Http Action").setName("Http Action")
                .setType(Type.CUSTOM).setProperties(new HttpActionProperties()).setConfiguration(variables);
        var savedAction = actionDefinitionRepo.save(httpActionDefinition);

        EntityDefinition coreAccount = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account",
                GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("name", "John Doe");
        configMap.put("email", "john.doe@syncari.com");

        MappingGraph entityGraph = newGraph(coreAccount, null, actionDefinitionRepo)
                .src(srcEntity, "Source Account")
                .action(savedAction.getId(), "Http Action", "Custom Action", configMap)
                .dest(srcEntity, "Dest Account")
                .connect("Source Account", "Custom Action")
                .connect("Custom Action", "Dest Account").getGraph();

        configMap = ((GenericActionConfig)entityGraph.findNodeByName("Custom Action").get().getTypedConfiguration()).getConfigMap();

        assertEquals("John Doe", configMap.get("name"));
        assertEquals("john.doe@syncari.com", configMap.get("email"));
    }
}
