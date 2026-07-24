package com.syncari.karibu.rest.controllers;

import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.changelogs.syncari.M0004_InitialUsers;
import com.syncari.core.commands.DBMigrator;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.SyncariRepo;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.EventService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureDataMongo
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:test_application.properties")
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
    EntityRepo entityRepo;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private MappingNodeRepo nodeRepo;
    @Autowired
    private EdgeRepo edgeRepo;


    static boolean dbSetup = false;
    private static Organization org;
    private static User user;
    private static Instance instance;

    @Autowired
    TestRedisConfiguration testRedisConfiguration;

    @MockBean
    public Publisher publisher;
    @Getter
    @AllArgsConstructor
    class Context{
        private Organization org;
        private User user;
        private Instance instance;
    }
    private Stack<Context> contextStack=new Stack<>();

    @Before
    public void setUp() {
        System.setProperty("DB.TRACE", "true");
        if (!dbSetup) {
            MigrationContext.setApplicationContext(context);
            migrator.migrateSyncari();
            org = new Organization("Test Org");
            instance = new Instance("test_org_instance", "test");
            Resource resource = new Resource(ResourceType.DATABASE);
            resource.setConfiguration(Map.of("database", "test_org_db"));
            instance.addResource(resource);
            org.addInstance(instance);

            user = new User("test@email.com", "test", Status.ACTIVE, instance.getSyncariId());
            user.setFirstName("fname");
            user.setLastName("lname");
            user.setSystemUser(true);
            user.setSuperAdmin(true);
            user.setStatus(Status.ACTIVE);
            user.addAvailableInstance(instance);
            // add api info
            user.setApiUser(true);
            user.setClientId("Fu3dgclRwNBKgod_KhnS_xYrWiY");
            user.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));

            org = organizationRepo.save(org);
            user = userRepo.save(user);
            SyncariContext.setOrganziation(org);
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);
            MigrationContext.setSyncariId(instance.getSyncariId());
            migrator.migrateCustomers();

            // add test specific seed data
            addTestSeed();

            doNothing().when(eventService).log(any());
            doNothing().when(emailService).sendHtml(any(), any(), any());
            doNothing().when(plgEmailService).sendHtml(any(), any(), any());
            dbSetup = true;
        } else {
            SyncariContext.setOrganziation(org);
            SyncariContext.setUser(user);
            SyncariContext.setInstance(instance);
            org.getInstances().clear();
            org.addInstance(instance);
        }
        updateSecurityContextWithSyncariId();

    }

    private void updateSecurityContextWithSyncariId() {
        if(SecurityContextHolder.getContext()!=null && SecurityContextHolder.getContext().getAuthentication()!=null) {
            UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
            authentication.setDetails(Map.of("syncariId", SyncariContext.getSyncariId()));
        }
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
        int remaining = maxRetries - 1;
        try {
            return r.get();
        } catch (Throwable t) {
            if (remaining == 0) {
                throw t;
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
                return retry(r, remaining);
            }
        }
    }

    protected void pushContext(){
        contextStack.push(new Context(SyncariContext.getOrganziation(),SyncariContext.getUser(),SyncariContext.getInstance()));
    }
    protected void restoreContext(){
        var context = contextStack.pop();
        SyncariContext.resetAll();
        SyncariContext.setOrganziation(context.getOrg());
        SyncariContext.setInstance(context.getInstance());
        SyncariContext.setUser(context.getUser());
    }

    @After
    public void tearDown() {
        //Context may already have been reset due to MVC interceptor tests
        SyncariContext.resetAll();
    }
}
