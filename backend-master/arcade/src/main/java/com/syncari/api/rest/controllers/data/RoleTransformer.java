package com.syncari.api.rest.controllers.data;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.syncari.core.SyncariContext;
import com.syncari.core.service.UserService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.Role;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.ResourcePrivilege;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.TagService;
import com.syncari.utils.I18n;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RoleTransformer {
	@Autowired
    TagService tagService;

	@Autowired
	UserService userService;
	
	public RoleResponseDTO toDTO(Role role, List<User> users, Optional<User> selectedUser) {
		Set<ResourcePrivilege> privileges = new HashSet<>();
		privileges.addAll(role.getPrivileges().stream()
				.filter(p -> !Permissions.getCustomRoleExcludedPermissions().contains(p.getPrivilegeId()))
				.collect(Collectors.toList()));
		Permissions.getBasePermissions().forEach(p -> {
			privileges.add(new ResourcePrivilege("global", p));
		});
		return new RoleResponseDTO()
				.setId(role.getId())
				.setName(role.getName())
				.setDescription(role.getDescription() == null ? role.getName() : role.getDescription())
				.setActive(role.isActive())
				.setSystem(role.isSystem())
				.setTags(tagService.getTagNames(Taggable.role, role.getId()))
				.setPrivileges(privileges.stream().map(priv -> toDTO(priv)).collect(Collectors.toList()))
				.setUsers(users.stream().map(user -> {
					UserResponse userResponse = toDTO(user);
					selectedUser.ifPresent(u -> {
						// this is to improve performance so that only add for selected user and caller will userservice.isOrgAdmininAnyInstance once
						// and set the response user isAdmin flag.
						if (u.getId().equals(user.getId())){
							userResponse.setOrgAdmin(u.isAdmin());
						}
					});
					return userResponse;
				}).collect(Collectors.toList()));
	}
	
	public Role toRole(RoleRequestDTO request) {
		var role =  new Role()
				.setName(request.getName())
				.setDescription(request.getDescription())
				.setActive(request.isActive())
				.setSystem(false);
		Set<ResourcePrivilege> privileges = new HashSet<>();
		Permissions.getBasePermissions().forEach(p -> {
			privileges.add(new ResourcePrivilege("global", p));
		});
		if (CollectionUtils.isNotEmpty(request.getPrivileges())) {
			privileges.addAll(request.getPrivileges().stream().map(p -> new ResourcePrivilege("global", p))
					.collect(Collectors.toSet()));
		}
		role.setPrivileges(privileges);
		return role;
	}
	
	public PrivilegeDTO toDTO(ResourcePrivilege priv) {
		return new PrivilegeDTO()
				.setPrivilegeId(priv.getPrivilegeId())
				.setResourceId(priv.getResourceId())
				.setDisplayName(I18n.i18n("permission_" +priv.getPrivilegeId().toLowerCase()));
	}
	
	private UserResponse toDTO(User user) {
		var userDTO = new UserResponse();
		userDTO.setId(user.getId());
		userDTO.setEmail(user.getEmail());
		userDTO.setFirstName(user.getFirstName());
		userDTO.setLastName(user.getLastName());

		return userDTO;
	}
	
}
