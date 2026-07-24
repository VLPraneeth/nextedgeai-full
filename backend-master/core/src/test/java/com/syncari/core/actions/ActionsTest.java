package com.syncari.core.actions;

import com.google.common.collect.Maps;
import com.sforce.soap.partner.IError;
import com.sforce.soap.partner.SaveResult;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.service.MarketoService;
import com.syncari.connector.service.SalesforceService;
import com.syncari.connector.zendesk.ZendeskService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.TestConfig;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.UpdateRecordsAction;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.messagingservice.SlackService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.*;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class ActionsTest extends AbstractSyncariTest {
    @Autowired
    Actions actions;
    @Autowired
    Actions actionservice;
    @Autowired
    EndSystemConfig config;
    @Autowired
    ConnectorService connectorService;
    @Mock
    MappingGraphService mappingGraphService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    SchemaService originalSchemaService;
    @Autowired
    MappingGraphService realMappingGraphService;
    @Autowired
    IdMappingRepo idMappingRepo;
    @Autowired
    RequeueRequestRepo requeueRequestRepo;
    @Autowired
    DataTransformer transformer;
    private static Connector connector;
    private static ConnectorMetadata metadata;
    @Autowired
    UpdateRecordsAction updateRecordsAction;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @After
    public void tearDown() {
        schemaService.setMappingGraphService(realMappingGraphService);
        resetRepos(idMappingRepo,requeueRequestRepo);
        super.tearDown();
    }

    @Before
    public void setUp() {
        super.setUp();
        connectorService.publisher = publisher;
        if(connector == null) {
            metadata = connectorService.describe("salesforce");
            connector = new Connector("sfdc1", metadata.getId(), config.getSalesforceUrl(),
                    config.getUser(), config.getPassword());
            connector.getAuthConfig().setToken(config.getToken());
            connector = connectorService.save(connector);
            connectorService.authenticated(connector.getId());
            connectorService.activate(connector.getId(), false, "actionstest");
        }
        when(mappingGraphService.initializeEntityGraph(any(), any())).thenReturn(null);
        schemaService.setMappingGraphService(mappingGraphService);
        connectorService.setSchemaService(schemaService);
    }

    @Test
    public void createTask() throws Exception {
        actions.schemaService = originalSchemaService;
        Connector connector = new Connector("sfdc2", metadata.getId(), config.getSalesforceUrl(),
                config.getUser(), config.getPassword());
        connector.getAuthConfig().setToken(config.getToken());
        connector = connectorService.save(connector);
        connectorService.authenticated(connector.getId());
        connectorService.activate(connector.getId(), false, "actionstest1");
        EntityDefinition task = originalSchemaService.getEntity(connector.getId(), "Task");
        EntityData input = new EntityData("Task");
        input.setConnectorId(connector.getId());
        Map<String, Object> params = new HashMap<String, Object>();
        String syncariId = ObjectId.get().toHexString();
        idMappingRepo.save(new IdMapping().setEntityName("user").setSyncariId(syncariId)
                .setMappings(List.of(IdMapping.mapping(connector.getId(), "0056g000001Me74", task.getId())))
        );
        params.put("OwnerId", syncariId);
        params.put("Priority", "High");
        params.put("Status", "In Progress");
        params.put("Subject", "Something");
        params.put("ActivityDate", "2022-01-01");
        params.put("synapseId", connector.getId());
        GraphContext context = new GraphContext();
        actions.createSalesforceTask(g(params), context);
    }

    private static GenericActionConfig g(Map<String, Object> params) {
        return new GenericActionConfig().setConfigMap(params);
    }

    @Test
    public void addToSfdcCampaign() throws Exception {
        actions.schemaService = originalSchemaService;
        Connector connector = new Connector("sfdc3", metadata.getId(), config.getSalesforceUrl(),
                config.getUser(), config.getPassword());
        connector.getAuthConfig().setToken(config.getToken());
        connector = connectorService.save(connector);
        connectorService.authenticated(connector.getId());
        connectorService.activate(connector.getId(), false, "actionstest2");
        EntityDefinition leadSchema = originalSchemaService.getEntity(connector.getId(), "Lead");
        MappingNode currentNode = new MappingNode();
        currentNode.setId(ObjectId.get().toHexString());
        GraphContext context = new GraphContext().setCurrentNode(currentNode)
                .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
        String syncariId = ObjectId.get().toHexString();
        idMappingRepo.save(new IdMapping().setEntityName("lead").setSyncariId(syncariId)
                .setMappings(List.of(IdMapping.mapping(connector.getId(), "00Qg000000F4l52EAB", leadSchema.getId())))
        );
        EntityData lead = new EntityData("lead");
        lead.setId(syncariId);
        context.put("record", lead);
        Map<String, Object> param1 = Map.of("campaignId", "701g0000001YofmAAC", "synapseId", connector.getId(), "status", "Enquired");
        Map<String, Object> param2 = Map.of("campaignId", "701g0000001YofhAAC", "synapseId", connector.getId(), "status", "Responded");
        actions.addToSfdcCampaign(g(param1), context);
        actions.addToSfdcCampaign(g(param2), context);
    }
    
    @Test
    public void addToSfdcCampaignError() throws Exception {
    	MappingNode currentNode = new MappingNode();
    	currentNode.setId(ObjectId.get().toHexString());
    	GraphContext context = new GraphContext().setCurrentNode(currentNode)
    			.setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
    	String syncariId = ObjectId.get().toHexString();
    	EntityData lead = new EntityData("unknown");
    	lead.setId(syncariId);
    	context.put("record", lead);
    	context.getBatchActionContext().enableRunActions();
    	var sfdcService = actions.salesforceService;

        SalesforceService mockSalesforceService = mock(SalesforceService.class);
        SaveResult[] results = new SaveResult[1];
        SaveResult saveResult = new SaveResult();
        saveResult.setSuccess(false);
        IError[] e = new IError[1];
        saveResult.setErrors(e);
        results[0] = new SaveResult();
        doReturn(results).when(mockSalesforceService).addToCampaign(any(), any(), any());
    	actions.salesforceService = mockSalesforceService;
        Connector c = getSalesforceConnector();
        c.setStatus(ConnectorStatus.ACTIVE);
        c.setId(null);
        c.setMetadata(metadata);
        c.setMetadataId(metadata.getId());
        c = connectorService.save(c);
    	Map<String, Object> param1 = Map.of("campaignId", "701g0000001YofmAAC", "synapseId", c.getId(), "status", "Enquired");
    	try {
            actions.addToSfdcCampaign(g(param1), context);
		} finally {
			actions.salesforceService = sfdcService;
		}
    }

    @Test
    public void createZendeskTicket() throws Exception {
        Connector zendeskConnector = new Connector("zendeskTest", connectorService.describe(Constants.ZENDESK).getId(), "");
        zendeskConnector = connectorService.save(zendeskConnector);
        var zendeskService = actions.zendeskService;
        var oldSchemaService = actions.schemaService;
        try{
            ZendeskService mockZendeskService = mock(ZendeskService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);

            actions.zendeskService = mockZendeskService;
            actions.schemaService = mockSchemaService;
            doReturn(new EntityDefinition().setApiName("ticket")).when(mockSchemaService).getEntity(any(), any());
            doReturn(new SyncResponse()).when(mockZendeskService).create(any());

            //when().thenReturn(new SyncResponse());

            MappingNode currentNode = new MappingNode();
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("user"));
            String syncariId = ObjectId.get().toHexString();
            EntityData user = new EntityData("user");
            user.addValue("name", "John Doe");

            user.setId(syncariId);
            context.put("record", user);
            Map<String, Object> param1 = Maps.newHashMap(Map.of("synapseId", zendeskConnector.getId(), "subject", "Provision instance for {{record.values.name}}",
                    "zendeskDescription", "Provision a sandbox instance for user {{record.values.name}}", "priority", "normal",
                    "type", "task"));

            actions.createZendeskTicket(g(param1), context);
            ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
            verify(mockZendeskService).create(captor.capture());

            EntityData data = captor.getValue().getData().get(captor.getValue().getConnector().getId()).get(0);
            assertEquals("Provision instance for John Doe", data.getValueAsString("subject"));
            assertEquals("Provision a sandbox instance for user John Doe", data.getValueAsString("description"));
            assertEquals("normal", data.getValueAsString("priority"));
            assertEquals("task", data.getValueAsString("type"));
        } finally {
            actions.zendeskService = zendeskService;
            actions.schemaService = oldSchemaService;
        }
    }

    @Test
    public void sendSlackMessage() throws Exception {
        var orgSlackService = actions.slackService;
        try{
            SlackService mockSlackService = mock(SlackService.class);
            ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> captor3 = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> captor4 = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> captor5 = ArgumentCaptor.forClass(String.class);
            actions.slackService = mockSlackService;
            doNothing().when(mockSlackService).sendMessage(any(), any(), any(), any(), any());

            MappingNode currentNode = new MappingNode();
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
            String syncariId = ObjectId.get().toHexString();
            EntityData lead = new EntityData("lead");
            lead.addValue("name", "unit-tests");
            lead.setId(syncariId);
            context.put("record", lead);
            Map<String, Object> param1 = Map.of("message", "This is a test message {{record.values.name}}", 
                "channel", "{{record.values.name}}", "serviceId", "");
            actions.sendSlackMessage(g(param1), context);
            verify(mockSlackService).sendMessage(captor1.capture(), captor2.capture(), captor3.capture(), captor4.capture(), captor5.capture());
            assertEquals("This is a test message unit-tests", captor3.getValue());
            assertEquals("unit-tests", captor4.getValue());
        } finally {
            actions.slackService = orgSlackService;
        }
    }

    @Test
    public void addToMarketoListInCollectMode() {
        MappingNode currentNode = new MappingNode();
        currentNode.setId(ObjectId.get().toHexString());
        GraphContext context = new GraphContext().setCurrentNode(currentNode)
                .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
        Map<String, Object> param1 = Map.of("listId", "123", "leadId", "4566");
        Map<String, Object> param2 = Map.of("listId", "123", "leadId", "4567");
        Map<String, Object> param3 = Map.of("listId", "123", "leadId", "4568");
        Map<String, Object> param4 = Map.of("listId", "123", "leadId", "4569");
        actions.addToMarketoList(g(param1), context);
        actions.addToMarketoList(g(param2), context);
        actions.addToMarketoList(g(param3), context);
        actions.addToMarketoList(g(param4), context);
        assertEquals(Set.of(currentNode), context.getBatchActionContext().getBatchActionNodes());
        assertEquals(List.of(param1, param2, param3, param4), context.getBatchActionContext().get(currentNode.getId()));
    }
    
    @Test
    public void convertSfdcInCollectMode(){
    	var orgSalesforceService = actions.salesforceService;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;
        try{
            Connector sfdcConnector = getSalesforceConnector();

            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(new EntityDefinition("lead", "Lead")).when(mockSchemaService).getEntity(sfdcConnector.getId(), "Lead");
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());
            doReturn(sfdcConnector).when(mockConnService).get(sfdcConnector.getId());
            SalesforceService mockSalesforceService = mock(SalesforceService.class);

            ConvertResult res = new ConvertResult().setLeadId("lead1").setAccountId("acc1").setContactId("contact1").setSuccess(true);
            ConvertResponse response = new ConvertResponse();
            response.setData(List.of(res));

            ConvertData data = new ConvertData().setLeadId("lead1").setAccountId("acc1").setContactId("contact1").setConvertedStatus("Converted").setOpportunityId(null).setOwnerId("owner1");
            ConvertRequest req = new ConvertRequest().setConnector(transformer.toConnectorInfo(sfdcConnector)).setData(List.of(data));

            doReturn(response).when(mockSalesforceService).convertLead(req);
            actions.salesforceService = mockSalesforceService;
            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;

            EntityData record = new EntityData();
            record.setId("lead1");
            record.setValues(Map.of("Status", "Converted", "LeadId", "lead1", "AccountId", "acc1", "ContactId", "contact1", "OwnerId", "owner1"));

            MappingNode currentNode = new MappingNode();
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead")).set("record", record);

            // collect mode
            var param1 = g(Map.of("synapseId", sfdcConnector.getId(), "ownerId",
                    "{{record.values.OwnerId}}", "leadId", "{{record.values.LeadId}}", "contactId",
                    "{{record.values.ContactId}}", "accountId", "{{record.values.AccountId}}", "convertedStatus",
                    "{{record.values.Status}}", "captureResults", false));
            actions.convertSalesforceLead(param1, context);
            assertNull(context.get(currentNode.getApiName()));
            assertNotNull(context.getBatchActionContext().get(context.getCurrentNode().getId()));
            verify(mockSalesforceService, times(0)).convertLead(any(ConvertRequest.class));
            
            // batch run mode
            context.getBatchActionContext().enableRunActions();
            actions.convertSalesforceLead(param1, context);
            assertNull(context.get(currentNode.getApiName()));
            assertNotNull(context.getBatchActionContext().get(context.getCurrentNode().getId()));
            verify(mockSalesforceService, times(1)).convertLead(any(ConvertRequest.class));

        } finally {
            actions.salesforceService = orgSalesforceService;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }
    
	@Test
	public void sendEmail() throws Exception {
		var emailService = actions.emailService;
		try {
			EmailService mockService = mock(EmailService.class);
			ArgumentCaptor<List> captor1 = ArgumentCaptor.forClass(List.class);
			actions.emailService = mockService;
			doNothing().when(mockService).sendHtml(any(), any(), any());

			MappingNode currentNode = new MappingNode();
			currentNode.setId(ObjectId.get().toHexString());
			GraphContext context = new GraphContext().setCurrentNode(currentNode)
					.setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
			context.put("record", new EntityData("lead").addValue("email", "test@test.com").setId(ObjectId.get().toHexString()));
			actions.sendEmail(
                    g(Map.of("recipients", List.of("{{record.values.email}}"), "subject", "test", "body", "test")),
                    context);
			verify(mockService).sendHtml(captor1.capture(), ArgumentCaptor.forClass(String.class).capture(),
					ArgumentCaptor.forClass(String.class).capture());
			assertEquals("test@test.com", captor1.getValue().get(0));
		} finally {
			actions.emailService = emailService;
		}
	}

    @Test
    public void addToMarketoProgramInCollectMode(){
        var orgMarketoService = actions.marketoService;
        var orgConnService = actions.connectorService;
        try{
            Connector mktoConnector = getMktoConnector();
            ConnectorService mockConnService = mock(ConnectorService.class);
            doReturn(Optional.of(mktoConnector)).when(mockConnService).find(mktoConnector.getId());
            MarketoService mockMarketoService = mock(MarketoService.class);
            doReturn(0l).when(mockMarketoService).addToProgram(anyString(), anyList(), anyString(), any());
            actions.marketoService = mockMarketoService;
            actions.connectorService = mockConnService;
            MappingNode currentNode = new MappingNode();
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
            var param1 = g(Map.of("synapseId", mktoConnector.getId(), "programId", "123", "leadId", "4566", "programStatus", "Member"));
            var param2 = g(Map.of("synapseId", mktoConnector.getId(), "programId", "123", "leadId", "4567"));
            var param3 = g(Map.of("synapseId", mktoConnector.getId(), "programId", "124", "leadId", "4568"));
            var param4 = g(Map.of("synapseId", mktoConnector.getId(), "programId", "124", "leadId", "4569"));
            actions.addToMarketoProgram(param1, context);
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            actions.addToMarketoProgram(param2, context);
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            actions.addToMarketoProgram(param3, context);
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            actions.addToMarketoProgram(param4, context);
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            assertEquals(Set.of(currentNode), context.getBatchActionContext().getBatchActionNodes());
            assertEquals(4, context.getBatchActionContext().get(currentNode.getId()).size());

            // run action
            context.getBatchActionContext().enableRunActions();
            actions.addToMarketoProgram(param4, context);
            // since there are 2 distinct programIds in collectedParams, assert that there are 2 calls to add + 1 with explicity program status
            verify(mockMarketoService, times(3)).addToProgram(anyString(), anyList(), anyString(), any());
            verify(mockConnService).find(mktoConnector.getId());
        } finally {
            actions.marketoService = orgMarketoService;
            actions.connectorService = orgConnService;
        }
    }

    @Test
    public void addToMarketoProgramInCollectMode_WithInvalidLeadIds(){
        var orgMarketoService = actions.marketoService;
        var orgConnService = actions.connectorService;
        try{
            Connector mktoConnector = getMktoConnector();
            ConnectorService mockConnService = mock(ConnectorService.class);
            doReturn(Optional.of(mktoConnector)).when(mockConnService).find(mktoConnector.getId());
            MarketoService mockMarketoService = mock(MarketoService.class);
            doReturn(0l).when(mockMarketoService).addToProgram(anyString(), anyList(), anyString(), any());
            actions.marketoService = mockMarketoService;
            actions.connectorService = mockConnService;
            MappingNode currentNode = new MappingNode();
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));
            var param1 = g(Map.of("synapseId", mktoConnector.getId(), "programId", "123", "leadId", "INVALID"));
            var param2 = g(Map.of("synapseId", mktoConnector.getId(), "programId", "123", "leadId", ""));
            var param3 = g(new HashMap<>(Map.of("synapseId", mktoConnector.getId(), "programId", "123", "leadId", "")));
            param3.getConfigMap().put("leadId", null);
            actions.addToMarketoProgram(param1, context);
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            actions.addToMarketoProgram(param2, context);
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            actions.addToMarketoProgram(param3, context);
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            assertEquals(Set.of(currentNode), context.getBatchActionContext().getBatchActionNodes());
            assertEquals(1, context.getBatchActionContext().get(currentNode.getId()).size());

            // run action
            context.getBatchActionContext().enableRunActions();
            actions.addToMarketoProgram(g(Map.of()), context);
            // since there are no valid leadIds, addToProgram is never called
            verify(mockMarketoService, never()).addToProgram(anyString(), anyList(), anyString(), any());
            verify(mockConnService).find(mktoConnector.getId());
        } finally {
            actions.marketoService = orgMarketoService;
            actions.connectorService = orgConnService;
        }
    }

    @Test
    public void convertSalesforceLead_WithTokens(){
        var orgSalesforceService = actions.salesforceService;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;
        try{
            Connector sfdcConnector = getSalesforceConnector();

            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(new EntityDefinition("lead", "Lead")).when(mockSchemaService).getEntity(sfdcConnector.getId(), "Lead");
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());
            doReturn(sfdcConnector).when(mockConnService).get(sfdcConnector.getId());
            SalesforceService mockSalesforceService = mock(SalesforceService.class);

            ConvertResult res = new ConvertResult().setLeadId("lead1").setAccountId("acc1").setContactId("contact1").setSuccess(true);
            ConvertResponse response = new ConvertResponse();
            response.setData(List.of(res));

            ConvertData data = new ConvertData().setLeadId("lead1").setAccountId("acc1").setContactId("contact1").setConvertedStatus("Converted").setOpportunityId(null).setOwnerId("owner1");
            ConvertRequest req = new ConvertRequest().setConnector(transformer.toConnectorInfo(sfdcConnector)).setData(List.of(data));

            doReturn(response).when(mockSalesforceService).convertLead(req);
            actions.salesforceService = mockSalesforceService;
            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;

            EntityData record = new EntityData();
            record.setId("lead1");
            record.setValues(Map.of("Status", "Converted", "LeadId", "lead1", "AccountId", "acc1", "ContactId", "contact1", "OwnerId", "owner1"));

            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("convertSalesforceLead");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead")).set("record", record);

            var param1 = g(Map.of("synapseId", sfdcConnector.getId(), "ownerId", "{{record.values.OwnerId}}", "leadId", "{{record.values.LeadId}}", "contactId",
                    "{{record.values.ContactId}}", "accountId", "{{record.values.AccountId}}", "convertedStatus", "{{record.values.Status}}"));
            actions.convertSalesforceLead(param1, context);
            assertEquals(res, context.get(currentNode.getApiName()));

            assertTrue(Boolean.parseBoolean(actions.tokenHelper.resolveTokens(context, "{{convertSalesforceLead.isSuccess}}")));
            assertEquals("lead1", actions.tokenHelper.resolveTokens(context, "{{convertSalesforceLead.leadId}}"));
            assertEquals("contact1", actions.tokenHelper.resolveTokens(context, "{{convertSalesforceLead.contactId}}"));
            assertEquals("acc1", actions.tokenHelper.resolveTokens(context, "{{convertSalesforceLead.accountId}}"));


        } finally {
            actions.salesforceService = orgSalesforceService;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    @Test
    public void convertSalesforceWithError(){
        var orgSalesforceService = actions.salesforceService;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;
        try{
            Connector sfdcConnector = getSalesforceConnector();

            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(new EntityDefinition("lead", "Lead")).when(mockSchemaService).getEntity(sfdcConnector.getId(), "Lead");
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());
            doReturn(sfdcConnector).when(mockConnService).get(sfdcConnector.getId());
            SalesforceService mockSalesforceService = mock(SalesforceService.class);

            ConvertResult res = new ConvertResult().setLeadId("lead1").setAccountId("acc1").setContactId("contact1").setSuccess(false).setError("Convert Error: Lead already converted");
            ConvertResponse response = new ConvertResponse();
            response.setData(List.of(res));

            ConvertData data = new ConvertData().setLeadId("lead1").setAccountId("acc1").setContactId("contact1").setConvertedStatus("Converted").setOpportunityId(null).setOwnerId("owner1");
            ConvertRequest req = new ConvertRequest().setConnector(transformer.toConnectorInfo(sfdcConnector)).setData(List.of(data));

            doReturn(response).when(mockSalesforceService).convertLead(req);
            actions.salesforceService = mockSalesforceService;
            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;

            EntityData record = new EntityData();
            record.setId("lead1");
            record.setValues(Map.of("Status", "Converted", "LeadId", "lead1", "AccountId", "acc1", "ContactId", "contact1", "OwnerId", "owner1"));

            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("convertSalesforceLead");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead")).set("record", record);

            var param1 = g(Map.of("synapseId", sfdcConnector.getId(), "ownerId", "{{record.values.OwnerId}}", "leadId", "{{record.values.LeadId}}", "contactId",
                    "{{record.values.ContactId}}", "accountId", "{{record.values.AccountId}}", "convertedStatus", "{{record.values.Status}}"));

            try {
                actions.convertSalesforceLead(param1, context);
            } catch (Exception e) {
            }

            assertEquals(res, context.get(currentNode.getApiName()));
            assertEquals("Convert Error: Lead already converted", actions.tokenHelper.resolveTokens(context, "{{convertSalesforceLead.error}}"));

        } finally {
            actions.salesforceService = orgSalesforceService;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    @Ignore
    @Test
    public void createExternalRecordToken(){
        SchemaService mockSchemaService = mock(SchemaService.class);
        EntityDefinition e =new EntityDefinition("lead", "Lead");
        AttributeDefinition a = new AttributeDefinition();
        a.setApiName("address");
        a.setDataType(new ObjectType());
        a.setId("123");
        e.addField(a);
        doReturn(e).when(mockSchemaService).getEntity("123");
        actionservice.schemaService = mockSchemaService;
        Map<String, Object> params = new HashMap<String, Object>();
        Map address = Map.of("k1", "v1", "k2", "v2");
        params.put("entityId", "123");
        params.put("insertFields", List.of(Map.of("updateField", Map.of("value", "123"), "newValue", Map.of("value", "{{addressToken}}"))));
        GraphContext context = new GraphContext();
        context.put("addressToken", address);
        EntityData externalRecordObject = actionservice.createExternalRecordObject(g(params), context);
        assertEquals(externalRecordObject.getValue("address"), address);
    }

    @Test
    public void updateSyncariRecord(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        EntityData input = new EntityData();
        input.setName("coreAccount");
        input.setSyncariEntityId(coreEntity.getId());
        input.addValue("corefield1", "testlookup");
        input.setLastModified(System.currentTimeMillis());
        input = entityRepo.save(input);

        MappingGraph entityGraph = newGraph(coreEntity, null, actionDefinitionRepo)
                .src(srcEntity)
                .action(ActionConstants.UPDATE_SYNCARI_RECORDS, "Update Syncari record")
                .connect("srcAccount", "coreAccount")
                .connect("coreAccount", "Update Syncari record").getGraph();
        MappingNode actionNode = entityGraph.findNodeByName("Update Syncari record").get();

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "_id"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{record.id}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.cache(coreEntity.getId(), coreEntity);
        graphContext.put("record", input);
        graphContext.setGraph(entityGraph);
        graphContext.setCurrentNode(actionNode);
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("predicate", eq);
        configMap.put("updateFields", List.of(Map.of("updateField", Map.of("value", coreField1.getId()), "newValue", Map.of("value", "updated"))));
        configMap.put("syncariEntityDefId", coreEntity.getId());
        GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
        actionConfig.setConfigMap(configMap);
        actionNode.setConfiguration(actionConfig);

        ActionResult updated = updateRecordsAction.execute(actionConfig, graphContext);
        assertNotNull(updated.getResult());
        Optional<EntityData> byId = entityRepo.findById(coreEntity, input.getId());
        assertEquals("updated", byId.get().getValueAsString("corefield1"));
    }

    @Ignore
    @Test
    public void updateExternalRecord(){
        var orgSalesforceService = actions.salesforceService;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;
        EntityDefinition leadSchema = schemaService.getEntity(connector.getId(), "Lead");

        try{
            Connector sfdcConnector = getSalesforceConnector();

            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(new EntityDefinition("lead", "Lead")).when(mockSchemaService).getEntity(sfdcConnector.getId(), "Lead");
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());
            doReturn(sfdcConnector).when(mockConnService).get(sfdcConnector.getId());
            SalesforceService mockSalesforceService = mock(SalesforceService.class);

            Result res = new Result(true, "123", "345");
            SyncResponse response = new SyncResponse();
            response.setResults(List.of(res));

            EntityData data = new EntityData().setId("lead1");
            SyncRequest req = new SyncRequest().setConnector(transformer.toConnectorInfo(sfdcConnector)).setData(Map.of(sfdcConnector.getId(), List.of(data)));

            doReturn(response).when(mockSalesforceService).update(req);
            actions.salesforceService = mockSalesforceService;
            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;

            EntityData record = new EntityData();
            record.setId("lead1");
            record.setValues(Map.of("Status", "Converted", "LeadId", "lead1", "AccountId", "acc1", "ContactId", "contact1", "OwnerId", "owner1"));

            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("updateExternal");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead")).set("record", record);
            var param1 = g(Map.of("synapseId", sfdcConnector.getId(), "entityId", leadSchema.getId(), "recordId", "123",
                    "updateFields", List.of(Map.of())));
            actions.updateExternalRecord(param1, context);
            assertEquals(res, context.get(currentNode.getApiName()));

            assertTrue(Boolean.parseBoolean(actions.tokenHelper.resolveTokens(context, "{{updateExternal.isSuccess}}")));
            assertEquals("lead1", actions.tokenHelper.resolveTokens(context, "{{updateExternal.leadId}}"));
            assertEquals("contact1", actions.tokenHelper.resolveTokens(context, "{{updateExternal.contactId}}"));
            assertEquals("acc1", actions.tokenHelper.resolveTokens(context, "{{updateExternal.accountId}}"));
            actions.deleteExternalRecord(param1, context);

        } finally {
            actions.salesforceService = orgSalesforceService;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    @Test
    public void updateExternalRecord_WithValidAttributeId() {
        var orgDataServiceFactory = actions.dataServiceFactory;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;

        try {
            Connector sfdcConnector = getSalesforceConnector();
            sfdcConnector.setMetadata(metadata);
            sfdcConnector.setMetadataId(metadata.getId());

            // Create entity definition with attributes
            EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
            leadEntity.setId(ObjectId.get().toHexString());
            leadEntity.setConnectorId(sfdcConnector.getId());

            AttributeDefinition statusAttr = new AttributeDefinition();
            statusAttr.setId(ObjectId.get().toHexString());
            statusAttr.setApiName("Status");
            statusAttr.setDataType(StringType.VALUE);
            leadEntity.addField(statusAttr);

            // Mock services
            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(leadEntity).when(mockSchemaService).getEntity(leadEntity.getId());
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());

            var mockDataServiceFactory = mock(com.syncari.core.service.DataServiceFactory.class);
            var mockDataService = mock(com.syncari.connector.service.def.DataService.class);
            doReturn(mockDataService).when(mockDataServiceFactory).getDataService(any());

            SyncResponse response = new SyncResponse();
            response.setSuccess(true);
            doReturn(response).when(mockDataService).update(any());

            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;
            actions.dataServiceFactory = mockDataServiceFactory;

            // Setup context
            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("updateExternalRecord");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));

            // Test with valid attribute ID (not a token)
            var param = g(Map.of(
                    "synapseId", sfdcConnector.getId(),
                    "entityId", leadEntity.getId(),
                    "recordId", "00Q1234567890",
                    "updateFields", List.of(Map.of(
                            "updateField", Map.of("value", statusAttr.getId()),
                            "newValue", Map.of("value", "Qualified")
                    ))
            ));

            actions.updateExternalRecord(param, context);

            // Verify the response was set in context
            assertNotNull(context.get(String.format("Result From %s", currentNode.getName())));

        } finally {
            actions.dataServiceFactory = orgDataServiceFactory;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    @Test
    public void updateExternalRecord_WithTokenResolvedToValidAttributeId() {
        var orgDataServiceFactory = actions.dataServiceFactory;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;

        try {
            Connector sfdcConnector = getSalesforceConnector();
            sfdcConnector.setMetadata(metadata);
            sfdcConnector.setMetadataId(metadata.getId());

            // Create entity definition with attributes
            EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
            leadEntity.setId(ObjectId.get().toHexString());
            leadEntity.setConnectorId(sfdcConnector.getId());

            AttributeDefinition statusAttr = new AttributeDefinition();
            statusAttr.setId(ObjectId.get().toHexString());
            statusAttr.setApiName("Status");
            statusAttr.setDataType(StringType.VALUE);
            leadEntity.addField(statusAttr);

            // Mock services
            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(leadEntity).when(mockSchemaService).getEntity(leadEntity.getId());
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());

            var mockDataServiceFactory = mock(com.syncari.core.service.DataServiceFactory.class);
            var mockDataService = mock(com.syncari.connector.service.def.DataService.class);
            doReturn(mockDataService).when(mockDataServiceFactory).getDataService(any());

            SyncResponse response = new SyncResponse();
            response.setSuccess(true);
            doReturn(response).when(mockDataService).update(any());

            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;
            actions.dataServiceFactory = mockDataServiceFactory;

            // Setup context with token that resolves to valid attribute ID
            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("updateExternalRecord");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));

            // Add a temp variable that contains the attribute ID
            context.put("fieldToUpdate", statusAttr.getId());

            // Test with token that resolves to valid attribute ID
            var param = g(Map.of(
                    "synapseId", sfdcConnector.getId(),
                    "entityId", leadEntity.getId(),
                    "recordId", "00Q1234567890",
                    "updateFields", List.of(Map.of(
                            "updateField", Map.of("value", "{{fieldToUpdate}}"),
                            "newValue", Map.of("value", "Qualified")
                    ))
            ));

            actions.updateExternalRecord(param, context);

            // Verify the response was set in context
            assertNotNull(context.get(String.format("Result From %s", currentNode.getName())));

        } finally {
            actions.dataServiceFactory = orgDataServiceFactory;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    @Test
    public void updateExternalRecord_WithTokenResolvedToValidApiName() {
        var orgDataServiceFactory = actions.dataServiceFactory;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;

        try {
            Connector sfdcConnector = getSalesforceConnector();
            sfdcConnector.setMetadata(metadata);
            sfdcConnector.setMetadataId(metadata.getId());

            // Create entity definition with attributes
            EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
            leadEntity.setId(ObjectId.get().toHexString());
            leadEntity.setConnectorId(sfdcConnector.getId());

            AttributeDefinition statusAttr = new AttributeDefinition();
            statusAttr.setId(ObjectId.get().toHexString());
            statusAttr.setApiName("Status");
            statusAttr.setDataType(StringType.VALUE);
            leadEntity.addField(statusAttr);

            // Mock services
            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(leadEntity).when(mockSchemaService).getEntity(leadEntity.getId());
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());

            var mockDataServiceFactory = mock(com.syncari.core.service.DataServiceFactory.class);
            var mockDataService = mock(com.syncari.connector.service.def.DataService.class);
            doReturn(mockDataService).when(mockDataServiceFactory).getDataService(any());

            SyncResponse response = new SyncResponse();
            response.setSuccess(true);
            doReturn(response).when(mockDataService).update(any());

            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;
            actions.dataServiceFactory = mockDataServiceFactory;

            // Setup context with token that resolves to API name
            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("updateExternalRecord");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));

            // Add a temp variable that contains the API name instead of ID
            context.put("fieldToUpdate", "Status");

            // Test with token that resolves to API name
            var param = g(Map.of(
                    "synapseId", sfdcConnector.getId(),
                    "entityId", leadEntity.getId(),
                    "recordId", "00Q1234567890",
                    "updateFields", List.of(Map.of(
                            "updateField", Map.of("value", "{{fieldToUpdate}}"),
                            "newValue", Map.of("value", "Qualified")
                    ))
            ));

            actions.updateExternalRecord(param, context);

            // Verify the response was set in context
            assertNotNull(context.get(String.format("Result From %s", currentNode.getName())));

        } finally {
            actions.dataServiceFactory = orgDataServiceFactory;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    @Test
    public void updateExternalRecord_WithTokenResolvedToInvalidAttributeId() {
        var orgDataServiceFactory = actions.dataServiceFactory;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;

        try {
            Connector sfdcConnector = getSalesforceConnector();
            sfdcConnector.setMetadata(metadata);
            sfdcConnector.setMetadataId(metadata.getId());

            // Create entity definition with attributes
            EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
            leadEntity.setId(ObjectId.get().toHexString());
            leadEntity.setConnectorId(sfdcConnector.getId());

            AttributeDefinition statusAttr = new AttributeDefinition();
            statusAttr.setId(ObjectId.get().toHexString());
            statusAttr.setApiName("Status");
            statusAttr.setDataType(StringType.VALUE);
            leadEntity.addField(statusAttr);

            // Mock services
            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(leadEntity).when(mockSchemaService).getEntity(leadEntity.getId());
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());

            var mockDataServiceFactory = mock(com.syncari.core.service.DataServiceFactory.class);
            var mockDataService = mock(com.syncari.connector.service.def.DataService.class);
            doReturn(mockDataService).when(mockDataServiceFactory).getDataService(any());

            SyncResponse response = new SyncResponse();
            response.setSuccess(true);
            doReturn(response).when(mockDataService).update(any());

            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;
            actions.dataServiceFactory = mockDataServiceFactory;

            // Setup context with token that resolves to invalid attribute
            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("updateExternalRecord");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));

            // Add a temp variable that contains an invalid attribute ID/name
            context.put("fieldToUpdate", "InvalidFieldName");

            // Test with token that resolves to invalid attribute - should throw exception
            var param = g(Map.of(
                    "synapseId", sfdcConnector.getId(),
                    "entityId", leadEntity.getId(),
                    "recordId", "00Q1234567890",
                    "updateFields", List.of(Map.of(
                            "updateField", Map.of("value", "{{fieldToUpdate}}"),
                            "newValue", Map.of("value", "Qualified")
                    ))
            ));

            // This should throw exception for invalid field
            try {
                actions.updateExternalRecord(param, context);
                fail("Expected RuntimeException for invalid attribute");
            } catch (RuntimeException e) {
                assertTrue(e.getMessage().contains("Attribute not found for id/apiName:"));
                assertTrue(e.getMessage().contains("InvalidFieldName"));
                assertTrue(e.getMessage().contains("in entity: lead"));
            }

        } finally {
            actions.dataServiceFactory = orgDataServiceFactory;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    @Test
    public void updateExternalRecord_WithMultipleFieldsIncludingTokens() {
        var orgDataServiceFactory = actions.dataServiceFactory;
        var orgConnService = actions.connectorService;
        var orgSchemaService = actions.schemaService;

        try {
            Connector sfdcConnector = getSalesforceConnector();
            sfdcConnector.setMetadata(metadata);
            sfdcConnector.setMetadataId(metadata.getId());

            // Create entity definition with multiple attributes
            EntityDefinition leadEntity = new EntityDefinition("lead", "Lead");
            leadEntity.setId(ObjectId.get().toHexString());
            leadEntity.setConnectorId(sfdcConnector.getId());

            AttributeDefinition statusAttr = new AttributeDefinition();
            statusAttr.setId(ObjectId.get().toHexString());
            statusAttr.setApiName("Status");
            statusAttr.setDataType(StringType.VALUE);
            leadEntity.addField(statusAttr);

            AttributeDefinition companyAttr = new AttributeDefinition();
            companyAttr.setId(ObjectId.get().toHexString());
            companyAttr.setApiName("Company");
            companyAttr.setDataType(StringType.VALUE);
            leadEntity.addField(companyAttr);

            // Mock services
            ConnectorService mockConnService = mock(ConnectorService.class);
            SchemaService mockSchemaService = mock(SchemaService.class);
            doReturn(leadEntity).when(mockSchemaService).getEntity(leadEntity.getId());
            doReturn(Optional.of(sfdcConnector)).when(mockConnService).find(sfdcConnector.getId());

            var mockDataServiceFactory = mock(com.syncari.core.service.DataServiceFactory.class);
            var mockDataService = mock(com.syncari.connector.service.def.DataService.class);
            doReturn(mockDataService).when(mockDataServiceFactory).getDataService(any());

            SyncResponse response = new SyncResponse();
            response.setSuccess(true);
            doReturn(response).when(mockDataService).update(any());

            actions.connectorService = mockConnService;
            actions.schemaService = mockSchemaService;
            actions.dataServiceFactory = mockDataServiceFactory;

            // Setup context
            MappingNode currentNode = new MappingNode();
            currentNode.setApiName("updateExternalRecord");
            currentNode.setId(ObjectId.get().toHexString());
            GraphContext context = new GraphContext().setCurrentNode(currentNode)
                    .setCurrentBatch(new CurrentBatch(null).setSyncariEntityName("lead"));

            // Add temp variable for dynamic field
            context.put("dynamicField", "Company");

            // Test with mix of direct attribute ID and token
            var param = g(Map.of(
                    "synapseId", sfdcConnector.getId(),
                    "entityId", leadEntity.getId(),
                    "recordId", "00Q1234567890",
                    "updateFields", List.of(
                            Map.of(
                                    "updateField", Map.of("value", statusAttr.getId()),
                                    "newValue", Map.of("value", "Qualified")
                            ),
                            Map.of(
                                    "updateField", Map.of("value", "{{dynamicField}}"),
                                    "newValue", Map.of("value", "Acme Corp")
                            )
                    )
            ));

            actions.updateExternalRecord(param, context);

            // Verify the response was set in context
            assertNotNull(context.get(String.format("Result From %s", currentNode.getName())));

        } finally {
            actions.dataServiceFactory = orgDataServiceFactory;
            actions.connectorService = orgConnService;
            actions.schemaService = orgSchemaService;
        }
    }

    private FunctionCall createCall(Object... keyValues) {
        Map<String, Object> config = new HashMap<>();
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                config.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return new FunctionCall().setConfig(config).setParams(List.of(ParameterValue.string("param", "input")));
    }

    private Connector getMktoConnector(){
        Connector connector = new Connector();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId("CLIENT_ID");
        authConfig.setClientSecret("CLIENT_SECRET");
        authConfig.setConsumerKey("USER_ID");
        authConfig.setConsumerSecret("ENCRYPTION_KEY");
        connector.setAuthConfig(authConfig);
        connector.getMetaConfig().put("munchkin", "MUNCHKIN");
        connector.setId("mkto");
        connector.setName("mkto");
        return connector;
    }

    private Connector getSalesforceConnector(){
        Connector connector = new Connector();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setUserName("USER_NAME");
        authConfig.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        authConfig.setToken("TOKEN");
        connector.setAuthConfig(authConfig);
        connector.setId("sfdc");
        connector.setName("sfdc");
        return connector;
    }

}
