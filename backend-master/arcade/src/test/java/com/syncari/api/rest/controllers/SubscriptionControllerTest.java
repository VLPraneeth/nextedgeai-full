package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.InstanceRequest;
import com.syncari.api.rest.controllers.data.UserRequest;
import com.syncari.api.rest.controllers.data.UserResponse;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Instance;
import com.syncari.core.model.InstanceState;
import com.syncari.core.model.Quota;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.QuotaType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.customer.FeatureRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.UserService;
import com.syncari.restutils.data.ProvisionRequest;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;

public class SubscriptionControllerTest extends AbstractSyncariTest {
	@Autowired
	SubscriptionController subController;
	@Autowired
	FeatureRepo featureRepo;
	@Autowired
	OrganizationRepo orgRepo;
	@Autowired
	ProvisioningService provisioningService;
	@Autowired
	UserService userService;
	@Autowired
	ObjectTransformer transformer;
	@Autowired
	ObjectMapper mapper;


	@After
	public void tearDown() {
		featureRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "admin", authorities = { PROVISION_ORG, DELETE_INSTANCE })
	public void provisionOrganizationWithDefaultPlan() {
		ProvisionRequest request = new ProvisionRequest("Demo Org", "Demo instance", "production",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "default", "test", null, null);
		var saved = subController.addOrganization(request);
		assertEquals("Demo Org", saved.getName());
		assertNotNull(saved.getId());
		var fromRepo = orgRepo.findByName("Demo Org");
		assertNotNull(fromRepo);
		assertEquals("Demo Org", fromRepo.get().getName());
		assertEquals(saved.getId(), fromRepo.get().getId());
		provisioningService.deprovisionInstance(fromRepo.get().getInstances().get(0).getSyncariId(), true);
		subController.deleteOrganization(fromRepo.get().getId());
	}
	
	@Test
	@WithMockUser(username = "admin", authorities = { PROVISION_ORG })
	public void provisionOrganizationWithExistingOrg() {
		ProvisionRequest request = new ProvisionRequest("Demo Org123", "Demo instance", "production",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "default", "test", null, null);
		subController.addOrganization(request);
		try {
			subController.addOrganization(request);
			fail();
		} catch (Exception e) {
			assertEquals("Organization with name Demo Org123 already exists", e.getMessage());
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = { PROVISION_ORG, ADD_INSTANCE, DELETE_INSTANCE })
	public void provisionOrgThenAddInstance() {
		ProvisionRequest request = new ProvisionRequest("Demo Org1", "Demo instance1", "production",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "default", "test", null, null);
		var saved = subController.addOrganization(request);
		assertEquals("Demo Org1", saved.getName());
		assertNotNull(saved.getId());

		var fromRepo = orgRepo.findByName("Demo Org1");
		assertNotNull(fromRepo);
		assertEquals("Demo Org1", fromRepo.get().getName());
		assertEquals(saved.getId(), fromRepo.get().getId());
		Optional<User> user = userService.findActiveUserByEmail("test@email.com");
		assertTrue(user.isPresent());

		try{
			Optional<User> userToActivate = userRepo.findByEmail("test1@email.com");
			assertTrue(userToActivate.isPresent());
			userService.activateUser(userToActivate.get().getId());

			SyncariContext.setOrganziation(fromRepo.get());
			Optional<User> userToLogin = userService.findActiveUserByEmail("test1@email.com");
			assertTrue(userToLogin.isPresent());

			SyncariContext.setUser(userToLogin.get());
			SyncariContext.setInstance(fromRepo.get().getInstances().get(0));

			Map<String, Set<String>> roles = userService.getUserRoles(userToLogin.get().getId());
			assertNotNull(roles);
			assertTrue(roles.values().stream().filter(x -> x.contains(RoleConstants.ORG_ADMIN)).collect(Collectors.toList()).size()==1);

			InstanceRequest instanceRequest = new InstanceRequest(fromRepo.get().getId(), "newInstance", "newInstanceDP",InstanceType.production, "default");

			Instance instance = subController.addInstance(instanceRequest);
			assertNotNull(instance);
			roles = userService.getUserRoles(userToLogin.get().getId());
			assertTrue(roles.values().stream().filter(x -> x.contains(RoleConstants.ORG_ADMIN)).collect(Collectors.toList()).size()==2);
			Optional<User> userToCheck = userService.findUserById(userToLogin.get().getId());
			assertTrue(userToCheck.isPresent());
			assertTrue(userToCheck.get().getAvailableInstances().contains(instance.getSyncariId()));
			userToLogin.get().setSuperAdmin(true);
		}finally {
			subController.deleteOrganization(saved.getId());
			Optional<User> user1 = userService.getUserByEmail("test1@email.com");
			user1.ifPresent(u -> userService.deleteUser(u.getId()));
			SyncariContext.restore();
		}

	}

	@Test
	@WithMockUser(username = "admin", authorities = { PROVISION_ORG, ADD_INSTANCE, DELETE_INSTANCE })
	public void provisionPartnerOrgWithInstancelimit() {
		ProvisionRequest request = new ProvisionRequest("Demo Org Partner", "Demo instance Partner", "production",
				OrganizationType.partner.name(), "Demo instance", "test2@email.com", "default", "test2", null, "2");
		var saved = subController.addOrganization(request);
		assertEquals("Demo Org Partner", saved.getName());
		assertNotNull(saved.getId());

		var fromRepo = orgRepo.findByName("Demo Org Partner");
		assertNotNull(fromRepo);
		assertEquals("Demo Org Partner", fromRepo.get().getName());
		assertEquals(saved.getId(), fromRepo.get().getId());

		try{
			Optional<User> userToActivate = userRepo.findByEmail("test2@email.com");
			assertTrue(userToActivate.isPresent());
			userService.activateUser(userToActivate.get().getId());

			SyncariContext.setOrganziation(fromRepo.get());
			Optional<User> userToLogin = userService.findActiveUserByEmail("test2@email.com");
			assertTrue(userToLogin.isPresent());

			SyncariContext.setUser(userToLogin.get());
			SyncariContext.setInstance(fromRepo.get().getInstances().get(0));

			Map<String, Set<String>> roles = userService.getUserRoles(userToLogin.get().getId());
			assertNotNull(roles);
			assertTrue(roles.values().stream().filter(x -> x.contains(RoleConstants.ORG_ADMIN)).collect(Collectors.toList()).size()==1);

			InstanceRequest instanceRequest = new InstanceRequest(fromRepo.get().getId(), "newInstance1", "newInstanceDP1",InstanceType.production, "default");

			Instance instance = subController.addInstance(instanceRequest);
			assertNotNull(instance);
			roles = userService.getUserRoles(userToLogin.get().getId());
			assertTrue(roles.values().stream().filter(x -> x.contains(RoleConstants.ORG_ADMIN)).collect(Collectors.toList()).size()==2);
			Optional<User> userToCheck = userService.findUserById(userToLogin.get().getId());
			assertTrue(userToCheck.isPresent());
			assertTrue(userToCheck.get().getAvailableInstances().contains(instance.getSyncariId()));
			InstanceRequest instanceRequest2 = new InstanceRequest(fromRepo.get().getId(), "newInstance2", "newInstanceDP2",InstanceType.production, "default");

			try{
				subController.addInstance(instanceRequest2);
				fail();
			}catch (SyncariValidationException e){
				userToLogin.get().setSuperAdmin(true);
				assertEquals("You have reached the max number of instances in this subscription. Please contact Syncari support to provision more instances.",e.getMessage());
			}

		}finally {
			subController.deleteOrganization(saved.getId());
			Optional<User> user1 = userService.getUserByEmail("test1@email.com");
			user1.ifPresent(u -> userService.deleteUser(u.getId()));
			SyncariContext.restore();
		}

	}
	@Test
	@WithMockUser(username = "admin", authorities = { PROVISION_TRIAL_ORG , LIST_INSTANCE_STATE})
	public void getInstanceStateTest() throws JsonProcessingException {
		ProvisionRequest request = new ProvisionRequest("Demo Org3", "Demo instance", "trial",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "trial", null, null, null);
		var wrapper = provisioningService.provision(
				request.getInstanceName(),
				InstanceType.trial,
				request.getInstanceDisplayName(),
				request.getOrganizationName(),
				request.getAdminUserName(),
				request.getPlanName(),
				RoleConstants.ORG_ADMIN,
				request.getAdminFirstName(),
				request.getAdminLastName(),
				OrganizationType.trial, null
		);
		var saved = wrapper.getOrganization();
		try{
			assertEquals("Demo Org3", saved.getName());
			assertNotNull(saved.getId());

			var fromRepo = orgRepo.findByName("Demo Org3");
			assertNotNull(fromRepo);
			assertEquals("Demo Org3", fromRepo.get().getName());
			assertEquals(saved.getId(), fromRepo.get().getId());
			assertEquals(1, saved.getInstances().size());
			String instanceId = saved.getInstances().get(0).getSyncariId();
			SyncariContext.setOrganziation(saved);
			SyncariContext.setInstance(saved.getInstances().get(0));
			InstanceState instanceState = subController.getInstanceState(instanceId);
			assertNotNull(instanceState);
			assertFalse(instanceState.isRefDataLimitExpired());
			assertFalse(instanceState.isPublishLimitExpired());
			assertFalse(instanceState.isRecordLimitExpired());
		}finally {
			provisioningService.deprovisionInstance(saved.getInstances().get(0).getSyncariId(), true);
			orgRepo.deleteById(saved.getId());
			Optional<User> user = userService.getUserByEmail("test1@email.com");
			user.ifPresent(u -> userService.deleteUser(u.getId()));
			SyncariContext.restore();
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = { PROVISION_ORG, SUB_EDIT })
	public void extendTrialTest(){
		ProvisionRequest request = new ProvisionRequest("Demo Org2", "Demo instance", "trial",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "trial", null, null, null);
		SyncariContext.getUser().setTimeZone("PST8PDT");
		var wrapper = provisioningService.provision(
				request.getInstanceName(),
				InstanceType.trial,
				request.getInstanceDisplayName(),
				request.getOrganizationName(),
				request.getAdminUserName(),
				request.getPlanName(),
				RoleConstants.ORG_ADMIN,
				request.getAdminFirstName(),
				request.getAdminLastName(),
				OrganizationType.trial, null
		);
		var saved = wrapper.getOrganization();
		try{
			assertEquals("Demo Org2", saved.getName());
			assertNotNull(saved.getId());

			var fromRepo = orgRepo.findByName("Demo Org2");
			assertTrue(fromRepo.isPresent());
			assertEquals("Demo Org2", fromRepo.get().getName());
			assertEquals(saved.getId(), fromRepo.get().getId());
			assertEquals(1, saved.getInstances().size());
			String instanceId = saved.getInstances().get(0).getSyncariId();

			boolean isExtended = subController.extendTrialInstance(instanceId,null,100);
			assertTrue(isExtended);
			fromRepo = orgRepo.findByName("Demo Org2");
			assertTrue(fromRepo.isPresent());
			assertEquals("Demo Org2", fromRepo.get().getName());
			assertEquals(saved.getId(), fromRepo.get().getId());
			assertEquals(1, fromRepo.get().getInstances().size());
			Instance fromDbInstance = fromRepo.get().getInstances().get(0);
			assertNotNull(fromDbInstance);
			List<Quota> limitQuota = fromDbInstance.getQuota().stream().filter(q -> q.getType() == QuotaType.RECORDS_LIMIT).collect(Collectors.toList());
			assertNotNull(limitQuota);
			assertEquals(1, limitQuota.size());
			assertEquals("10100", limitQuota.get(0).getValue());

			Calendar today = Calendar.getInstance();
			today.add(Calendar.DAY_OF_MONTH, 30);
			String pattern = "yyyy-MM-dd'T'HH:mm:ss";
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
			boolean isExtendedDate = subController.extendTrialInstance(instanceId,simpleDateFormat.format(today.getTimeInMillis()),0);
			assertTrue(isExtendedDate);
			fromRepo = orgRepo.findByName("Demo Org2");
			assertTrue(fromRepo.isPresent());
			assertEquals("Demo Org2", fromRepo.get().getName());
			assertEquals(saved.getId(), fromRepo.get().getId());
			assertEquals(1, fromRepo.get().getInstances().size());
			Instance secondCallfromDbInstance = fromRepo.get().getInstances().get(0);
			assertNotNull(secondCallfromDbInstance);
			limitQuota = fromDbInstance.getQuota().stream().filter(q -> q.getType() == QuotaType.TRIAL_DAYS_LIMIT).collect(Collectors.toList());
			assertNotNull(limitQuota);
			assertEquals(1, limitQuota.size());
			assertEquals("15", limitQuota.get(0).getValue());

			List<Quota> triaLimitQuota = secondCallfromDbInstance.getQuota().stream().filter(q -> q.getType() == QuotaType.TRIAL_DAYS_LIMIT).collect(Collectors.toList());
			assertNotNull(triaLimitQuota);
			assertEquals(1, triaLimitQuota.size());
			assertTrue( Long.valueOf(triaLimitQuota.get(0).getValue()) >= 29 );

			assertNotNull(saved);
		}finally {
			provisioningService.deprovisionInstance(saved.getInstances().get(0).getSyncariId(), true);
			orgRepo.deleteById(saved.getId());
			Optional<User> user = userService.getUserByEmail("test1@email.com");
			user.ifPresent(u -> userService.deleteUser(u.getId()));
		}


	}

	@Test
	@WithMockUser(username = "admin", authorities = { PROVISION_ORG })
	public void provisionOrganizationWithLongOrgName() {
		ProvisionRequest request = new ProvisionRequest("chromedriver_chrome_on_windows_09df0365e74c3a9504ebcea68f151ba6_2022030406541", "Demo instance", "production",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "default", "test", null, null);
		try{
			var saved = subController.addOrganization(request);
			assertTrue(saved.getName().length()==30);
		}catch (SyncariValidationException exception){
			fail();
		}

		request = new ProvisionRequest("Demo Org", "chromedriver_chrome_on_windows_09df0365e74c3a9504ebcea68f151ba6_2022030406541", "production",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "default", "test", null, null);
		try{
			var saved = subController.addOrganization(request);
			fail();
		}catch (SyncariValidationException exception){
			assertTrue(exception.getMessage().contains("Instance"));
			assertTrue(exception.getMessage().contains("more"));
		}
		request = new ProvisionRequest("", "chromedriver_chrome_on_windows_09df0365e74c3a9504ebcea68f151ba6_2022030406541", "production",
				OrganizationType.standard.name(), "Demo instance", "test1@email.com", "default", "test", "test", null);
		try{
			var saved = subController.addOrganization(request);
			fail();
		}catch (SyncariValidationException exception){
			assertTrue(exception.getMessage().contains("empty"));
		}

	}

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_INSTANCE })
	public void provisionInstanceWithLongName() {
		InstanceRequest request = new InstanceRequest("test","chromedriver_chrome_on_windows_09df0365e74c3a9504ebcea68f151ba6_2022030406541", "Demo instance", InstanceType.production,
				"default");
		try{
			var saved = subController.addInstance(request);
			fail();
		}catch (SyncariValidationException exception){
			assertTrue(exception.getMessage().contains("more"));
			assertTrue(exception.getMessage().contains("Instance"));
		}
		request = new InstanceRequest("test","", "Demo instance", InstanceType.production,
				"default");
		try{
			var saved = subController.addInstance(request);
			fail();
		}catch (SyncariValidationException exception){
			assertTrue(exception.getMessage().contains("empty"));
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_INSTANCE, EDIT_INSTANCE })
	public void provisionInstanceAndEdit() {
		InstanceRequest request = new InstanceRequest("test","editinstance", "Demo instance1", InstanceType.production,
				"default");
		var saved = subController.addInstance(request);
		request.setType(InstanceType.sandbox);
		request.setSyncariId(saved.getSyncariId());
		saved = subController.editInstance(request);
		assertEquals(InstanceType.sandbox, saved.getType());
	}
	
	@Test
	@WithMockUser(username = "admin", authorities = { LIST_USER })
	public void list() {
		List<UserResponse> users = subController.list();
		users.forEach(u -> {
			assertTrue(!"System User".equalsIgnoreCase(u.getLastName()));
		});
	}

	@Test
	@WithMockUser(username = "admin", authorities = { INVITE_USER })
	public void addApiUser() {
		// create user request
		UserRequest ur = new UserRequest();
		ur.setSuperAdmin(false);
		ur.setAdmin(true);
		ur.setApiUser(true);
		ur.setEmail("apiuser@apiuser.com");
		ur.setFirstName("apiuser");
		ur.setLastName("apiuser");

		// call controller
		subController.invite(ur);

		// get new user data and user request data
		User apiUser = userService.getUser(ur.getEmail());
		UserResponse userDTO = transformer.toUserResponse(apiUser);

		//verify user data
		assertEquals("ACTIVE", userDTO.getStatus().toString());
		assertTrue(userDTO.getIsApiUser());
		assertNotNull(userDTO.getClientId());
		assertNotNull(userDTO.getClientSecret());
		assertNotNull(apiUser.getClientSecret());

		// adding new user where there is another user exist with same email
		UserRequest user2 = new UserRequest();
		user2.setSuperAdmin(false);
		user2.setAdmin(true);
		user2.setApiUser(true);
		user2.setEmail("apiuser@apiuser.com");
		user2.setFirstName("apiuser2");
		user2.setLastName("apiuser2");

		try {
			subController.invite(user2);
			fail();
		} catch (Exception e) {
			assertEquals("User with email "+user2.getEmail()+" already exists", e.getMessage());
		}

	}

	@Test
	@WithMockUser(username = "admin", authorities = { INVITE_USER })
	public void addNonApiUser() {
		// create user request
		UserRequest ur = new UserRequest();
		ur.setSuperAdmin(false);
		ur.setAdmin(true);
		ur.setEmail("nonapiuser@nonapiuser.com");
		ur.setFirstName("nonapiuser");
		ur.setLastName("nonapiuser");

		// call controller
		subController.invite(ur);

		// get new user data and user request data
		User apiUser = userService.getUser(ur.getEmail());
		UserResponse userDTO = transformer.toUserResponse(apiUser);

		//verify user data
		assertEquals("PENDING", userDTO.getStatus().toString());
		assertFalse(userDTO.getIsApiUser());
		assertNull(userDTO.getClientId());
		assertNull(userDTO.getClientSecret());

	}

	@Test
	@WithMockUser(username = "admin", authorities = { INVITE_USER })
	public void addSuperAdminUser() {
		// create user request
		UserRequest ur = new UserRequest();
		ur.setSuperAdmin(true);
		ur.setAdmin(true);
		ur.setEmail("superadminuser@nonapiuser.com");
		ur.setFirstName("superadminuser");
		ur.setLastName("superadminuser");

		// call controller
		subController.invite(ur);

		// get new user data and user request data
		User apiUser = userService.getUser(ur.getEmail());
		UserResponse userDTO = transformer.toUserResponse(apiUser);

		//verify user data
		assertEquals("PENDING", userDTO.getStatus().toString());
		assertTrue(userDTO.getIsSuperAdmin());
	}
	
	@Test
	@WithMockUser(username = "admin", authorities = { INVITE_USER })
	public void updateUser() {
		// create user request
		UserRequest ur = new UserRequest();
		ur.setSuperAdmin(false);
		ur.setAdmin(false);
		ur.setOrgAdmin(false);
		ur.setEmail("orgadmintest@orgadmintest.com");
		ur.setFirstName("orgadmintest");
		ur.setLastName("orgadmintest");

		// call controller
		subController.invite(ur);

		// get new user data and user request data
		User user = userService.getUser(ur.getEmail());
		userService.activateUser(user.getId());
		user = userService.getUser(ur.getEmail());
		UserResponse userDTO = transformer.toUserResponse(user);

		//verify user data
		assertEquals("ACTIVE", userDTO.getStatus().toString());
		ur.setOrgAdmin(true);
		ur.setUserRoles(Map.of(SyncariContext.getSyncariId(), new HashSet<String>()));
		subController.updateUser(user.getId(), ur);
		assertTrue(userService.getUserRoleForInstance(user.getId(), SyncariContext.getInstance()).contains(RoleConstants.ORG_ADMIN));
		
		ur.setOrgAdmin(false);
		ur.setUserRoles(Map.of(SyncariContext.getSyncariId(), Set.of(RoleConstants.VIEWER)));
		subController.updateUser(user.getId(), ur);
		assertFalse(userService.getUserRoleForInstance(user.getId(), SyncariContext.getInstance()).contains(RoleConstants.ORG_ADMIN));

	}
}
