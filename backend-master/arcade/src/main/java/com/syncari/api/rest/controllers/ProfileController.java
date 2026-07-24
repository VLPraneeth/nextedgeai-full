package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.controllers.data.*;
import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Organization;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.GhostAccessAuditRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.*;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.restutils.data.InstanceResponse;
import com.syncari.restutils.utils.ImageUtil;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.core.utils.ImageUtils.getMediaType;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/user")
public class ProfileController {
	@Autowired
	UserService userService;
	@Autowired
	ObjectTransformer transformer;
	@Autowired
	AuthzService authzService;
	@Autowired
	ProvisioningService provisioningService;
	@Autowired
	SubscriptionService subService;
	@Autowired
	private SyncariContextHandler synCtxHandler;
	@Autowired
	private Util util;
	@Autowired
	AppConfig appConfig;
	@Autowired
	FileUtil fileUtil;
	@Autowired
	ImageUtil imageUtil;
	@Autowired
	ErrorNotificationService errorNotificationService;
	@Autowired
	InstanceConfigurationService instanceConfigurationService;
	@Autowired
	GhostAccessAuditRepo ghostAccessAuditRepo;

//	@Secured(GET_PROFILE)
	@RequestMapping(method = RequestMethod.GET)
	public UserResponse getProfile() {
		User user = SyncariContext.getUser();
		UserResponse userDTO = transformer.toUserResponse(user);
		userDTO.setOrgAdmin(userService.isOrgAdminInAnyInstance(user));
		var userRoles = userService.getUserRoles(user.getId(), userDTO.isOrgAdmin());
		userRoles.forEach((key, val) -> {
			if(val != null) {
				userRoles.put(key, val.stream().filter(r -> !RoleConstants.ORG_ADMIN.equals(r)).collect(Collectors.toSet()));
			}
		});
		userDTO.setUserRoles(userRoles);
		userDTO.setPrivileges(authzService.listPrivileges().stream().map(p -> p.getPrivilegeId()).collect(Collectors.toList()));
		return userDTO;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/photo")
	@ResponseBody
	public ResponseEntity<StreamingResponseBody> getPhoto() {
		var photoStream = userService.getProfilePhoto(SyncariContext.getUser());
		String photoLocation = SyncariContext.getUser().getPhotoLocation();
		if (photoLocation == null) {
			photoLocation = UserService.DEFAULT_PROFILE_PICTURE;
		}
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(getMediaType(photoLocation));
		StreamingResponseBody stream = outputStream -> photoStream.transferTo(outputStream);
		return new ResponseEntity<>(stream, headers, HttpStatus.OK);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST)
	public UserResponse updateProfile(@RequestParam("firstName") String firstName,
			@RequestParam("lastName") String lastName,
			@RequestParam(name = "photo", required = false) MultipartFile file,
			@RequestParam("timeZone") String timeZone) throws IOException {
		User user = userService.findUserById(SyncariContext.getUser().getId()).get();
		user.setFirstName(firstName);
		user.setLastName(lastName);
        user.setTimeZone(timeZone == null || timeZone.equalsIgnoreCase("null") ? "" : timeZone);
        imageUtil.validateFile(file);
		return transformer.toUserResponse(userService.updateUser(user, file == null ? null : file.getInputStream(),
				file == null ? null : fileUtil.sanitizeFileName(file.getOriginalFilename())));
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/dashboard")
	public DashboardPreference updateDashboardPreference(@RequestBody DashboardPreference value) {
		UserPreference preference = userService.updateDashboardPreference(SyncariContext.getUser().getId(), "dashboard",
				value);
		return preference.getDashboard();
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/zoom")
	public ZoomPreference updateZoomPreference(@RequestBody ZoomPreference value) {
		UserPreference preference = userService.updateZoomPreference(SyncariContext.getUser().getId(), "zoom", value);
		return preference.getZoom();
	}

	@Secured(READ_PROFILE)
	@RequestMapping(method = RequestMethod.GET, value = "/preference")
	public UserPreference getPreference() {
		return userService.getPreference(SyncariContext.getUser().getId());
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/schemaStudio/entityColumns")
	public UserPreference updateSchemaStudioEntityColumnsPreference(@RequestBody LinkedHashSet<Map<String, Object>> columns) {
		return userService.updateSchemaStudioEntityColumnsPreference(SyncariContext.getUser().getId(), columns);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/schemaStudio/fieldColumns")
	public UserPreference updateSchemaStudioFieldColumnsPreference(@RequestBody LinkedHashSet<Map<String, Object>> columns) {
		return userService.updateSchemaStudioFieldColumnsPreference(SyncariContext.getUser().getId(), columns);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/dataStudio/columns/{entityId}")
	public UserPreference updateDataStudioPreference(@PathVariable String entityId,
			@RequestBody LinkedHashSet<Map<String, Object>> columns) {
		return userService.updateDataStudioColumnPreference(SyncariContext.getUser().getId(), entityId, columns);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/syncStudio/fieldFilters/{entityId}")
	public UserPreference updateSyncStudioFieldFilters(@PathVariable String entityId,
			@RequestBody LinkedHashSet<String> filterSelections) {
		return userService.updateSyncStudioFieldsFiltersPreference(SyncariContext.getUser().getId(), entityId,
				filterSelections);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/syncStudio/hiddenFields/{entityId}")
	public UserPreference updateSyncStudioHiddenFields(@PathVariable String entityId,
			@RequestBody LinkedHashSet<String> hiddenFieldIds) {
		return userService.updateSyncStudioHiddenFieldsPreference(SyncariContext.getUser().getId(), entityId,
				hiddenFieldIds);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/syncStudio/pipelineViewports/{pipelineId}")
	public UserPreference updateSyncStudioPipelineViewports(@PathVariable String pipelineId,
			@RequestBody ArrayList<Number> pipelineViewports) {
		// The connector and entity graphs use this endpoint as well. They use
		// CONNECTOR_EDITOR_VIEWPORT and ENTITY_EDITOR_VIEWPORT as the pipelineIds respectively.
		return userService.updateSyncStudioPipelineViewportsPreference(SyncariContext.getUser().getId(), pipelineId,
				pipelineViewports);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/entityGraph")
	public UserPreference updateEntityGraphPreference(@RequestBody GraphPreference graphPreference) {
		return userService.updateEntityGraphPreference(SyncariContext.getUser().getId(), graphPreference);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/connectorGraph")
	public UserPreference updateConnectorGraphPreference(@RequestBody GraphPreference graphPreference) {
		return userService.updateConnectorGraphPreference(SyncariContext.getUser().getId(), graphPreference);
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/customPreference")
	public KeyValue updateCustomPreference(@RequestBody KeyValue customPreference) {
		return userService.updateCustomPreference(SyncariContext.getUser().getId(), customPreference);
	}

	@Secured(READ_PROFILE)
	@RequestMapping(method = RequestMethod.GET, value = "/preference/customPreference")
	public KeyValue getCustomPreference() {
		return userService.getCustomPreference(SyncariContext.getUser().getId());
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.PUT, value = "/updatepassword/{userId}")
	public UserResponse updatePassword(@PathVariable String userId, @RequestBody ResetPasswordRequest request) {
		return transformer
				.toUserResponse(userService.resetPassword(userId, request.getCurrentPwd(), request.getNewPwd()));
	}

	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.PUT, value = "/resetpassword/{userId}")
	public UserResponse resetPassword(@PathVariable String userId, @RequestBody ResetPasswordRequest request) {
		ValidationUtils.validateCondition(!userId.equals(SyncariContext.getUser().getId()), "User can only reset their own password");
		return transformer
				.toUserResponse(userService.resetPassword(userId, request.getCurrentPwd(), request.getNewPwd(), false));
	}

	@RequestMapping(method = RequestMethod.POST, value = "/setpassword/{invitationId}")
	public String setPassword(@PathVariable String invitationId, @RequestBody SetPasswordRequest request) {
		userService.setPassword(invitationId, request.getPassword());
		return "success";
	}

	@RequestMapping(method = RequestMethod.POST, value = "/forgotPassword")
	public Map<String, String> forgotPassword(@RequestParam("email") String email) {
		try {
			userService.forgotPassword(email);
		} catch (Exception e) {
			log.error(e.getMessage());
			try {
				//This is to delay the response if the email not found in the system.
				log.warn("ForgotPassword called with an invalid email {}", email);
				Thread.sleep(500);
			} catch (InterruptedException e1) {
				log.error("InterruptedException ", e);
			}
			if ((e instanceof SyncariValidationException) && (StringUtils.isNotEmpty(e.getMessage())) && (e.getMessage().contains("enabled SSO"))){
				return Map.of("header", I18n.i18n("sso_user_password_reset_header"), "subheader", I18n.i18n("sso_user_password_reset"));
			}
			if(e instanceof LockedException) {
				return Map.of("header", I18n.i18n("password_reset_header_locked"), "subheader", I18n.i18n("password_reset_subheader_locked"));
			}
		}
		return Map.of("header", I18n.i18n("password_reset_header"), "subheader", I18n.i18n("password_reset_subheader", email));
	}

	@RequestMapping(method = RequestMethod.GET, value = "/switch/instance/{syncariId}")
	public UserResponse switchInstance(@PathVariable String syncariId,@RequestHeader(SecurityConstants.TOKEN_HEADER) String previousToken, HttpServletResponse response) {

		User user = SyncariContext.getUser();
		log.info(String.format("Switching User: %s to Instance: %s", user.getEmail(), syncariId));
		Set<String> permissions = new HashSet<>();
		if (SyncariContext.getUser().isSuperAdmin() && SyncariContext.isGhost()) {
			// don't change current instance for superadmins whhen ghosted
			permissions.addAll(Permissions.adminPermissions());
			synCtxHandler.setContext(syncariId);
		} else {
			userService.validateUserInstances(user, syncariId);
			synCtxHandler.setContext(syncariId);
			Set<String> userRoles = userService.getUserRoleForInstance(user.getId(), SyncariContext.getInstance());
			userRoles.forEach(role -> permissions.addAll(authzService.getPermissions(role, user.isSuperAdmin())));
			userService.updateUserCurrentInstance(user, syncariId);
		}
		util.setInsightsProviderContext(SyncariContext.getInstance());

		// Check if user has active ghost access for this instance
		boolean isGhosted = SyncariContext.isGhost();
		if (!isGhosted) {
			List<GhostAccessAudit> activeGhostAccess = ghostAccessAuditRepo.findByRequesterIdAndSyncariIdAndStatus(
					user.getId(), syncariId, Status.ACTIVE.name());
			isGhosted = CollectionUtils.isNotEmpty(activeGhostAccess);
		}

		// create new JWT token and set in response header
		String token = util.parseJWTTokenAndUpdateUserWithNewLoginDetails(previousToken.replace("Bearer ", ""),
				user, new ArrayList<>(permissions), isGhosted);

		response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);

		return transformer.toUserResponse(user);
	}

	@RequestMapping(method = RequestMethod.GET, value = "/instances")
		public List<InstanceResponse> listUserInstances() {
		if(userService.isOrgAdminInAnyInstance(SyncariContext.getUser())) {
			List<String> orgIns = SyncariContext.getOrganziation().getInstances().stream()
					.filter(ins -> ins.getStatus() != Status.DELETED).map(ins -> ins.getSyncariId()).collect(Collectors.toList());
			var userIns = SyncariContext.getUser().getAvailableInstances();
			orgIns.forEach(insId -> {
				if(!userIns.contains(insId)) {
					userService.addToUserAvailableInstance(insId, SyncariContext.getUser().getId());
				}
			});
		}
		List<Instance> userInstances = userService.getUserActiveInstances(SyncariContext.getUser());
		List<InstanceResponse> responses = transformer.toInstanceResponses(userInstances);
		responses.stream().forEach(i -> {
			Optional<Organization> org = subService.getOptionalOrgBySyncariId(i.getSyncariId());
			org.ifPresentOrElse(o -> {
				i.setOrgName(o.getName());
				i.setOrgId(o.getId());
			}, () -> {
				log.warn("Org with syncariId {} not found", i.getSyncariId());
			});
		});
		return responses;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/zendeskJwtToken")
	public String generateZendeskJwtToken() {
		return util.generateZendeskJwtToken(appConfig.getZendeskSharedSecret());
	}

	@Deprecated
	@Secured(READ_PROFILE)
	@RequestMapping(method = RequestMethod.GET, value = "/errorCatalogMetaData")
	@ResponseBody
	public ErrorCatalogMetaData getErrorCatalogMetaData() {
		var frequencies = Arrays.asList(ErrorNotificationFrequency.values()).stream()
				.filter(f -> f != ErrorNotificationFrequency.IMMEDIATE).map(f -> {
					return ErrorFrequencyMetaData.builder().frequency(f.name()).label(f.getLabel()).build();
				}).collect(Collectors.toList());

		return new ErrorCatalogMetaData(errorNotificationService.getErrorCatalogs(),
				errorNotificationService.getChannels(), frequencies);
	}

	@Deprecated
	@Secured(WRITE_PROFILE)
	@RequestMapping(method = RequestMethod.POST, value = "/preference/errorNotification")
	public UserPreference updateErrorNotificationPreference(@RequestBody ErrorNotificationPreference errorNotification) {
		return userService.updateErrorNotificationPreference(SyncariContext.getUser().getId(), errorNotification);
	}

	@Secured(WRITE_STUDIO)
	@RequestMapping(method = RequestMethod.POST, value = "/realtimeIpWhitelist")
	public Map<String, String>  addOrUpdateAllowedRealtimeIps(@RequestBody String allowedIpsJson) {
		InstanceConfiguration instanceConfiguration = new InstanceConfiguration();
		ObjectMapper mapper = new ObjectMapper();
		try{
			Map<String, String> map = mapper.readValue(allowedIpsJson, new TypeReference<Map<String, String>>(){});
			instanceConfiguration.setKey(InstanceConfigurationService.INSTANCE_CONFIG_IPWHITELIST_KEY);
			instanceConfiguration.setValue(map.get(InstanceConfigurationService.INSTANCE_CONFIG_IPWHITELIST_KEY)); // New line seperated list
		}catch (JsonProcessingException jsonProcessingException){
			log.error("JsonProcessingException occurred ::", jsonProcessingException);
		}
		InstanceConfiguration savedConfig = instanceConfigurationService.saveInstanceConfiguration(instanceConfiguration, InstanceConfigurationService.INSTANCE_CONFIG_IPWHITELIST_KEY);
		return  Map.of(InstanceConfigurationService.INSTANCE_CONFIG_IPWHITELIST_KEY,savedConfig.getValue().toString());
	}

	@Secured(READ_STUDIO)
	@RequestMapping(method = RequestMethod.GET, value = "/realtimeIpWhitelist")
	public Map<String, String> listAllowedRealtimeIps() {
		Optional<InstanceConfiguration> savedConfig = instanceConfigurationService.getInstanceConfigurationByKey(InstanceConfigurationService.INSTANCE_CONFIG_IPWHITELIST_KEY);
		return savedConfig.map(s -> Map.of(InstanceConfigurationService.INSTANCE_CONFIG_IPWHITELIST_KEY,s.getValue().toString())).orElse(Map.of());
	}

}
