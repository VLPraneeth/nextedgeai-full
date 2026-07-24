package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.core.utils.ImageUtils.getMediaType;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.InstanceState;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.service.*;
import com.syncari.restutils.data.InstanceResponse;
import com.syncari.restutils.validations.OrganisationValidations;
import com.syncari.utils.I18n;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.syncari.restutils.utils.ImageUtil;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.core.util.SSOConfigTransformer;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.controllers.data.InstanceRequest;
import com.syncari.api.rest.controllers.data.Organization;
import com.syncari.restutils.data.ProvisionRequest;
import com.syncari.api.rest.controllers.data.UserRequest;
import com.syncari.api.rest.controllers.data.UserResponse;
import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Instance;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.RoleConstants;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/organization")
public class SubscriptionController {
    @Autowired
    UserService userService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ProvisioningService provisioningService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    SSOConfigTransformer ssoConfigTransformer;
    @Autowired
    SyncariContextHandler syncariContextHandler;
    @Autowired
    Util util;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    AppConfig appConfig;
    @Autowired
    ImageUtil imageUtil;
    @Autowired
    OrganisationValidations organisationValidations;
    @Autowired
    ConnectorMetadataService connectorMetadataService;
    @Autowired
    GCSFileManager gcsFileManager;
    private static final Map<String, MediaType> mediatypeMap = Map.of(
            "png", MediaType.IMAGE_PNG,
            "svg", MediaType.parseMediaType("image/svg+xml")
    );

    @Secured(LIST_ORG)
    @RequestMapping(method = RequestMethod.GET, value = "/")
    public List<Organization> listOrg() { return transformer.toOrgs(provisioningService.listOrg()); }

    @Secured(PROVISION_ORG)
    @RequestMapping(method = RequestMethod.POST, value = "/")
    public Organization addOrganization(@RequestBody ProvisionRequest request) {
        validateAddOrgRequest(request);
		InstanceType type = InstanceType.production;
		try {
			type = InstanceType.valueOf(request.getInstanceType());
		} catch (Exception e) {
		}
        // trim organization name
        String organizationName = request.getOrganizationName();
		organizationName = StringUtils.substring(organizationName, 0, Math.min(organizationName.length(), 30));
		OrganizationType organizationType = getOrgType(request.getOrgType());
		String maxInstances = provisioningService.getMaxInstances(request.getMaxInstance(), organizationType);

        return transformer.toOrg(
            provisioningService.provision(
                request.getInstanceName(),
                type,
                request.getInstanceDisplayName(),
                organizationName,
                request.getAdminUserName(),
                request.getPlanName(),
                RoleConstants.ORG_ADMIN,
                request.getAdminFirstName(),
                request.getAdminLastName(), getOrgType(request.getOrgType()),maxInstances
            )
        );
    }

    @Secured(PROVISION_ORG)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{orgId}")
    public String deleteOrganization(@PathVariable("orgId") String orgId) {
        provisioningService.deprovision(orgId, false);
        log.info("Successfully marked subscription {} for deletion", orgId);
        return "success";
    }

    @Secured(DELETE_INSTANCE)
    @RequestMapping(method = RequestMethod.DELETE, value = "/instance/{syncariId}")
    public String deleteInstance(@PathVariable String syncariId) {
        provisioningService.deprovInstance(syncariId, false);
        log.info("Successfully marked instance {} for deletion", syncariId);
        return "success";
    }

    @Secured(SUB_EDIT)
    @RequestMapping(method = RequestMethod.PUT, value = "/")
    public Organization updateOrganization(
            @RequestParam("id") String id,
            @RequestParam("name") String name,
            @RequestParam(name = "logo", required = false) MultipartFile file,
            @RequestParam("type") OrganizationType type,@RequestParam("maxInstance") String maxInstance
    ) throws IOException {
        if(StringUtils.isBlank(name)) throw new RuntimeException("Subscription name cannot be blank");
        com.syncari.core.model.Organization org = new com.syncari.core.model.Organization();
        // Make sure user is updating the current org he/she logged in and not any orgId passed to API
        org.setId(SyncariContext.getOrganziation().getId());

        org.setName(name);
        org.setOrgType(type);
        imageUtil.validateFile(file);
        String maximumInstances = provisioningService.getMaxInstances(maxInstance, type);
        if (StringUtils.isNotEmpty(maximumInstances) ){
            if (!SyncariContext.getUser().isSuperAdmin()){
                throw new SyncariValidationException(I18n.i18n("permission_denied_update_max_instance"));
            }
            org.setMaxNumberOfInstances(maximumInstances);
        }
        return transformer.toOrg(subscriptionService.updateOrg(org, file == null ? null : file.getInputStream(),
                file == null ? null : file.getOriginalFilename()));
    }

    @Secured(SUB_EDIT)
    @RequestMapping(method = RequestMethod.PUT, value = "/extendTrial")
    public boolean extendTrialInstance(@RequestParam("instanceId") String instanceId, @RequestParam(name="extendedDate", required = false) String extendedDate,
                                       @RequestParam(name = "extendedRecordLimit", required = false) Integer extendedRecordLimit){
        if(StringUtils.isBlank(extendedDate) && (extendedRecordLimit == 0)) throw new RuntimeException("Instance extended Date or extended record limit, one of them should be provided to extend");
        if(StringUtils.isBlank(instanceId) ) throw new RuntimeException("Instance Id should be provided to extend the trial");
        // validate date
        return subscriptionService.extendTrialInstance(instanceId, Optional.ofNullable(extendedDate), Optional.ofNullable(extendedRecordLimit));
    }

    @Secured(LIST_INSTANCE_STATE)
    @RequestMapping(method = RequestMethod.GET, value = "/instanceState/{instanceId}")
    public InstanceState getInstanceState(@PathVariable String instanceId){
        if (!SyncariContext.getUser().isSuperAdmin() && !SyncariContext.isGhost()) {
            User user = SyncariContext.getUser();
            userService.validateUserInstances(user, instanceId);
        }
        return subscriptionService.getInstanceState(instanceId);
    }


    /*@Secured(SUB_EDIT)
    @RequestMapping(method = RequestMethod.POST, value = "/{orgId}/sso")
    public SSOAuthConfigDTO updateSSOConfig(@PathVariable("orgId") String orgId, @RequestBody SSOAuthConfigDTO ssoConfig) {
        validateCondition(!SyncariContext.getOrganziation().getId().equals(orgId), "Invalid orgId");
        SSOAuthConfig ssoAuthConfig = subscriptionService.updateSSOForOrg(SyncariContext.getOrganziation(), ssoConfigTransformer.toSSOAuthConfig(ssoConfig));
        return ssoConfigTransformer.toSSOAuthConfigDTO(ssoAuthConfig);
    }

    @Secured(SUB_EDIT)
    @RequestMapping(method = RequestMethod.GET, value = "/{orgId}/sso")
    public SSOAuthConfigDTO getSSOConfig(@PathVariable("orgId") String orgId) {
        validateCondition(!SyncariContext.getOrganziation().getId().equals(orgId), "Invalid orgId");
        return ssoConfigTransformer.toSSOAuthConfigDTO(SyncariContext.getOrganziation().getSsoConfig());
    }*/

    @ResponseBody
//    @Secured(SUB_EDIT)
    @RequestMapping(method = RequestMethod.GET, value = "/photo")
    public ResponseEntity<StreamingResponseBody> getOrgLogo() {
        var photoStream = subscriptionService.getOrgLogo(SyncariContext.getOrganziation());
        String photoLocation = SyncariContext.getOrganziation().getLogoLocation();
        if (photoLocation == null) {
            photoLocation = GlobalConstants.BUSINESS_LOGO;
        }
       HttpHeaders headers = new HttpHeaders();
        headers.setContentType(getMediaType(photoLocation));
        StreamingResponseBody stream = outputStream -> photoStream.transferTo(outputStream);
        return new ResponseEntity<>(stream, headers, HttpStatus.OK);
    }

    @Secured(ADD_INSTANCE)
    @RequestMapping(method = RequestMethod.POST, value = "/instance")
    public Instance addInstance(@RequestBody InstanceRequest request) {
        validateAddInstanceRequest(request);
        return provisioningService.provisionInstance(SyncariContext.getOrganziation(), request.getInstanceName(), request.getDisplayName(),
                request.getType(), request.getPlanName(), SyncariContext.getUser());
    }

    @Secured(EDIT_INSTANCE)
    @RequestMapping(method = RequestMethod.PUT, value = "/instance")
    public Instance editInstance(@RequestBody InstanceRequest request) {
        if (StringUtils.isBlank(request.getSyncariId()) || StringUtils.isBlank(request.getDisplayName())
                    || request.getType() == null){
            String message = i18n("blank_instance_request_attribute");
            log.error(message);
            throw new SyncariValidationException(message);
        }
        return provisioningService.updateInstance(request.getSyncariId(), request.getDisplayName(), request.getType());
    }

    @Secured(LIST_INSTANCE)
    @RequestMapping(method = RequestMethod.GET, value = "/instance")
    public List<InstanceResponse> listInstances() {
        return transformer
                .toInstanceResponses(provisioningService.listInstances(SyncariContext.getOrganziation().getId()));
    }

    @Secured(INVITE_USER)
    @RequestMapping(method = RequestMethod.POST, value = "/user")
    public UserResponse invite(@RequestBody UserRequest user) {
        organisationValidations.validateNewUser(user.getEmail(), user.isApiUser(), user.getUserRoles());
        User newUser = transformer.toUser(user);
        newUser.setPassword(User.generatePassword());
        newUser = provisioningService.inviteUser(newUser, user.getUserRoles(), false, Optional.empty());
        return transformer.toUserResponse(newUser);
    }

    @Secured(REINVITE_USER)
    @RequestMapping(method = RequestMethod.POST, value = "/user/reinvite/{userId}")
    public void reinvite(@PathVariable String userId) {
        provisioningService.reinviteUser(userId);
    }

    // TODO: EDIT_USR permission?
    @Secured(INVITE_USER)
    @RequestMapping(method = RequestMethod.PATCH, value = "/user/{userId}/roles")
    public void updateUser(@PathVariable String userId, @RequestBody UserRequest user) {
        user.getUserRoles().forEach((instanceId, roles) -> {
        	if(user.isOrgAdmin()) {
        		roles.add(RoleConstants.ORG_ADMIN);
        	}
            if(roles.size() < 1) {
                throw new RuntimeException(String.format(i18n("missing_user_roles"), instanceId));
            }
        });
        User newUser = transformer.toUser(user);
        newUser.setId(userId);
        userService.updateUser(newUser, null, null);
        userService.updateUserRoles(userId, user.getUserRoles());
    }

    @Secured(REMOVE_USER)
    @RequestMapping(method = RequestMethod.POST, value = "/user/{userId}/remove")
    public void removeUser(@PathVariable String userId) {
        userService.removeUserFromCurrentOrg(userId);
    }

    @Secured(DELETE_USR)
    @RequestMapping(method = RequestMethod.DELETE, value = "/user/{userId}")
    public void deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
    }

    @Secured(DEACTIVATE_USER)
    @RequestMapping(method = RequestMethod.POST, value = "/user/{userId}/deactivate")
    public void deactivateUser(@PathVariable String userId) {
        userService.deactivateUser(userId);
    }

    @Secured(ACTIVATE_USER)
    @RequestMapping(method = RequestMethod.POST, value = "/user/{userId}/activate")
    public void activateUser(@PathVariable String userId) {
        userService.activateUser(userId);
    }

    @Secured(LIST_USER)
    @RequestMapping(method = RequestMethod.GET, value = "/users")
    public List<UserResponse> list() {
        List<User> users = userService.list(SyncariContext.getOrganziation().getId());
        List<UserResponse> response = new ArrayList<>();
        users.stream().forEach(u -> {
        	if(!u.isSystemUser()) {
        		if(!userService.isGhost(u)) {
                    UserResponse usr = transformer.toUserResponse(u);
        			response.add(usr);
        		}
        	}
        });
        return response;
    }

    @Secured(LIST_USER)
    @RequestMapping(method = RequestMethod.GET, value = "/system-users")
    public List<UserResponse> listSystemUsers() {
        List<User> users = userService.getAllActiveSystemUsers();
        List<UserResponse> response = new ArrayList<>();
        users.stream().forEach(u -> {
            UserResponse usr = transformer.toUserResponse(u);
            response.add(usr);
        });
        return response;
    }

    @Secured(READ_PROFILE)
    @RequestMapping(method = RequestMethod.GET, value = "/icon")
    public ResponseEntity<StreamingResponseBody> getIcon(@RequestParam String path) {
        HttpHeaders headers = new HttpHeaders();
        var extensionParts = path.split("\\.");
        var extension = extensionParts.length > 0 ? extensionParts[extensionParts.length - 1] : "png";
        headers.setContentType(mediatypeMap.getOrDefault(extension.toLowerCase(), MediaType.IMAGE_PNG));
        String iconPath = extensionParts.length == 1 ? ConnectorMetadataService.CUSTOM_SYNAPSE_DEFAULT_ICON : path;

        var iconStream = gcsFileManager.readFile(iconPath);
        StreamingResponseBody stream = outputStream -> iconStream.transferTo(outputStream);
        return new ResponseEntity<>(stream, headers, HttpStatus.OK);

    }

    private void validateAddOrgRequest(ProvisionRequest request){
        String firstName = request.getAdminFirstName();
        String userName = request.getAdminUserName();
        String organizationName = request.getOrganizationName();
        String instanceName = request.getInstanceName();
        if (StringUtils.isBlank(firstName) || StringUtils.isBlank(userName) || StringUtils.isBlank(organizationName)){
            String message = i18n("blank_provision_request_attribute");
            log.error(message);
            throw new SyncariValidationException(message);
        }
        if (StringUtils.isBlank(instanceName)){
            String message = i18n("blank_instance_request_attribute");
            log.error(message);
            throw new SyncariValidationException(message);
        }
        if ((instanceName.length() > 30)) {
            String message = i18n("length_instance_request_attribute");
            log.error(message);
            throw new SyncariValidationException(message);
        }
        String emailRegex = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])";
        Pattern p = Pattern.compile(emailRegex);
        //lowercase email and setting that to request to store that
        userName = userName.toLowerCase();
        request.setAdminUserName(userName);
        Matcher matcher = p.matcher(userName);
        if (!matcher.matches()){
            String message = i18n("username_invalid_request_attribute");
            log.error(message);
            throw new SyncariValidationException(message);
        }
    }

    private void validateAddInstanceRequest(InstanceRequest request){
        String instanceName = request.getInstanceName();
        String displayName = request.getDisplayName();

        if (StringUtils.isBlank(instanceName) || StringUtils.isBlank(displayName)){
            String message = i18n("blank_instance_request_attribute");
            log.error(message);
            throw new SyncariValidationException(message);
        }
        if ((instanceName.length() > 30) || (displayName.length() > 30)) {
            String message = i18n("length_instance_request_attribute");
            log.error(message);
            throw new SyncariValidationException(message);
        }
    }

    private OrganizationType getOrgType(String type) {
        OrganizationType orgType = OrganizationType.standard;
        if(!StringUtils.isBlank(type)) {
            try {
                orgType = OrganizationType.valueOf(type);
            } catch (Exception e) {
                log.error("Invalid org type {}", type);
            }
        }
        return orgType;
    }
}
