package com.syncari.analytics;

import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.commands.DBMigrator;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.Resource;
import com.syncari.core.model.ResourceType;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.EventService;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;


@EnableRetry
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public abstract class AbstractSyncariTest {
	@MockBean
	EventService eventService;
	@Autowired
	DBMigrator migrator;
	@Autowired
	OrganizationRepo organizationRepo;
	@Autowired
	EventStore eventStore;
	@Autowired
	ApplicationContext context;

	private static final Logger log = LoggerFactory.getLogger(AbstractSyncariTest.class);
	protected static Organization org;
	protected static Instance instance;
	private static boolean setupDone = false;

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
			org = organizationRepo.save(org);

			log.info("**************************************************");
			log.info("Creating BigQuery tables for id {}",instance.getSyncariId());
			log.info("**************************************************");
			eventStore.provision(instance.getSyncariId());
			doNothing().when(eventService).log(any());
			setupDone = true;
		}

		SyncariContext.setInstance(instance);
		SyncariContext.setOrganziation(org);
	}

	private void registerExitHandler() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			SyncariContext.setOrganziation(org);
			SyncariContext.setInstance(instance);
			log.info("**************************************************");
			log.info("De-provisioning BigQuery tables for id {}",instance.getSyncariId());
			log.info("**************************************************");
			eventStore.deprovision(instance.getSyncariId());
		}));
	}

	@After
	public void tearDown() {
		SyncariContext.resetAll();
	}

}
