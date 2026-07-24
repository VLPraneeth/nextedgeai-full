package com.syncari.api.rest.controllers;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Role;
import com.syncari.core.model.UserRole;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.repositories.customer.UserRoleRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.UserService;
import io.jsonwebtoken.Jwts;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.api.rest.controllers.data.UserResponse;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.ProvisioningService;

public class UserControllerTest extends AbstractSyncariTest{
	@Autowired
	ProvisioningController userController;
	@Autowired
	UserRepo userRepo;
	@Autowired
	ProfileController profileController;
	@Autowired
	ProvisioningService provisioningService;
	@Autowired
	RoleRepo roleRepo;
	@Autowired
	UserRoleRepo userRoleRepo;
	@Autowired
	SyncariContextHandler synCtxHandler;
	@Autowired
	UserService userService;
	@Autowired
	AppConfig appConfig;


	@Autowired
	Util util;
	
    @Override
    public void tearDown() {
    }

	@Test
	public void addUser() {
//		long existingCount = userRepo.count();
//		User user = new User("dummy@email.com", "test", "dummyorgid");
//		user.setFirstName("first name");
//		user.setLastName("last name");
// 		user.setTimeZone("America/Los_Angeles");
//		User savedUser = userController.addUser(user);
//		assertEquals(existingCount + 1, userRepo.count());
//		assertNotNull(savedUser.getId());
//
//		User persisted = userRepo.findByEmail("dummy@email.com").get();
//		assertEquals("dummy@email.com", persisted.getEmail());
//		assertEquals("first name", persisted.getFirstName());
//		assertEquals("last name", persisted.getLastName());
//		assertEquals("America/Los_Angeles", persisted.getTimeZone());
//		assertNotNull(persisted.getCreatedAt());
////		TODO set created by for saved user assertNotNull(persisted.getCreatedBy());
//		assertNull(persisted.getUpdatedAt());
//		assertNull(persisted.getUpdatedBy());
//
//		User byEmail = userRepo.findByEmail("dummy@email.com").get();
//		assertEquals("dummy@email.com", byEmail.getEmail());
//		assertEquals("first name", byEmail.getFirstName());
//		assertEquals("last name", byEmail.getLastName());
//		assertNotNull(byEmail.getCreatedAt());
////		TODO set created by for saved user assertNotNull(persisted.getCreatedBy());
//		assertNull(byEmail.getUpdatedAt());
//		assertNull(byEmail.getUpdatedBy());
	}

	/*@Test
	@WithMockUser(username = "admin", authorities = { ADD_USR })
	public void addUserValidations() {
		User user = new User(null, null, null, null);
		user.setFirstName("first name");
		user.setLastName("last name");
		try {
			userController.addUser(user);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("User email is required"));
			assertTrue(e.getMessage().contains("User org is required"));
			assertTrue(e.getMessage().contains("User password is required"));
		}
		
		try {
			userController.addUser(new User("invalid-email", "test", "dummyorgid"));
			fail();
		} catch (Exception e) {
			assertEquals("email: must be a well-formed email address", e.getMessage());
		}
		
		try {
			userController.addUser(new User( "test@email.com", "test", "dummyorgid"));
			userController.addUser(new User("test@email.com", "test", "dummyorgid"));
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("dup key"));
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_USR, RESET_PWD })
	public void changePassword() {
		User user = new User("dummy@email.com", "test", "dummyorgid");
		User savedUser = userController.addUser(user);
		profileController.resetPassword(savedUser.getId(), new ResetPasswordRequest("test", "changed"));
		User persisted = userRepo.findByEmail("dummy@email.com").get();
		assertTrue(new BCryptPasswordEncoder().matches("changed", persisted.getPassword()));
		userRepo.delete(persisted);
	}*/

	@Test
	public void testEncod(){

		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		System.out.println(encoder.encode("H`7Ev'?zK="));
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {})
	public void switchInstanceInvalidSyncariId(){

		try {
			profileController.switchInstance("", null,null);
			fail("switchInstance to invalid syncariId should fail");
		} catch (Exception e){
			assertEquals("Please provide a valid Instance ID", e.getMessage());
		}
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {})
	public void switchInstanceValid(){
		Organization org = SyncariContext.getOrganziation();
		User user = SyncariContext.getUser();
		try {
			user.setSystemUser(false);
			userRepo.save(user);
			assertEquals("test_org_instance", SyncariContext.getInstance().getName());
			SyncariContext.push();
			List<Instance> activeInstances = org.getActiveInstances();

			Instance newInstance = provisioningService.provisionInstance(org, "newInstance", "New Inst", InstanceType.trial, "default", user);
			HttpServletResponse resp = new MockHttpServletResponse();

			String previousToken = util.getToken(user.getEmail(),List.of(),false, UUID.randomUUID().toString());
			UserResponse response = profileController.switchInstance(newInstance.getSyncariId(),previousToken, resp);

			assertEquals("newInstance", SyncariContext.getInstance().getName());
			assertEquals("New Inst", response.getCurrentInstanceName());
			assertEquals(newInstance.getSyncariId(), response.getCurrentInstanceNextEdgeId());
			assertEquals(newInstance.getSyncariId(), userService.getUser(user.getEmail()).getCurrentInstanceId());
			provisioningService.deprovisionInstance(newInstance.getSyncariId(), true);
			User updatedUser = userService.getUser(user.getEmail());
			assertFalse(updatedUser.getCurrentInstanceId().isEmpty());
			assertNotEquals(newInstance.getSyncariId(), updatedUser.getCurrentInstanceId());
		}finally {
			SyncariContext.restore();
			assertEquals("test_org_instance", SyncariContext.getInstance().getName());
			user.setSystemUser(true);
			userRepo.save(user);
		}
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {})
	public void switchInstance_WithDifferentRoles(){

		Organization org = SyncariContext.getOrganziation();
		User user = SyncariContext.getUser();
		assertEquals("test_org_instance", SyncariContext.getInstance().getName());
		Instance newInstance = provisioningService.provisionInstance(org, "instanceSwitchWithRole", "New Inst", InstanceType.trial, "default", user);
		try {
			SyncariContext.push();
			// just keep viewer role for user in new instance
			SyncariContext.runWithContext(org, newInstance, user, () -> {
				Role viewerRole = roleRepo.findByName(RoleConstants.VIEWER).get();
				UserRole userRole = userRoleRepo.findByUserId(user.getId()).get();
				userRole.setRoleIds(Set.of(viewerRole.getId()));
				userRoleRepo.save(userRole);

				// set superAdmin to false temporarily for this test. gets reset in finally block
				user.setSuperAdmin(false);
				userRepo.save(user);
			});

			HttpServletResponse resp = new MockHttpServletResponse();
			String previousToken = util.getToken(user.getEmail(),List.of(RoleConstants.VIEWER),false, UUID.randomUUID().toString());
			UserResponse response = profileController.switchInstance(newInstance.getSyncariId(),previousToken, resp);

			assertEquals("instanceSwitchWithRole", SyncariContext.getInstance().getName());
			assertEquals("New Inst", response.getCurrentInstanceName());
			assertEquals(newInstance.getSyncariId(), response.getCurrentInstanceNextEdgeId());

			var authToken = resp.getHeader(SecurityConstants.TOKEN_HEADER);
			List<String> permissions = extractPermissionsFromToken(authToken);
			assertEquals(permissions.size(), Permissions.viewerPermissions().size() + 1);
		}finally {
			SyncariContext.restore();
			// viewer role not able to deprovision
			user.setSuperAdmin(true);
			userRepo.save(user);
			provisioningService.deprovisionInstance(newInstance.getSyncariId(), true);
			assertEquals("test_org_instance", SyncariContext.getInstance().getName());
		}
	}
	
	@Test
	@WithMockUser(username = "test@email.com", authorities = {})
	public void switchInstanceValidNoDisplayName(){
	    
	    try {
	        Organization org = SyncariContext.getOrganziation();
	        User user = SyncariContext.getUser();

	        assertEquals("test_org_instance", SyncariContext.getInstance().getName());
	        SyncariContext.push();

	        Instance newInstance = provisioningService.provisionInstance(org, "newInstanceNoDisplay", null, InstanceType.trial, "default", user);
	        HttpServletResponse resp = new MockHttpServletResponse();
			String previousToken = util.getToken(user.getEmail(),List.of(),false, UUID.randomUUID().toString());
			UserResponse response = profileController.switchInstance(newInstance.getSyncariId(), previousToken,resp);
	        
	        assertEquals("newInstanceNoDisplay", SyncariContext.getInstance().getName());
	        assertEquals("newInstanceNoDisplay", response.getCurrentInstanceName());
	        assertEquals(newInstance.getSyncariId(), response.getCurrentInstanceNextEdgeId());
	        provisioningService.deprovisionInstance(newInstance.getSyncariId(), true);
	        org = SyncariContext.getOrganziation();
	        List<Instance> activeInstances = org.getActiveInstances();
	    } finally {
	        SyncariContext.restore();
	        assertEquals("test_org_instance", SyncariContext.getInstance().getName());
	    }
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {})
	public void switchInstanceDifferentOrg(){
		Organization org = SyncariContext.getOrganziation();
		User user = SyncariContext.getUser();
		try {
			user.setSystemUser(false);
			userRepo.save(user);
			assertEquals("test_org_instance", SyncariContext.getInstance().getName());

			SyncariContext.push();

			// create new org and instance
			var org2 = new Organization("New Org");
			org2 = organizationRepo.save(org2);

			Instance newInstance = provisioningService.provisionInstance(org2, "newInstance", "newInstance", InstanceType.trial, "default", user);
			HttpServletResponse resp = new MockHttpServletResponse();
			String previousToken = util.getToken(user.getEmail(),List.of(),false, UUID.randomUUID().toString());
			UserResponse response = profileController.switchInstance(newInstance.getSyncariId(), previousToken,resp);

			assertEquals("newInstance", SyncariContext.getInstance().getName());
			assertEquals("newInstance", response.getCurrentInstanceName());
			assertEquals(newInstance.getSyncariId(), response.getCurrentInstanceNextEdgeId());
			assertEquals("New Org", SyncariContext.getOrganziation().getName());
			assertEquals(newInstance.getSyncariId(), userService.getUser(user.getEmail()).getCurrentInstanceId());
			provisioningService.deprovisionInstance(newInstance.getSyncariId(), true);
			User updatedUser = userService.getUser(user.getEmail());
			assertFalse(updatedUser.getCurrentInstanceId().isEmpty());
			assertNotEquals(newInstance.getSyncariId(), updatedUser.getCurrentInstanceId());
		}finally {
			// delete both the instances
			SyncariContext.restore();
			//this will reload the context from DB
	        synCtxHandler.setContext(SyncariContext.getInstance().getSyncariId());
			assertEquals("test_org_instance", SyncariContext.getInstance().getName());
			user.setSystemUser(true);
			userRepo.save(user);
		}
	}

	@Test
	@WithMockUser(username = "test@email.com", authorities = {})
	public void listInstances() throws Exception {
		Organization org = SyncariContext.getOrganziation();
		User user = SyncariContext.getUser();
		Instance newInstance1 = provisioningService.provisionInstance(org, "newInstance1", "newInstance1", InstanceType.trial, "default", user);
		assertTrue(profileController.listUserInstances().stream().map(i->i.getSyncariId()).collect(Collectors.toList()).contains(newInstance1.getSyncariId()));
		Instance newInstance2 = provisioningService.provisionInstance(org, "newInstance2", "newInstance2", InstanceType.trial, "default", user);
		assertTrue(profileController.listUserInstances().stream().map(i->i.getSyncariId()).collect(Collectors.toList()).contains(newInstance2.getSyncariId()));
	}

	private List<String> extractPermissionsFromToken(String token){
		var signingKey = appConfig.getJwtSecret().getBytes();
		var parsedToken = Jwts.parser()
				.setSigningKey(signingKey)
				.parseClaimsJws(token.replace("Bearer ", ""));

		return (List<String>) parsedToken.getBody().get("rol");
	}
}
