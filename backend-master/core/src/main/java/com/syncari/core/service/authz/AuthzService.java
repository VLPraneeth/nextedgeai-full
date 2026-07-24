package com.syncari.core.service.authz;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.Role;
import com.syncari.core.model.Tag;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.ResourcePrivilege;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.customer.PrivilegeRepo;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.repositories.customer.UserRoleRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.TagService;
import com.syncari.core.service.UserService;
import com.syncari.core.template.TemplateRenderer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuthzService {
	private static final String ROLE_REVOKE_TEMPLATE_PATH = "templates/user.role.revoke.template";
	
    @Autowired
    UserRepo userRepo;
    @Autowired
    RoleRepo roleRepo;
    @Autowired
    PrivilegeRepo privRepo;
    @Autowired
    UserRoleRepo userRoleRepo;
    @Autowired
    UserService userService;
    @Autowired
    TagService tagService;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    TemplateRenderer renderer;

    public Stream<String> listPrivileges(String username) {
        User user = userService.getUserByEmail(username).get();

        if(user.isSuperAdmin()) return Permissions.allPermissions().stream();
        
        Stream<Role> roles = userRoleRepo
        		.findByUserId(user.getId()).stream()
                .flatMap(userRoles -> roleRepo.findByIdIn(userRoles.getRoleIds()).stream())
                .filter(r -> r.isActive());

        Set<String> privileges = roles.flatMap(r -> r.getPrivileges().stream()).map(p -> p.getPrivilegeId()).collect(Collectors.toSet());
        
		if (user.isGhostUser()) {
			privileges.addAll(Permissions.ghostPermissions());
		}
		
		//include base permissions
		Permissions.getBasePermissions().forEach(p -> {
			if(!privileges.contains(p)) {
				privileges.add(p);
			}
		});

        return privileges.stream();
    }
    
    public Set<ResourcePrivilege> listPrivileges() {
        User user = SyncariContext.getUser();
        List<Role> roles = userRoleRepo.findByUserId(user.getId()).stream()
                .flatMap(userRoles -> roleRepo.findByIdIn(userRoles.getRoleIds()).stream()).collect(Collectors.toList());
        var privilegesSet = new HashSet<ResourcePrivilege>();
        privilegesSet.addAll(roles.stream().filter(r -> r.isActive()).flatMap(r -> r.getPrivileges().stream()).collect(Collectors.toSet()));
        //if superadmin include all permissions else include base permissions
		var permissionsList = user.isSuperAdmin() ? Permissions.allPermissions() : Permissions.getBasePermissions();
		permissionsList.forEach(p -> {
			privilegesSet.add(new ResourcePrivilege("global", p));
		});
		return privilegesSet;
    }
    
    public Set<String> getPermissions(String role, boolean superAdmin) {
    	Set<String> privileges = new HashSet<>();
    	if(superAdmin) {
    		privileges.addAll(Permissions.allPermissions());
    	} else {
	    	validateCondition(StringUtils.isBlank(role), i18n("invalid_role_name_empty"));
	        roleRepo.findByName(role).ifPresent(r -> {
	        	privileges.addAll(r.getPrivileges().stream().map(p -> p.getPrivilegeId()).collect(Collectors.toSet()));
	        });
	        Permissions.getBasePermissions().forEach(p -> {
	        	privileges.add(p);
			});
    	}
        return privileges;
    }

    public Role addRole(Role role, Set<String> tags, List<String> userIds) {
    	validateCondition(StringUtils.isBlank(role.getName()), i18n("invalid_role_name_empty"));
    	validateCondition(roleRepo.findByName(role.getName()).isPresent(), i18n("invalid_role_name_exist"));
    	role = roleRepo.save(role);
    	var roleId = role.getId();
    	if(tags == null) {
    		tags = Set.of();
    	}
    	var tagMap = tags.stream().map(name -> new Tag(name, true, Taggable.role, roleId))
				.collect(Collectors.toList());
    	tagService.addTags(tagMap);
    	role.setTags(tagService.addTags(tagMap));
    	String roleName = role.getName();
    	if(CollectionUtils.isNotEmpty(userIds)) {
    		userIds.stream().forEach(id -> {
    			userService.findUserById(id).ifPresent(u -> {
    				var existingRoles = getExisingRoles(u.getId());
    				var finalRoles = new HashSet<>(existingRoles);
    				finalRoles.add(roleName);
    				userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), u, finalRoles);
    			});
    		});
    	}
    	return role;
    }
    
    private Set<String> getExisingRoles(String userId) {
    	Set<String> roleIds = new HashSet<> ();
    	userRoleRepo.findByUserId(userId).ifPresent(ur -> {
    		if(ur.getRoleIds() != null) {
    			roleIds.addAll(ur.getRoleIds());
    		}
    	});
		return roleRepo.findByIdIn(roleIds).stream().map(r -> r.getName()).collect(Collectors.toSet());
	}

	public Optional<Role> getRole(String id) {
        return roleRepo.findById(id);
    }

    public Optional<Role> getRoleByName(String name) {
        return roleRepo.findByName(name);
    }

    public void deleteRole(String roleId) {
        roleRepo.findById(roleId).ifPresent(role -> {
        	validateCondition(role.isSystem(), "role_delete_error_system");
        	userService.getUsersByRole(role).stream().forEach(user -> {
        		userService.removeRoleFromUser(user.getId(), roleId);
        		sendEmail(user, role);
        	});
        	tagService.removeTagsFor(Taggable.role, roleId);
        	roleRepo.deleteById(roleId);
        });
    }

	public Set<Role> listRoles() {
		Set<Role> roles = new HashSet<>();
		List<Role> allRoles = roleRepo.findAll().stream()
				.filter(r -> !RoleConstants.GHOST.equalsIgnoreCase(r.getName()))
				.filter(r -> !RoleConstants.SYNAPSE_APPROVER.equalsIgnoreCase(r.getName()))
				.collect(Collectors.toList());
		allRoles.forEach(r -> {
			Set<ResourcePrivilege> privileges = new HashSet<>();
			privileges.addAll(r.getPrivileges());
			Permissions.getBasePermissions().forEach(p -> {
				privileges.add(new ResourcePrivilege("global", p));
			});
			r.setPrivileges(privileges);
		});
		// add SYNAPSE_APPROVER role to only super admins
		if(SyncariContext.getUser().isSuperAdmin() || SyncariContext.getUser().isGhostUser()){
			roleRepo.findByName(RoleConstants.SYNAPSE_APPROVER).ifPresent(r -> allRoles.add(r));
		}
		if (SyncariContext.getUser().isSuperAdmin() || SyncariContext.getUser().isGhostUser()
				|| userService.isOrgAdminInAnyInstance(SyncariContext.getUser())) {
			return allRoles.stream().collect(Collectors.toSet());
		}
		Set<String> userRoleNames = userService.getUserRoles(SyncariContext.getUser().getId())
				.getOrDefault(SyncariContext.getSyncariId(), Set.of());
		List<Role> userRoles = roleRepo.findByNameIn(userRoleNames);
		userRoles.forEach(r -> {
			Set<ResourcePrivilege> privileges = new HashSet<>();
			privileges.addAll(r.getPrivileges());
			Permissions.getBasePermissions().forEach(p -> {
				privileges.add(new ResourcePrivilege("global", p));
			});
			r.setPrivileges(privileges);
		});
		Set<String> userRolePrivs = new HashSet<String>();
		userRoles.forEach(ur -> {
			userRolePrivs.addAll(ur.getPrivileges().stream().map(p -> p.getResourceId() + p.getPrivilegeId())
						.collect(Collectors.toSet()));
		});
		allRoles.stream().forEach(r -> {
			Set<String> allRolePrivs = r.getPrivileges().stream().map(p -> p.getResourceId() + p.getPrivilegeId())
					.collect(Collectors.toSet());
			if(userRolePrivs.containsAll(allRolePrivs)) {
				roles.add(r);
			}
		});
		return roles;
	}

    public void addPrivilegeToRole(AuthzContext context) {
        Role existingRole = roleRepo.findById(context.getRoleId()).get();
        existingRole.getPrivileges().add(new ResourcePrivilege(context.getResource(), context.getPrivilege()));
        roleRepo.save(existingRole);
    }

    public void removePrivilegeFromRole(AuthzContext context) {
        Role existingRole = roleRepo.findById(context.getRoleId()).get();
        existingRole.getPrivileges().remove(new ResourcePrivilege(context.getResource(), context.getPrivilege()));
        roleRepo.save(existingRole);
    }

	public Role editRole(String roleId, Role inRole, Set<String> tags, List<String> users) {
		 roleRepo.findById(roleId).ifPresent(r -> {
			 validateCondition(r.isSystem(), "role_edit_error_system");
			 validateCondition(StringUtils.isBlank(inRole.getName()), i18n("invalid_role_name_empty"));
			 var roleForNameCheck = roleRepo.findByName(inRole.getName());
			 validateCondition(roleForNameCheck.isPresent() && !roleForNameCheck.get().getId().equals(roleId), i18n("invalid_role_name_exist"));
			 r.setName(inRole.getName())
			 .setDescription(inRole.getDescription())
			 .setActive(inRole.isActive())
			 .setSystem(false)
			 .setPrivileges(inRole.getPrivileges());
			 roleRepo.save(r);
			 
			 List<Tag> tagMap = tags.stream().map(name -> new Tag(name, true, Taggable.role, roleId))
					 .collect(Collectors.toList());
			 tagService.updateTagsFor(roleId, Taggable.role, tagMap);
			 
			 updateUserRoles(r, users);
			 
	     });
    	
    	return getRole(roleId).get();
	}

	private void updateUserRoles(Role role, List<String> userIds) {
    	String roleName = role.getName();
    	var usersFromDb = userService.getUsersByRole(role).stream().map(r->r.getId()).collect(Collectors.toList());
    	if(CollectionUtils.isNotEmpty(userIds)) {
    		userIds.stream().forEach(id -> {
    			userService.findUserById(id).ifPresent(u -> {
    				var existingRoles = getExisingRoles(u.getId());
    				var finalRoles = new HashSet<>(existingRoles);
    				finalRoles.add(roleName);
    				userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), u, finalRoles);
    				usersFromDb.remove(u.getId());
    			});
    		});
    	}
    	usersFromDb.stream().forEach(userId -> {
    		userService.removeRoleFromUser(userId, role.getId());
    		sendEmail(userService.getUserById(userId), role);
    	});
	}
	
	private void sendEmail(User user, Role role) {
		try {
			String userName = "";
			if (StringUtils.isNotBlank(user.getFirstName()) && StringUtils.isNotBlank(user.getLastName())) {
				userName = user.getFirstName() + " " + user.getLastName();
			} else {
				userName = user.getEmail();
			}

			String subject = i18n("role_revoke_email_subject", role.getName(), userName,
					SyncariContext.getInstance().getName());

			Map<String, Object> context = new HashMap<>();
			context.put("name", userName);
			context.put("instance_name", SyncariContext.getInstance().getName());
			String body = renderer.render(ROLE_REVOKE_TEMPLATE_PATH, context);
			log.debug("Email Subject: " + subject);
			emailService.sendHtml(List.of(user.getEmail()), subject, body);
		} catch (Exception e) {
			log.error("sending email notification failed ", e);
		}
	}
}
