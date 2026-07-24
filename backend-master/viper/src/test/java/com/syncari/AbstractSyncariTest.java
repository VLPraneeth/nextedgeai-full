package com.syncari;

import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.commands.DBMigrator;
import com.syncari.core.event.Publisher;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.SyncariRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.EventService;
import com.syncari.viper.ViperContext;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public abstract class AbstractSyncariTest {
    @MockBean
    protected EventService eventService;
    private static final Logger log = LoggerFactory.getLogger(AbstractSyncariTest.class);
    @Autowired
    DBMigrator migrator;
    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    UserRepo userRepo;
    @Autowired
    EventStore eventStore;
    @Autowired
    ApplicationContext context;
    @Autowired
    MongoTemplate syncariMongoTemplate;
    protected static Organization org;
    protected static Instance instance;
    protected static User user;
    private static boolean setupDone = false;
    protected  ViperContext viperContext;

    @Autowired
    TestRedisConfiguration testRedisConfiguration;

    @MockBean
    public Publisher publisher;

    @Before
    public void setUp() {
        if (!setupDone) {
            MigrationContext.setApplicationContext(context);
            registerExitHandler();
            migrator.migrateSyncari();
            org = new Organization("Test Org");
            instance = new Instance("test_org_instance", "test_org_instance");
            Resource resource = new Resource(ResourceType.DATABASE);
            resource.setConfiguration(Map.of("database", "test_org_db"));
            instance.addResource(resource);
            org.addInstance(instance);

            user = new User("test@email.com", "test", Status.ACTIVE, "123");
            user.setSystemUser(true);
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);

            org = organizationRepo.save(org);
            userRepo.save(user);
            SyncariContext.setOrganziation(org);
            SyncariContext.setUser(user);

            MigrationContext.setSyncariId(instance.getSyncariId());
            migrator.migrateCustomers();
            log.info("****************************");
            log.info("Creating BigQuery tables for id {}",instance.getSyncariId());
            log.info("****************************");
            eventStore.provision(instance.getSyncariId());
            doNothing().when(eventService).log(any());
            setupDone=true;

            // add test specific seed data
            addTestSeed();

        }

        SyncariContext.setUser(user);
        SyncariContext.setOrganziation(org);
        SyncariContext.setInstance(instance);
        viperContext = ViperContext.fromCurrentContext();

        doNothing().when(publisher).publishToGenericQueue(ArgumentMatchers.any(Event.class));
        doNothing().when(publisher).publishToGenericQueue(ArgumentMatchers.any(String.class));
    }

    protected void addTestSeed(){
        // add Test synpse in connector metadata
        MongoCollection<Document> meta = syncariMongoTemplate.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.TEST_SYNAPSE));
    }

    private void registerExitHandler() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SyncariContext.setOrganziation(org);
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);
            eventStore.deprovision(instance.getSyncariId());
        }));
    }

    @After
    public void tearDown() {
        SyncariContext.resetAll();
    }

    protected void resetRepos(SyncariRepo... repos) {
        for (int i = 0; i < repos.length; i++) {
            repos[i].reset();
        }
    }

}

