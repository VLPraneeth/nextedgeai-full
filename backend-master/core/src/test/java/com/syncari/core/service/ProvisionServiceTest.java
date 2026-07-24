package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.misc.ServiceCredentialType;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.repositories.customer.UserRoleRepo;
import com.syncari.core.repositories.syncari.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
public class ProvisionServiceTest extends AbstractSyncariTest {
	@Autowired
	OrganizationRepo orgRepo;
	@Mock
	EventService eventService;
	@Autowired
	ProvisioningService service;
	@Autowired
	UserRepo userRepo;
	@Autowired
	UserRoleRepo userRoleRepo;
	@Autowired
	PlanRepo planRepo;
	@Autowired
	NotificationRepo inboxRepo;
	@Autowired
	UserInvitationRepo inviteRepo;
	@Autowired
	RoleRepo roleRepo;
	@Mock
	DatastoreService datastoreService;
	@Autowired
	UserService userService;
	@Autowired
	SubscriptionService subService;
	@Autowired
	ClusterRepo repo;
	@Autowired
	ClusterService clusterService;

	@After
	public void tearDown() {
		super.tearDown();
		repo.deleteAll();
	}
	
	@Test
	public void provisionValidations() {
		try {
			service.provision(null, InstanceType.production, null, null, null, null, null, null, null, OrganizationType.standard, null);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Instance name is required"));
		}
		try {
			service.provision("test", InstanceType.production, null, null, null, null, null, null, null, OrganizationType.standard, null);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Organization name is required"));
		}
		try {
			service.provision("test", InstanceType.production, "test", "test", null, null, null, null, null, OrganizationType.standard, null);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Admin email is required"));
		}
	}

	@Test
	public void provisionValidSubscription() {
		long orgCount = orgRepo.count();
		long usrCount = userRepo.count();
		assertEquals("test_org_db", SyncariContext.getDatabase());
		
		String adminEmail = "admintest@email.com";
		doNothing().when(datastoreService).provision(any());
		service.datastoreService = datastoreService;

		ProvisioningResponse wrapper = service.provision("provisionTestInstance", InstanceType.production, "provisionValidTestOrg", "provisionValidTestOrg",
				adminEmail, null, RoleConstants.ORG_ADMIN, "provisionTestFirstName", "provisionTestLastName", OrganizationType.standard, null);
		Organization newSub = wrapper.getOrganization();
		var user = userRepo.findByEmail(adminEmail).get();
		var instance = newSub.getInstances().get(0);
		planRepo.findByName("default");
		assertEquals(orgCount+1, service.listOrg().size());
		assertEquals(orgCount + 1, orgRepo.count());
		assertTrue(newSub.getInstances().size() == 1);
		//only admin user is created
		assertTrue(userRepo.count() == (usrCount+1));
		assertEquals(Status.PENDING, user.getStatus());
		assertTrue(user.isAdmin());
		assertEquals("test_org_db", SyncariContext.getDatabase());
		List<Instance> listInstances = service.listInstances(newSub.getId());
		assertEquals(1, listInstances.size());

		// check if the new instance is added to user's availableInstances
		assertEquals(1, user.getAvailableInstances().size());
		assertTrue(user.getAvailableInstances().contains(instance.getSyncariId()));
		assertEquals(instance.getSyncariId(), user.getCurrentInstanceId());

		SyncariContext.push();
        try {
            SyncariContext.setOrganziation(newSub);
            SyncariContext.setInstance(instance);
            assertEquals(String.format("provisiontestinstance_%s_db", SyncariContext.getInstance().getSyncariId()), SyncariContext.getDatabase());
            assertEquals(2, userRoleRepo.count());
            var userRole = userRoleRepo.findByUserId(user.getId()).get();
            assertEquals(1, userRole.getRoleIds().size());
            assertTrue(userRole.getRoleIds().contains(roleRepo.findByName(RoleConstants.ORG_ADMIN).get().getId()));
        } finally {
            SyncariContext.restore();
            assertEquals("test_org_db", SyncariContext.getDatabase());
        }

		service.deprovisionInstance(newSub.getInstances().get(0).getSyncariId(), true);
		user = userService.getUserById(user.getId());
		assertTrue(userService.getUserActiveInstances(user).isEmpty());
		assertTrue(service.listOrg().stream().filter(org -> org.getName().equals("provisionValidTestOrg")).findFirst().get().getInstances().isEmpty());

	}

	@Test
	public void provisionInstanceLimit() {
		long orgCount = orgRepo.count();
		assertEquals("test_org_db", SyncariContext.getDatabase());

		String adminEmail = "adminvalidtest@email.com";
		doNothing().when(datastoreService).provision(any());
		service.datastoreService = datastoreService;

		ProvisioningResponse wrapper = service.provision("provisionTestInstance", InstanceType.production, "provisionTestOrg", "provisionTestOrg",
				adminEmail, null, RoleConstants.ORG_ADMIN, "provisionTestFirstName", "provisionTestLastName", OrganizationType.standard, null);
		Organization newSub = wrapper.getOrganization();
		var user = userRepo.findByEmail(adminEmail).get();
		assertEquals(orgCount + 1, orgRepo.count());
		assertTrue(newSub.getInstances().size() == 1);
		assertEquals("test_org_db", SyncariContext.getDatabase());
		List<Instance> listInstances = service.listInstances(newSub.getId());
		assertEquals(1, listInstances.size());

		SyncariContext.push();
		Instance inst1 = null;
		Instance inst2 = null;
		try {
			SyncariContext.setOrganziation(newSub);
			SyncariContext.setInstance(newSub.getInstances().get(0));
			SyncariContext.setUser(user);
			inst1 = service.provisionInstance(newSub, "instancelimit1", "instancelimit1",
					InstanceType.sandbox, "default", user);
			inst2 = service.provisionInstance(newSub, "instancelimit2", "instancelimit2",
					InstanceType.sandbox, "default", user);
			try {
				service.provisionInstance(newSub, "instancelimit3", "instancelimit3",
						InstanceType.sandbox, "default", user);
				fail();
			} catch (SyncariValidationException e) {
				assertEquals("You have reached the max number of instances in this subscription. Please contact Syncari support to provision more instances.", e.getMessage());
			}
		} finally {
			service.deprovisionInstance(inst1.getSyncariId(), true);
			service.deprovisionInstance(inst2.getSyncariId(), true);
			SyncariContext.restore();
			assertEquals("test_org_db", SyncariContext.getDatabase());
		}

		service.deprovisionEventStore(newSub.getInstances().get(0).getSyncariId());
		service.deprovisionInstance(newSub.getInstances().get(0).getSyncariId(), true);
	}
	
	@Test
	public void provisionNonAdminAdedAsUser() {
		long orgCount = orgRepo.count();
		long usrCount = userRepo.count();
		assertEquals("test_org_db", SyncariContext.getDatabase());
		
		//create non admin user
		String adminEmail = "nonadmin@email.com";
		var nonadmin = new User();
		nonadmin.setFirstName("nonadmin");
		nonadmin.setLastName("nonadmin");
		nonadmin.setEmail(adminEmail);
		nonadmin.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
		nonadmin.setStatus(Status.ACTIVE);
        userRepo.save(nonadmin);
		
		doNothing().when(datastoreService).provision(any());
		service.datastoreService = datastoreService;
		ProvisioningResponse wrapper = service.provision("provisionTestInstanceAdmin", InstanceType.production, "provisionTestOrgAdmin", "provisionTestOrgAdmin",
				adminEmail, null, RoleConstants.ORG_ADMIN, "provisionTestFirstName", "provisionTestLastName", OrganizationType.standard, null);
		Organization newSub = wrapper.getOrganization();
		var user = userRepo.findByEmail(adminEmail).get();
		var instance = newSub.getInstances().get(0);
		planRepo.findByName("default");
		assertEquals(orgCount+1, service.listOrg().size());
		assertEquals(orgCount + 1, orgRepo.count());
		assertTrue(newSub.getInstances().size() == 1);
		//only admin user is created
		assertTrue(userRepo.count() == (usrCount+1));
		assertEquals(Status.ACTIVE, user.getStatus());
		assertFalse(user.isAdmin());
		assertEquals("test_org_db", SyncariContext.getDatabase());
		List<Instance> listInstances = service.listInstances(newSub.getId());
		assertEquals(1, listInstances.size());

		// check if the new instance is added to user's availableInstances
		assertEquals(1, user.getAvailableInstances().size());
		assertTrue(user.getAvailableInstances().contains(instance.getSyncariId()));

		SyncariContext.push();
        try {
            SyncariContext.setOrganziation(newSub);
            SyncariContext.setInstance(instance);
			assertEquals(String.format("provisiontestinstanceadmin_%s_db", SyncariContext.getInstance().getSyncariId()),
					SyncariContext.getDatabase());
            assertEquals(2, userRoleRepo.count());
            var userRole = userRoleRepo.findByUserId(user.getId()).get();
            assertEquals(1, userRole.getRoleIds().size());
            assertTrue(userRole.getRoleIds().contains(roleRepo.findByName(RoleConstants.ORG_ADMIN).get().getId()));
        } finally {
            SyncariContext.restore();
            assertEquals("test_org_db", SyncariContext.getDatabase());
            userRepo.delete(nonadmin);
        }
		service.deprovisionInstance(newSub.getInstances().get(0).getSyncariId(), true);
		service.deprovisionEventStore(newSub.getInstances().get(0).getSyncariId());

	}

	@Test
	public void provisionValidInstaceInSameOrg() {
		long orgCount = orgRepo.count();
		long usrCount = userRepo.count();
		assertEquals("test_org_db", SyncariContext.getDatabase());
		SyncariContext.getOrganziation().getInstances().forEach(x -> log.info("Found Instance {}--{}", x.getName(), x.getDisplayName()));
		log.info("Available Instances: " + SyncariContext.getOrganziation().getInstances());
		assertEquals(1, SyncariContext.getOrganziation().getInstances().size());

		User user = SyncariContext.getUser();
		int availableInstanceSizeBefore = user.getAvailableInstances().size();
        doNothing().when(datastoreService).provision(any());
        service.datastoreService = datastoreService;
		Instance newInstance = service.provisionInstance(SyncariContext.getOrganziation(), "testInstance", "testInstance", InstanceType.sandbox, "default", user);


		user = userRepo.findById(user.getId()).get();
		Organization org = orgRepo.findById(SyncariContext.getOrganziation().getId()).get();

		assertEquals(orgCount, service.listOrg().size());
		assertEquals(orgCount, orgRepo.count());
		assertEquals(2, org.getInstances().size());
		//No new user
		assertTrue(userRepo.count() == usrCount);
		assertEquals("test_org_db", SyncariContext.getDatabase());

		// check if the new instance is added to user's availableInstances
		assertEquals(availableInstanceSizeBefore+1, user.getAvailableInstances().size());
		assertTrue(user.getAvailableInstances().contains(newInstance.getSyncariId()));
		assertNotEquals(newInstance.getSyncariId(), user.getCurrentInstanceId());

		SyncariContext.push();
		try {
			SyncariContext.setInstance(newInstance);
			assertEquals(String.format("testinstance_%s_db", SyncariContext.getInstance().getSyncariId()), SyncariContext.getDatabase());
			assertEquals(2, userRoleRepo.count());
			var userRole = userRoleRepo.findByUserId(user.getId()).get();
			assertEquals(1, userRole.getRoleIds().size());
			assertTrue(userRole.getRoleIds().contains(roleRepo.findByName(RoleConstants.ORG_ADMIN).get().getId()));
		} finally {
			SyncariContext.restore();
			assertEquals("test_org_db", SyncariContext.getDatabase());
		}
		service.deprovisionInstance(newInstance.getSyncariId(), true);
		service.deprovisionEventStore(newInstance.getSyncariId());
		assertTrue(userService.getUserActiveInstances(user).stream()
				.filter(inst -> inst.getName().equals("testInstance")).findFirst().isEmpty());
		assertTrue(service.listOrg().stream()
				.filter(org1 -> org1.getName().equals(SyncariContext.getOrganziation().getName())).findFirst().get()
				.getInstances().stream().filter(inst -> inst.getName().equals("testInstance")).findFirst().isEmpty());
	}

	@Test
	public void reinviteUserValidations() {
		try {
			service.reinviteUser("123");
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("User with Id 123 not found"));
		}
		try {
			service.reinviteUser(SyncariContext.getUser().getId());
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("User cannot be re-invited, the user is probably active or deleted"));
		}
	}
	
	@Test
	public void reinviteUser() {
		String adminEmail = "admintest1@email.com";
		long inviteCount = inviteRepo.count();
        doNothing().when(datastoreService).provision(any());
        service.datastoreService = datastoreService;
		ProvisioningResponse wrapper = service.provision("provisionTestInstance1", InstanceType.production, "provisionTestOrg1", "provisionTestInstance1", adminEmail, null, RoleConstants.ORG_ADMIN, "provisionTestFirstName", "provisionTestLastName",
				OrganizationType.standard, null);
		Organization newSub = wrapper.getOrganization();
		assertEquals(inviteCount+1, inviteRepo.count());
		String userId = userRepo.findByEmail(adminEmail).get().getId();
		String inviteId = inviteRepo.findByUserId(userId).get().getId();
		
		service.reinviteUser(userId);
		assertEquals(inviteCount+1, inviteRepo.count());
		assertNotEquals(inviteId, inviteRepo.findByUserId(userId).get().getId());
		service.deprovisionInstance(newSub.getInstances().get(0).getSyncariId(),true);
		service.deprovisionEventStore(newSub.getInstances().get(0).getSyncariId());
	}
	
	@Test
	public void encryptServiceCred() {
	    ServiceCredential credential = new ServiceCredential().setApiKey("test").setName("test").setCredentialType(ServiceCredentialType.ENRICH);
	    credential = service.addServiceCredential(credential);
	    assertNotEquals("test", credential.getApiKey());
	}
	
	@Test
	public void decryptServiceCred() {
	    ServiceCredential credential = new ServiceCredential().setApiKey("test").setName("test").setCredentialType(ServiceCredentialType.ENRICH);
	    credential = service.addServiceCredential(credential);
	    assertNotEquals("test", service.getCredentials(credential.getId()));
	}
	
	@Test
	public void encryptServiceCredNull() {
	    ServiceCredential credential = new ServiceCredential().setName("test").setCredentialType(ServiceCredentialType.ENRICH);
	    credential = service.addServiceCredential(credential);
	    assertNull(credential.getApiKey());
	}
	
	@Test
	public void decryptServiceCredNull() {
	    ServiceCredential credential = new ServiceCredential().setName("test").setCredentialType(ServiceCredentialType.ENRICH);
	    credential = service.addServiceCredential(credential);
        assertNull(credential.getApiKey());
	}

	@Test
	public void deprovisonInstance(){
		var orgDatastoreService = service.datastoreService;
		var orgEventStore = service.eventStore;
		try {

			DatastoreService mockDatastoreService = mock(DatastoreService.class);
			EventStore mockEventStore = mock(EventStore.class);
			service.datastoreService = mockDatastoreService;
			service.eventStore = mockEventStore;
			Organization org = SyncariContext.getOrganziation();
			User user = SyncariContext.getUser();
			user.setSystemUser(false);
			userRepo.save(user);
			assertEquals("test_org_instance", SyncariContext.getInstance().getName());

			List<User> admins = userService.getAdmins();
			assertFalse(admins.isEmpty());

			doNothing().when(emailService).sendText(anyList(), anyString(), anyString());
			Instance newInstance = service.provisionInstance(org, "newTestInstanceToDeprovision",
					"New Test Instance", InstanceType.trial, "default", user);

			user = userService.getUserById(user.getId());
			assertTrue(user.getAvailableInstances().contains(newInstance.getSyncariId()));

			doNothing().when(mockDatastoreService).deprovision(newInstance.getSyncariId());
			doNothing().when(mockEventStore).deprovision(newInstance.getSyncariId());
			service.deprovisionInstance(newInstance.getSyncariId(), false);

			org = subService.getOrgBySyncariId(SyncariContext.getInstance().getSyncariId());
			assertTrue(org.getInstance(newInstance.getSyncariId()).isEmpty());
			verify(mockDatastoreService).deprovision(newInstance.getSyncariId());
			verify(mockEventStore).deprovision(newInstance.getSyncariId());
			verify(emailService).sendText(anyList(), anyString(), anyString());

			user = userService.getUserById(user.getId());
			assertFalse(user.getAvailableInstances().contains(newInstance.getSyncariId()));
			user.setSystemUser(true);
			userRepo.save(user);
		} finally {
			service.datastoreService = orgDatastoreService;
			service.eventStore = orgEventStore;
		}

	}

	@Test
	public void deprovisonInstanceWithDatastore(){
		var orgEventStore = service.eventStore;
		try {

			EventStore mockEventStore = mock(EventStore.class);
			service.eventStore = mockEventStore;
			Organization org = SyncariContext.getOrganziation();
			User user = SyncariContext.getUser();
			assertEquals("test_org_instance", SyncariContext.getInstance().getName());

			List<User> admins = userService.getAdmins();
			assertFalse(admins.isEmpty());

			doNothing().when(emailService).sendText(anyList(), anyString(), anyString());
			Instance newInstance = service.provisionInstance(org, "newTestInstanceWithDatastore",
					"New Test Instance With Datastore", InstanceType.trial, "default", user);
			SyncariContext.runWithContext(org, newInstance, SyncariContext.getUser(), () -> {
				datastoreService.provision(newInstance.getSyncariId());
			});
			user = userService.getUserById(user.getId());
			user.setSystemUser(false);
			userRepo.save(user);
			assertTrue(user.getAvailableInstances().contains(newInstance.getSyncariId()));

			doNothing().when(mockEventStore).deprovision(newInstance.getSyncariId());
			service.deprovisionInstance(newInstance.getSyncariId(), false);

			org = subService.getOrgBySyncariId(SyncariContext.getInstance().getSyncariId());
			assertTrue(org.getInstance(newInstance.getSyncariId()).isEmpty());
			verify(mockEventStore).deprovision(newInstance.getSyncariId());
			verify(emailService).sendText(anyList(), anyString(), anyString());

			user = userService.getUserById(user.getId());
			assertFalse(user.getAvailableInstances().contains(newInstance.getSyncariId()));
			user.setSystemUser(true);
			userRepo.save(user);
		} finally {
			service.eventStore = orgEventStore;
		}

	}
	@Test
	@Ignore
	public void provisionTrial() {
		//assertTrue(repo.count() == 0);
		try {
			long count = repo.count();
			Cluster c = new Cluster();
			c.setHasSyncariDb(false);
			c.setHost("localhosttrial");
			c.setProvisionActive(true);
			c = repo.save(c);
			// no trial cluster defaults to existing
			Cluster existing = repo.findAll().get(0);
			Instance instance = service.createInstance("trialInstance", "Trial", InstanceType.trial, "default");
			assertTrue(instance.getResource(ResourceType.DATABASE).get().getConfiguration().get("clusterId").equalsIgnoreCase(existing.getId()));

			c.setTrial(true);
			c = repo.save(c);
			instance = service.createInstance("trialInstance", "Trial", InstanceType.trial, "default");
			assertTrue(instance.getResource(ResourceType.DATABASE).get().getConfiguration().get("clusterId").equalsIgnoreCase(c.getId()));
			repo.delete(c);
			assertTrue(repo.findById(c.getId()).isEmpty());
			assertTrue(repo.findActive().isEmpty());
			assertTrue(repo.count() == count);

		} finally {
			repo.deleteAll();
			clusterService.invalidateCache();
		}
	}
}
