package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Index;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.enrich.ClearbitService;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.misc.ServiceCredentialType;
import com.syncari.core.model.misc.ServiceType;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.In;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.*;
import com.syncari.core.utils.MongoUtils;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class LookUpFunctionsTest extends AbstractSyncariTest {

    @Value("${clearbit.api.key}")
    String clearbitApiKey;
    @MockBean
    ClearbitService clearbitService;

    @Autowired
    LookUpFunctions lookUpFunctions;
    
    @Autowired
    IdMappingRepo mappingRepo;
    
    @Autowired
    SchemaService schemaService;

    @Autowired
    EntityRepoService entityRepoService;
    @Mock
    ConnectorService mockConnectorService;

    @MockBean
    DataServiceFactory dataServiceFactory;

    @Autowired
    EntityRepo entityRepo;
    @Autowired
    ConnectorRepo connectorRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;
    
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    StagedBatchRecordRepo stagedBatchRecordRepo;

    @Mock
    SchemaService mockSchemaService;

    @Autowired
    TransactionLogService txnService;

    @Mock
    ReferenceDataService refDataService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    ServiceCredentialService serviceCredentialService;

    @Autowired
    ServiceCredentialRepo serviceCredRepo;

    @Autowired
    NotificationRepo notifRepo;

    @Autowired
    MongoTemplate customerMongoTemplate;

    @Before
    public void setUp(){
        super.setUp();
        lookUpFunctions.clearbitService = clearbitService;
        when(mockSchemaService.getEntity("synEntId")).thenReturn(getSyncariAccount());
        when(mockSchemaService.getEntity(any(), any())).thenReturn(getExternalAccount());
        when(mockSchemaService.getEntity("extEntId")).thenReturn(getExternalAccount());
        when(mockSchemaService.getEntity("extEnt2")).thenReturn(getExternalAccount("Customer","connector2","extEnt2"));
        lookUpFunctions.schemaService = mockSchemaService;

        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());
        lookUpFunctions.connectorService = mockConnectorService;
    }

    @Test
    public void lookUpRefDataSingleValue(){
        when(refDataService.lookUp(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyMap())).thenReturn("LOOKEDUP_VALUE");
        lookUpFunctions.refDataService = refDataService;
        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");
        FunctionCall functionCall = createCall("datasetId", "1234", "lookUpKey", "testKey", "destinationFieldName", "email");
        Object lookedUp = lookUpFunctions.lookUpRefData("test", functionCall, graphContext);
        assertEquals("LOOKEDUP_VALUE", lookedUp.toString());
    }
    
    @Test
    public void lookUpRefDataNullInput(){
        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");
        FunctionCall functionCall = createCall("defaultValue", "defaultValue");
        Object lookedUp = lookUpFunctions.lookUpRefData(null, functionCall, graphContext);
        assertEquals("defaultValue", (String)lookedUp);
    }

    @Test
    public void lookUpRefDataMultipleValues(){
        when(refDataService.lookUp(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyMap())).thenReturn("LOOKEDUP_VALUE1", "LOOKEDUP_VALUE2", "LOOKEDUP_VALUE3");
        lookUpFunctions.refDataService = refDataService;
        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");
        FunctionCall functionCall = createCall("datasetId", "1234", "lookUpKey", "key", "destinationFieldName", "email");
        Object lookedUp = lookUpFunctions.lookUpRefData(List.of("test1", "test2", "test3"), functionCall, graphContext);
        assertEquals("LOOKEDUP_VALUE1", ((List)lookedUp).get(0).toString());
        assertEquals("LOOKEDUP_VALUE2", ((List)lookedUp).get(1).toString());
        assertEquals("LOOKEDUP_VALUE3", ((List)lookedUp).get(2).toString());
    }
    
    @Test
    public void enrichPerson(){
        ServiceCredential clearbit = getClearbitServiceCred();
        when(clearbitService.lookUpLead(any(), any(), any())).thenReturn("ENRICHED_VALUE");
        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");
        FunctionCall functionCall = createCall("emailField", "email", "lookUpKey", "lookUpField", "enrichOnEmptyValue", false, "serviceId", clearbit.getId());
        Object enriched = lookUpFunctions.enrichPerson("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("ENRICHED_VALUE", enriched.toString());
    }

    @Test
    public void enrichPerson_FailureWithNotification(){
        ServiceCredential clearbit = getClearbitServiceCred();
        when(clearbitService.lookUpLead(any(), any(), any())).thenThrow(new NonRetriableException("ERROR_CODE", "Error in lookupLead", "ERROR_STATUS"));
        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");
        graphContext.setGraph(new MappingGraph().setName("pipeline1"));
        graphContext.setSyncariEntity(new EntityDefinition("coreEntity", "Core Entity"));
        FunctionCall functionCall = createCall("emailField", "email", "lookUpKey", "lookUpField", "enrichOnEmptyValue", false, "serviceId", clearbit.getId());
        Object enriched = lookUpFunctions.enrichPerson(null, functionCall, graphContext);
        assertNull(enriched);

        List<Notification> notifications = notificationService.getByKey(SyncariContext.getUser().getId(), clearbit.getId()+"_ERROR_CODE");
        assertFalse(notifications.isEmpty());
        assertEquals(1, notifications.size());
        verify(emailService, times(1)).sendSupportEmail(any(), any());

        enriched = lookUpFunctions.enrichPerson(null, functionCall, graphContext);
        assertNull(enriched);
        notifications = notificationService.getByKey(SyncariContext.getUser().getId(), clearbit.getId()+"_ERROR_CODE");
        assertFalse(notifications.isEmpty());
        assertEquals(1, notifications.size()); // new notification is not sent as last one is within 24 hr window
        verify(emailService, times(1)).sendSupportEmail(any(), any()); // no new email is sent
    }

    @Test
    public void enrichPerson_FailureWithNotificationInTestMode(){
        ServiceCredential clearbit = getClearbitServiceCred();
        when(clearbitService.lookUpLead(any(), any(), any())).thenThrow(new NonRetriableException("ERROR_CODE", "Error in lookupLead", "ERROR_STATUS"));
        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");
        graphContext.setGraph(new MappingGraph().setName("pipeline1"));
        graphContext.setTestMode(true).setSimulationMode(false);
        graphContext.setSyncariEntity(new EntityDefinition("coreEntity", "Core Entity"));
        FunctionCall functionCall = createCall("emailField", "email", "lookUpKey", "lookUpField", "enrichOnEmptyValue", false, "serviceId", clearbit.getId());
        Object enriched = lookUpFunctions.enrichPerson(null, functionCall, graphContext);
        assertNull(enriched);

        List<Notification> notifications = notificationService.getByKey(SyncariContext.getUser().getId(), clearbit.getId()+"_ERROR_CODE");
        assertFalse(notifications.isEmpty());
        assertEquals(1, notifications.size());
        verify(emailService, times(1)).sendSupportEmail(any(), any());

        graphContext.setTestMode(false).setSimulationMode(true);
        enriched = lookUpFunctions.enrichPerson(null, functionCall, graphContext);
        assertNull(enriched);
        notifications = notificationService.getByKey(SyncariContext.getUser().getId(), clearbit.getId()+"_ERROR_CODE");
        assertFalse(notifications.isEmpty());
        assertEquals(2, notifications.size()); // new notification is sent as pipeline is in testMode
        verify(emailService, times(2)).sendSupportEmail(any(), any());
    }

    @Test
    public void enrichPerson_FailureWithNotificationWindow(){
        ServiceCredential clearbit = getClearbitServiceCred();

        // create a notification withn 24 hr window
        Notification n = new Notification(clearbit.getId()+"_ERROR_CODE", "some_subject", "some_body", NotificationType.WARN, SyncariContext.getUser().getId());
        n.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()));
        n = notifRepo.save(n);
        List<Notification> notifications = notificationService.getByKey(SyncariContext.getUser().getId(), clearbit.getId()+"_ERROR_CODE");
        assertFalse(notifications.isEmpty());
        assertEquals(1, notifications.size());

        when(clearbitService.lookUpLead(any(), any(), any())).thenThrow(new NonRetriableException("ERROR_CODE", "Error in lookupLead", "ERROR_STATUS"));
        GraphContext graphContext = new GraphContext().set("field_email", "EMAIL");
        graphContext.setGraph(new MappingGraph().setName("pipeline1"));
        graphContext.setSyncariEntity(new EntityDefinition("coreEntity", "Core Entity"));
        FunctionCall functionCall = createCall("emailField", "email", "lookUpKey", "lookUpField", "enrichOnEmptyValue", false, "serviceId", clearbit.getId());
        Object enriched = lookUpFunctions.enrichPerson(null, functionCall, graphContext);
        assertNull(enriched);

        notifications = notificationService.getByKey(SyncariContext.getUser().getId(), clearbit.getId()+"_ERROR_CODE");
        assertFalse(notifications.isEmpty());
        assertEquals(1, notifications.size()); // new notification is not created as latest one is within 24 hr window
        verify(emailService, times(1)).sendSupportEmail(any(), any()); // notif to admin@syncari user is sent and thus the email to support

        // set the notification outside of 24 hr window
        n.setCreatedAt(new Date(Instant.now().minus(25, ChronoUnit.HOURS).toEpochMilli()));
        n = notifRepo.save(n);
        enriched = lookUpFunctions.enrichPerson(null, functionCall, graphContext);
        assertNull(enriched);
        notifications = notificationService.getByKey(SyncariContext.getUser().getId(), clearbit.getId()+"_ERROR_CODE");
        assertFalse(notifications.isEmpty());
        assertEquals(2, notifications.size()); // new notification is sent as last one is outside 24 hr window
        verify(emailService, times(2)).sendSupportEmail(any(), any()); // new notification is sent
    }

    @Test
    public void enrichPerson_NoFieldValue(){
        when(clearbitService.lookUpLead(any(), any(), any())).thenReturn("ENRICHED_VALUE");
        GraphContext graphContext = new GraphContext().set("field_email", null);
        FunctionCall functionCall = createCall("emailField", "email", "lookUpKey", "lookUpField", "enrichOnEmptyValue", true);
        Object enriched = lookUpFunctions.enrichPerson("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("DEFAULT", enriched.toString());
    }

    @Test
    public void enrichCompany_ByDomain_enrichOnEmptyFalse(){
        ServiceCredential clearbit = getClearbitServiceCred();
        when(clearbitService.lookUpCompany(any(), any(), any())).thenReturn("ENRICHED_VALUE");
        GraphContext graphContext = new GraphContext().set("field_domain", "DOMAIN_NAME");
        FunctionCall functionCall = createCall("domainField", "domain", "lookUpKey", "lookUpField", "enrichUsing", "domain", 
            "enrichOnEmptyValue", false, "serviceId", clearbit.getId());
        Object enriched = lookUpFunctions.enrichCompany("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("ENRICHED_VALUE", enriched.toString());
    }

    @Test
    public void enrichCompany_ByDomain_enrichOnEmptyTrue(){
        when(clearbitService.lookUpCompany(any(), any(), any())).thenReturn("ENRICHED_VALUE");
        GraphContext graphContext = new GraphContext().set("field_domain", "DOMAIN_NAME");
        FunctionCall functionCall = createCall("domainField", "domain", "lookUpKey", "lookUpField", "enrichUsing", "domain", 
            "enrichOnEmptyValue", true);
        Object enriched = lookUpFunctions.enrichCompany("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("DEFAULT", enriched.toString());
    }

    @Test
    public void enrichCompany_ReturnDefault(){
        when(clearbitService.lookUpCompany(any(), any(), any())).thenReturn(null);
        GraphContext graphContext = new GraphContext().set("field_domain", "DOMAIN_NAME");
        FunctionCall functionCall = createCall("domainField", "domain", "lookUpKey", "lookUpField", "enrichUsing", "domain");
        Object enriched = lookUpFunctions.enrichCompany("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("DEFAULT", enriched.toString());
    }

    @Test
    public void enrichCompany_NoFieldValue(){
        when(clearbitService.lookUpCompany(any(), any(), any())).thenReturn(null);
        GraphContext graphContext = new GraphContext().set("field_domain", null);
        FunctionCall functionCall = createCall("domainField", "domain", "lookUpKey", "lookUpField", "enrichUsing", "domain");
        Object enriched = lookUpFunctions.enrichCompany("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("DEFAULT", enriched.toString());
    }

    @Test
    public void enrichCompany_ByIP(){
        ServiceCredential clearbit = getClearbitServiceCred();
        when(clearbitService.lookUpCompanyByIPAddress(any(), any(), any())).thenReturn("ENRICHED_VALUE");
        GraphContext graphContext = new GraphContext().set("field_ip", "IP_ADDRESS");
        FunctionCall functionCall = createCall("domainField", "ip", "lookUpKey", "lookUpField", "enrichUsing", "ip", "enrichOnEmptyValue", false, "serviceId", clearbit.getId());
        Object enriched = lookUpFunctions.enrichCompany("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("ENRICHED_VALUE", enriched.toString());
    }

    @Test
    public void enrichCompany_NoEnrichUsingConfig(){
        ServiceCredential clearbit = getClearbitServiceCred();
        // when enrichUsing field is not provided the default lookup should be by domain
        when(clearbitService.lookUpCompany(any(), any(), any())).thenReturn("ENRICHED_VALUE_BY_DOMAIN");
        when(clearbitService.lookUpCompanyByIPAddress(any(), any(), any())).thenReturn("ENRICHED_VALUE_BY_IP");
        GraphContext graphContext = new GraphContext().set("field_domain", "DOMAIN_NAME");
        FunctionCall functionCall = createCall("domainField", "domain", "lookUpKey", "lookUpField", "enrichOnEmptyValue", false, "serviceId", clearbit.getId());
        Object enriched = lookUpFunctions.enrichCompany("DEFAULT", functionCall, graphContext);
        assertNotNull(enriched);
        assertEquals("ENRICHED_VALUE_BY_DOMAIN", enriched.toString());
        verify(clearbitService).lookUpCompany(any(), any(), any());
        verify(clearbitService, never()).lookUpCompanyByIPAddress(any(), any(), any());

    }

    @Test
    public void attachExistingSyncariFoundAndMappingNotFound(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("Account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        IdMapping syncariId = new IdMapping();
        syncariId.setEntityName("account");
        syncariId.setId("synId");
        mappingRepo.save(syncariId);
        GraphContext graphContext = new GraphContext().set("field_name", "test");
        FunctionCall functionCall = getAttachFunctionCall();
        
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("123");
        input.addValue("Name", "test");
        entityRepo.save(input);
        
        input.setId("567");
        input.setConnectorId("extConnectorId");

        lookUpFunctions.stagedBatchRecordRepo = mock(StagedBatchRecordRepo.class);
        when(lookUpFunctions.stagedBatchRecordRepo.findFirstByExternalEntityDefinitionIdAndExternalRecordId("extEntId","567")).thenReturn(Optional.of(new StagedBatchRecord()));
        Object attached = lookUpFunctions.attachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
        verify(lookUpFunctions.stagedBatchRecordRepo).findFirstByExternalEntityDefinitionIdAndExternalRecordId("extEntId","567");
    }


    @Test
    public void advancedAttach_existing_syncari_found_and_mapping_not_found(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");
        existing.addValue("Name", "test");

        EntityData saved = entityRepo.save(existing);
        IdMapping idMapping = new IdMapping();
        idMapping.setEntityName("account");
        idMapping.setSyncariId(saved.getSyncariEntityId());
        mappingRepo.save(idMapping);

        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
    }

    @Test
    public void advancedAttach_existing_syncari_found_and_another_mapping_found(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");
        existing.addValue("Name", "test");

        EntityData saved = entityRepo.save(existing);
        IdMapping idMapping = new IdMapping();
        idMapping.setEntityName("account");
        idMapping.setSyncariId(saved.getSyncariEntityId());
        //same connector & entity, but connected to another record
        idMapping.addMapping("extConnectorId","700","extEntId");
        mappingRepo.save(idMapping);

        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setSyncariEntityId("fabricated_syncari_id");
        input.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //No changes to incoming record. There was another record connected, so we treat this as a duplicate
        assertEquals("fabricated_syncari_id",((EntityData)attached).getSyncariEntityId());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
    }
    @Test
    public void advancedAttach_with_multiple_synapse_withoutemptypredicate_records_samebatch(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");
        existing.addValue("AccountName", "test");
        existing.setConnectorId("record1connectorId");
        existing.setSyncariEntityId("record1syncariId");

        GraphContext graphContext = new GraphContext().set("previous", existing).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        //graphContext.set("previous", existing);
        Object attached1 = lookUpFunctions.advancedAttachRecord(existing, functionCall, graphContext);


        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setSyncariEntityId("fabricated_syncari_id");
        input.setConnectorId("extConnectorId");


        graphContext.set("previous", input);
        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //Not Attached to existing syncari id
        assertEquals(existing.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
    }


    @Test
    public void advancedAttach_with_multiple_synapse_with_emptypredicate_records_samebatch(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setId("123");
        existing.setName("Account");
        //existing.addValue("AccountName", "test");
        existing.setConnectorId("record1connectorId");
        existing.setSyncariEntityId("record1syncariId");

        GraphContext graphContext = new GraphContext().set("previous", existing).setGraph(new MappingGraph()
                .setTargetId("synEntId"));
        graphContext.set("previous", existing);
        Object attached1 = lookUpFunctions.advancedAttachRecord(existing, functionCall, graphContext);

        EntityData input = new EntityData();
        //input.addValue("AccountName","test");
        input.setName("Account");
        input.setId("567");
        input.setSyncariEntityId("fabricated_syncari_id");
        input.setConnectorId("extConnectorId");
        graphContext.set("previous", input);
        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //No changes to incoming record. There was another record connected, so we treat this as a duplicate
        assertEquals(input.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());

    }

    @Test
    public void advancedAttach_existing_syncari_found_without_mapping(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.addValue("Name", "test");

        EntityData saved = entityRepo.save(existing);

        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setSyncariEntityId("fabricated_syncari_id");
        input.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //No changes to incoming record. There was another matching record, but without an id mapping , so we treat this as the same record
        assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
        assertEquals(1,findByExternalId.get().getMappings().size());
        assertEquals("account",findByExternalId.get().getEntityName());
        assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());
    }

    @Test
    public void advancedAttach_existing_syncari_found_without_mapping_equalsIgnoreCase(){
    	MongoUtils.createIndexes(customerMongoTemplate, "syncari_account", List.of(
                new Index("name_case_insensitive_idx", false, false, "Name")
        ));

        try {
            Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
            assertFalse(findByExternalId.isPresent());
            Optional<Connector> syncari = connectorRepo.findByName("syncari");
            when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

            var ieq = Map.of(
                    "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                    "operator", "ieq",
                    "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
            );

            FunctionCall functionCall = getAttachFunctionCall(ieq);

            EntityData existing = new EntityData();
            existing.setName("Account");
            existing.addValue("Name", "TeST");

            EntityData saved = entityRepo.save(existing);

            EntityData input = new EntityData();
            input.setName("Account");
            input.addValue("AccountName","test");
            input.setId("567");
            input.setSyncariEntityId("fabricated_syncari_id");
            input.setConnectorId("extConnectorId");

            GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                    .setTargetId("synEntId"));

            Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
            //Since we are not connecting cross-synapse objects, connected records should be empty
            assertTrue(graphContext.getAllConnectedRecords().isEmpty());
            assertNotNull(attached);
            //No changes to incoming record. There was another matching record, but without an id mapping , so we treat this as the same record
            assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
            findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
            assertTrue(findByExternalId.isPresent());
            assertEquals(1,findByExternalId.get().getMappings().size());
            assertEquals("account",findByExternalId.get().getEntityName());
            assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());
        } finally {
        	MongoUtils.dropIndexes(customerMongoTemplate, "syncari_account", List.of(
                    new Index("name_case_insensitive_idx", false, false, "Name")
            ));
        }
    }

    @Test
    public void advancedAttach_existing_syncari_found_with_multiple_synapse_records(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");
        existing.addValue("Name", "test");

        EntityData saved = entityRepo.save(existing);
        IdMapping idMapping = new IdMapping();
        idMapping.setEntityName("account");
        idMapping.setSyncariId(saved.getSyncariEntityId());
        //same connector & entity, but connected to another record
        idMapping.addMapping("extConnectorId2","700","extEntId2");
        mappingRepo.save(idMapping);

        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setSyncariEntityId("fabricated_syncari_id");
        input.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //No changes to incoming record. There was another record connected, so we treat this as a duplicate
        assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
        assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());
        //The other externalRecord also connected to the same id mapping
        assertTrue(findByExternalId.get().findMapping("extConnectorId2","extEntId2","700").isPresent());

    }

    @Test
    public void advancedAttach_existing_syncari_found_with_multiple_synapse_records_equalsIgnoreCase(){
    	MongoUtils.createIndexes(customerMongoTemplate, "syncari_account", List.of(
                new Index("name_case_insensitive_idx", false, false, "Name")
        ));

        try {
            Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
            assertFalse(findByExternalId.isPresent());
            Optional<Connector> syncari = connectorRepo.findByName("syncari");
            when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());
    
            var ieq = Map.of(
                    "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                    "operator", "ieq",
                    "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
            );
    
            FunctionCall functionCall = getAttachFunctionCall(ieq);
    
            EntityData existing = new EntityData();
            existing.setName("Account");
            existing.setId("123");
            // For equalsIgnoreCase
            existing.addValue("Name", "TeSt");
    
            EntityData saved = entityRepo.save(existing);
            IdMapping idMapping = new IdMapping();
            idMapping.setEntityName("account");
            idMapping.setSyncariId(saved.getSyncariEntityId());
            //same connector & entity, but connected to another record
            idMapping.addMapping("extConnectorId2","700","extEntId2");
            mappingRepo.save(idMapping);
    
            EntityData input = new EntityData();
            input.setName("Account");
            input.addValue("AccountName","test");
            input.setId("567");
            input.setSyncariEntityId("fabricated_syncari_id");
            input.setConnectorId("extConnectorId");
    
            GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                    .setTargetId("synEntId"));
    
            Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
            //Since we are not connecting cross-synapse objects, connected records should be empty
            assertTrue(graphContext.getAllConnectedRecords().isEmpty());
            assertNotNull(attached);
            //No changes to incoming record. There was another record connected, so we treat this as a duplicate
            assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
            findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
            assertTrue(findByExternalId.isPresent());
            assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());
            //The other externalRecord also connected to the same id mapping
            assertTrue(findByExternalId.get().findMapping("extConnectorId2","extEntId2","700").isPresent());
        } finally {
        	MongoUtils.dropIndexes(customerMongoTemplate, "syncari_account", List.of(
                    new Index("name_case_insensitive_idx", false, false, "Name")
            ));
        }
    }

    @Test
    public void advancedAttach_existing_syncari_found_with_multiple_entity_records_equalsIgnoreCase(){
    	MongoUtils.createIndexes(customerMongoTemplate, "syncari_account", List.of(
                new Index("name_case_insensitive_idx", false, false, "Name")
        ));

        try {
            Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
            assertFalse(findByExternalId.isPresent());
            Optional<Connector> syncari = connectorRepo.findByName("syncari");
            when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());
    
            var ieq = Map.of(
                    "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                    "operator", "ieq",
                    "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
            );
    
            FunctionCall functionCall = getAttachFunctionCall(ieq);
            
            // First entity record.
            EntityData existing = new EntityData();
            existing.setName("Account");
            existing.setId("123");
            // For equalsIgnoreCase
            existing.addValue("Name", "TeSt");
            EntityData saved = entityRepo.save(existing);
            IdMapping idMapping = new IdMapping();
            idMapping.setEntityName("account");
            idMapping.setSyncariId(saved.getSyncariEntityId());
            //same connector & entity, but connected to another record
            idMapping.addMapping("extConnectorId2","700","extEntId2");
            mappingRepo.save(idMapping);

            // Second entity record. DOES not participate in the attach record even though the name matches.
            EntityData existing2 = new EntityData();
            existing2.setName("Account");
            existing2.setId("234");
            // For equalsIgnoreCase
            existing2.addValue("Name", "test");
            EntityData saved2 = entityRepo.save(existing2);
            IdMapping idMapping2 = new IdMapping();
            idMapping2.setEntityName("account");
            idMapping2.setSyncariId(saved2.getSyncariEntityId());
            //same connector & entity, but connected to another record
            idMapping2.addMapping("extConnectorId2","700","extEntId2_2");
            mappingRepo.save(idMapping2);
    
            // The incoming input record
            EntityData input = new EntityData();
            input.setName("Account");
            input.addValue("AccountName","test");
            input.setId("567");
            input.setSyncariEntityId("fabricated_syncari_id");
            input.setConnectorId("extConnectorId");
    
            GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                    .setTargetId("synEntId"));
    
            Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
            //Since we are not connecting cross-synapse objects, connected records should be empty
            assertTrue(graphContext.getAllConnectedRecords().isEmpty());
            assertNotNull(attached);
            //No changes to incoming record. There was another record connected, so we treat this as a duplicate
            assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
            findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
            assertTrue(findByExternalId.isPresent());
            assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());
            //The other externalRecord also connected to the same id mapping
            assertTrue(findByExternalId.get().findMapping("extConnectorId2","extEntId2","700").isPresent());
        } finally {
        	MongoUtils.dropIndexes(customerMongoTemplate, "syncari_account", List.of(
                    new Index("name_case_insensitive_idx", false, false, "Name")
            ));
        }
    }

    @Test
    public void advancedAttach_move_id_mapping_behavior(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");
        existing.addValue("Name", "test");

        EntityData saved = entityRepo.save(existing);
        IdMapping idMapping = new IdMapping();
        idMapping.setEntityName("account");
        idMapping.setSyncariId(saved.getSyncariEntityId());
        //same connector & entity, but connected to another record
        idMapping.addMapping("extConnectorId2","700","extEntId2");
        mappingRepo.save(idMapping);

        EntityData existingFromAccount = entityRepo.save(new EntityData().setName("Account").setId(ObjectId.get().toHexString()).addValue("Name", "test"));

        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setSyncariEntityId(existingFromAccount.getId());
        input.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId("synEntId"));
        IdMapping idMappingForInput = new IdMapping();
        idMappingForInput.setEntityName("account");
        idMappingForInput.setSyncariId(existingFromAccount.getSyncariEntityId());
        //same connector & entity, but connected to another record
        idMappingForInput.addMapping("extConnectorId","567","extEntId");
        mappingRepo.save(idMappingForInput);

        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //Incoming record has a change. There was another record connected, so we treat this as a duplicate
        assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
        assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());
        //The other externalRecord also connected to the same id mapping
        assertTrue(findByExternalId.get().findMapping("extConnectorId2","extEntId2","700").isPresent());
        //old mapping was empty and is deleted
        assertTrue(mappingRepo.findBySyncariId("account",existingFromAccount.getId()).isEmpty());
        //old record is deleted as well.
        assertTrue(entityRepo.findByIdsIn("account",List.of(existingFromAccount.getId())).isEmpty());

    }

    @Test
    public void advancedAttach_move_id_mapping_does_not_delete_non_empty_id_mappings(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());
        SchemaService mockSchemaService = mock(SchemaService.class);
        lookUpFunctions.schemaService = mockSchemaService;

        EntityDefinition externalId1 = new EntityDefinition().setApiName("extEntId");
        externalId1.setId("extEntId");
        EntityDefinition externalId2 = new EntityDefinition().setApiName("extEntId2");
        externalId2.setId("extEntId2");
        EntityDefinition externalId3 = new EntityDefinition().setApiName("extEntId3");
        externalId3.setId("extEntId3");

        EntityDefinition syncariEntity = new EntityDefinition().setApiName("account");
        syncariEntity.setId(ObjectId.get().toHexString());
        syncariEntity.addField(new AttributeDefinition().setApiName("Name").setDataType(StringType.VALUE));
        syncariEntity.addField(new AttributeDefinition().setApiName("syncari_id_1").setDataType(ExternalIdType.VALUE).setReferenceTo(externalId1.getId()));
        syncariEntity.addField(new AttributeDefinition().setApiName("syncari_id_2").setDataType(ExternalIdType.VALUE).setReferenceTo(externalId2.getId()));
        syncariEntity.addField(new AttributeDefinition().setApiName("syncari_id_3").setDataType(ExternalIdType.VALUE).setReferenceTo(externalId3.getId()));
        when(mockSchemaService.getEntity(syncariEntity.getId())).thenReturn(syncariEntity);

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "Name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");
        existing.addValue("Name", "test");

        EntityData saved = entityRepo.save(existing);
        IdMapping idMapping = new IdMapping();
        idMapping.setEntityName("account");
        idMapping.setSyncariId(saved.getSyncariEntityId());
        //same connector & entity, but connected to another record
        idMapping.addMapping("extConnectorId2","700","extEntId2");
        mappingRepo.save(idMapping);
        EntityData existingFromAccount = entityRepo.save(new EntityData().setName("Account").setId(ObjectId.get().toHexString()).addValue("Name", "test"));

        when(mockSchemaService.getEntity(syncariEntity.getId())).thenReturn(syncariEntity);

        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setSyncariEntityId(existingFromAccount.getId());
        input.setConnectorId("extConnectorId");
        when(mockSchemaService.getEntity("extConnectorId", "Account")).thenReturn(externalId1);

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId(syncariEntity.getId()));
        IdMapping idMappingForInput = new IdMapping();
        idMappingForInput.setEntityName("account");
        idMappingForInput.setSyncariId(existingFromAccount.getSyncariEntityId());
        //This record is connected to one more synapse, in addition to the incooming one
        idMappingForInput.addMapping("extConnectorId","567","extEntId");
        idMappingForInput.addMapping("c","20000","extEntId3");
        mappingRepo.save(idMappingForInput);

        entityRepoService.connectExternalId(syncariEntity, existingFromAccount, externalId1.getId(), Optional.empty(), "567");
        entityRepo.updateValues(syncariEntity, List.of(existingFromAccount));
        entityRepoService.connectExternalId(syncariEntity, existingFromAccount, externalId2.getId(), Optional.empty(), "700");
        entityRepo.updateValues(syncariEntity, List.of(existingFromAccount));
        entityRepoService.connectExternalId(syncariEntity, existingFromAccount, externalId3.getId(), Optional.empty(), "20000");
        entityRepo.updateValues(syncariEntity, List.of(existingFromAccount));

        assertEquals("567", entityRepo.findById(syncariEntity, existingFromAccount.getId()).get().getValue("syncari_id_1"));
        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //Incoming record has a change. There was another record connected, so we treat this as a duplicate
        assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
        assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());
        //The other externalRecord also connected to the same id mapping
        assertTrue(findByExternalId.get().findMapping("extConnectorId2","extEntId2","700").isPresent());
        //old mapping was not empty , so it is retained
        Optional<IdMapping> oldIdMapping = mappingRepo.findBySyncariId("account", existingFromAccount.getId());
        assertTrue(oldIdMapping.isPresent());
        assertTrue(oldIdMapping.get().isMapped("extEntId3"));
        assertFalse(oldIdMapping.get().isMapped("extEntId"));

        //old record is connected to synapse3, so its retained as well.
        assertEquals(1,entityRepo.findByIdsIn("account",List.of(existingFromAccount.getId())).size());
        assertNull(entityRepo.findById(syncariEntity, existingFromAccount.getId()).get().getValue("syncari_id_1"));
        lookUpFunctions.schemaService = schemaService;
    }
    @Test
    public void advancedAttach_ignores_empty_valued_matches(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");

        EntityData saved = entityRepo.save(existing);
        IdMapping idMapping = new IdMapping();
        idMapping.setEntityName("account");
        idMapping.setSyncariId(saved.getSyncariEntityId());
        //same connector & entity, but connected to another record
        idMapping.addMapping("extConnectorId2","700","extEntId2");
        mappingRepo.save(idMapping);

        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("567");
        input.setSyncariEntityId("fabricated_syncari_id");
        input.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //No changes to incoming record. The match is null
        assertEquals("fabricated_syncari_id",((EntityData)attached).getSyncariEntityId());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertFalse(findByExternalId.isPresent());
        //The other externalRecord is still there, with one mapping
        Optional<IdMapping> otherMapping = mappingRepo.findByExternalId("account", "extConnectorId2", "extEntId2", "700");
        assertTrue(otherMapping.get().findMapping("extConnectorId2","extEntId2","700").isPresent());
        //But is not connected to the incoming record
        assertFalse(otherMapping.get().findMapping("extConnectorId","extEntId","567").isPresent());

    }
    @Test
    public void advancedAttach_existing_syncari_not_found_with_multiple_synapse_records(){
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData input1 = new EntityData();
        input1.setName("Account");
        input1.addValue("AccountName","test");
        input1.setId("567");
        input1.setSyncariEntityId("fabricated_syncari_id1");
        input1.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input1).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input1, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //No changes to incoming record. There was another record connected, so we treat this as a duplicate
        assertEquals("fabricated_syncari_id1",((EntityData)attached).getSyncariEntityId());

        var eqSynapse2 = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.NameOfAccount}}")
        );
        FunctionCall functionCall2 = getAttachFunctionCall(eqSynapse2);
        EntityData input2 = new EntityData();
        input2.setName("Account2");
        input2.addValue("NameOfAccount","test");
        input2.setId("700");
        input2.setSyncariEntityId("fabricated_syncari_id2");
        input2.setConnectorId("extConnectorId2");
        graphContext.set("previous", input2);
        Object attached2= lookUpFunctions.advancedAttachRecord(input2, functionCall2, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached2);
        //This record has the same matching criteria. So this should be pointing to input1's syncari id
        assertEquals("fabricated_syncari_id1",((EntityData)attached2).getSyncariEntityId());

    }

    @Test
    public void advancedAttach_existing_syncari_not_found_with_dupe_records(){
        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData input1 = new EntityData();
        input1.setName("Account");
        input1.addValue("AccountName","test");
        input1.setId("567");
        input1.setSyncariEntityId("fabricated_syncari_id1");
        input1.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input1).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input1, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //No changes to incoming record. There was another record connected, so we treat this as a duplicate
        assertEquals("fabricated_syncari_id1",((EntityData)attached).getSyncariEntityId());

        var eqSynapse2 = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );
        FunctionCall functionCall2 = getAttachFunctionCall(eqSynapse2);
        EntityData input2 = new EntityData();
        input2.setName("Account");
        input2.addValue("AccountName","test");
        input2.setId("700");
        input2.setSyncariEntityId("fabricated_syncari_id2");
        input2.setConnectorId("extConnectorId");
        graphContext.set("previous", input2);
        Object attached2= lookUpFunctions.advancedAttachRecord(input2, functionCall2, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached2);
        //This record has the same matching criteria, but from same synapse/entitydef. So this should NOT be pointing to input1's syncari id
        assertEquals("fabricated_syncari_id2",((EntityData)attached2).getSyncariEntityId());

    }
    @Test
    public void advancedAttach_incoming_record_connected_to_another_syncari_record(){
        IdMapping idMapping = new IdMapping();
        idMapping.setEntityName("account");
        idMapping.setSyncariId(ObjectId.get().toHexString());
        //External Record connected to a syncari record
        idMapping.addMapping("extConnectorId","567","extEntId");
        IdMapping previousIdMapping = mappingRepo.save(idMapping);


        Optional<Connector> syncari = connectorRepo.findByName("syncari");
        when(mockConnectorService.getSyncariConnector()).thenReturn(syncari.get());

        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );

        FunctionCall functionCall = getAttachFunctionCall(eq);

        EntityData existing = new EntityData();
        existing.setName("Account");
        existing.setId("123");
        existing.addValue("Name", "test");

        EntityData saved = entityRepo.save(existing);
        IdMapping idMappingForSaved = new IdMapping();
        idMappingForSaved.setEntityName("account");
        idMappingForSaved.setSyncariId(saved.getSyncariEntityId());
        idMappingForSaved.addMapping("someOtherCOnnector","someOtherEntittId","someOtherentity");
        mappingRepo.save(idMappingForSaved);

        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("AccountName","test");
        input.setId("567");
        input.setSyncariEntityId("fabricated_syncari_id");
        input.setConnectorId("extConnectorId");

        GraphContext graphContext = new GraphContext().set("previous", input).setGraph(new MappingGraph()
                .setTargetId("synEntId"));

        Object attached = lookUpFunctions.advancedAttachRecord(input, functionCall, graphContext);
        //Since we are not connecting cross-synapse objects, connected records should be empty
        assertTrue(graphContext.getAllConnectedRecords().isEmpty());
        assertNotNull(attached);
        //incoming record connected to the mathcing syncari record
        assertEquals(saved.getSyncariEntityId(),((EntityData)attached).getSyncariEntityId());
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
        assertEquals(saved.getSyncariEntityId(),findByExternalId.get().getSyncariId());

        //old id mapping is removed
        assertTrue(mappingRepo.findById(previousIdMapping.getId()).isEmpty());
    }

    @Test
    public void attachExistingSyncariNotFound(){
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("Account", "123", "extEntId","");
        assertFalse(findByExternalId.isPresent());
        GraphContext graphContext = new GraphContext();
        FunctionCall functionCall = getAttachFunctionCall();
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("123");
        input.setConnectorId("234");
        Object attached = lookUpFunctions.attachRecord(input , functionCall, graphContext);
        assertNotNull(attached);
        findByExternalId = mappingRepo.findByExternalId("Account", "123", "extEntId","");
        assertFalse(findByExternalId.isPresent());
    }

    @Test
    public void attachExistingSyncariNotFoundFOrExistingIntegration(){
        lookUpFunctions.stagedBatchRecordRepo.save(new StagedBatchRecord().setExternalEntityDefinitionId("extEnt2").setExternalRecordId("extEntitiy2Value").setStagedBatchId("stagedBatchId1"));
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "connector2", "extEnt2","extEntitiy2Value");
        assertFalse(findByExternalId.isPresent());
        GraphContext graphContext = new GraphContext();
        graphContext.set("field_refToExtEntity2","extEntitiy2Value");
        FunctionCall functionCall =  createCall("syncariEntityDefId", "synEntId", "searchFieldId", "name","inputFieldId", "refToExtEntity2", "externalEntityDefId","extEnt2");
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("123");
        input.setSyncariEntityId("some");
        input.addValue("refToExtEntity2","extEntitiy2Value");
        input.setConnectorId("extConnectorId");
        Object attached = lookUpFunctions.attachRecord(input , functionCall, graphContext);
        assertNotNull(attached);
        findByExternalId = mappingRepo.findByExternalId("account", "connector2", "extEnt2","extEntitiy2Value");
        assertTrue(findByExternalId.isPresent());
    }

    @Test
    public void attachRecord_LooksUp_External_Record_If_Absent(){
        Connector connector2 = new Connector("connector2");
        connector2.setMetadata(new ConnectorMetadata("meta1"));
        when(mockConnectorService.find("connector2")).thenReturn(Optional.of(connector2));
        DataService mockDataService = mock(DataService.class);
        when(mockDataService.getByIds(any())).thenReturn(List.of(new EntityData().setName("account").setConnectorId("connector2").setId("extEntitiy2Value").setName("extEnt2")));
        when(dataServiceFactory.getDataService(any())).thenReturn(mockDataService);
        assertTrue(lookUpFunctions.stagedBatchRecordRepo.findFirstByExternalEntityDefinitionIdAndExternalRecordId("extEnt2","extEntitiy2Value").isEmpty());
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "connector2", "extEnt2","extEntitiy2Value");
        assertFalse(findByExternalId.isPresent());
        GraphContext graphContext = new GraphContext();
        graphContext.set("field_refToExtEntity2","extEntitiy2Value");

        FunctionCall functionCall =  createCall("syncariEntityDefId", "synEntId", "searchFieldId", "name","inputFieldId", "refToExtEntity2", "externalEntityDefId","extEnt2");
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("123");
        input.setSyncariEntityId("some");
        input.addValue("refToExtEntity2","extEntitiy2Value");
        input.setConnectorId("extConnectorId");
        graphContext.setStagedBatchRecord(new StagedBatchRecord().setStagedBatchId("stagedBatchId").setExternalEntityDefinitionId("ent1").setSyncariId("some").setExternalRecordId("123").setEntityData(input));
        Object attached = lookUpFunctions.attachRecord(input , functionCall, graphContext);
        assertNotNull(attached);
        findByExternalId = mappingRepo.findByExternalId("account", "connector2", "extEnt2","extEntitiy2Value");
        assertTrue(findByExternalId.isPresent());
        Map<EntityDefinition, List<EntityData>> allConnectedRecords = graphContext.getAllConnectedRecords();
        assertEquals(1,allConnectedRecords.size());
        EntityData externalRecord = allConnectedRecords.entrySet().stream().findFirst().get().getValue().get(0);
        assertEquals("extEntitiy2Value", externalRecord.getId());
        assertEquals("some", externalRecord.getSyncariEntityId());
    }

    @Test
    public void attachMultipleRecordsToSameIdMapping(){
        lookUpFunctions.stagedBatchRecordRepo.save(new StagedBatchRecord().setExternalEntityDefinitionId("extEnt2").setExternalRecordId("v").setStagedBatchId("stagedBatchId1"));
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "connector2", "extEnt2","extEntitiy2Value");
        assertFalse(findByExternalId.isPresent());
        GraphContext graphContext = new GraphContext();
        graphContext.set("field_refToExtEntity2","extEntitiy2Value");
        FunctionCall functionCall =  createCall("syncariEntityDefId", "synEntId", "searchFieldId", "name","inputFieldId", "refToExtEntity2", "externalEntityDefId","extEnt2");
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("123");
        input.setSyncariEntityId("some");
        input.addValue("refToExtEntity2","extEntitiy2Value");
        input.setConnectorId("extConnectorId");
        Object attach1 = lookUpFunctions.attachRecord(input , functionCall, graphContext);
        assertNotNull(attach1);


        input.setName("Account");
        input.setId("234");
        input.setSyncariEntityId("some2");
        input.addValue("refToExtEntity2","extEntitiy2Value");
        input.setConnectorId("extConnectorId");
        Object attach2 = lookUpFunctions.attachRecord(input , functionCall, graphContext);

        assertEquals("some", ((EntityData)attach2).getSyncariEntityId());
    }

    @Test
    public void attachMultipleRecordsToSameIdMappingWithNonIdField(){
        lookUpFunctions.schemaService = mock(SchemaService.class);
        EntityDefinition externalEntity = new EntityDefinition().setApiName("Account");
        externalEntity.setId("extEnt2");
        AttributeDefinition externalName = new AttributeDefinition().setApiName("Name").setDataType(StringType.VALUE);
        externalName.setId(ObjectId.get().toHexString());
        externalEntity.addField(externalName);
        externalEntity.setConnectorId("connector2");
        EntityDefinition syncariEntity = new EntityDefinition().setApiName("account");
        syncariEntity.setId("synEntId");
        AttributeDefinition name = new AttributeDefinition().setApiName("Name").setDataType(StringType.VALUE);
        name.setId(ObjectId.get().toHexString());
        syncariEntity.addField(name);
        when(lookUpFunctions.schemaService.getEntity("extEnt2")).thenReturn(externalEntity);
        when(lookUpFunctions.schemaService.getEntity("synEntId")).thenReturn(syncariEntity);
        GraphContext graphContext = new GraphContext();
        graphContext.set("field_"+externalName.getId(),"Some Account Name");
        FunctionCall functionCall =  createCall("syncariEntityDefId", "synEntId", "searchFieldId", name.getId(),"inputFieldId", externalName.getId(), "externalEntityDefId","extEnt2");
        EntityData existingObject = new EntityData();
        existingObject.setName("Account");
        existingObject.addValue("Name","Some Account Name");
        existingObject.setConnectorId("syncari");
        EntityData saved = entityRepo.save(existingObject);
        mappingRepo.save(new IdMapping().setEntityName("account").setSyncariId(saved.getSyncariEntityId()).addMapping("existingConnector","existingExternalId1","existingEntityDef"));
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "connector2", "extEnt2","123");
        assertFalse(findByExternalId.isPresent());

        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("123");
        input.setSyncariEntityId("someOther");
        input.addValue("Name","Some Account Name");
        input.setConnectorId("connector2");
        Object attach1 = lookUpFunctions.attachRecord(input , functionCall, graphContext);
        assertNotNull(attach1);

        assertEquals(saved.getSyncariEntityId(), ((EntityData)attach1).getSyncariEntityId());
        Optional<IdMapping> newIdMapping = mappingRepo.findByExternalId("account", "connector2", "extEnt2","123");
        assertTrue(newIdMapping.isPresent());
        assertEquals("123",newIdMapping.get().getMapping("connector2","extEnt2").get().getEntityId());
        lookUpFunctions.schemaService = schemaService;
    }

    @Test
    public void existingIdMappingUpdatedWhenIntegrationsPresent(){
        lookUpFunctions.stagedBatchRecordRepo.save(new StagedBatchRecord().setExternalEntityDefinitionId("extEnt2").setExternalRecordId("extEntitiy2Value").setStagedBatchId("stagedBatchId1"));
        mappingRepo.save(new IdMapping().setEntityName("account").setSyncariId("some").addMapping("extConnectorId","123","extEntId"));
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","123");
        assertEquals(1,findByExternalId.get().getMappings().size());
        GraphContext graphContext = new GraphContext();
        graphContext.set("field_refTExtEntity2","extEntitiy2Value");
        FunctionCall functionCall =  createCall("syncariEntityDefId", "synEntId", "searchFieldId", "name","inputFieldId", "refTExtEntity2", "externalEntityDefId","extEnt2");
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("123");
        input.setSyncariEntityId("some");
        input.addValue("refTExtEntity2","extEntitiy2Value");
        input.setConnectorId("234");
        Object attached = lookUpFunctions.attachRecord(input , functionCall, graphContext);
        assertNotNull(attached);
        findByExternalId = mappingRepo.findByExternalId("account", "connector2", "extEnt2","extEntitiy2Value");
        assertTrue(findByExternalId.isPresent());
        assertEquals(2,findByExternalId.get().getMappings().size());
    }

    @Test
    public void attachExistingSyncariFoundAndMappingFound(){
        lookUpFunctions.stagedBatchRecordRepo.save(new StagedBatchRecord().setExternalEntityDefinitionId("extEntId").setExternalRecordId("567").setStagedBatchId("stagedBatchId1"));
        EntityData input = new EntityData();
        input.setName("Account");
        input.addValue("Name", "test");
        EntityData saved = entityRepo.save(input);
        IdMapping syncariId = new IdMapping();
        syncariId.setSyncariId(saved.getId());
        syncariId.setEntityName("account");
        mappingRepo.save(syncariId);
        assertEquals(1, mappingRepo.findAll().size());
        Optional<IdMapping> findByExternalId = mappingRepo.findByExternalId("Account", "extConnectorId","extEntId", "567");
        assertFalse(findByExternalId.isPresent());
        GraphContext graphContext = new GraphContext().set("field_name", "test");
        FunctionCall functionCall = getAttachFunctionCall();
        
        input.setId("567");
        input.setSyncariEntityId(null);
        input.setConnectorId("extConnectorId");
        
        Object attached = lookUpFunctions.attachRecord(input, functionCall, graphContext);
        assertNotNull(attached);
        //No new idMappingRecords added
        assertEquals(1, mappingRepo.findAll().size());
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());

        //Repeated attach does not create duplicates
        assertEquals(1, mappingRepo.findAll().size());
        attached = lookUpFunctions.attachRecord(input, functionCall, graphContext);
        assertNotNull(attached);
        findByExternalId = mappingRepo.findByExternalId("account", "extConnectorId", "extEntId","567");
        assertTrue(findByExternalId.isPresent());
    }

    @Test
    public void advanced_lookup_value_found_simple(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        input.setLastModified(System.currentTimeMillis());
        entityRepo.save(input);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);

        // assert empty predicates picks up one record instead of an NPE on empty predicates.
        FunctionCall fcNullPreds = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", null);
        FunctionResult lookedUpEmpty = lookUpFunctions.advancedLookUpSyncariRecord(input, fcNullPreds, graphContext);
        assertNotNull(lookedUpEmpty.getLookupResult());

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    
    @Test
    public void advanced_lookup_value_dontMatchBlank(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        input.setLastModified(System.currentTimeMillis());
        entityRepo.save(input);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
 
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "ne",
                "right", Map.of("type", "literal", "value", "")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);


        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq, "dontMatchBlank", true);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNull(lookedUp.getLookupResult());
        
        functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq, "dontMatchBlank", false);
        lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    
    @Test
    public void advanced_lookup_value_found_in_on_string(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        input.setLastModified(System.currentTimeMillis());
        entityRepo.save(input);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "in",
                "right", Map.of("type", "literal", "value", "This \"contains\" testlookup and should pass")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);

        // assert empty predicates picks up one record instead of an NPE on empty predicates.
        FunctionCall fcNullPreds = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", null);
        FunctionResult lookedUpEmpty = lookUpFunctions.advancedLookUpSyncariRecord(input, fcNullPreds, graphContext);
        assertNotNull(lookedUpEmpty.getLookupResult());

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void advanced_lookup_value_not_in_on_string(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        input.setLastModified(System.currentTimeMillis());
        entityRepo.save(input);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "not_in",
                "right", Map.of("type", "literal", "value", "This  does not \"contain\" what we are looking for")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);

        // assert empty predicates picks up one record instead of an NPE on empty predicates.
        FunctionCall fcNullPreds = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", null);
        FunctionResult lookedUpEmpty = lookUpFunctions.advancedLookUpSyncariRecord(input, fcNullPreds, graphContext);
        assertNotNull(lookedUpEmpty.getLookupResult());

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());

        var notIn = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "not_in",
                "right", Map.of("type", "literal", "value", "This   \"contains\" what we are looking for and that iss testlookup")
        );

        graphContext = new GraphContext();
        graphContext.put("previous", input);


        FunctionCall notInFunctionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", notIn);
        FunctionResult failed = lookUpFunctions.advancedLookUpSyncariRecord(input, notInFunctionCall, graphContext);
        assertNull(failed.getLookupResult());

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    @Test
    public void advanced_lookup_value_found_attributename_with_hiphen(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account").setId("1234").addValue("Full-Name", "testlookup").setLastModified(System.currentTimeMillis());
        entityRepo.save(input);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Full-Name")
                .setDataType(new StringType()).setDisplayName("Full - Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Full-Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);

        // assert empty predicates picks up one record instead of an NPE on empty predicates.
        FunctionCall fcNullPreds = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", null);
        FunctionResult lookedUpEmpty = lookUpFunctions.advancedLookUpSyncariRecord(input, fcNullPreds, graphContext);
        assertNotNull(lookedUpEmpty.getLookupResult());

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void advanced_lookup_value_sort(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        input.addValue("Address", "address1");
        input.addValue("LastAssigned", ZonedDateTime.now().minusDays(1));
        entityRepo.save(input);
        EntityData secondOne = new EntityData();
        secondOne.setName("Account");
        secondOne.setId("1235");
        secondOne.addValue("Name", "testlookup");
        secondOne.addValue("Address", "address2");
        secondOne.addValue("LastAssigned", ZonedDateTime.now());
        entityRepo.save(secondOne);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition lastAssigned = new AttributeDefinition().setApiName("LastAssigned")
                .setDataType(new DatetimeType()).setDisplayName("LastAssigned").setEntityId(sourceEntity.getId());
        lastAssigned.setDraftStatus(DraftStatus.APPROVED);
        lastAssigned.setStatus(Status.ACTIVE);
        lastAssigned = attributeProxyRepo.save(lastAssigned);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        var sortFields=List.of(Map.of("sortDirection",Map.of("name","sortDirection","value","desc"),"sortField",
                Map.of("name","sortField","value",lastAssigned.getId())));
        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq, "sortFields",sortFields,"count",true);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        assertEquals("address2",((EntityData)lookedUp.getLookupResult()).getValueAsString("Address"));

        assertEquals(Long.valueOf(2l),lookedUp.getLookupCount());

        var sortFields2=List.of(Map.of("sortDirection",Map.of("name","sortDirection","value","asc"),"sortField",
                Map.of("name","sortField","value","Address")));
        FunctionCall functionCall2 = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq, "sortFields",sortFields2);
        FunctionResult lookedUp2 = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall2, graphContext);
        assertNotNull(lookedUp2.getLookupResult());
        //no counts by default
        assertNull(lookedUp2.getLookupCount());
        assertEquals("address1",((EntityData)lookedUp2.getLookupResult()).getValueAsString("Address"));

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    @Test
    public void advanced_lookup_value_default_sort(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.setLastModified(System.currentTimeMillis());
        input.addValue("Name", "testlookup");
        input.addValue("Address", "address1");
        entityRepo.save(input);
        EntityData secondOne = new EntityData();
        secondOne.setName("Account");
        secondOne.setId("1235");
        secondOne.addValue("Name", "testlookup");
        secondOne.addValue("Address", "address2");
        secondOne.setLastModified(System.currentTimeMillis()-1000);
        entityRepo.save(secondOne);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"count","true");
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        assertEquals(Long.valueOf(2l), lookedUp.getLookupCount());
        assertEquals("address1",((EntityData)lookedUp.getLookupResult()).getValueAsString("Address"));

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    
    @Test
    public void advanced_lookup_contains(){
        entityRepo.deleteAll("account");
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        entityRepo.save(input);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var contains = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", name.getId()),
                "operator", "contains",
                "right", Map.of("type", "literal", "value", "look")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        FunctionCall functionCallContains = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", contains);
        FunctionResult lookedUpContains = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCallContains, graphContext);
        assertNotNull(lookedUpContains.getLookupResult());

        var notContains = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", name.getId()),
                "operator", "not_contains",
                "right", Map.of("type", "literal", "value", "look")
        );

        FunctionCall functionCallNotContains = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", notContains);
        FunctionResult lookedUpNotContains = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCallNotContains, graphContext);
        assertNull(lookedUpNotContains.getLookupResult());

        var notContains2 = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", name.getId()),
                "operator", "not_contains",
                "right", Map.of("type", "literal", "value", "invalid")
        );

        functionCallNotContains = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", notContains2);
        lookedUpNotContains = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCallNotContains, graphContext);
        assertNotNull(lookedUpNotContains.getLookupResult());

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void advanced_lookup_in(){
        entityRepo.deleteAll("account");
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        final EntityData saved = entityRepo.save(input);
        Connector connector = new Connector();
        connector.setName("My Connector");
        connector.setId(ObjectId.get().toHexString());
        when(mockConnectorService.get(connector.getId())).thenReturn(connector);
        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setConnectorId(connector.getId());
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);

        mappingRepo.save(new IdMapping()
                .setSyncariId(saved.getSyncariEntityId())
                .setEntityName("Account")
                .addMapping(connector.getId(),"externalId123",sourceEntity.getId())
        );

        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var contains = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", name.getId()),
                "operator", "in",
                "right", Map.of("type", "literal", "value", List.of("look","up","testlookup"))
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        FunctionCall functionCallContains = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", contains);
        FunctionResult lookedUpContains = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCallContains, graphContext);
        assertNotNull(lookedUpContains.getLookupResult());
        EntityData record = (EntityData) lookedUpContains.getLookupResult();
        assertEquals("externalId123",record.getExternalIds().get("My_Connector").get("Account"));

        var notContains = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", name.getId()),
                "operator", "not_in",
                "right", Map.of("type", "literal", "value", List.of("look","up","testlookup"))
        );

        FunctionCall functionCallNotContains = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", notContains);
        FunctionResult lookedUpNotContains = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCallNotContains, graphContext);
        assertNull(lookedUpNotContains.getLookupResult());

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    @Test
    public void advanced_lookup_on_field_in(){
        entityRepo.deleteAll("account");
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        final EntityData saved = entityRepo.save(input);
        Connector connector = new Connector();
        connector.setName("My Connector");
        connector.setId(ObjectId.get().toHexString());
        when(mockConnectorService.get(connector.getId())).thenReturn(connector);
        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setConnectorId(connector.getId());
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);

        mappingRepo.save(new IdMapping()
                .setSyncariId(saved.getSyncariEntityId())
                .setEntityName("Account")
                .addMapping(connector.getId(),"externalId123",sourceEntity.getId())
        );

        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var contains = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", name.getId()),
                "operator", "in",
                "right", Map.of("type", "literal", "value", List.of("look","up","testlookup"))
        );
        GraphContext graphContext = new GraphContext();
        graphContext.setCurrentNode(new MappingNode().setName("some lookup function"));
        graphContext.put("previous", input);
        FunctionCall functionCallContains = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", contains,"findAll","true");
        FunctionResult lookedUpContains = lookUpFunctions.advancedLookUpSyncariRecordOnField(input, functionCallContains, graphContext);
        assertNotNull(lookedUpContains.getLookupResult());
        EntityData record = (EntityData) lookedUpContains.getLookupResult();
        assertEquals("externalId123",record.getExternalIds().get("My_Connector").get("Account"));

        EntityData input2 = new EntityData();
        input2.setName("Account");
        input2.setId(ObjectId.get().toHexString());
        input2.addValue("Name", "testlookup");
        final EntityData saved2 = entityRepo.save(input2);

        mappingRepo.save(new IdMapping()
                .setSyncariId(saved2.getSyncariEntityId())
                .setEntityName("Account")
                .addMapping(connector.getId(),"externalId1234",sourceEntity.getId())
        );

        FunctionResult lookedUpAll = lookUpFunctions.advancedLookUpSyncariRecordOnField(input, functionCallContains, graphContext);
        assertNotNull(lookedUpAll.getLookupResult());
        EntityData record1 = (EntityData) lookedUpAll.getLookupResult();
        assertEquals("externalId123",record.getExternalIds().get("My_Connector").get("Account"));
        List<EntityData> records = (List<EntityData>) graphContext.get("allPreviousLookupRecords");

        assertEquals(2, records.size());
        assertEquals("externalId123",records.get(0).getExternalIds().get("My_Connector").get("Account"));
        assertEquals("externalId1234",records.get(1).getExternalIds().get("My_Connector").get("Account"));
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void advanced_lookup_excludes_deleted(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        entityRepo.save(input);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        input.setDeleted(true);
        entityRepo.save(input);
        assertNull(lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext).getLookupResult());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    @Test
    public void advanced_lookup_value_found_by_id(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        EntityData saved = entityRepo.save(input);
        input.addValue("myId", saved.getSyncariEntityId());
        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition id = new AttributeDefinition().setApiName("myId")
                .setDataType(new IdType()).setDisplayName("Id").setEntityId(sourceEntity.getId())
                .setIdField(true).setStatus(Status.ACTIVE);
        id.setDraftStatus(DraftStatus.APPROVED);
        id = attributeProxyRepo.save(id);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", id.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.myId}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(id);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void advanced_lookup_value_found_complex(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        input.addValue("Website", "www.testlookup.com");
        input.addValue("Age", 10);
        entityRepo.save(input);
        
        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website = attributeProxyRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attributeProxyRepo.save(age);
        
        var eq = List.of(
                Map.of(
                    "left", Map.of("datatype", "string", "type", "variable", "value", name.getId()),
                    "operator", "eq",
                    "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
                        ),
                Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value", website.getId()),
                        "operator", "contains",
                        "right", Map.of("type", "literal", "value", "{{previous.values.Website}}")
                        ),
                Map.of(
                        "left", Map.of("datatype", "double", "type", "variable", "value", age.getId()),
                        "operator", "lt",
                        "right", Map.of("type", "literal", "value", "50")
                        ));
        var predicateMap = Map.of("predicates", eq, "operator", "AND");
        
        GraphContext graphContext = new GraphContext().set("previous", input);
        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", predicateMap);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNotNull(lookedUp.getLookupResult());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        attributeProxyRepo.delete(website);
        attributeProxyRepo.delete(age);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    

    @Test
    public void advanced_lookup_value_not_found(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1234");
        input.addValue("Name", "testlookup");
        entityRepo.save(input);
        
        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name = attributeProxyRepo.save(name);
        
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "notfound")
        );
        
        GraphContext graphContext = new GraphContext().set("previous", input);
        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq);
        FunctionResult lookedUp = lookUpFunctions.advancedLookUpSyncariRecord(input, functionCall, graphContext);
        assertNull(lookedUp.getLookupResult());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_values(){

        Instant now = Instant.now();
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        String id1 = ObjectId.get().toHexString();
        String id2 = ObjectId.get().toHexString();
        String id3 = ObjectId.get().toHexString();
        EntityData record1 = new EntityData().setName("Account").setId(id1).setSyncariEntityId(id1).addValue("Name", "testlookup").addValue("Address","address1");
        EntityData record2 = new EntityData().setName("Account").setId(id2).setSyncariEntityId(id2).addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId(id3).setSyncariEntityId(id3).addValue("Name", "testlookup-nomatch").addValue("Address","address3");
        EntityData noChanges = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","newAddress").addValue("SomeField","HardCoded");
        entityRepo.saveAll(List.of(record1,record2,record3, noChanges));

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(2, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        assertEquals("newAddress", records.getContent().get(0).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(0).getValue("SomeField"));
        assertEquals("newAddress", records.getContent().get(1).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(1).getValue("SomeField"));
        assertEquals("address3", records.getContent().get(2).getValue("Address"));
        assertFalse(records.getContent().get(2).has("SomeField"));
        //unchanged record with same valuess
        assertEquals("newAddress", records.getContent().get(3).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(3).getValue("SomeField"));
        Page<TransactionLog> transactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 3 records matched
        assertEquals(2, transactions.getNumberOfElements());
        assertEquals(record1.getId(), transactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress", transactions.getContent().get(0).getChanges().get(address.getId()).getNewValue());
        assertEquals("address1", transactions.getContent().get(0).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(0).getChanges().get(newField.getId()).getNewValue());
        assertEquals(syncarEntityId, transactions.getContent().get(0).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(0).getSources().get(0).getConnectorName());

        assertEquals(record2.getId(), transactions.getContent().get(1).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(1).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(1).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress", transactions.getContent().get(1).getChanges().get(address.getId()).getNewValue());
        assertEquals("address2", transactions.getContent().get(1).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(1).getChanges().get(newField.getId()).getNewValue());
        assertEquals(syncarEntityId, transactions.getContent().get(1).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(1).getSources().get(0).getConnectorName());
        //txnService.deleteAll();
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void testUpdateTxnLog(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");

        String id1 = ObjectId.get().toHexString();
        String id2 = ObjectId.get().toHexString();
        String id3 = ObjectId.get().toHexString();

        Instant now = Instant.now();

        EntityData record1 = new EntityData().setName("Account").setId(id1).setSyncariEntityId(id1).addValue("Name", "testlookup").addValue("Address","address1");
        EntityData record2 = new EntityData().setName("Account").setId(id2).setSyncariEntityId(id2).addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId(id3).setSyncariEntityId(id3).addValue("Name", "testlookup-nomatch").addValue("Address","address3");
        EntityData noChanges = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","newAddress").addValue("SomeField","HardCoded");
        entityRepo.saveAll(List.of(record1,record2,record3, noChanges));

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(2, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        assertEquals("newAddress", records.getContent().get(0).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(0).getValue("SomeField"));
        assertEquals("newAddress", records.getContent().get(1).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(1).getValue("SomeField"));
        assertEquals("address3", records.getContent().get(2).getValue("Address"));
        assertFalse(records.getContent().get(2).has("SomeField"));
        //unchanged record with same valuess
        assertEquals("newAddress", records.getContent().get(3).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(3).getValue("SomeField"));
        Page<TransactionLog> transactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 3 records matched
        assertEquals(2, transactions.getNumberOfElements());
        assertEquals(record1.getId(), transactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress", transactions.getContent().get(0).getChanges().get(address.getId()).getNewValue());
        assertEquals("address1", transactions.getContent().get(0).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(0).getChanges().get(newField.getId()).getNewValue());
        assertEquals("", transactions.getContent().get(0).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(0).getSources().get(0).getConnectorName());

        assertEquals(record2.getId(), transactions.getContent().get(1).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(1).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(1).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress", transactions.getContent().get(1).getChanges().get(address.getId()).getNewValue());
        assertEquals("address2", transactions.getContent().get(1).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(1).getChanges().get(newField.getId()).getNewValue());
        assertEquals("", transactions.getContent().get(1).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(1).getSources().get(0).getConnectorName());

        assertEquals(records.getContent().get(0).getLastTransactionLogId(), transactions.getContent().get(0).getId());
        assertEquals(records.getContent().get(1).getLastTransactionLogId(), transactions.getContent().get(1).getId());

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }


    @Test
    public void updateValuesRejectEmpty(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("5");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        EntityData record1 = new EntityData().setName("Account").setId("1").addValue("Name", "testlookup").addValue("Address","address1");
        EntityData record2 = new EntityData().setName("Account").setId("2").addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId("3").addValue("Name", "testlookup-nomatch").addValue("Address","address3");
        EntityData noChanges = new EntityData().setName("Account").setId("4").addValue("Name", "testlookup").addValue("Address","newAddress").addValue("SomeField","HardCoded");
        entityRepo.saveAll(List.of(record1,record2,record3, noChanges));

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);

        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.MailingAddress}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields, "rejectEmpty", true);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(2, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        var recordList = records.getContent();
        assertEquals("address1", recordList.get(0).getValue("Address"));
        assertEquals("address2", recordList.get(1).getValue("Address"));
        assertEquals("newAddress", recordList.get(3).getValue("Address"));

        functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields, "rejectEmpty", false);
        output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(3, graphContext.get("Account_recordsUpdated"));
        records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        recordList = records.getContent();
        assertNull(recordList.get(0).getValue("Address"));
        assertNull(recordList.get(1).getValue("Address"));
        assertNull(recordList.get(3).getValue("Address"));

        //txnService.deleteAll();
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void insertRecord(){
        lookUpFunctions.schemaService = schemaService;
        Instant now = Instant.now();
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);

        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);
        graphContext.setCurrentNode(new MappingNode().setName("Create Record"));
        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"))
        );

        List<EntityData> records = entityRepo.find("Account", now, Pageable.unpaged()).getContent();
        assertEquals(0, records.size());
        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "insertFields",updateFields);
        Object output = lookUpFunctions.insertRecord(input, functionCall, graphContext);
        assertEquals(input, output);
        assertNotNull(graphContext.get("Record from Create Record"));
        records = entityRepo.find("Account", now, Pageable.unpaged()).getContent();
        Page<TransactionLog> transactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 3 records matched
        assertEquals(1, transactions.getNumberOfElements());
        assertEquals(records.get(0).getId(), transactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.create, transactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress", transactions.getContent().get(0).getChanges().get(address.getId()).getNewValue());
        assertNull(transactions.getContent().get(0).getChanges().get(address.getId()).getOldValue());
        assertEquals(records.get(0).getLastTransactionLogId(), transactions.getContent().get(0).getId());
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_null_values(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1235");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        EntityData record1 = new EntityData().setName("Account").setId("1234").addValue("Name", "testlookup").addValue("Address","address1");
        EntityData record2 = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup-nomatch").addValue("Address","address3");
        EntityData noChanges = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","newAddress").addValue("SomeField","HardCoded");
        entityRepo.saveAll(List.of(record1,record2,record3, noChanges));

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        Map<String, Object> map1 = new HashMap();
        map1.put("name","newValue");
        map1.put("value",null);
        var updateFields = List.of(
                Map.of("updateField", Map.of("name", "updateField", "value", address.getId()), "newValue", map1),
                Map.of("updateField", Map.of("name", "updateField", "value", newField.getId()), "newValue", map1));

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(0, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        assertEquals("address1", records.getContent().get(0).getValue("Address"));
        assertEquals("address2", records.getContent().get(1).getValue("Address"));
        assertEquals("address3", records.getContent().get(2).getValue("Address"));
        assertEquals("newAddress", records.getContent().get(3).getValue("Address"));

        functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields,
                "rejectEmpty", false);
        
        output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(3, graphContext.get("Account_recordsUpdated"));
        records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        assertNull(records.getContent().get(0).getValue("Address"));
        assertNull(records.getContent().get(1).getValue("Address"));
        assertEquals("address3", records.getContent().get(2).getValue("Address"));
        assertNull(records.getContent().get(3).getValue("Address"));

        //txnService.deleteAll();
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    @Test
    public void update_values_with_current_record_tokens(){
        lookUpFunctions.schemaService = schemaService;
        Instant now = Instant.now();
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        String id1 = ObjectId.get().toHexString();
        String id2 = ObjectId.get().toHexString();
        String id3 = ObjectId.get().toHexString();
        EntityData record1 = new EntityData().setName("Account").setId(id1).setSyncariEntityId(id1).addValue("Name", "testlookup").addValue("Address","address1");
        EntityData record2 = new EntityData().setName("Account").setId(id2).setSyncariEntityId(id2).addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId(id3).setSyncariEntityId(id3).addValue("Name", "testlookup-nomatch").addValue("Address","address3");
        entityRepo.saveAll(List.of(record1,record2,record3));

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(2, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(3, records.getNumberOfElements());
        assertEquals("newAddress-address1", records.getContent().get(0).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(0).getValue("SomeField"));
        assertEquals("newAddress-address2", records.getContent().get(1).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(1).getValue("SomeField"));
        assertEquals("address3", records.getContent().get(2).getValue("Address"));
        assertFalse(records.getContent().get(2).has("SomeField"));
        Page<TransactionLog> transactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 3 records matched
        assertEquals(2, transactions.getNumberOfElements());
        assertEquals(record1.getId(), transactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress-address1", transactions.getContent().get(0).getChanges().get(address.getId()).getNewValue());
        assertEquals("address1", transactions.getContent().get(0).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(0).getChanges().get(newField.getId()).getNewValue());
        assertEquals(syncarEntityId, transactions.getContent().get(0).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(0).getSources().get(0).getConnectorName());


        assertEquals(record2.getId(), transactions.getContent().get(1).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(1).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(1).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress-address2", transactions.getContent().get(1).getChanges().get(address.getId()).getNewValue());
        assertEquals("address2", transactions.getContent().get(1).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(1).getChanges().get(newField.getId()).getNewValue());
        assertEquals(syncarEntityId, transactions.getContent().get(1).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(1).getSources().get(0).getConnectorName());

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_values_multivalued_field_operations(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        Instant now = Instant.now();

        String id1 = ObjectId.get().toHexString();
        String id2 = ObjectId.get().toHexString();
        String id3 = ObjectId.get().toHexString();
        EntityData record1 = new EntityData().setName("Account").setId(id1).setSyncariEntityId(id1).addValue("Name", "testlookup").addValue("Address","address1").addValue("SomeField",new ArrayList<>(List.of("v1","v2")));
        EntityData record2 = new EntityData().setName("Account").setId(id2).setSyncariEntityId(id2).addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId(id3).setSyncariEntityId(id3).addValue("Name", "testlookup-nomatch").addValue("Address","address3")
                .addValue("SomeField",new ArrayList<>(List.of("nochange1","nochange2")));;
        EntityData record4 = new EntityData().setName("Account").setId("1237").addValue("Name", "testlookup").addValue("Address","address4")
                .addValue("SomeField",new ArrayList<>(List.of("HardCoded")));
        entityRepo.saveAll(List.of(record1,record2,record3,record4));


        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId()).setMultiValueField(true);
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"),"operation",Map.of("name","operation","value","prepend"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(3  , graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        assertEquals("newAddress-address1", records.getContent().get(0).getValue("Address"));
        assertEquals(List.of("HardCoded","v1","v2"), records.getContent().get(0).getValue("SomeField"));
        assertEquals("newAddress-address2", records.getContent().get(1).getValue("Address"));
        assertEquals(List.of("HardCoded"), records.getContent().get(1).getValue("SomeField"));
        assertEquals("address3", records.getContent().get(2).getValue("Address"));
        //no changes to record #3
        assertEquals(List.of("nochange1","nochange2"), records.getContent().get(2).getValue("SomeField"));
        //No changes to record #4, even though it matches the update criteria, because we are adding a duplicate value
        assertEquals(List.of("HardCoded"), records.getContent().get(3).getValue("SomeField"));
        Page<TransactionLog> transactions = txnService.findAll(Pageable.unpaged(), now);
        assertEquals(3, transactions.getNumberOfElements());
        assertEquals(record1.getId(), transactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress-address1", transactions.getContent().get(0).getChanges().get(address.getId()).getNewValue());
        assertEquals("address1", transactions.getContent().get(0).getChanges().get(address.getId()).getOldValue());
        assertEquals(syncarEntityId, transactions.getContent().get(0).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(0).getSources().get(0).getConnectorName());

        System.out.println(transactions.getContent().get(0));
        assertEquals(List.of("v1","v2"), transactions.getContent().get(0).getChanges().get(newField.getId()).getOldValue());
        assertEquals(List.of("HardCoded","v1","v2"), transactions.getContent().get(0).getChanges().get(newField.getId()).getNewValue());

        assertEquals(record2.getId(), transactions.getContent().get(1).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(1).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(1).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress-address2", transactions.getContent().get(1).getChanges().get(address.getId()).getNewValue());
        assertEquals("address2", transactions.getContent().get(1).getChanges().get(address.getId()).getOldValue());
        assertNull(transactions.getContent().get(1).getChanges().get(newField.getId()).getOldValue());
        assertEquals(List.of("HardCoded"), transactions.getContent().get(1).getChanges().get(newField.getId()).getNewValue());
        assertEquals(syncarEntityId, transactions.getContent().get(1).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(1).getSources().get(0).getConnectorName());

        //test append
        var updateFieldsAppendToList=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCodedAppend"),"operation",Map.of("name","operation","value","append"))
        );
        //clear tx repo
        //txnService.deleteAll();
        now = Instant.now();
        FunctionCall functionCallForAppend = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFieldsAppendToList);
        Object appendOutput = lookUpFunctions.updateSyncariRecords(input, functionCallForAppend, graphContext);
        assertEquals(input, appendOutput);
        assertEquals(3, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> appendedRecords = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, appendedRecords.getNumberOfElements());
        assertEquals("newAddress-newAddress-address1", appendedRecords.getContent().get(0).getValue("Address"));
        assertEquals(List.of("HardCoded","v1","v2","HardCodedAppend"), appendedRecords.getContent().get(0).getValue("SomeField"));
        assertEquals("newAddress-newAddress-address2", appendedRecords.getContent().get(1).getValue("Address"));
        assertEquals(List.of("HardCoded","HardCodedAppend"), appendedRecords.getContent().get(1).getValue("SomeField"));
        assertEquals("address3", appendedRecords.getContent().get(2).getValue("Address"));
        //no changes to record #3
        assertEquals(List.of("nochange1","nochange2"), appendedRecords.getContent().get(2).getValue("SomeField"));
        Page<TransactionLog> appendedTransactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 4 records matched
        assertEquals(3, appendedTransactions.getNumberOfElements());
        assertEquals(record1.getId(), appendedTransactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.update, appendedTransactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), appendedTransactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress-newAddress-address1", appendedTransactions.getContent().get(0).getChanges().get(address.getId()).getNewValue());
        assertEquals("newAddress-address1", appendedTransactions.getContent().get(0).getChanges().get(address.getId()).getOldValue());
        assertEquals(List.of("HardCoded","v1","v2"), appendedTransactions.getContent().get(0).getChanges().get(newField.getId()).getOldValue());
        assertEquals(List.of("HardCoded","v1","v2","HardCodedAppend"), appendedTransactions.getContent().get(0).getChanges().get(newField.getId()).getNewValue());

        assertEquals(record2.getId(), appendedTransactions.getContent().get(1).getSyncariId());
        assertEquals(Operation.update, appendedTransactions.getContent().get(1).getOperation());
        assertEquals(graph.getId(), appendedTransactions.getContent().get(1).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress-newAddress-address2", appendedTransactions.getContent().get(1).getChanges().get(address.getId()).getNewValue());
        assertEquals("newAddress-address2", appendedTransactions.getContent().get(1).getChanges().get(address.getId()).getOldValue());
        assertEquals(List.of("HardCoded"), appendedTransactions.getContent().get(1).getChanges().get(newField.getId()).getOldValue());
        assertEquals(List.of("HardCoded","HardCodedAppend"), appendedTransactions.getContent().get(1).getChanges().get(newField.getId()).getNewValue());

        //remove a value
        //clear tx repo
        //txnService.deleteAll();
        now = Instant.now();
        //test append
        var updateFieldsRemoveFromList=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCodedAppend"),"operation",Map.of("name","operation","value","remove"))
        );

        FunctionCall functionCallForRemove = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFieldsRemoveFromList);
        Object removeOutput = lookUpFunctions.updateSyncariRecords(input, functionCallForRemove, graphContext);
        assertEquals(input, removeOutput);
        assertEquals(3, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> removedRecords = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, removedRecords.getNumberOfElements());
        assertEquals(List.of("HardCoded","v1","v2"), removedRecords.getContent().get(0).getValue("SomeField"));
        assertEquals(List.of("HardCoded"), removedRecords.getContent().get(1).getValue("SomeField"));
        //no changes to record #3
        assertEquals(List.of("nochange1","nochange2"), removedRecords.getContent().get(2).getValue("SomeField"));
        Page<TransactionLog> removedTransactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 3 records matched
        assertEquals(3, removedTransactions.getNumberOfElements());
        assertEquals(record1.getId(), removedTransactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.update, removedTransactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), removedTransactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals(List.of("HardCoded","v1","v2","HardCodedAppend"), removedTransactions.getContent().get(0).getChanges().get(newField.getId()).getOldValue());
        assertEquals(List.of("HardCoded","v1","v2"), removedTransactions.getContent().get(0).getChanges().get(newField.getId()).getNewValue());

        assertEquals(record2.getId(), removedTransactions.getContent().get(1).getSyncariId());
        assertEquals(Operation.update, removedTransactions.getContent().get(1).getOperation());
        assertEquals(graph.getId(), removedTransactions.getContent().get(1).getAdditionalInfo().get("graphId"));
        assertEquals(List.of("HardCoded","HardCodedAppend"), removedTransactions.getContent().get(1).getChanges().get(newField.getId()).getOldValue());
        assertEquals(List.of("HardCoded"), removedTransactions.getContent().get(1).getChanges().get(newField.getId()).getNewValue());

        //txnService.deleteAll();
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_values_multivalued_as_token(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        input.addValue("SomeField", new ArrayList<>(List.of("v1","v2","v3")));
        EntityData record1 = new EntityData().setName("Account").setId("1234").addValue("Name", "testlookup").addValue("Address","address1").addValue("SomeField",new ArrayList<>(List.of("v1","v2")));
        EntityData record2 = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId("1236").addValue("Name", "testlookup-nomatch").addValue("Address","address3")
                .addValue("SomeField",new ArrayList<>(List.of("nochange1","nochange2")));;
        EntityData record4 = new EntityData().setName("Account").setId("1237").addValue("Name", "testlookup").addValue("Address","address4")
                .addValue("SomeField",new ArrayList<>(List.of("HardCoded")));
        entityRepo.saveAll(List.of(record1,record2,record3,record4));

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId()).setMultiValueField(true);
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        AttributeDefinition multiField = new AttributeDefinition().setApiName("MultiField")
                .setDataType(new StringType()).setDisplayName("MultiField").setEntityId(sourceEntity.getId()).setMultiValueField(true);
        multiField.setDraftStatus(DraftStatus.APPROVED);
        multiField.setStatus(Status.ACTIVE);
        multiField = attributeProxyRepo.save(multiField);

        var insertFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",multiField.getId()),"newValue",
                        Map.of("name","newValue","value","{{record.values.SomeField}}"),"operation",Map.of("name","operation","value","replace"))
        );

        GraphContext graphContext = new GraphContext();
        graphContext.put("record", input);
        // Insert a record into Syncari
        FunctionCall  functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "insertFields", insertFields);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);
        graphContext.setCurrentNode(new MappingNode().setName("insertRecord"));
        Object output = lookUpFunctions.insertRecord(input, functionCall, graphContext);
        assertEquals(input, output);
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(5, records.getNumberOfElements());
        assertEquals(List.of("v1","v2", "v3"), records.getContent().get(4).getValue("MultiField"));

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",multiField.getId()),"newValue",
                        Map.of("name","newValue","value","{{current.values.SomeField}}"),"operation",Map.of("name","operation","value","replace"))
        );

        functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(3  , graphContext.get("Account_recordsUpdated"));
        records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(5, records.getNumberOfElements());
        assertEquals("newAddress-address1", records.getContent().get(0).getValue("Address"));
        assertEquals(List.of("v1","v2"), records.getContent().get(0).getValue("MultiField"));
        //txnService.deleteAll();
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_values_suffix(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");

        EntityData record1 = new EntityData().setName("Account").setId("1234").addValue("Name", "testlookup").addValue("Address","address1").addValue("SomeField","v1");
        EntityData record2 = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId("1236").addValue("Name", "testlookup-nomatch").addValue("Address","address3")
                .addValue("SomeField",new ArrayList<>(List.of("nochange1","nochange2")));;
        EntityData record4 = new EntityData().setName("Account").setId("1237").addValue("Name", "testlookup").addValue("Address","address4")
                .addValue("SomeField",new ArrayList<>(List.of("HardCoded")));
        entityRepo.saveAll(List.of(record1,record2,record3,record4));


        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId()).setMultiValueField(true);
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        AttributeDefinition multiField = new AttributeDefinition().setApiName("MultiField")
                .setDataType(new StringType()).setDisplayName("MultiField").setEntityId(sourceEntity.getId()).setMultiValueField(true);
        multiField.setDraftStatus(DraftStatus.APPROVED);
        multiField.setStatus(Status.ACTIVE);
        multiField = attributeProxyRepo.save(multiField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","{{current.values.SomeField}}{{previous.values.SomeField}}"),"operation",Map.of("name","operation","value","suffix"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);

        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());

        assertEquals("[v1][[v1]]", records.getContent().get(0).getValue("SomeField"));
        //txnService.deleteAll();
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_values_prefix(){
        lookUpFunctions.schemaService = schemaService;
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        
        EntityData record1 = new EntityData().setName("Account").setId("1234").addValue("Name", "testlookup").addValue("Address","address1").addValue("SomeField","v1");
        EntityData record2 = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId("1236").addValue("Name", "testlookup-nomatch").addValue("Address","address3")
                .addValue("SomeField",new ArrayList<>(List.of("nochange1","nochange2")));;
        EntityData record4 = new EntityData().setName("Account").setId("1237").addValue("Name", "testlookup").addValue("Address","address4")
                .addValue("SomeField",new ArrayList<>(List.of("HardCoded")));
        entityRepo.saveAll(List.of(record1,record2,record3,record4));


        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId()).setMultiValueField(true);
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        AttributeDefinition multiField = new AttributeDefinition().setApiName("MultiField")
                .setDataType(new StringType()).setDisplayName("MultiField").setEntityId(sourceEntity.getId()).setMultiValueField(true);
        multiField.setDraftStatus(DraftStatus.APPROVED);
        multiField.setStatus(Status.ACTIVE);
        multiField = attributeProxyRepo.save(multiField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}-{{current.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","{{current.values.SomeField}}{{previous.values.SomeField}}"),"operation",Map.of("name","operation","value","prefix"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);

        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());

        assertEquals("[[v1]][v1]", records.getContent().get(0).getValue("SomeField"));
        //txnService.deleteAll();
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_values_paginated(){
        lookUpFunctions.schemaService = schemaService;
        Instant now = Instant.now();
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        List<EntityData> matchingRecords = new ArrayList<>();
        for(int i=0;i<234;i++) {
           matchingRecords.add(new EntityData().setName("Account").setId("1234"+i).addValue("Name", "testlookup").addValue("Address", "address1"));
        }
        entityRepo.saveAll(matchingRecords);

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecords(input, functionCall, graphContext);
        assertEquals(input, output);
        assertEquals(234, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(234, records.getNumberOfElements());
        for(EntityData record: records.getContent()){
            assertEquals("newAddress", record.getValue("Address"));
            assertEquals("HardCoded", record.getValue("SomeField"));
        }

        Page<TransactionLog> transactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 3 records matched
        assertEquals(234, transactions.getNumberOfElements());
        for(int i=0;i<234;i++) {
            assertEquals(records.getContent().get(i).getId(), transactions.getContent().get(i).getSyncariId());
            assertEquals(Operation.update, transactions.getContent().get(i).getOperation());
            assertEquals(graph.getId(), transactions.getContent().get(i).getAdditionalInfo().get("graphId"));
            assertEquals("newAddress", transactions.getContent().get(i).getChanges().get(address.getId()).getNewValue());
            assertEquals("address1", transactions.getContent().get(i).getChanges().get(address.getId()).getOldValue());
            assertEquals("HardCoded", transactions.getContent().get(i).getChanges().get(newField.getId()).getNewValue());
            assertEquals(syncarEntityId, transactions.getContent().get(i).getSources().get(0).getExternalId());
            assertEquals("syncari", transactions.getContent().get(i).getSources().get(0).getConnectorName());
        }

        //txnService.deleteAll();
        entityRepo.deleteAll("Account");
        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }

    @Test
    public void update_values_on_field(){
        lookUpFunctions.schemaService = schemaService;
        Instant now = Instant.now();
        EntityData input = new EntityData();
        input.setName("Account");
        input.setId("1233");
        var syncarEntityId = ObjectId.get().toHexString();
        input.setSyncariEntityId(syncarEntityId);
        input.addValue("Name", "testlookup");
        input.addValue("Address", "newAddress");
        String id1 = ObjectId.get().toHexString();
        String id2 = ObjectId.get().toHexString();
        String id3 = ObjectId.get().toHexString();
        EntityData record1 = new EntityData().setName("Account").setId(id1).setSyncariEntityId(id1).addValue("Name", "testlookup").addValue("Address","address1");
        EntityData record2 = new EntityData().setName("Account").setId(id2).setSyncariEntityId(id2).addValue("Name", "testlookup").addValue("Address","address2");
        EntityData record3 = new EntityData().setName("Account").setId(id3).setSyncariEntityId(id3).addValue("Name", "testlookup-nomatch").addValue("Address","address3");
        EntityData noChanges = new EntityData().setName("Account").setId("1235").addValue("Name", "testlookup").addValue("Address","newAddress").addValue("SomeField","HardCoded");
        entityRepo.saveAll(List.of(record1,record2,record3, noChanges));

        EntityDefinition sourceEntity = new EntityDefinition("Account", "Account");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition address = new AttributeDefinition().setApiName("Address")
                .setDataType(new StringType()).setDisplayName("Address").setEntityId(sourceEntity.getId());
        address.setDraftStatus(DraftStatus.APPROVED);
        address.setStatus(Status.ACTIVE);
        address = attributeProxyRepo.save(address);
        AttributeDefinition newField = new AttributeDefinition().setApiName("SomeField")
                .setDataType(new StringType()).setDisplayName("SomeField").setEntityId(sourceEntity.getId());
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setStatus(Status.ACTIVE);
        newField = attributeProxyRepo.save(newField);
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", name.getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.Name}}")
        );
        GraphContext graphContext = new GraphContext();
        graphContext.put("previous", input);
        graphContext.put("record", input);
        MappingGraph graph = new MappingGraph();
        graph.setName("Custom Graph");
        graph.setId(ObjectId.get().toHexString());
        graphContext.setGraph(graph);

        var updateFields=List.of(
                Map.of("updateField",Map.of("name","updateField","value",address.getId()),"newValue",
                        Map.of("name","newValue","value","{{previous.values.Address}}")),
                Map.of("updateField",Map.of("name","updateField","value",newField.getId()),"newValue",
                        Map.of("name","newValue","value","HardCoded"))
        );

        FunctionCall functionCall = createCall("syncariEntityDefId", sourceEntity.getId(), "predicate", eq,"updateFields",updateFields);
        Object output = lookUpFunctions.updateSyncariRecordsOnField("fieldValue", functionCall, graphContext);
        assertEquals("fieldValue", output);
        assertEquals(2, graphContext.get("Account_recordsUpdated"));
        Page<EntityData> records = entityRepo.findEntities("Account", Pageable.unpaged());
        assertEquals(4, records.getNumberOfElements());
        assertEquals("newAddress", records.getContent().get(0).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(0).getValue("SomeField"));
        assertEquals("newAddress", records.getContent().get(1).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(1).getValue("SomeField"));
        assertEquals("address3", records.getContent().get(2).getValue("Address"));
        assertFalse(records.getContent().get(2).has("SomeField"));
        //unchanged record with same valuess
        assertEquals("newAddress", records.getContent().get(3).getValue("Address"));
        assertEquals("HardCoded", records.getContent().get(3).getValue("SomeField"));
        Page<TransactionLog> transactions = txnService.findAll(Pageable.unpaged(), now);
        //Only two transactions, even though 3 records matched
        assertEquals(2, transactions.getNumberOfElements());
        assertEquals(record1.getId(), transactions.getContent().get(0).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(0).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(0).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress", transactions.getContent().get(0).getChanges().get(address.getId()).getNewValue());
        assertEquals("address1", transactions.getContent().get(0).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(0).getChanges().get(newField.getId()).getNewValue());
        assertEquals(syncarEntityId, transactions.getContent().get(0).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(0).getSources().get(0).getConnectorName());

        assertEquals(record2.getId(), transactions.getContent().get(1).getSyncariId());
        assertEquals(Operation.update, transactions.getContent().get(1).getOperation());
        assertEquals(graph.getId(), transactions.getContent().get(1).getAdditionalInfo().get("graphId"));
        assertEquals("newAddress", transactions.getContent().get(1).getChanges().get(address.getId()).getNewValue());
        assertEquals("address2", transactions.getContent().get(1).getChanges().get(address.getId()).getOldValue());
        assertEquals("HardCoded", transactions.getContent().get(1).getChanges().get(newField.getId()).getNewValue());
        assertEquals(syncarEntityId, transactions.getContent().get(1).getSources().get(0).getExternalId());
        assertEquals("syncari", transactions.getContent().get(1).getSources().get(0).getConnectorName());

        entityProxyRepo.delete(sourceEntity);
        attributeProxyRepo.delete(name);
        lookUpFunctions.schemaService = mockSchemaService;
    }
    private FunctionCall getAttachFunctionCall(Map<String, Object> predicate) {
        return createCall("attachPredicate", predicate);
    }
    private FunctionCall getAttachFunctionCall() {

        return createCall("syncariEntityDefId", "synEntId", "searchFieldId", "name", "inputFieldId", "name","externalEntityDefId","extEntId");
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
    
    private EntityDefinition getSyncariAccount() {
        EntityDefinition syncariEntityDef = new EntityDefinition("account", "Account");
        syncariEntityDef.setConnectorId("syncConnectorId");
        syncariEntityDef.setId("synEntId");
        AttributeDefinition name = new AttributeDefinition();
        name.setApiName("Name");
        name.setDisplayName("Account Name");
        name.setId("name");
        name.setDataType(StringType.VALUE);
        syncariEntityDef.addField(name);
        return syncariEntityDef;
    }
    
    private EntityDefinition getExternalAccount() {
        EntityDefinition syncariEntityDef = new EntityDefinition("Account", "Account");
        syncariEntityDef.setConnectorId("extConnectorId");
        syncariEntityDef.setId("extEntId");
        AttributeDefinition name = new AttributeDefinition();
        name.setApiName("Name");
        name.setDisplayName("Account Name");
        name.setId("name");
        name.setDataType(StringType.VALUE);
        syncariEntityDef.addField(name);
        return syncariEntityDef;
    }

    private EntityDefinition getExternalAccount(String entitDefName,String connectorId, String id) {
        EntityDefinition syncariEntityDef = new EntityDefinition(entitDefName, entitDefName);
        syncariEntityDef.setConnectorId(connectorId);
        syncariEntityDef.setId(id);
        AttributeDefinition name = new AttributeDefinition();
        name.setApiName("Name");
        name.setDisplayName("Account Name");
        name.setId("name");
        name.setDataType(StringType.VALUE);
        syncariEntityDef.addField(name);
        return syncariEntityDef;
    }

    @Override
    public void tearDown() {
        entityRepo.deleteAll("Account");
        mappingRepo.deleteAll();
        super.tearDown();
        resetRepos(lookUpFunctions.stagedBatchRecordRepo,mappingRepo,serviceCredRepo,notifRepo);
    }

    private ServiceCredential getClearbitServiceCred(){
        ServiceCredential clearbitCreds = new ServiceCredential();
        clearbitCreds.setServiceType(ServiceType.Clearbit);
        clearbitCreds.setApiKey(clearbitApiKey);
        clearbitCreds.setName("Clearbit");
        clearbitCreds.setCredentialType(ServiceCredentialType.ENRICH);
        clearbitCreds = serviceCredentialService.addServiceCredential(clearbitCreds);

        return clearbitCreds;
    }
}
