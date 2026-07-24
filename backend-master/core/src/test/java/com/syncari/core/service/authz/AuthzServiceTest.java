 package com.syncari.core.service.authz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Role;
import com.syncari.core.model.misc.ResourcePrivilege;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.UserService;

public class AuthzServiceTest extends AbstractSyncariTest {
    @Autowired
    AuthzService authzService;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Override
    public void tearDown() {
        super.tearDown();
    }
    
    @Test
    public void testAddEditListRoleTest(){
    	// Add role
    	ResourcePrivilege rp = new ResourcePrivilege("global", Permissions.ACTION_READ);
    	Role role = new Role();
    	role.setActive(true).setDescription("Test Role").setName("Test Role").setPrivileges(Set.of(rp));
    	authzService.addRole(role, Set.of(), List.of(SyncariContext.getUser().getId()));
    	
    	
    	var roles = authzService.listRoles();
    	assertNotNull(roles);
    	assertFalse(roles.isEmpty());
    	assertEquals(8, roles.size());
    	assertEquals(1, roles.stream().filter(r -> r.getName().equals("Test Role")).count());
    	
    	ResourcePrivilege rp2 = new ResourcePrivilege("global", Permissions.ACTION_SHARE);
    	Role role2 = new Role();
    	role2.setActive(true).setDescription("Test Role edited").setName("Test Role edited").setPrivileges(Set.of(rp, rp2));
    	authzService.editRole(roles.stream().filter(r -> r.getName().equals("Test Role")).findFirst().get().getId(), role2, Set.of(), List.of(SyncariContext.getUser().getId()));
    	
    	roles = authzService.listRoles();
    	assertNotNull(roles);
    	assertFalse(roles.isEmpty());
    	assertEquals(8, roles.size());
    	assertEquals(0, roles.stream().filter(r -> r.getName().equals("Test Role")).count());
    	assertEquals(1, roles.stream().filter(r -> r.getName().equals("Test Role edited")).count());
    	
    	var privileges = authzService.listPrivileges();
    	assertNotNull(privileges);
    	assertFalse(privileges.isEmpty());
    	assertEquals(Permissions.allPermissions().size(), privileges.size()); // since user is super admin they have all permissions
    	
    	var permissions = authzService.getPermissions("Test Role edited", false);
    	assertNotNull(permissions);
    	assertFalse(permissions.isEmpty());
        assertEquals(6, permissions.size());
    	
    	try {
    	permissions = authzService.getPermissions("", false);
    	fail();
    	} catch (Exception e) {	}
    	
    	var user = SyncariContext.getUser();
    	var isSuperAdmin = user.isSuperAdmin();
    	var isAdmin = user.isAdmin();
    	var isSystemUser = user.isSystemUser();
    	user.setSuperAdmin(false);
    	user.setAdmin(false);
    	user.setSystemUser(false);
    	assertEquals(1, authzService.listRoles().size());
    	user.setSuperAdmin(isSuperAdmin);
    	user.setAdmin(isAdmin);
    	user.setSystemUser(isSystemUser);
    }
}