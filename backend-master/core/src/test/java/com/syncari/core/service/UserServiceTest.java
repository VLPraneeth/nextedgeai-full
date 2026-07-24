package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.repositories.customer.UserPreferenceRepo;
import com.syncari.core.repositories.customer.UserRoleRepo;
import com.syncari.core.repositories.syncari.UserInvitationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.security.Permissions;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static com.syncari.core.model.User.generatePassword;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

public class UserServiceTest extends AbstractSyncariTest {
	@Autowired
	UserService service;
	@Autowired
	RoleRepo roleRepo;
	@Autowired
	UserRoleRepo userRoleRepo;

	@Autowired
	UserRepo userRepo;

	@Autowired
	NotificationRepo notificationRepo;
	@Autowired
	EncryptionService encryptionService;
	@Autowired
	UserInvitationRepo invitationRepo;
	@Autowired
	ProvisioningService provService;
	@Autowired
	UserPreferenceRepo prefRepo;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Mock
	DatastoreService datastoreService;
	@Autowired
	SubscriptionService subService;

	@Override
	public void setUp() {
		super.setUp();
		service.emailService = emailService;
	}

	@Override
	public void tearDown() {
		super.tearDown();
		resetRepos(prefRepo, invitationRepo, userRoleRepo, notificationRepo);
	}

	@Test
	public void addRoleToUserFailsForInvalidData() {
		try {
			service.assignRolesToUser(null, null,null, Set.of());
			fail();
		} catch (Exception e) {
			assertEquals("User cannot be null", e.getMessage());
		}
		try {
			User u = new User();
			u.setId("123");
			service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), u, Set.of());
		} catch (Exception e) {
			fail();
		}
	}

	@Test
	public void addRoleToUser() {
		User user = new User("test1@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		Role role = new Role("Test Role");
		role = roleRepo.save(role);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of("Test Role"));
		List<User> usersByRole = service.getUsersByRole(role);
		assertEquals(1, usersByRole.size());
		assertEquals(saved.getId(), usersByRole.get(0).getId());
		roleRepo.delete(role);
		service.deleteUser(saved.getId());
	}

	@Test
	public void onlySuperAdminOrAdminCanAddOtherAdmins() {
		SyncariContext.getUser().setSuperAdmin(false);
		User user = new User("test2@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		user.setSuperAdmin(true);
		try {
			service.addUser(user);
			fail();
		} catch (Exception e) {
			assertEquals("You do not have permission to create super admins", e.getMessage());
		}
		SyncariContext.getUser().setSuperAdmin(false);
		SyncariContext.getUser().setAdmin(false);
		SyncariContext.getUser().setSystemUser(false);
		user.setSuperAdmin(false);
		user.setAdmin(true);
		try {
			service.addUser(user);
			fail();
		} catch (Exception e) {
			assertEquals("You do not have permission to create admins", e.getMessage());
		}
		SyncariContext.getUser().setAdmin(true);
		SyncariContext.getUser().setSuperAdmin(true);
		SyncariContext.getUser().setSystemUser(true);
	}

	@Test
	public void onlySuperAdminCanCreateGhostUser() {
		SyncariContext.getUser().setSuperAdmin(false);
		User user = new User("test2@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		user.setGhostUser(true);
		try {
			service.addUser(user);
			fail();
		} catch (Exception e) {
			assertEquals("You do not have permission to create ghost user", e.getMessage());
		}
		SyncariContext.getUser().setSuperAdmin(false);
		SyncariContext.getUser().setAdmin(false);
		SyncariContext.getUser().setSystemUser(false);
		user.setSuperAdmin(false);
		user.setGhostUser(true);
		try {
			service.addUser(user);
			fail();
		} catch (Exception e) {
			assertEquals("You do not have permission to create ghost user", e.getMessage());
		}
		SyncariContext.getUser().setAdmin(true);
		SyncariContext.getUser().setSuperAdmin(true);
		SyncariContext.getUser().setSystemUser(true);
	}

	@Test
	public void emailListTest() {
		assertEquals(1, service.getAdminEmailList().size());
		assertTrue(service.getAdminEmailList().contains("admin@syncari.com"));
		assertEquals(3, service.getInternalAdminEmailList().size());
		assertTrue(service.getInternalAdminEmailList().contains("admin@syncari.com"));
		assertTrue(service.getInternalAdminEmailList().contains("dev@syncari.com"));
		assertTrue(service.getInternalAdminEmailList().contains("test@email.com"));
	}
	
	@Test
	public void adminUserHasAdminRoleAssigned() {
		User user = new User("test3@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		user.setAdmin(true);
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		Set<String> roleIds = userRoleRepo.findByUserId(saved.getId()).get().getRoleIds();
		assertEquals(1, roleIds.size());
		assertEquals(Status.PENDING, saved.getStatus());
		assertTrue(roleIds.contains(roleRepo.findByName(RoleConstants.ORG_ADMIN).get().getId()));

		List<User> admins = service.getAdmins();
		admins.contains(saved.getId());
		service.deleteUser(saved.getId());
	}

	@Test
	public void updateUser() {
		User user = new User("test6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		saved.setLastName("last name");
		saved.setFirstName("first name");
		saved.setEmail("email changed");
		saved.setTimeZone("America/Los_Angeles");
		saved = service.updateUser(user, null, null);
		assertEquals("last name", saved.getLastName());
		assertEquals("first name", saved.getFirstName());
		assertEquals("test6@email.com", saved.getEmail());
		assertEquals("America/Los_Angeles", saved.getTimeZone());
		service.deleteUser(saved.getId());
	}

	@Test
	public void updateGhostedLoggedinUser() {
		User contextUser = SyncariContext.getUser();
		User user = new User(contextUser.getEmail(), generatePassword(), SyncariContext.getInstance().getSyncariId());
		user.setId(contextUser.getId());
		user.setLastName(contextUser.getLastName());
		user.setFirstName(contextUser.getFirstName());
		user.setEmail(contextUser.getEmail());
		user.setTimeZone("America/Los_Angeles");
		user.setGhostUser(true);
		contextUser.setSuperAdmin(false);
		try {
			service.updateUser(user, null, null);
			fail();
		} catch (Exception e) {
			assertEquals("Non super admins cannot assign ghost to user", e.getMessage());
		}
		contextUser.setGhostUser(true);
		userRepo.save(contextUser);
		service.updateUser(user, null, null);
		contextUser.setGhostUser(false);
		contextUser.setSuperAdmin(true);
		userRepo.save(contextUser);
	}

	@Test
	public void updateUserValidations() {
		User user = new User("test6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		User contextUser = SyncariContext.getUser();
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		SyncariContext.setUser(saved);
		saved.setLastName("last name");
		saved.setFirstName("first name");
		saved.setEmail("email changed");
		saved.setTimeZone("America/Los_Angeles");
		saved.setGhostUser(true);
		try {
			saved = service.updateUser(user, null, null);
			fail();
		} catch (Exception e) {
			assertEquals("Non super admins cannot assign ghost to user", e.getMessage());
		}
		SyncariContext.setUser(contextUser);
		service.deleteUser(saved.getId());
	}

	@Test
	public void caseInsensitiveUserEmail() {
		User user = new User("TEST6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

		// getUserByEmail
		Optional<User> retrieved = service.getUserByEmail("test6@email.com");
		assertTrue(retrieved.isPresent());

		retrieved = service.getUserByEmail("TEST6@email.com");
		assertTrue(retrieved.isPresent());

		retrieved = service.getUserByEmail("tEsT6@email.com");
		assertTrue(retrieved.isPresent());

		retrieved = service.getUserByEmail("test_6@email.com");
		assertFalse(retrieved.isPresent());

		retrieved = service.getUserByEmail("");
		assertFalse(retrieved.isPresent());

		retrieved = service.getUserByEmail(null);
		assertFalse(retrieved.isPresent());

		// activate user
		service.activateUser(saved.getId());

		// findActiveUserByEmail
		retrieved = service.findActiveUserByEmail("test6@email.com");
		assertTrue(retrieved.isPresent());

		retrieved = service.findActiveUserByEmail("TEST6@email.com");
		assertTrue(retrieved.isPresent());

		retrieved = service.findActiveUserByEmail("tEsT6@email.com");
		assertTrue(retrieved.isPresent());

		retrieved = service.findActiveUserByEmail("test_6@email.com");
		assertFalse(retrieved.isPresent());

		retrieved = service.findActiveUserByEmail("");
		assertFalse(retrieved.isPresent());

		retrieved = service.findActiveUserByEmail(null);
		assertFalse(retrieved.isPresent());

		service.deleteUser(saved.getId());
	}

	@Test
	public void userEmailWithSpecialCharacters() {
		// case 1: email with +
		User user = new User("TEST+6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

		Optional<User> retrieved= service.getUserByEmail("test+6@email.com");
		assertTrue(retrieved.isPresent());
		service.activateUser(saved.getId());

		retrieved = service.findActiveUserByEmail("teSt+6@email.com");
		assertTrue(retrieved.isPresent());
		service.deleteUser(saved.getId());

		// case 1: email with .
		user = new User("TEST.123@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

		retrieved= service.getUserByEmail("test.123@email.com");
		assertTrue(retrieved.isPresent());

		service.activateUser(saved.getId());

		retrieved = service.findActiveUserByEmail("teSt.123@email.com");
		assertTrue(retrieved.isPresent());

		service.deleteUser(saved.getId());
	}

	@Test
	public void testUpdateUserLoginDetails() {
		User user = new User("test6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		UserLoginDetails userLoginDetails = new UserLoginDetails("testToken",40000l);
		saved = service.updateUserLoginDetails(user, userLoginDetails);
		User userFetched = service.getUserByEmail("test6@email.com").get();
		assertEquals("testToken", userFetched.getUserLoginDetails().get(0).getTokenId());
		assertEquals(Long.valueOf(40000l), userFetched.getUserLoginDetails().get(0).getTokenValidFor());
		service.deleteUser(saved.getId());
	}

	@Test
	public void testUpdateUserLoginDetailsRemoved() {
		User user = new User("test6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		UserLoginDetails userLoginDetails = new UserLoginDetails("testToken",40000l);
		// first add login details and then remove
		saved = service.updateUserLoginDetails(user, userLoginDetails);
		assertNotNull(service.removeUserLoginDetails(user, userLoginDetails));
		User userFetched = service.getUserByEmail("test6@email.com").get();
		assertEquals(Collections.emptyList(),userFetched.getUserLoginDetails());
		service.deleteUser(saved.getId());
	}

	@Test
	public void testUpdateUserLoginDetailsForNullUser() {
		User user = new User("test7@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		UserLoginDetails userLoginDetails = new UserLoginDetails("testToken",40000l);
		service.deleteUser(saved.getId());
		assertNull(service.updateUserLoginDetails(null, userLoginDetails));
	}

	@Test
	public void testUpdateUserLoginDetailsForNullUserLogin() {
		User user = new User("test6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		saved.setUpdatedAt(new Date());
		service.deleteUser(saved.getId());
		assertNull(service.updateUserLoginDetails(user, null));
	}

	@Test
	public void deleteUser() {
	    User user = new User("test6@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
	    User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
	    saved = service.getUser("test6@email.com");
	    assertNotNull(saved);
	    service.deleteUser(saved.getId());
	    try {
	        saved = service.getUser("test6@email.com");
        } catch (Exception e) {
            assertEquals("User with email test6@email.com not found", e.getMessage());
        }
	}

	@Test
	public void deactivateUser() {
	    User user = new User("test6@email.com", SyncariContext.getInstance().getSyncariId());
	    User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
	    service.deactivateUser(saved.getId());
	    saved = service.getUser("test6@email.com");
	    assertEquals(Status.INACTIVE, saved.getStatus());
	    service.deleteUser(saved.getId());
	}

	@Test
	public void updateUserPreference() {
		User user = new User("test6@email.com", SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		DashboardPreference pref = new DashboardPreference();
		WidgetSetting widgetSetting = new WidgetSetting();
		ErrorNotificationPreference notifPreference = new ErrorNotificationPreference();
		notifPreference.setSubscriptions(List.of(new ErrorSubscription()));
		widgetSetting.setWidgetId("213");;
		pref.getWidgetPreferences().add(widgetSetting);
		service.updateDashboardPreference(saved.getId(), "dashboard", pref);
		service.updateErrorNotificationPreference(saved.getId(), notifPreference);
		UserPreference preference = service.getPreference(saved.getId());
		assertEquals(1, preference.getDashboard().getWidgetPreferences().size());
		assertNotNull(preference.getErrorNotification());
		assertEquals(1, preference.getErrorNotification().getSubscriptions().size());
	}

    @Test
    public void updateSchemaStudioEntityColumnsPreference() {
		User user = new User("testSchemaStudioPrefs@email.com",  SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

        SchemaStudioPreference studioPref = new SchemaStudioPreference();
        studioPref.setAllEntityColumns(new LinkedHashSet<Map<String, Object>>(
            Arrays.asList(Map.of("columnName", "id", "isSelected", true),
                Map.of("columnName", "apiName", "isSelected", true))));

        List<Map<String, Object>> expected =
            List.of(Map.of("columnName", "name", "isSelected", true),
                Map.of("columnName", "id", "isSelected", true),
                Map.of("columnName", "dateUpdated", "isSelected", true));
        service.updateSchemaStudioEntityColumnsPreference(user.getId(), new LinkedHashSet<Map<String, Object>>(expected));

		UserPreference preference = service.getPreference(saved.getId());
		LinkedHashSet<Map<String, Object>> recieved = preference.getSchemaStudio().getAllEntityColumns();

		assertEquals(3, recieved.size());

        int i = 0;
        for(Map<String, Object> col : recieved) {
            assertEquals(expected.get(i).get("columnName"), col.get("columnName"));
            i++;
        }
    }

    @Test
    public void updateSchemaStudioFieldColumnsPreference() {
		User user = new User("testSchemaStudioFieldColumnsPrefs@email.com", SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

        SchemaStudioPreference studioPref = new SchemaStudioPreference();
        studioPref.setAllFieldColumns(new LinkedHashSet<Map<String, Object>>(
            Arrays.asList(Map.of("columnName", "id", "isSelected", true),
                Map.of("columnName", "apiName", "isSelected", true))));

        List<Map<String, Object>> expected =
            List.of(Map.of("columnName", "apiName", "isSelected", true),
                Map.of("columnName", "id", "isSelected", true),
                Map.of("columnName", "displayName", "isSelected", true),
                Map.of("columnName", "isDeleted", "isSelected", true));
        service.updateSchemaStudioFieldColumnsPreference(user.getId(), new LinkedHashSet<Map<String, Object>>(expected));

		UserPreference preference = service.getPreference(saved.getId());
		LinkedHashSet<Map<String, Object>> columnList = preference.getSchemaStudio().getAllFieldColumns();
		assertEquals(4, columnList.size());

        int i = 0;
        for(Map<String, Object> col : columnList) {
          assertEquals(expected.get(i).get("columnName"), col.get("columnName"));
            i++;
        }
    }

    @Test
    public void updateDataStudioUserPreference() {
        User user = new User("testDataStudioPrefs@email.com", SyncariContext.getInstance().getSyncariId());
        User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

        LinkedHashSet<Map<String, Object>> set = new LinkedHashSet<Map<String, Object>>();
        set.add(Map.of("columnName", "id", "isSelected", true));
        set.add(Map.of("columnName", "apiName", "isSelected", true));
        service.updateDataStudioColumnPreference(user.getId(), "123", set);

        UserPreference preference = service.getPreference(saved.getId());
        assertEquals(1, preference.getDataStudio().getAllColumns().size());
        assertEquals(2, preference.getDataStudio().getAllColumns().get("123").size());
        Iterator<Map<String, Object>> iterator = preference.getDataStudio().getAllColumns().get("123").iterator();
        int i = 0;
        while(iterator.hasNext()) {
            if(i == 0) {
                assertEquals("id", iterator.next().get("columnName"));
            }
            if(i == 1) {
                assertEquals("apiName", iterator.next().get("columnName"));
            }
            i++;
        }
    }

    @Test
    public void updateSyncStudioFieldsFiltersPreference() {
        User user = new User("testSchemaStudioFilterPrefs@email.com", SyncariContext.getInstance().getSyncariId());
        User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

        LinkedHashSet<String> set = new LinkedHashSet<String>();
        set.add("displayName");
        set.add("billingAddress");
        service.updateSyncStudioFieldsFiltersPreference(user.getId(), "123", set);

        UserPreference preference = service.getPreference(saved.getId());
        assertEquals(1, preference.getSyncStudio().getFilterSelections().size());
        assertEquals(2, preference.getSyncStudio().getFilterSelections().get("123").size());
        Iterator<String> iterator = preference.getSyncStudio().getFilterSelections().get("123").iterator();
        int i = 0;
        while(iterator.hasNext()) {
            if(i == 0) {
                assertEquals("displayName", iterator.next());
            }
            if(i == 1) {
                assertEquals("billingAddress", iterator.next());
            }
            i++;
        }
    }

    @Test
    public void updateSyncStudioHiddenFieldsPreference() {
        User user = new User("testSchemaStudioHiddenFieldsPrefs@email.com", SyncariContext.getInstance().getSyncariId());
        User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

        LinkedHashSet<String> set = new LinkedHashSet<String>();
        set.add("displayName");
        set.add("billingAddress");
        service.updateSyncStudioHiddenFieldsPreference(user.getId(), "123", set);

        UserPreference preference = service.getPreference(saved.getId());
        assertEquals(1, preference.getSyncStudio().getHiddenFields().size());
        assertEquals(2, preference.getSyncStudio().getHiddenFields().get("123").size());
        Iterator<String> iterator = preference.getSyncStudio().getHiddenFields().get("123").iterator();
        int i = 0;
        while(iterator.hasNext()) {
            if(i == 0) {
                assertEquals("displayName", iterator.next());
            }
            if(i == 1) {
                assertEquals("billingAddress", iterator.next());
            }
            i++;
        }
    }

    @Test
    public void updateSyncStudioPipelineViewportsPreference() {
        User user = new User("testSchemaStudioPipelineViewportPrefs@email.com", SyncariContext.getInstance().getSyncariId());
        User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));

        ArrayList<Number> matrixList = new ArrayList<Number>();
		matrixList.add(0.895);
		matrixList.add(0);
		matrixList.add(0);
		matrixList.add(0);
		matrixList.add(0.895);
		matrixList.add(0);
		matrixList.add(272.48006412631105);
		matrixList.add(429.27345437134727);
		matrixList.add(1);

        service.updateSyncStudioPipelineViewportsPreference(user.getId(), "123", matrixList);

        UserPreference preference = service.getPreference(saved.getId());
        assertEquals(1, preference.getSyncStudio().getPipelineViewports().size());
        assertEquals(9, preference.getSyncStudio().getPipelineViewports().get("123").size());
    }

	@Test
	public void updateEntityGraphPreference() {
		User user = new User("testUpdateEntityGraphPreference@email.com", SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		GraphPreference pref = new GraphPreference();
		pref.setInstanceId(SyncariContext.getSyncariId());

		service.updateEntityGraphPreference(saved.getId(), pref);
		UserPreference preference = service.getPreference(saved.getId());
		assertEquals(0, preference.getEntityGraph().getNodes().size());
		assertEquals(0, preference.getEntityGraph().getEdges().size());
		service.deleteUser(saved.getId());
	}

	@Test
	public void updateConnectorGraphPreference() {
		User user = new User("testUpdateEntityGraphPreference@email.com", SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		GraphPreference pref = new GraphPreference();
		pref.setInstanceId(SyncariContext.getSyncariId());

		service.updateConnectorGraphPreference(saved.getId(), pref);
		UserPreference preference = service.getPreference(saved.getId());
		assertEquals(0, preference.getConnectorGraph().getNodes().size());
		assertEquals(0, preference.getConnectorGraph().getEdges().size());
		service.deleteUser(saved.getId());
	}

	@Test
	public void getUser() {
		try {
			service.getUser("nonexistant");
			fail();
		} catch (Exception e) {
			assertEquals("User with email nonexistant not found", e.getMessage());
		}
	}

	@Test
	public void resetPasswordValidations() {
		User user = new User("test8@email.com", "SomePassword12!", SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		try {
			service.resetPassword(null, null, null);
			fail();
		} catch (Exception e) {
			assertEquals("User id cannot be blank", e.getMessage());
		}
		try {
			service.resetPassword(user.getId(), null, null);
			fail();
		} catch (Exception e) {
			assertEquals("User password cannot be empty", e.getMessage());
		}
		try {
			service.resetPassword(user.getId(), "new", "new");
			fail();
		} catch (Exception e) {
			assertEquals("Password must be 8 or more characters in length.", e.getMessage());
		}
		try {
			service.resetPassword(user.getId(), "new", "newweakpassword");
			fail();
		} catch (Exception e) {
			assertEquals("Password must contain 1 or more uppercase characters.", e.getMessage());
		}
		try {
			service.resetPassword("123", "validpassword", "newvalidpassword");
			fail();
		} catch (Exception e) {
			assertEquals("User with Id 123 not found", e.getMessage());
		}

		// reset password with currentPassword validation
		try {
			service.resetPassword(SyncariContext.getUser().getId(), "invalid_password", "NewValidPassword0!", true);
		} catch (Exception e) {
			assertEquals("Current password does not match the existing password.", e.getMessage());
		}

		try {
			User updated = service.resetPassword(SyncariContext.getUser().getId(), "invalid_password", "SomePassword12!", false);
		} catch (Exception e) {
			assertEquals("New password can not be same as current password.", e.getMessage());
		}

		User updated = service.resetPassword(SyncariContext.getUser().getId(), "invalid_password", "NewValidPassword0!", false);
		assertTrue(passwordEncoder.matches("NewValidPassword0!", updated.getPassword()));
	}

	@Test
	public void resetPassword() {
		String password = generatePassword();
		User user = new User("test5@email.com", password, SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
        service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		service.resetPassword(saved.getId(), password, "1newvalidpwD!");
		saved = service.getUser("test5@email.com");
		assertTrue(passwordEncoder.matches("1newvalidpwD!",saved.getPassword()));
        try {
            service.resetPassword(saved.getId(), "invalidpwd", "Newvalidpwd1!");
            fail();
        } catch (Exception e) {
            assertEquals("Current password does not match the existing password.", e.getMessage());
        }
        saved = service.getUser("test5@email.com");
        assertTrue(passwordEncoder.matches("1newvalidpwD!",saved.getPassword()));
		service.deleteUser(saved.getId());
	}

	@Test
	public void setPasswordValidations() {
		try {
			service.setPassword("123", null);
			fail();
		} catch (Exception e) {
			assertEquals("Unknown or expired invitation", e.getMessage());
		}
		UserInvitation invite = new UserInvitation("123", UUID.randomUUID().toString());
		invite = invitationRepo.save(invite);
		try {
			service.setPassword(invite.getInvitationId(), "123");
			fail();
		} catch (Exception e) {
			assertEquals("User not found for invitation", e.getMessage());
		}
		invitationRepo.delete(invite);

		User user = service.addUser(new User("user123@sync.com", SyncariContext.getSyncariId()));
		invite = new UserInvitation(user.getId(), UUID.randomUUID().toString());
		invite = invitationRepo.save(invite);
		try {
			service.setPassword(invite.getInvitationId(), "Changed");
			fail();
		} catch (Exception e) {
			assertEquals("Password must be 8 or more characters in length.", e.getMessage());
		}
		invitationRepo.delete(invite);
	}

	@Test
	public void passwordCannotBeSetOnExpiredUser() {
	    UserInvitation invite = new UserInvitation(SyncariContext.getUser().getId(), UUID.randomUUID().toString());
	    invite.setCreatedAt(new Date(new Date().getTime() - (31L * 24 * 60 * 60 * 1000)));
	    assertTrue(invite.hasExpired());
	    invite.setCreatedAt(new Date());
	    assertFalse(invite.hasExpired());
	}

	@Test
	public void setPasswordForNewUserChangesPassword() {
		User newUser = new User("test@sync.com", SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(newUser , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)),false, Optional.empty());
		newUser = service.getUser("test@sync.com");
		assertEquals(Status.PENDING, newUser.getStatus());
		assertEquals(1, invitationRepo.count());
		service.setPassword(invitationRepo.findByUserId(newUser.getId()).get().getInvitationId(), "Changedpassw0rd!");
		newUser = service.getUser("test@sync.com");
		assertEquals(0, invitationRepo.count());
		assertEquals(Status.ACTIVE, newUser.getStatus());
		assertTrue(passwordEncoder.matches("Changedpassw0rd!",newUser.getPassword()));
		service.deleteUser(newUser.getId());
	}

	@Test
	public void existingUserClientSecretIsNotDoubleEncoded() {
		User newUser = new User("test@sync.com", SyncariContext.getInstance().getSyncariId());
		newUser.setApiUser(true);
		newUser = provService.inviteUser(newUser , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)),false, Optional.empty());
		String clientSecret = newUser.getClientSecret();
		newUser = service.getUser("test@sync.com");
		assertEquals(Status.ACTIVE, newUser.getStatus());

		User user = provService.inviteUser(newUser , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)),false, Optional.empty());
		assertEquals(newUser.getClientSecret(), user.getClientSecret());
		service.deleteUser(newUser.getId());
	}

	@Test
	public void setPasswordForExistingUserChangesPassword() {
		User user = service.getUser(SyncariContext.getUser().getEmail());
		UserInvitation invite = new UserInvitation(SyncariContext.getUser().getId(), UUID.randomUUID().toString());
		invite = invitationRepo.save(invite);
		service.setPassword(invite.getInvitationId(), "Changedpassw0rd!");
		user = service.getUser(SyncariContext.getUser().getEmail());
		assertEquals(0, invitationRepo.count());
		assertTrue(passwordEncoder.matches("Changedpassw0rd!",user.getPassword()));
	}

	@Test
	public void addUserWithEmailNull(){
		User user = new User(null, SyncariContext.getInstance().getSyncariId());
		try {
			service.addUser(user);

			fail();
		} catch (Exception e) {
			assertEquals("User email is required", e.getMessage());
		}

	}

	@Test
	public void updateUserRoles(){
		// create new user
		User newUser = new User("test@sync.com", SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(newUser , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)), false, Optional.empty());
		newUser = service.setPassword(invitationRepo.findByUserId(newUser.getId()).get().getInvitationId(), "Changedpassw0rd!");

		assertEquals(1, userRoleRepo.findByUserId(newUser.getId()).get().getRoleIds().size());

		Role adminRole = roleRepo.findByName(RoleConstants.ORG_ADMIN).get();
		Role viewerRole = roleRepo.findByName("Viewer").get();

		// add sync manager role to new user
		var updatedRoles = Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN, "Viewer"));
		service.updateUserRoles(newUser.getId(), updatedRoles);
		var updatedUserRoles = userRoleRepo.findByUserId(newUser.getId()).get();
		assertEquals(2, updatedUserRoles.getRoleIds().size());
		assertTrue(updatedUserRoles.getRoleIds().contains(viewerRole.getId()));
		assertTrue(updatedUserRoles.getRoleIds().contains(adminRole.getId()));

		// remove admin role
		updatedRoles = Map.of(SyncariContext.getInstance().getSyncariId(), Set.of("Viewer"));
		service.updateUserRoles(newUser.getId(), updatedRoles);
		updatedUserRoles = userRoleRepo.findByUserId(newUser.getId()).get();
		assertEquals(1, updatedUserRoles.getRoleIds().size());
		assertTrue(updatedUserRoles.getRoleIds().contains(viewerRole.getId()));
		assertFalse(updatedUserRoles.getRoleIds().contains(adminRole.getId()));

		// remove all the roles
		updatedRoles = Map.of();
		service.updateUserRoles(newUser.getId(), updatedRoles);
		assertEquals(0, newUser.getUserLoginDetails().size());
		assertFalse(userRoleRepo.findByUserId(newUser.getId()).isPresent());

        doNothing().when(datastoreService).provision(any());
        provService.datastoreService = datastoreService;
		// create new instance and assign newUser
		Instance newInstance = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance", "testInstance",
				InstanceType.sandbox, "default", SyncariContext.getUser());

		updatedRoles = Map.of(newInstance.getSyncariId(), Set.of(RoleConstants.ORG_ADMIN, "Viewer"));
		service.updateUserRoles(newUser.getId(), updatedRoles);

		SyncariContext.push();
		try {
			SyncariContext.setInstance(newInstance);
			updatedUserRoles = userRoleRepo.findByUserId(newUser.getId()).get();
			assertEquals(2, updatedUserRoles.getRoleIds().size());
			assertTrue(updatedUserRoles.getRoleIds().contains(roleRepo.findByName("Viewer").get().getId()));
			assertTrue(updatedUserRoles.getRoleIds().contains(roleRepo.findByName(RoleConstants.ORG_ADMIN).get().getId()));
		} finally {
			SyncariContext.restore();
		}

		// remove roles from newInstance
		updatedRoles = Map.of();
		service.updateUserRoles(newUser.getId(), updatedRoles);
		SyncariContext.push();
		try {
			SyncariContext.setInstance(newInstance);
			assertFalse(userRoleRepo.findByUserId(newUser.getId()).isPresent());
		} finally {
			SyncariContext.restore();
		}

		// destroy
		provService.deprovisionInstance(newInstance.getSyncariId(), true);
		provService.deprovisionEventStore(newInstance.getSyncariId());

		// update in-memory org in SyncariContext
		Organization org = subService.getOrgById(SyncariContext.getOrganziation().getId()).get();
		SyncariContext.setOrganziation(org);
		User user = service.getUserById(SyncariContext.getUser().getId());
		SyncariContext.setUser(user);
	}

	@Test
	public void removeInstanceFromUser(){
		SyncariContext.push();
		provService.datastoreService = datastoreService;
		Instance newInstance = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance", "testInstance",
				InstanceType.sandbox, "default", SyncariContext.getUser());
		try {
		SyncariContext.setInstance(newInstance);
		// create new user
		User newUser = new User("testrm@sync.com", SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(newUser , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)), false, Optional.empty());
		newUser = service.setPassword(invitationRepo.findByUserId(newUser.getId()).get().getInvitationId(), "Changedpassw0rd!");

		List<User>allActiveUsers = userRepo.findAllActiveStandard();

		User newUser1 = new User("testrminactive@sync.com", SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(newUser1 , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.INSTANCE_ADMIN)), false, Optional.empty());

		Optional<User> userInactive = userRepo.findByEmail("testrminactive@sync.com");
		assertTrue(userInactive.isPresent());
		assertFalse(userInactive.get().isActive());

		service.removeInstanceFromUser(newInstance.getSyncariId(), Optional.empty());
		//verify(emailService, times(4)).sendHtml(any(), any(), any());

		assertEquals(0, newUser.getUserLoginDetails().size());
		} finally {
			SyncariContext.restore();
			provService.deprovisionInstance(newInstance.getSyncariId(), true);
			provService.deprovisionEventStore(newInstance.getSyncariId());
		}
		// update in-memory org in SyncariContext
		Organization org = subService.getOrgById(SyncariContext.getOrganziation().getId()).get();
		SyncariContext.setOrganziation(org);
		User user = service.getUserById(SyncariContext.getUser().getId());
		SyncariContext.setUser(user);
	}

	@Test
	public void getUserRolesSuperAdminIsAddedToAllInstancesOfOrg(){

		User user = SyncariContext.getUser();
		var userRoleMap = service.getUserRoles(user.getId());
		assertEquals(0,userRoleMap.size());
		assertTrue(user.isSuperAdmin());
		// create another super admin user
		User newUser = new User("test2@email.com", SyncariContext.getInstance().getSyncariId());
		newUser.setSuperAdmin(true);
		newUser = service.addUser(newUser);
		userRoleMap = service.getUserRoles(newUser.getId());
		assertEquals(0,userRoleMap.size());
		assertTrue(newUser.isSuperAdmin());
	}

    @Test
    public void fiveHundredIsFiveHundred() {
        // IMPORTANT, do not remove.
        assertEquals(500, 500);
    }

    @Test
    public void testListInstancesWithPermission() {
		User newUser = new User("test@sync.com", SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(newUser , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)), false, Optional.empty());
		newUser = service.setPassword(invitationRepo.findByUserId(newUser.getId()).get().getInvitationId(), "Changedpassw0rd!");

		Instance newInstance1 = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance1", "testInstance1",
				InstanceType.production, "default", SyncariContext.getUser());
		Instance newInstance2 = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance2", "testInstance2",
				InstanceType.production, "default", SyncariContext.getUser());
		Instance newInstance3 = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance3", "testInstance3",
				InstanceType.production, "default", SyncariContext.getUser());

		newUser.addAvailableInstance(newInstance1);
		newUser.addAvailableInstance(newInstance2);
		newUser.addAvailableInstance(newInstance3);
		var updatedRoles = Map.of(newInstance1.getSyncariId(), Set.of(RoleConstants.ORG_ADMIN),
				newInstance2.getSyncariId(), Set.of("Viewer"), newInstance3.getSyncariId(), Set.of("Sync Manager"));
		service.updateUserRoles(newUser.getId(), updatedRoles);

		List<Instance> instances = service.listInstancesWithPermission(newUser, Permissions.QUICKSTART_SHARE);
		service.deleteUser(newUser.getId());
		provService.deprovisionInstance(newInstance1.getSyncariId(), true);
		provService.deprovisionInstance(newInstance2.getSyncariId(), true);
		provService.deprovisionInstance(newInstance3.getSyncariId(), true);

		assertEquals(3, instances.size());
		assertEquals("testInstance1", instances.get(0).getName());
		assertEquals("testInstance1", instances.get(0).getDisplayName());
	}


	@Test
	public void testCurrentInstanceDeletion() {
		User newUser = new User("testNew@sync.com", SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(newUser , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)), false, Optional.empty());
		newUser = service.setPassword(invitationRepo.findByUserId(newUser.getId()).get().getInvitationId(), "Changedpassw0rd!");

		Instance newInstance1 = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance1", "testInstance1",
				InstanceType.production, "default", SyncariContext.getUser());
		Instance newInstance2 = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance2", "testInstance2",
				InstanceType.production, "default", SyncariContext.getUser());
		Instance newInstance3 = provService.provisionInstance(SyncariContext.getOrganziation(), "testInstance3", "testInstance3",
				InstanceType.production, "default", SyncariContext.getUser());
		newUser.addAvailableInstance(newInstance1);
		newUser.addAvailableInstance(newInstance2);
		newUser.addAvailableInstance(newInstance3);
		newUser.setCurrentInstanceId(newInstance3.getSyncariId());
		var updatedRoles = Map.of(newInstance1.getSyncariId(), Set.of(RoleConstants.ORG_ADMIN),
				newInstance2.getSyncariId(), Set.of(RoleConstants.ORG_ADMIN), newInstance3.getSyncariId(), Set.of(RoleConstants.ORG_ADMIN));
		service.updateUserRoles(newUser.getId(), updatedRoles);
		service.userRepo.save(newUser);
		User userbefore = service.getUserById(newUser.getId());
		assertEquals(newInstance3.getSyncariId(), userbefore.getCurrentInstanceId());

		provService.deprovisionInstance(newInstance3.getSyncariId(), true);
		User userAfter = service.getUserById(newUser.getId());
		assertNotEquals(newInstance3.getSyncariId(), userAfter.getCurrentInstanceId());
		userAfter.removeAvailableInstance(newInstance2.getSyncariId());
		provService.deprovisionInstance(newInstance2.getSyncariId(), true);
		userAfter.removeAvailableInstance(newInstance1.getSyncariId());
		provService.deprovisionInstance(newInstance1.getSyncariId(), true);
		service.deleteUser(userAfter.getId());
		try{
			service.getUserById(newUser.getId());
			fail();
		}catch (RuntimeException e){
			assertTrue(e.getMessage().contains("not found"));
		}
	}


	@Test
	public void testUserHasPermission() {

		User user = SyncariContext.getUser();
		// This is a super admin, should have the QS_APPROVE permission
		assertTrue(service.doesUserHavePermission(user, SyncariContext.getSyncariId(), Permissions.QUICKSTART_PUBLISH));
		// Should have share permissions too
		assertTrue(service.doesUserHavePermission(user, SyncariContext.getSyncariId(), Permissions.QUICKSTART_SHARE));
	}

	@Test
	public void testApiuser() {
		// create test user with api access
		User apiUser = new User("apiuser@email.com", SyncariContext.getInstance().getSyncariId());
		apiUser.setAdmin(true);
		apiUser.setApiUser(true);
		apiUser = service.addUser(apiUser);

		// verify api access
		assertTrue(apiUser.isApiUser());
		assertNotNull(apiUser.getClientId());
		assertEquals("ACTIVE", apiUser.getStatus().toString());

		// create test user with api access
		User notApiUser = new User("notapiuser@email.com", SyncariContext.getInstance().getSyncariId());
		notApiUser.setApiUser(false);
		notApiUser = service.addUser(notApiUser);

		// verify api access
		assertFalse(notApiUser.isApiUser());
		assertNull(notApiUser.getClientId());
		assertEquals("PENDING", notApiUser.getStatus().toString());
	}
	
	@Test
	public void testUserLock() {
		String email = "userlocktest@email.com";
		User user = new User(email, SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(user , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)), false, Optional.empty());
		user = service.setPassword(invitationRepo.findByUserId(user.getId()).get().getInvitationId(), "Changedpassw0rd!");
		assertEquals(0, user.getFailedLoginAttempts());
		service.incrementFailedLoginAttempts(email);
		service.incrementFailedLoginAttempts(email);
		user = service.getUser(email);
		assertEquals(2, user.getFailedLoginAttempts());
		service.clearFailedLoginAttempts(email);
		user = service.getUser(email);
		assertEquals(0, user.getFailedLoginAttempts());
	}
	
	@Test
	public void testSetPassword() {
		String email = "usersp@email.com";
		User user = new User(email, SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(user , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)), false, Optional.empty());
		user.addUserlogindetail(new UserLoginDetails("t1", 1000L));
		user.addUserlogindetail(new UserLoginDetails("t2", 10000L));
		user = service.setPassword(invitationRepo.findByUserId(user.getId()).get().getInvitationId(), "Changedpassw0rd!");
		assertEquals(0, userRepo.findByEmail(email).get().getUserLoginDetails().size());
	}
	
	@Test
	public void testForgotPassword() {
		String email = "userfp@email.com";
		User user = new User(email, SyncariContext.getInstance().getSyncariId());
		provService.inviteUser(user , Map.of(SyncariContext.getInstance().getSyncariId(), Set.of(RoleConstants.ORG_ADMIN)), false, Optional.empty());
		user = service.setPassword(invitationRepo.findByUserId(user.getId()).get().getInvitationId(), "Changedpassw0rd!");
		user.addUserlogindetail(new UserLoginDetails("t1", 1000L));
		user.addUserlogindetail(new UserLoginDetails("t2", 10000L));
		service.forgotPassword(email);
		assertEquals(0, userRepo.findByEmail(email).get().getUserLoginDetails().size());
	}
	
	@Test
	public void isOrgAdminInAnyInstanceTest() {
		User user = new User("testorgadmin@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.ORG_ADMIN));
		assertTrue(service.isOrgAdminInAnyInstance(saved));
		service.deleteUser(saved.getId());
	}

	@Test
	public void removeUserFromOrgTest() {
		User user = new User("removeme_user@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		// assign role to the user in each instance
		SyncariContext.getOrganziation().getInstances().forEach(instance -> {
			service.assignRolesToUser(SyncariContext.getOrganziation(), instance, saved, Set.of(RoleConstants.VIEWER));
		});

		user = service.getUserById(saved.getId());
		assertFalse(StringUtils.isEmpty(user.getCurrentInstanceId()));
		assertTrue(user.getAvailableInstances().size() >= 1);

		// remove user
		service.removeUserFromCurrentOrg(saved.getId());
		user = service.getUserById(saved.getId());
		assertTrue(StringUtils.isEmpty(user.getCurrentInstanceId()));
		assertTrue(user.getAvailableInstances().isEmpty());

		// cleanup
		service.deleteUser(saved.getId());
	}
	
	@Test
	public void isOrgAdminInAnyInstanceTest2() {
		User user = new User("testorgadmin2@email.com", generatePassword(), SyncariContext.getInstance().getSyncariId());
		User saved = service.addUser(user);
		service.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), saved, Set.of(RoleConstants.VIEWER));
		assertFalse(service.isOrgAdminInAnyInstance(saved));
		service.deleteUser(saved.getId());
	}

}
