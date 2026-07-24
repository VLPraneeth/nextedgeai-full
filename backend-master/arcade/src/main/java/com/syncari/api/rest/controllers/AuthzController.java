package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ADD_PRIV_TO_ROLE;
import static com.syncari.core.security.Permissions.ADD_ROLE;
import static com.syncari.core.security.Permissions.DELETE_ROLE;
import static com.syncari.core.security.Permissions.EDIT_ROLE;
import static com.syncari.core.security.Permissions.LIST_ROLES;
import static com.syncari.core.security.Permissions.REMOVE_PRIV_FROM_ROLE;
import static com.syncari.core.security.Permissions.REMOVE_ROLE_FROM_USR;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.*;
import java.util.stream.Collectors;

import com.syncari.core.model.User;
import com.syncari.core.model.UserRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import com.syncari.api.rest.controllers.data.PrivilegeDTO;
import com.syncari.api.rest.controllers.data.RoleRequestDTO;
import com.syncari.api.rest.controllers.data.RoleResponseDTO;
import com.syncari.api.rest.controllers.data.RoleTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.model.Event;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.EventService;
import com.syncari.core.service.UserService;
import com.syncari.core.service.authz.AuthzContext;
import com.syncari.core.service.authz.AuthzService;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/v1/authz")
public class AuthzController {
	private static final String COMP = "arcade";
	@Autowired
	AuthzService authzService;
	@Autowired
	UserService userService;
	@Autowired
	EventService eventService;
	@Autowired
    RoleTransformer transformer;

	@Secured(ADD_ROLE)
	@RequestMapping(method = RequestMethod.POST, value = "/role")
	public RoleResponseDTO addRole(@RequestBody RoleRequestDTO dto) {
		validateCondition(dto == null, i18n("invalid_role_details"));
		List<String> userIds = List.of();
		if(dto.isActive()) {
			userIds = dto.getUsers();
		}
		var role = authzService.addRole(transformer.toRole(dto), dto.getTags() == null ? Set.of() : dto.getTags(),
				userIds == null ? List.of() : userIds);
		var users = userService.getUsersByRole(role);
		return transformer.toDTO(role, users, Optional.empty());
	}

	@Secured(LIST_ROLES)
	@RequestMapping(method = RequestMethod.GET, value = "/roles")
	public Set<RoleResponseDTO> list() {
		return authzService.listRoles().stream().map(role -> {
			var users = userService.getUsersByRole(role);
			return transformer.toDTO(role, users, Optional.empty());
		}).collect(Collectors.toSet());
	}
	
	@Secured(LIST_ROLES)
	@RequestMapping(method = RequestMethod.GET, value = "/roles/all")
	public Map<String, Set<RoleResponseDTO>> listAll() {
		var instances = SyncariContext.getOrganziation().getInstances();
		Map<String, Set<RoleResponseDTO>> instanceRoleMap = new HashMap<String, Set<RoleResponseDTO>>();
		SyncariContext.push();
		instances.forEach(inst -> {
			SyncariContext.setInstance(inst);
			var roles = authzService.listRoles().stream()
					.filter(r -> !RoleConstants.ORG_ADMIN.equals(r.getName()))
					.map(role -> {
				var users = userService.getUsersByRole(role);
				return transformer.toDTO(role, users, Optional.empty());
			}).collect(Collectors.toSet());
			instanceRoleMap.put(inst.getSyncariId(), roles);
		});
		SyncariContext.restore();
		return instanceRoleMap;
	}

	@Secured(LIST_ROLES)
	@RequestMapping(method = RequestMethod.POST, value = "/roles/all")
	public Map<String, Set<RoleResponseDTO>> listAllRoles(@RequestParam("userId") String userId) {
		validateCondition(StringUtils.isEmpty(userId), "Give user id to find roles cannot be empty");
		Optional<User> user = userService.findUserById(userId);
		var instances = SyncariContext.getOrganziation().getInstances();
		Map<String, Set<RoleResponseDTO>> instanceRoleMap = new HashMap<String, Set<RoleResponseDTO>>();
		try{
			SyncariContext.push();
			user.ifPresent(u -> {
				u.setAdmin(userService.isOrgAdminInAnyInstance(u));
				instances.forEach(inst -> {
					SyncariContext.setInstance(inst);
					var roles = authzService.listRoles().stream()
							.map(role -> {
								var users = userService.getUsersByRole(role);
								return transformer.toDTO(role, users, Optional.of(u));
							}).collect(Collectors.toSet());
					instanceRoleMap.put(inst.getSyncariId(), roles);
				});
			});
		}finally {
			SyncariContext.restore();
		}
		return instanceRoleMap;
	}
	
	@Secured(LIST_ROLES)
	@RequestMapping(method = RequestMethod.GET, value = "/role/{roleId}")
	public RoleResponseDTO getRole(@PathVariable String roleId) {
		validateCondition(StringUtils.isBlank(roleId), i18n("invalid_role_id", roleId));
		var roleOpt = authzService.getRole(roleId);
		validateCondition(roleOpt.isEmpty(), i18n("invalid_role_id", roleId));
		var role = roleOpt.get();
		var users = userService.getUsersByRole(role);
		return transformer.toDTO(role, users, Optional.empty());
	}

	@Secured(DELETE_ROLE)
	@RequestMapping(method = RequestMethod.DELETE, value = "/role/{roleId}")
	public void deleteRole(@PathVariable String roleId) {
		validateCondition(StringUtils.isBlank(roleId), i18n("invalid_role_id", roleId));
		authzService.deleteRole(roleId);
	}
	
	@Secured(EDIT_ROLE)
	@RequestMapping(method = RequestMethod.PUT, value = "/role/{roleId}")
	public RoleResponseDTO editRole(@PathVariable String roleId, @RequestBody RoleRequestDTO dto) {
		validateCondition(dto == null, "invalid_role_details");
		validateCondition(StringUtils.isBlank(roleId), i18n("invalid_role_id", roleId));
		List<String> userIds = List.of();
		if(dto.isActive()) {
			userIds = dto.getUsers();
		}
		var role = authzService.editRole(roleId, transformer.toRole(dto),
				dto.getTags() == null ? Set.of() : dto.getTags(), userIds == null ? List.of() : userIds);
		var users = userService.getUsersByRole(role);
		return transformer.toDTO(role, users, Optional.empty());
	}

	@Secured(ADD_PRIV_TO_ROLE)
	@RequestMapping(method = RequestMethod.POST, value = "/role/{roleId}/privilege/{privilegeId}")
	@ApiOperation(value = "addPrivilegeToRole", notes = "An api to add a privilege to a role.")
	public String addPrivilegeToRole(@PathVariable String roleId, @PathVariable String privilegeId) {
		authzService.addPrivilegeToRole(new AuthzContext(null, roleId, privilegeId, null));
		eventService.log(getEvent(EventTypes.ADD_PRIV_TO_ROLE));
		return "success";
	}

	@Secured(REMOVE_PRIV_FROM_ROLE)
	@RequestMapping(method = RequestMethod.DELETE, value = "/role/{roleId}/privilege/{privilegeId}")
	public String removePrivilegeFromRole(@PathVariable String roleId, @PathVariable String privilegeId) {
		authzService.removePrivilegeFromRole(new AuthzContext(null, roleId, privilegeId, null));
		eventService.log(getEvent(EventTypes.REMOVE_PRIV_FROM_ROLE));
		return "success";
	}

	@Secured(REMOVE_ROLE_FROM_USR)
	@RequestMapping(method = RequestMethod.DELETE, value = "/user/{userId}/role/{roleId}")
	public String removeRoleFromUser(@PathVariable String userId, @PathVariable String roleId) {
		userService.removeRoleFromUser(userId, roleId);
		eventService.log(getEvent(EventTypes.REMOVE_ROLE_FROM_USR));
		return "success";
	}
	
	@Secured(LIST_ROLES)
	@RequestMapping(method = RequestMethod.GET, value = "/privileges")
	public Set<PrivilegeDTO> getPrivileges() {
		return authzService.listPrivileges().stream().filter(priv -> !Permissions.getCustomRoleExcludedPermissions().contains(priv.getPrivilegeId()))
				.map(priv -> transformer.toDTO(priv)).collect(Collectors.toSet());
	}
	
	private Event getEvent(String type) {
		return new Event().setType(type).setClient("application").setComponent(COMP);
	}
	
}
