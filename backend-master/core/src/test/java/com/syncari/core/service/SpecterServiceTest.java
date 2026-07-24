package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.service.SalesforceService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.core.*;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.repositories.syncari.ConnectorMetadataRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.schema.Schema;
import com.syncari.core.utils.CustomerMongoUtils;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@Ignore
@TestPropertySource(locations = "classpath:test_application.properties")
public class SpecterServiceTest extends AbstractSyncariTest {

    @Autowired
    SpecterService supportToolService;

    @Autowired
    ConnectorMetadataRepo metaRepo;

    @Autowired
    ConnectorService connService;

    @Autowired
    SalesforceService salesforceService;

    @Autowired
    TestHelper testHelper;

    @Autowired
    ConnectorRepo connectorRepo;

    @Autowired
    EndSystemConfig config;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    DataTransformer transformer;

    @Autowired
    EntityRepo entityRepo;

    @Autowired
    private IdMappingRepo idMappingRepo;

    @Autowired
    private StagedBatchRecordRepo stagedBatchRecordRepo;

    @Autowired
    private SyncDetailRepo syncDetailRepo;

    @Autowired
    private SinkLogRepo sinkLogRepo;

    @Autowired
    private StagedBatchRepo stagedBatchRepo;

    @Autowired
    private StreamRepo streamRepo;

    @Autowired
    private MappingGraphRepo mappingGraphRepo;

    @Autowired
    private MappingNodeRepo mappingNodeRepo;

    @Autowired
    private EdgeRepo edgeRepo;

    @Autowired
    private LayoutRepo layoutRepo;
    
    @Autowired
    private CustomerMongoUtils customerMongoUtils;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private MappingGraphService mappingGraphService;

    @Autowired
    private DataServiceFactory dataServiceFactory;

    @Autowired
    WatermarkService watermarkService;

    @Autowired
    ProvisioningService provisioningService;

    @Autowired
    UserService userService;

    @Autowired
    OrganizationRepo organizationRepo;

    @MockBean(name="eventStore")
    EventStore eventStore;

    Connector connector;
    @Override
    public void setUp(){
        super.setUp();

        connector = new Connector("sfdc1", metaRepo.findByName(Constants.SALESFORCE).get(0).getId(),
                config.getSfResetTestUrl(), config.getSfResetTestUser(), config.getSfResetTestPassword());
        connector.getAuthConfig().setToken(config.getSfResetTestToken());

        doNothing().when(eventStore).provision(any());
        doNothing().when(eventStore).deprovision(any());
        supportToolService.setEventStore(eventStore);
    }

    @After
    public void teardown(){
        resetRepos(syncDetailRepo, idMappingRepo,sinkLogRepo,stagedBatchRecordRepo, stagedBatchRepo,connectorRepo,streamRepo,
                mappingGraphRepo,mappingNodeRepo,edgeRepo,layoutRepo, attributeProxyRepo, entityProxyRepo);
        super.tearDown();
    }

//    @Test
    public void resetExternalConnector(){

        ProvisioningResponse provisioningResponse = provisioningService.provision(
            "resetTestInstance",
            InstanceType.production,
            "resetTestOrg",
            "resetTestOrg",
            "resettestadmin@email.com",
            null,
            RoleConstants.ORG_ADMIN,
            "resetTestAdminFirst",
            "resetTestAdminLast", OrganizationType.standard, null
        );
        Organization org = provisioningResponse.getOrganization();
        Instance instance = org.getInstances().get(0);

        SyncariContext.runWithContext(org, instance, SyncariContext.getUser(), () -> {
            connector = connService.save(connector);
            setupDataInSalesforce();
            connService.authenticated(connector.getId());
            connService.activate(connector.getId());
            setupDataInSyncariEntities();

            assertFalse(mappingGraphRepo.findAll().isEmpty());
            assertFalse(mappingNodeRepo.findAll().isEmpty());
            assertFalse(edgeRepo.findAll().isEmpty());
            assertFalse(layoutRepo.findAll().isEmpty());
            assertFalse(attributeProxyRepo.findAll().stream().filter(a -> !a.isSeeded()).collect(Collectors.toList()).isEmpty());
            assertFalse(entityProxyRepo.findByConnectorId(connector.getId()).isEmpty());
            assertFalse(customerMongoUtils.getCollectionNamesStartWith("syncari_").isEmpty());
            assertTrue(checkIfCustomFieldExists("Contact", "testField__c"));
            assertNotNull(connService.get(connector.getId()));
        });

        supportToolService.resetSubscription(instance.getSyncariId(), Long.valueOf(30));

        SyncariContext.runWithContext(org, instance, SyncariContext.getUser(), () -> {
            assertTrue(idMappingRepo.findAll().isEmpty());
            assertTrue(stagedBatchRecordRepo.findAll().isEmpty());
            assertTrue(syncDetailRepo.findAll().isEmpty());
            assertTrue(sinkLogRepo.findAll().isEmpty());
            assertTrue(stagedBatchRepo.findAll().isEmpty());
            assertTrue(streamRepo.findAll().isEmpty());
            assertTrue(mappingGraphRepo.findAll().isEmpty());
            assertTrue(mappingNodeRepo.findAll().isEmpty());
            assertTrue(edgeRepo.findAll().isEmpty());
            assertTrue(layoutRepo.findAll().isEmpty());
            assertFalse(attributeProxyRepo.findAll().stream().filter(a -> !a.isSeeded()).collect(Collectors.toList()).isEmpty());
            assertFalse(entityProxyRepo.findByConnectorId(connector.getId()).isEmpty());
            assertFalse(checkIfCustomFieldExists("Contact", "testField__c"));

            // test if connector status is changed back to authenticated
            assertTrue(connService.get(connector.getId()).getStatus().equals(ConnectorStatus.AUTHENTICATED));
            assertTrue(customerMongoUtils.getCollectionNamesStartWith("syncari_").isEmpty());

            assertTrue(connectorHasData(Constants.CONTACT));
            assertTrue(connectorHasData(Constants.ACCOUNT));
        });

        // cleanup
        provisioningService.deprovisionEventStore(org.getInstances().get(0).getSyncariId());
        provisioningService.deprovisionInstance(org.getInstances().get(0).getSyncariId(), true);
        organizationRepo.deleteById(org.getId());
        userService.deleteUser(userService.getUser("resettestadmin@email.com").getId());
    }

    @Test
    public void populateSynapseNoConnector(){

//        connector = connService.save(connector);
//        setupDataInSalesforce();
//        connService.authenticated(connector.getId());
//        connService.activate(connector.getId());
//        setupDataInSyncariEntities();
//
//        supportToolService.resetExternalConnector(connector, Long.valueOf(30));
//
//        Optional<Connector> conn = connService.getAll().stream()
//                .filter(c -> Constants.SALESFORCE.equalsIgnoreCase(c.getMetadata().getName()))
//                .findFirst();
//        assertFalse(conn.isPresent());
//
//        supportToolService.populateSynapses();
//        assertFalse(conn.isPresent());
        //assertFalse(connectorHasData(Constants.CONTACT));
        //assertFalse(connectorHasData(Constants.ACCOUNT));
    }

    private void setupDataInSalesforce() {

        AttributeSchema schema = new AttributeSchema("testField", "Text");
        schema.setDisplayName("testField");
        CreateFieldRequest createFieldRequest = new CreateFieldRequest(Constants.CONTACT, transformer.toConnectorInfo(connector), schema);
        schema = salesforceService.createField(createFieldRequest);
        SyncRequest syncRequestContact = testHelper.createSyncRequestForEntity(Constants.CONTACT, connector);
        SyncResponse acc = salesforceService.create(testHelper.createSyncRequestForEntity(Constants.ACCOUNT, connector));

        syncRequestContact.getData().get(connector.getId()).get(0).addValue("AccountId", acc.getResults().get(0).getId());
        salesforceService.create(syncRequestContact);

    }

    private void setupDataInSyncariEntities(){

        Schema syncariSchema = schemaService.getSyncariSchema();
        var entity = new EntityData("account");
        entity.addValue("name", "Test Account");
        entity.addValue("address", "some address");
        entity.addValue("some", ZonedDateTime.now());
        EntityData d1 = entityRepo.save(entity);

        entity = new EntityData("contact");
        entity.addValue("firstName", "First");
        entity.addValue("LastName", "Last");
        EntityData d2 = entityRepo.save(entity);

        syncariSchema.getEntities().stream().forEach(e -> {
            if (e.getApiName().equalsIgnoreCase("account") || e.getApiName().equalsIgnoreCase("contact")) {
                Optional<MappingGraph> entityGraph = mappingGraphService.retrieveDraftEntityGraph(e.getId());
                assertTrue(entityGraph.isPresent());
                mappingGraphService.approveDraft(entityGraph.get());
            }
        });
    }

    private boolean checkIfCustomFieldExists(String entityName, String fieldName){
        MetadataService dataService = dataServiceFactory.getSchemaService(connector.getMetadata());
        DescribeRequest request = new DescribeRequest(transformer.toConnectorInfo(connector), entityName);
        EntitySchema entitySchema = dataService.describe(request).get();
        return entitySchema.getAttributes().stream().anyMatch(a -> a.getApiName().equals(fieldName));

    }

    private boolean connectorHasData(String entityName){

        DescribeRequest request = new DescribeRequest(transformer.toConnectorInfo(connector), entityName);
        EntitySchema entitySchema = salesforceService.describe(request).get();

        long start = Instant.now().minus(Duration.ofMinutes(5)).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        WatermarkInfo watermark = new WatermarkInfo(start, end, false, 0);
        SyncRequest req = new SyncRequest()
                .Builder(transformer.toConnectorInfo(connector), entitySchema)
                .setWatermark(watermark);
        FetchResponse resp = salesforceService.getByWatermark(req);

        List<EntityData> data = new ArrayList<>();
        salesforceService.getByWatermark(req).getIterator().forEachRemaining(data::addAll);

        return data.stream().filter(d -> !d.isDeleted()).count() > 0;
    }

}
