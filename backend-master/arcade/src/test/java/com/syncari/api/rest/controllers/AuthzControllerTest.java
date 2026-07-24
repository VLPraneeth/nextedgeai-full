package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ADD_PRIV_TO_ROLE;
import static com.syncari.core.security.Permissions.ADD_ROLE;
import static com.syncari.core.security.Permissions.DELETE_ROLE;
import static com.syncari.core.security.Permissions.LIST_ROLES;
import static com.syncari.core.security.Permissions.REMOVE_PRIV_FROM_ROLE;
import static com.syncari.core.security.Permissions.EDIT_ROLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.api.rest.controllers.data.RoleRequestDTO;
import com.syncari.api.rest.controllers.data.RoleResponseDTO;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Privilege;
import com.syncari.core.model.Role;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.customer.PrivilegeRepo;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.EventService;
import com.syncari.core.service.authz.AuthzService;

public class AuthzControllerTest extends AbstractSyncariTest {
	@Autowired
	AuthzController authzController;
	@Autowired
	AuthzService authzService;
	@Autowired
	UserRepo userRepo;
	@Autowired
	RoleRepo roleRepo;
	@Autowired
	ProvisioningController userController;
	@Autowired
	PrivilegeRepo privRepo;
	@Mock
	EventService eventService;

	@Before
	public void setUp() {
		super.setUp();
		doNothing().when(eventService).log(any());
		authzController.eventService = eventService;
	}
	
    @Override
    public void tearDown() { 
    }

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_ROLE })
	public void roleValidations() {
		try {
			authzController.addRole(null);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Role details cannot be empty."));
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_ROLE })
	public void addRole() {
		RoleRequestDTO dto = new RoleRequestDTO()
				.setName("sfdc-user")
				.setActive(true)
				.setDescription("sfdc-user");
		RoleResponseDTO res = authzController.addRole(dto);
		Role persisted = roleRepo.findByName("sfdc-user").get();
		assertEquals("sfdc-user", persisted.getName());
		assertNotNull(persisted.getCreatedAt());
		assertNotNull(persisted.getId());
		assertNotNull(persisted.getUpdatedAt());
		assertNotNull(persisted.getUpdatedBy());
	}

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_ROLE, DELETE_ROLE })
	public void deleteRole() {
		RoleRequestDTO dto = new RoleRequestDTO()
				.setName("sfdc-user1")
				.setActive(true)
				.setDescription("sfdc-user1");
	    authzController.addRole(dto);
        Role persisted = roleRepo.findByName("sfdc-user1").get();
        assertEquals("sfdc-user1", persisted.getName());
        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getId());
        assertNotNull(persisted.getUpdatedAt());
        assertNotNull(persisted.getUpdatedBy());

		authzController.deleteRole(persisted.getId());
		assertTrue(roleRepo.findByName("sfdc-user1").isEmpty());
	}

//	@Test
//	@WithMockUser(username = "admin", authorities = { ADD_ROLE, ADD_ROLE_TO_USR, REMOVE_ROLE_FROM_USR, ADD_USR })
//	public void testAddDeleteRoleToUser() {
//		addRole();
//		Role role = roleRepo.findAll().get(0);
//
//		User user = userController.addUser(new User("dummy@email.com", "test", "dummyorgid"));
//		authzController.addRoleToUser(user.getId(), role.getId());
//		User persistedUser = userRepo.findByEmail("dummy@email.com").get();
//		assertEquals(1, persistedUser.getRoleIds().size());
//
//		authzController.removeRoleFromUser(user.getId(), role.getId());
//		persistedUser = userRepo.findAll().get(0);
//		assertEquals(0, persistedUser.getRoleIds().size());
//	}

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_ROLE, ADD_PRIV_TO_ROLE, REMOVE_PRIV_FROM_ROLE })
	public void testAddDeletePrivilegeToRole() {
		RoleRequestDTO dto = new RoleRequestDTO()
				.setName("sfdc-user5")
				.setActive(true)
				.setDescription("sfdc-user5");
	    authzController.addRole(dto);

		Role persisted = roleRepo.findByName("sfdc-user5").get();
		assertEquals(4, persisted.getPrivileges().size());

		Privilege priv = new Privilege("Activate sync");
		priv = privRepo.insert(priv);
		assertNotNull(priv.getId());

		authzController.addPrivilegeToRole(persisted.getId(), priv.getId());
		persisted = roleRepo.findByName("sfdc-user5").get();
        assertEquals(5, persisted.getPrivileges().size());

		authzController.removePrivilegeFromRole(persisted.getId(), priv.getId());
		persisted = roleRepo.findByName("sfdc-user5").get();
        assertEquals(4, persisted.getPrivileges().size());
	}

	@Test
	@WithMockUser(username = "admin", authorities = { ADD_ROLE, LIST_ROLES })
	public void getRoles() {
		Set<RoleResponseDTO> roles = authzController.list();
		RoleRequestDTO dto = new RoleRequestDTO()
				.setName("sfdc-user3")
				.setActive(true)
				.setDescription("sfdc-user3");
		authzController.addRole(dto);
		roles = authzController.list();
		assertTrue(roles.stream().filter(r -> r.getName().equals("sfdc-user3")).count() == 1);
		
		var role = authzController.getRole(roles.stream().filter(r -> r.getName().equals("sfdc-user3")).findFirst().get().getId());
		assertNotNull(role);
		assertEquals("sfdc-user3", role.getName());
	}
	
	@Test
	@WithMockUser(username = "admin", authorities = { ADD_ROLE, EDIT_ROLE })
	public void editRole() {
		var user = userRepo.findByEmail("test@email.com");
		RoleRequestDTO dto = new RoleRequestDTO()
				.setName("sfdc-user6")
				.setActive(true)
				.setDescription("sfdc-user6")
				.setUsers(List.of(user.get().getId()));
		RoleResponseDTO res = authzController.addRole(dto);
		Role persisted = roleRepo.findByName("sfdc-user6").get();
		assertEquals("sfdc-user6", persisted.getName());
		assertNotNull(persisted.getCreatedAt());
		assertNotNull(persisted.getId());
		assertEquals(1, res.getUsers().size());
		assertNotNull(persisted.getUpdatedAt());
		assertNotNull(persisted.getUpdatedBy());
		
		dto.setUsers(List.of(user.get().getId()));
		dto.setActive(false);
		res = authzController.editRole(res.getId(), dto);
		assertEquals(0, res.getUsers().size());
	}
	
	@Test
	@WithMockUser(username = "admin", authorities = { ADD_ROLE, LIST_ROLES })
	public void getAllRoles() {
		assertEquals(1, authzService.listRoles().stream().filter(r -> r.getName().equals(RoleConstants.ORG_ADMIN)).count());
		var roles = authzController.listAll();
		assertEquals(0, roles.get(SyncariContext.getSyncariId()).stream().filter(r -> r.getName().equals(RoleConstants.ORG_ADMIN)).count());
	}

}
