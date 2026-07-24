package com.syncari.core.actions;

import com.syncari.connector.data.AuthType;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.actions.http.AuthenticationInfo;
import com.syncari.core.actions.http.HTTPAction;
import com.syncari.core.actions.http.HttpActionProperties;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Type;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.service.ActionService;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class HttpActionsValidationsTest extends AbstractSyncariTest {

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    ConnectorMetadataService connMetaService;

    @Autowired
    DataTransformer dataTransformer;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ActionService actionService;

    @Autowired
    EndSystemConfig config;

    @Autowired
    private HTTPAction httpAction;

    private static final String ACTION_ENDPOINT = "http://www.example.com/rest/v1/action/addLead";
    private static final HttpMethod HTTP_METHOD = HttpMethod.POST;
    private static Connector apiKeyCredential;
    private static Connector oauthCredential;


    @Override
    public void setUp() {
        super.setUp();

        apiKeyCredential = new Connector("New Generic API Key", connectorService.describe("genericApiKey").getId(), "");
        apiKeyCredential.getAuthConfig().setToken(config.getHttpActionAPIKey());
        apiKeyCredential.setAuthType(AuthType.ApiKey);
        apiKeyCredential = connectorService.save(apiKeyCredential);
        connectorService.authenticated(apiKeyCredential.getId());

        oauthCredential = new Connector("New Generic OAuth", connectorService.describe("genericSimpleOAuth").getId(), "");
        oauthCredential.getAuthConfig().setClientId(config.getHttpTestSimpleOAuthClientId());
        oauthCredential.getAuthConfig().setClientSecret(config.getHttpTestSimpleOAuthClientSecret());
        oauthCredential.setAuthType(AuthType.SimpleOAuth);
        oauthCredential = connectorService.save(oauthCredential);
        connectorService.authenticated(oauthCredential.getId());
    }

    @Test
    public void validate(){

        // setup action
        HttpActionProperties actionProperties = new HttpActionProperties().setEndPoint(ACTION_ENDPOINT).
                setMethod(HTTP_METHOD).setAuthenticationInfo(new AuthenticationInfo().setCredentialId(apiKeyCredential.getId()));

        List<FunctionConfiguration> variables = List.of(
                new FunctionConfiguration().setName("arg1").setDatatype(StringType.VALUE).setLabel("Argument 1").setRequired(true),
                new FunctionConfiguration().setName("arg2").setDatatype(StringType.VALUE).setLabel("Argument 2").setRequired(true));

        var httpActionDefinition = new CustomActionDefinition().setApiName("Test Http Action").setName("Test Http Action")
                .setType(Type.CUSTOM).setProperties(actionProperties).setConfiguration(variables);
        httpActionDefinition = actionService.save(httpActionDefinition);

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account",
                createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        // Case1: No required property
        Map<String, Object> configMap = new HashMap<>();
        MappingGraph entityGraph = newGraph(coreEntity, null, actionDefinitionRepo)
                .src(srcEntity)
                .action(httpActionDefinition.getId(), httpActionDefinition.getName(), "Execute Test Http Action", configMap)
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Execute Test Http Action").getGraph();

        MappingNode actionNode = entityGraph.findNodeByName("Execute Test Http Action").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(actionNode);
        try{
            httpAction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Argument 1 from Execute Test Http Action in graph coreAccount", e.getMessage());
        }

        configMap.clear();
        configMap.put("arg1", "test");
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);
        try{
            httpAction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Argument 2 from Execute Test Http Action in graph coreAccount", e.getMessage());
        }

        configMap.clear();
        configMap.put("arg1", "test1");
        configMap.put("arg2", "test2");
        actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);
        httpAction.validate(context);
    }
}
