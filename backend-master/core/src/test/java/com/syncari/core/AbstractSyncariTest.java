package com.syncari.core;

import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;
import com.syncari.core.changelogs.syncari.M0004_InitialUsers;
import com.syncari.core.commands.DBMigrator;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.SyncariRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.repositories.syncari.ClusterRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.DatastoreService;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.EventService;
import com.syncari.core.service.TestRedisConfiguration;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@EnableRetry
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
/*@EnableCaching
@ImportAutoConfiguration(classes = {
        CacheAutoConfiguration.class*//*,
        RedisAutoConfiguration.class*//*
})*/
//@Import(TestRedisConfiguration.class)
public abstract class AbstractSyncariTest {
    @MockBean
    EventService eventService;
    @Autowired
    MongoTemplate customerMongoTemplate;
    @Autowired
    NotificationRepo inboxRepo;
    @Autowired
    DBMigrator migrator;

    @Autowired
    MongoTemplate syncariMongoTemplate;

    @MockBean
    @Qualifier("defaultEmailService")
    public EmailService emailService;
    @MockBean
    @Qualifier("plgEmailService")
    public EmailService plgEmailService;
    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    UserRepo userRepo;
    @Autowired
    MappingGraphRepo mappingGraphRepo;

    @Autowired
    private MappingNodeRepo nodeRepo;
    @Autowired
    private EdgeRepo edgeRepo;

    @Value("${spring.data.mongodb.port}")
    private String port;

    @Value("${spring.data.mongodb.host}")
    private String host;

    @Autowired
    private ApplicationContext context;

    protected static boolean dbSteup = false;

    @MockBean
    public Publisher publisher;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    TestRedisConfiguration testRedisConfiguration;
    @Autowired
    private ClusterRepo clusterRepo;
    static Cluster cluster;
    @Before
    public void setUp() {
        System.setProperty("DB.TRACE", "true");
        if (!dbSteup) {
            MigrationContext.setApplicationContext(context);
            migrator.migrateSyncari();
            var org = new Organization("Test Org");
            var instance = new Instance("test_org_instance", "test instance");
            Resource resource = new Resource(ResourceType.DATABASE);
            resource.setConfiguration(Map.of("database", "test_org_db"));
            instance.addResource(resource);
            org.addInstance(instance);

            var user = new User("test@email.com", "test", Status.ACTIVE, instance.getSyncariId());
            user.setFirstName("fname");
            user.setLastName("lname");
            user.setSystemUser(true);
            user.setSuperAdmin(true);
            user.setStatus(Status.ACTIVE);
            user.addAvailableInstance(instance);
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);

            org = organizationRepo.save(org);
            userRepo.save(user);
            SyncariContext.setOrganziation(org);
            SyncariContext.setUser(user);
            datastoreService.createOrGetSyncariDSConnector(instance.getSyncariId());
            MigrationContext.setSyncariId(instance.getSyncariId());
            migrator.migrateCustomers();
            // add test specific seed data
            addTestSeed();

            doNothing().when(eventService).log(any());
            doNothing().when(emailService).sendHtml(any(), any(), any());
            doNothing().when(emailService).sendText(any(), any(), any());
            doNothing().when(emailService).sendSupportEmail(any(), any());

            doNothing().when(plgEmailService).sendHtml(any(), any(), any());
            doNothing().when(plgEmailService).sendText(any(), any(), any());
            doNothing().when(plgEmailService).sendSupportEmail(any(), any());
            dbSteup = true;
        }

        // Reset each repo before each test run
        List<String> exclusionRepos = Arrays.asList("RoleRepo", "InboxRepo", "OAuthRegistryRepo", "FunctionDefinitionRepo",
            "DfiRuleAssignmentRepo");
        var reflections = new Reflections(new ConfigurationBuilder()
                .filterInputsBy(new FilterBuilder().includePackage("com.syncari.core.repositories.customer"))
                .setUrls(ClasspathHelper.forPackage("com.syncari.core.repositories.customer"))
                .setScanners(new SubTypesScanner()));
        Set<Class<? extends SyncariRepo>> syncariRepos = reflections.getSubTypesOf(SyncariRepo.class);
        syncariRepos.forEach(repo -> {
            try {
                if (!exclusionRepos.contains(repo.getSimpleName())) {
                    var customerRepo = (SyncariRepo) context.getBean(repo);
                    customerRepo.reset();
                }
            } catch (BeansException e) {
                // Do nothing
            }
        });

        Set<Class<? extends DraftableRepo>> draftableRepos = reflections.getSubTypesOf(DraftableRepo.class);
        draftableRepos.forEach(repo -> {
            try {
                if (!exclusionRepos.contains(repo.getSimpleName())) {
                    var customerRepo = (DraftableRepo) context.getBean(repo);
                    customerRepo.reset();
                }
            } catch (BeansException e) {
                // Do nothing
            }
        });
        doNothing().when(publisher).publishToGenericQueue(ArgumentMatchers.any(Event.class));
        doNothing().when(publisher).publishToGenericQueue(ArgumentMatchers.any(String.class));
    }

    protected void withSyncariAdmin(Runnable runnable) {
        var currentOrg = SyncariContext.getOrganziation();
        var currentInstance = SyncariContext.getInstance();
        var currentUser = SyncariContext.getUser();
        var org = organizationRepo.findByName("syncari_admin");
        org.ifPresent(o -> {
            var user = userRepo.findByEmail(M0004_InitialUsers.SUPER_ADMIN_EMAIL).get();
            var instance = o.getInstances().get(0);
            SyncariContext.resetAll();
            SyncariContext.setInstance(instance);
            SyncariContext.setUser(user);
            SyncariContext.setOrganziation(o);
            try {
                runnable.run();
            } finally {
                SyncariContext.resetAll();
                SyncariContext.setInstance(currentInstance);
                SyncariContext.setUser(currentUser);
                SyncariContext.setOrganziation(currentOrg);
            }
        });

    }

    protected void addTestSeed(){
        // add Test synpse in connector metadata
        MongoCollection<Document> meta = syncariMongoTemplate.getCollection("connectorMetadata");
        meta.insertOne(new Document("name", Constants.TEST_SYNAPSE));

    }

    protected void resetRepos(SyncariRepo... repos) {
        for (int i = 0; i < repos.length; i++) {
            repos[i].reset();
        }
    }
	protected void retry(Runnable r) {
    	retry(r, 3);
	}
	protected <T> T retry(Supplier<T> r) {
    	return retry(r, 3);
	}
    protected void retry(Runnable r, int maxRetries) {
        int remaining = maxRetries - 1;
        try {
            r.run();
        } catch (Throwable t) {
            if (remaining == 0) {
                throw t;
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
                retry(r, remaining);

            }
        }
    }

    protected <T> T retry(Supplier<T> r, int maxRetries) {
        return retry(r, maxRetries, 500);
    }
    
    protected <T> T retry(Supplier<T> r, int maxRetries, long waitTime) {
        int remaining = maxRetries - 1;
        try {
            return r.get();
        } catch (Throwable t) {
            if (remaining == 0) {
                throw t;
            } else {
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                }
                return retry(r, remaining);
            }
        }
    }

    @After
    public void tearDown() {
//        customerMongoTemplate.getDb().drop();
//        syncariMongoTemplate.getDb().drop();
//        SyncariContext.resetAll();
        //delete all clusters except the primary
        resetRepos(mappingGraphRepo, nodeRepo, edgeRepo);
    }

}
