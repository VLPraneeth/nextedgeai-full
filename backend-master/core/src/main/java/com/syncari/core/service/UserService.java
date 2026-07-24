package com.syncari.core.service;

import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.misc.fragment.FragmentSharePreference;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.repositories.customer.UserPreferenceRepo;
import com.syncari.core.repositories.customer.UserRoleRepo;
import com.syncari.core.repositories.syncari.GhostAccessAuditRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserInvitationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.core.template.TemplateRenderer;
import com.syncari.utils.KeyValue;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component
public class UserService {
    private static final String RESET_PWD_TEMPLATE_PATH = "templates/password.reset.template";
    private static final String FORGOT_PWD_TEMPLATE_PATH = "templates/forgot.password.template";
    private static final String WELCOME_TEMPLATE_PATH = "templates/welcome.template";
    private static final String DEACTIVATE_USER_TEMPLATE_PATH = "templates/deactivate.user.template";
    private static final String REMOVE_USER_TEMPLATE_PATH = "templates/remove.user.template";
    public static final String DEFAULT_PROFILE_PICTURE = "syncaroo.png";
    public static final String SYNCARI_ADMIN_EMAIL = "admin@syncari.com";
    @Autowired
    UserRepo userRepo;
    @Autowired
    UserPreferenceRepo userPreferenceRepo;
    @Autowired
    UserRoleRepo userRoleRepo;
    @Autowired
    RoleRepo roleRepo;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    @Qualifier("plgEmailService")
    EmailService plgEmailService;

    @Autowired
    TemplateRenderer renderer;
    @Autowired
    UserInvitationRepo invitationRepo;
    @Autowired
    NotificationRepo inboxRepo;
    @Autowired
    GCSFileManager gcsFileManager;
    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    AppConfig appConfig;
    @Autowired
    UserInvitationRepo userInvitationRepo;
    @Autowired
    SubscriptionService subService;
    @Autowired
    EncryptionService encryptionService;
    @Autowired
    NotificationService notificationService;
    @Autowired
    AuthzService authzService;

    @Autowired
    GhostAccessAuditRepo ghostAccessAuditRepo;

    @Autowired
    InsightsSharingService insightsSharingService;

    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;

    private static final int MAX_RETRIES = 3;

    public void updateUserRoles(String userId, Map<String, Set<String>> instanceRoleMapping) {
        List<Instance> instances = SyncariContext.getOrganziation().getInstances();
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));

        try {
            SyncariContext.push();
            instances.forEach(instance -> {
                SyncariContext.setInstance(instance);

                String instanceId = instance.getSyncariId();
                Optional<UserRole> userRoleOpt = userRoleRepo.findByUserId(userId);

                if(!instanceRoleMapping.containsKey(instanceId) && userRoleOpt.isPresent()) {
                    // remove instance roles if the updated instance roles doesn't have a
                    // mapping for this instance
                    userRoleRepo.deleteById(userRoleOpt.get().getId());
                    user.removeAvailableInstance(instanceId);
                    // update current instance id if it matches the removed instanceId
                    if(StringUtils.equals(instanceId, user.getCurrentInstanceId())){
                        user.setCurrentInstanceId(user.getAvailableInstances().isEmpty() ? "" : user.getAvailableInstances().stream().findFirst().get());
                        log.info("Available instances {},  current instance {} for userid {}", user.getAvailableInstances(), user.getCurrentInstanceId(), user.getId());
                        user.removeAllLoginDetails();
                    }
                }
                else if(instanceRoleMapping.containsKey(instanceId)) {
                    // update instance roles
                    assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(),user, instanceRoleMapping.get(instanceId));
                }
            });
        }
        finally {
            SyncariContext.restore();
        }

        userRepo.save(user);
    }

    public User resetToken(User user) {
        return getUserByEmail(user.getEmail()).map(existingUser -> {
            if (existingUser.isApiUser()) {
                String clientId = generateRandomString(20);
                String clientSecret = generateRandomString(32);

                // validate client id is unique. if by chance it is not unique retry with a max number of retries 3
                for (int i = 0; i <= MAX_RETRIES; i++) {
                    try {
                        User clientIdUser = getUserByClientId(clientId);
                        clientId = generateRandomString(20);
                        if (i == MAX_RETRIES ) throw new RuntimeException("Issue setting client id");
                    } catch (NotFoundException nfe) {
                        break;
                    }
                }

                // update, save and return the user
                existingUser.setClientId(clientId);
                existingUser.setClientSecret(passwordEncoder.encode(clientSecret));

                userRepo.save(existingUser);
                existingUser.setClientSecret(clientSecret);
            }
            return existingUser;
        }).orElse(user);
    }


    public User addUser(User user) {
        if(user.getEmail() == null){
            throw new RuntimeException("User email is required");
        }
        if (user.isSuperAdmin() && !SyncariContext.getUser().isSuperAdmin()) {
            throw new RuntimeException("You do not have permission to create super admins");
        }
        if (user.isAdmin() && (!SyncariContext.getUser().isSuperAdmin() && !SyncariContext.getUser().isAdmin() & !SyncariContext.getUser().isSystemUser())) {
            throw new RuntimeException("You do not have permission to create admins");
        }
        if (user.isGhostUser() && (!SyncariContext.getUser().isSuperAdmin())) {
            throw new RuntimeException("You do not have permission to create ghost user");
        }
        // Set the user as ACTIVE if SSO is enabled for the org
        if(SyncariContext.getOrganziation().isSSOEnabled()){
            user.setStatus(Status.ACTIVE);
            user.setPassword(null);
        } else {
            user.setStatus(Status.PENDING);
            if (user.getPassword() != null) {
                user.setPassword(encryptPassword(user.getPassword()));
            }
        }
        user.setEmail(user.getEmail().toLowerCase());

        // add client id/secret if the user is an api user
        String clientSecret = null;
        if(user.isApiUser()){
            // set status to active
            user.setStatus(Status.ACTIVE);

            // generate client id/secret
            String clientId = generateRandomString(20);
            clientSecret = generateRandomString(32);

            // validate client id is unique. if by chance it is not unique retry with a max number of retries 3
            for (int i = 0; i <= MAX_RETRIES; i++) {
                try {
                    User clientIdUser = getUserByClientId(clientId);
                    clientId = generateRandomString(20);
                    if (i == MAX_RETRIES ) throw new RuntimeException("Issue setting client id");
                } catch (NotFoundException nfe) {
                    break;
                }
            }

            // update, save and return the user
            user.setClientId(clientId);
            user.setClientSecret(passwordEncoder.encode(clientSecret));
        }

        // save the user with encoded password but change the response to non-encoded password during add user only.
        userRepo.save(user);
        user.setClientSecret(clientSecret);

        return user;
    }

    public void assignRolesToUser(Organization org, Instance instance, User user, Set<String> roles) {
        if (user == null)
            throw new RuntimeException(i18n("user_cannot_null"));
        if (CollectionUtils.isEmpty(roles))
            return;
        Optional<User> currentUser = userRepo.findById(user.getId());
        currentUser.ifPresentOrElse(foundUser -> {
            canAssignRole(roles);
            SyncariContext.push();
            try {
                SyncariContext.setOrganziation(org);
                SyncariContext.setInstance(instance);
                Set<String> roleIds = roleRepo.findByNameIn(roles).stream().map(role->role.getId()).collect(Collectors.toSet());
                Optional<UserRole> existingRole = userRoleRepo.findByUserId(foundUser.getId());

                var newRole = existingRole.orElse(new UserRole(foundUser.getId())).setRoleIds(roleIds);
                userRoleRepo.save(newRole);

                Optional<UserPreference> userPref = userPreferenceRepo.findByUserId(foundUser.getId());
                if (userPref.isEmpty()) {
                    userPreferenceRepo.save(new UserPreference(foundUser.getId()));
                }
                log.info("Assigned roles {} to user {}", roles, foundUser.getId());
                insightsProviderIntegrator.assignGroupForRoles(roles,foundUser,SyncariContext.getSyncariId());
            } finally {
                SyncariContext.restore();
            }
            // Since user has added roles, add it to availableInstance of user
            user.addAvailableInstance(instance);
            userRepo.save(user);
        },() -> {
            throw new RuntimeException(i18n("user_not_found",user.getId()));
        });
    }


    /**
     * Removes ORG_ADMIN role from all instances of the given org
     * @param user
     * @param org
     */
    public void removeOrgAdminRoleForUser(User user, Organization org){
        org.getInstances().forEach(instance -> {
            SyncariContext.runWithContext(org, instance, SyncariContext.getUser(), () -> {
                removeRolesFromUser(user, Set.of(RoleConstants.ORG_ADMIN));
            });
        });
    }

    public void unAssignAllRolesFromUser(Organization org, Instance instance, User user) {
        if (user == null)
            throw new RuntimeException(i18n("user_cannot_null"));
        Optional<User> currentUser = userRepo.findById(user.getId());
        currentUser.ifPresentOrElse(foundUser -> {
            SyncariContext.push();
            try {
                SyncariContext.setOrganziation(org);
                SyncariContext.setInstance(instance);
                Optional<UserRole> existingRole = userRoleRepo.findByUserId(foundUser.getId());
                existingRole.ifPresent(eR -> removeRolesByIdsFromUser(foundUser, eR.getRoleIds()));
            } finally {
                SyncariContext.restore();
            }
            // Since user has added roles, add it to availableInstance of user
            foundUser.removeAvailableInstance(instance.getSyncariId());
            userRepo.save(foundUser);
        },() -> {
            throw new RuntimeException(i18n("user_not_found",user.getId()));
        });
    }

    public void addToUserAvailableInstance(String syncariId, String userId) {
    	Optional<User> currentUser = userRepo.findById(userId);
    	currentUser.ifPresent(user -> {
    		user.getAvailableInstances().add(syncariId);
    		log.info("Successfully added instance {} to user {}", syncariId, user.getId());
    		userRepo.save(user);
    	});
    }

    public void removeRoleFromUser(String userId, String roleId) {
        if (StringUtils.isBlank(userId))
            throw new RuntimeException("User id cannot be blank");
        if (StringUtils.isBlank(roleId))
            throw new RuntimeException("Role cannot be blank");
    	Role role = roleRepo.findById(roleId).get();
    	removeRolesFromUser(userRepo.findById(userId).get(), Set.of(role.getName()));
    }

    public void removeRolesFromUser(User user, Set<String> roleNames) {
        canAssignRole(roleNames);
        Set<String> roleIds = new HashSet<>();
        if (CollectionUtils.isNotEmpty(roleNames)){
            roleIds.addAll(roleRepo.findByNameIn(roleNames).stream().map(role->role.getId()).collect(Collectors.toSet()));
        }
        Optional<UserRole> existingRole = userRoleRepo.findByUserId(user.getId());
        existingRole.ifPresent(roles -> {
            roleIds.forEach(role -> roles.removeRole(role));
            userRoleRepo.save(roles);
            if (CollectionUtils.isEmpty(roles.getRoleIds())){
                userRoleRepo.deleteById(roles.getId());
            }
        	log.info("Removed roles {} from user {}", roleNames, user.getId());
        });
    }

    private void removeRolesByIdsFromUser(User user, Set<String> roleIds) {
        Optional<UserRole> existingRole = userRoleRepo.findByUserId(user.getId());
        existingRole.ifPresent(roles -> {
            roleIds.forEach(role -> roles.removeRole(role));
            userRoleRepo.save(roles);
            log.info("Removed roles {} from user {}", roleIds, user.getId());
        });
    }

    private void canAssignRole(Set<String> roleNames) {
    	if(SyncariContext.getUser().isSuperAdmin() || SyncariContext.getUser().isGhostUser() || SyncariContext.getUser().isSystemUser()) return;
    	Map<String, Set<String>> userRoles = getUserRoles(SyncariContext.getUser().getId());
    	for (Entry<String, Set<String>> entry : userRoles.entrySet()) {
    		// if the user is org admin in any one of the instance, they can assign roles to other instance
    		// note that eventually role should be assigned at org level too
			if(entry.getValue().contains(RoleConstants.ORG_ADMIN)) {
				return;
			}
		}
    	Optional<UserRole> currentUserRole = userRoleRepo.findByUserId(SyncariContext.getUser().getId());
    	currentUserRole.ifPresentOrElse(role -> {
    		Set<Role> currentUserRoles = roleRepo.findByIdIn(role.getRoleIds());
			Set<String> currentUserPrivileges = currentUserRoles.stream()
					.flatMap(r -> r.getPrivileges().stream().map(p -> p.getPrivilegeId())).collect(Collectors.toSet());

    		List<Role> newUserRoles = roleRepo.findByNameIn(roleNames);
			Set<String> newUserPrivilges = newUserRoles.stream()
					.flatMap(r -> r.getPrivileges().stream().map(p -> p.getPrivilegeId())).collect(Collectors.toSet());

    		if(!currentUserPrivileges.containsAll(newUserPrivilges)) {
    			throw new SyncariValidationException("Current user does not have permissions to assign/remove this role");
    		}
        },()-> {throw new SyncariValidationException("User does not have permissions");});
	}

	public boolean doesUserHavePermission(User user, String instanceId, String permission) {
        if (user.isSuperAdmin()) return true;
        Set<String> roles = getUserRoles(user.getId()).getOrDefault(instanceId, new HashSet<>());
        return roles.stream().anyMatch(role -> authzService.getPermissions(role, user.isSuperAdmin()).contains(permission));
    }

    public List<User> getAdmins() {
        List<User> admins = new ArrayList<>();
        roleRepo.findByName(RoleConstants.ORG_ADMIN).ifPresent(adminRole -> {
            admins.addAll(getUsersByRole(adminRole));
        });
        log.info("Org Admins for Instance {}:{}", SyncariContext.getSyncariId(), admins.stream().map(u -> u.getEmail()).collect(Collectors.toList()));
        return admins;
    }

    public List<User> getAllUsersFromInstance() {
        Set<String> userIds = new HashSet<>();
        List<User> allUsersFromInstance = new ArrayList<>();
        userRoleRepo.findAll().stream().forEach(userRole -> userIds.add(userRole.getUserId()));
        userIds.forEach(u -> {
            Optional<User> userFromDb = userRepo.findById(u);
            userFromDb.ifPresent(usr -> allUsersFromInstance.add(usr));
        });
        return allUsersFromInstance;
    }

    public List<User> getSuperAdmins() {
        return userRepo.findAllSuperAdmins();
    }

    public List<String> getInternalAdminEmailList() {
        List<String> toList = new ArrayList(appConfig.getErrorEmail());
        toList.addAll(appConfig.getErrorSupportEmail());
        toList.addAll(getSuperAdmins().stream().map(a -> a.getEmail()).collect(Collectors.toList()));
        log.debug("Internal Admin List for Instance {}:{}", SyncariContext.getSyncariId(), toList.stream().distinct().collect(Collectors.toList()));
        return toList.stream().distinct().collect(Collectors.toList());
    }

    public List<String> getAdminEmailList() {
        List<String> toList = new ArrayList<>();
        toList.addAll(getAdmins().stream()
                .filter(u -> (!u.isApiUser() && !u.isSystemUser() && !u.isGhostUser() && u.isActive()))
                .map(a -> a.getEmail()).collect(Collectors.toList()));
        log.debug("Admin List for Instance {}:{}", SyncariContext.getSyncariId(), toList.stream().distinct().collect(Collectors.toList()));
        return toList.stream().distinct().collect(Collectors.toList());
    }

    public User getSystemUser(){
        User systemUser = userRepo.findByEmail(User.SYSTEM_USER_PREFIX)
                .orElseThrow(() -> new RuntimeException("System user not found"));
        return systemUser;
    }

    public List<User> getUsersByRole(Role role) {
        List<UserRole> userRoles = userRoleRepo.findByRoleIdsIn(Set.of(role.getId()));
        List<String> userIds = userRoles.stream().map(u -> u.getUserId()).collect(Collectors.toList());
        return IteratorUtils.toList(userRepo.findAllById(userIds).iterator());
    }

    public void deleteUser(String userId) {
        Optional<User> existingUser = userRepo.findById(userId);
        User syncariContextUser = SyncariContext.getUser();
        existingUser.ifPresent(user -> {
            validateCondition(!canChangeUser(user), String.format("Unable to delete user with id %s", userId));
            log.info("User to be deleted username is {}", user.getEmail());
            // validate that only superadmins can delete another superadmin
            validateCondition(user.isSuperAdmin() && !SyncariContext.getUser().isSuperAdmin(), i18n("superadmin_deletion_error"));
            userRepo.delete(user);
            insightsSharingService.deleteSharedItemsByRecipientsId(user.getId());
            String providerUserId = user.getInsightsProviderUserId();
            if (StringUtils.isNotEmpty(syncariContextUser.getInsightsProviderUserName()) && StringUtils.isNotEmpty(providerUserId)){
                insightsProviderIntegrator.deleteUserById(providerUserId, Optional.of(TSService.TS_ADMIN_USER));
            }
        });
    }

    public void activateUser(String userId) {
        User existingUser = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
        validateCondition(!canChangeUser(existingUser), String.format("Unable to activate user with id %s", userId));
        existingUser.setStatus(Status.ACTIVE);
        userRepo.save(existingUser);
    }

    public void deactivateUser(String userId) {
        User existingUser = findUserById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, "id", userId));
        validateCondition(!canChangeUser(existingUser), String.format("Unable to deactivate user with id %s", userId));
        existingUser.setStatus(Status.INACTIVE);
        userRepo.save(existingUser);
    }

    public InputStream getProfilePhoto(User user){
        var existingUser = userRepo.findById(user.getId()).get();
        if(existingUser.getPhotoLocation() == null) {
            return gcsFileManager.readFile(DEFAULT_PROFILE_PICTURE);
        }
        return gcsFileManager.readFile(existingUser.getPhotoLocation());
    }

    public User updateUser(User user, InputStream photoStream, String fileName) {
        User existingUser = userRepo.findById(user.getId()).get();
        if (photoStream != null) {
            existingUser.setPhotoLocation(existingUser.getId() + "_" + fileName);
            gcsFileManager.uploadFile(photoStream, existingUser.getPhotoLocation());
        }
        if (!StringUtils.isBlank(user.getFirstName())) {
            existingUser.setFirstName(user.getFirstName());
        }
        if (!StringUtils.isBlank(user.getLastName())) {
            existingUser.setLastName(user.getLastName());
        }
        if((!existingUser.isGhostUser() && user.isGhostUser()) || (existingUser.isGhostUser() && !user.isGhostUser())) {
        	if(!SyncariContext.getUser().isSuperAdmin()) {
        		throw new SyncariValidationException("Non super admins cannot assign ghost to user");
        	} else {
                existingUser.setGhostUser(user.isGhostUser());
                log.info("Ghostuser flag changed to {} for user {} by user {}", user.isGhostUser(), user.getId(), SyncariContext.getUser().getId());
        	}
        }
        existingUser.setTimeZone(user.getTimeZone());
        log.info(format("Successfully updated user profile for %s", existingUser.getId()));
        return userRepo.save(existingUser);
    }

    public User removeUserLoginDetails(User user, UserLoginDetails userLoginDetails) {
        Optional<User> existingUser = userRepo.findById(user.getId());
        return existingUser.map(userToUpdate -> {
            userToUpdate.removeLoginDetails(userLoginDetails);
            return userRepo.save(userToUpdate);
        }).orElse(null);
    }

    public User updateUserLoginDetails(User user, UserLoginDetails userLoginDetail){
        return Optional.ofNullable(user).map(presentUser -> {
            Optional<User> existingUser = userRepo.findById(presentUser.getId());
            return existingUser.map( userToUpdate -> Optional.ofNullable(userLoginDetail).map(loginDetail -> {
                Optional<UserLoginDetails> loginDetailsFromDb = userToUpdate.findLoginDetails(loginDetail.getTokenId());
                loginDetailsFromDb.ifPresentOrElse(detail -> {
                    detail.setLastAccessed(loginDetail.getLastAccessed());
                }, () ->{
                    userToUpdate.addUserlogindetail(userLoginDetail);
                });
                log.debug(format("Successfully updated user profile for %s", userToUpdate.getId()));
                return userRepo.save(userToUpdate);
            }).orElse(null)).orElse(null);
        }).orElse(null);

    }

    public User updateUserApiRefreshToken(User user, String refreshToken) {
        User existingUser = userRepo.findById(user.getId()).get();
        existingUser.setRefreshToken(encryptionService.encrypt(refreshToken));
        log.info(format("Successfully updated user refreshToken %s", existingUser.getId()));
        return userRepo.save(existingUser);
    }
    
    public User updateUserOAuthDetails(User user, UserOAuthDetails oauthDetails) {
        User existingUser = userRepo.findById(user.getId()).get();
        // Encrypt sensitive information before storing
        if (oauthDetails.getRefreshToken() != null) {
            oauthDetails.setRefreshToken(encryptionService.encrypt(oauthDetails.getRefreshToken()));
        }
        final Instant now = Instant.now();
        oauthDetails.setUpdatedAt(now);
        oauthDetails.setLastUsed(now);
        if (oauthDetails.getCreatedAt() == null) {
            oauthDetails.setCreatedAt(now);
        }
        existingUser.setOauthDetails(oauthDetails);
        log.info(format("Successfully updated user OAuth details for user %s", existingUser.getId()));
        return userRepo.save(existingUser);
    }

    public Optional<Pair<User, UserOAuthDetails>> getUserOauthDetailByClientIdAndRefreshToken(String clientId, String refreshToken) {
        List<User> users = userRepo.findByOAuthClientId(clientId);
        return users.stream().flatMap(user ->
                user.getOauthServices()
                        .stream()
                        .filter(a -> encryptionService.decrypt(a.getRefreshToken()).equals(refreshToken))
                        .map(a -> Pair.of(user, a))
        ).findFirst();
    }

    public UserPreference updateDashboardPreference(String userId, String key, DashboardPreference value) {
        validateUser(userId);
        if (StringUtils.isBlank(key))
            new RuntimeException("Key is required");
        UserPreference preference = getPreference(userId);
        preference.setDashboard(value);
        return userPreferenceRepo.save(preference);
    }

    public UserPreference updateSchemaStudioEntityColumnsPreference(String userId, LinkedHashSet<Map<String, Object>> columns) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        SchemaStudioPreference studioPref = Optional.ofNullable(preference.getSchemaStudio()).orElse(new SchemaStudioPreference());

        studioPref.setAllEntityColumns(columns);

        preference.setSchemaStudio(studioPref);
        return userPreferenceRepo.save(preference);
    }

    public UserPreference updateSchemaStudioFieldColumnsPreference(String userId, LinkedHashSet<Map<String, Object>> columns) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        SchemaStudioPreference studioPref = Optional.ofNullable(preference.getSchemaStudio()).orElse(new SchemaStudioPreference());

        studioPref.setAllFieldColumns(columns);

        preference.setSchemaStudio(studioPref);
        return userPreferenceRepo.save(preference);
    }

    public UserPreference updateDataStudioColumnPreference(String userId, String entityId, LinkedHashSet<Map<String, Object>> columns) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        DataStudioPreference studioPref = Optional.ofNullable(preference.getDataStudio()).orElse(new DataStudioPreference());
        studioPref.getAllColumns().put(entityId, columns);
        preference.setDataStudio(studioPref);
        return userPreferenceRepo.save(preference);
    }

    public UserPreference updateSyncStudioFieldsFiltersPreference(String userId, String entityId, LinkedHashSet<String> filterSelections) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        SyncStudioPreference syncStudioPref = Optional.ofNullable(preference.getSyncStudio()).orElse(new SyncStudioPreference());

        syncStudioPref.getFilterSelections().put(entityId, filterSelections);
        preference.setSyncStudio(syncStudioPref);

        return userPreferenceRepo.save(preference);
    }

    public UserPreference updateSyncStudioHiddenFieldsPreference(String userId, String entityId, LinkedHashSet<String> filterSelections) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        SyncStudioPreference syncStudioPref = Optional.ofNullable(preference.getSyncStudio()).orElse(new SyncStudioPreference());

        syncStudioPref.getHiddenFields().put(entityId, filterSelections);
        preference.setSyncStudio(syncStudioPref);

        return userPreferenceRepo.save(preference);
    }

    public UserPreference updateSyncStudioPipelineViewportsPreference(String userId, String pipelineId, ArrayList<Number> pipelineViewports) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        SyncStudioPreference syncStudioPref = Optional.ofNullable(preference.getSyncStudio()).orElse(new SyncStudioPreference());

        syncStudioPref.getPipelineViewports().put(pipelineId, pipelineViewports);
        preference.setSyncStudio(syncStudioPref);

        return userPreferenceRepo.save(preference);
    }

    public UserPreference updateFilterBookmark(String userId, String filterId, boolean bookmark) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        DataStudioPreference studioPref = Optional.ofNullable(preference.getDataStudio()).orElse(new DataStudioPreference());
        if(bookmark) {
            studioPref.getFilterIds().add(filterId);
        } else {
            studioPref.getFilterIds().remove(filterId);
        }
        preference.setDataStudio(studioPref);
        return userPreferenceRepo.save(preference);
    }

    public Set<String> getBookMarkedFilters(String userId) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        DataStudioPreference studioPref = Optional.ofNullable(preference.getDataStudio()).orElse(new DataStudioPreference());
        return studioPref.getFilterIds();
    }

    public Set<String> getDataStudioColumns(String userId, String entityId) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        DataStudioPreference studioPref = Optional.ofNullable(preference.getDataStudio()).orElse(new DataStudioPreference());
        var selectedCols =
            Optional.ofNullable(studioPref.getAllColumns().get(entityId)).orElse(Set.of()).stream()
                .filter(c -> BooleanUtils.isTrue((Boolean) c.get("isSelected")))
                .map(c -> (String) c.get("columnName")).collect(Collectors.toList());
        return new LinkedHashSet<String>(selectedCols);
    }

    public UserPreference updateZoomPreference(String userId, String key, ZoomPreference value) {
        User existingUser = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException(format("User with id %s not found", userId)));
        if (StringUtils.isBlank(key))
            new RuntimeException("Key is required");
        UserPreference preference = getPreference(userId);
        preference.setZoom(value);
        preference = userPreferenceRepo.save(preference);
        log.info(format("Successfully updated user preference for %s", existingUser.getId()));
        return preference;
    }


    public UserPreference updateFragmentSharePreference(String userId, FragmentSharePreference value) {
        User existingUser = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException(format("User with id %s not found", userId)));
        UserPreference preference = getPreference(userId);
        preference.setFragmentShare(value);
        preference = userPreferenceRepo.save(preference);
        log.info(format("Successfully updated user preference for %s", existingUser.getId()));
        return preference;
    }

    public UserPreference getPreference(String userId) {
        return userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
    }

    public UserPreference updateEntityGraphPreference(String userId, GraphPreference pref) {
        validateUser(userId);
        if (!SyncariContext.getInstance().getSyncariId().equalsIgnoreCase(pref.getInstanceId())) {
            throw new RuntimeException(i18n("user_preference_instance_mismatch"));
        }
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        preference.setEntityGraph(pref);
        preference = userPreferenceRepo.save(preference);
        return preference;
    }

    public UserPreference updateConnectorGraphPreference(String userId, GraphPreference pref) {
        validateUser(userId);
        if (!SyncariContext.getInstance().getSyncariId().equalsIgnoreCase(pref.getInstanceId())) {
            throw new RuntimeException(i18n("user_preference_instance_mismatch"));
        }
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        preference.setConnectorGraph(pref);
        preference = userPreferenceRepo.save(preference);
        return preference;
    }

    public KeyValue updateCustomPreference(String userId, KeyValue customPreference) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        KeyValue userCustomPreference = Optional.ofNullable(preference.getCustomPreference()).orElse(new KeyValue());

        // We add/replace custom preference
        userCustomPreference.putAll(customPreference);
        
        // Remove the custom preference if value is null
        for (String key : customPreference.keySet()) {
            if (null == customPreference.get(key)) {
                userCustomPreference.remove(key);
            }
        }

        preference.setCustomPreference(userCustomPreference);
        userPreferenceRepo.save(preference);
        return userCustomPreference;
    }

    public KeyValue getCustomPreference(String userId) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        return Optional.ofNullable(preference.getCustomPreference()).orElse(new KeyValue());
    }

    public User getUser(String email) {
        return getUserByEmail(email)
                .orElseThrow(() -> new RuntimeException(format("User with email %s not found", email)));
    }

    public Optional<User> getUserByEmail(String email) {
        if(StringUtils.isBlank(email)) return Optional.empty();
        return userRepo.findByEmail(email.toLowerCase());
    }

    public Optional<User> findActiveUserByEmail(String email) {
        if(StringUtils.isBlank(email)) return Optional.empty();
        return userRepo.findByActiveByEmail(email.toLowerCase());
    }

    public User getUserById(String id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException(format("User with id %s not found", id)));
    }

    public User getUserByClientId(String clientId) {
        return userRepo.findByClientId(clientId)
                .orElseThrow(() -> new NotFoundException(format("User with clientId %s not found", clientId)));
    }

    public Optional<User> findUserById(String id) {
        return userRepo.findById(id);
    }

    public Map<String, User> getUsersById(Set<String> id) {
        Map<String, User> result = new HashMap<>();
        Iterator<User> iterator = userRepo.findAllById(id).iterator();
        while (iterator.hasNext()) {
            User user = (User) iterator.next();
            result.put(user.getId(), user);
        }
        return result;
    }

    public Map<String, Set<String>> getUserRoles(String userId, boolean allowEmptyRoles) {
    	List<Instance> instances = SyncariContext.getOrganziation().getInstances();
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
        Map<String, Set<String>> instanceRoleMapping = new HashMap<>();
        try {
            SyncariContext.push();
            instances.forEach(instance -> {
                SyncariContext.setInstance(instance);
                Optional<UserRole> userRoleOpt = userRoleRepo.findByUserId(userId);
                Set<String> roleNames = new HashSet<>();
                userRoleOpt.ifPresent(userRole -> {
                    Set<Role> roles = roleRepo.findByIdIn(userRole.getRoleIds());
                    roleNames.addAll(roles.stream().map(r -> r.getName()).collect(Collectors.toSet()));
                });
                if(!roleNames.isEmpty() || allowEmptyRoles) {
                    instanceRoleMapping.put(instance.getSyncariId(), roleNames);
                }
            });
        } finally {
            SyncariContext.restore();
        }

        return instanceRoleMapping;
    }

    public Map<String, Set<String>> getUserRolesForOrg(String userId, boolean allowEmptyRoles, Organization org) {
        List<Instance> instances = org.getInstances();
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
        Map<String, Set<String>> instanceRoleMapping = new HashMap<>();
        try {
            SyncariContext.push();
            instances.forEach(instance -> {
                SyncariContext.setInstance(instance);
                Optional<UserRole> userRoleOpt = userRoleRepo.findByUserId(userId);
                Set<String> roleNames = new HashSet<>();
                userRoleOpt.ifPresent(userRole -> {
                    Set<Role> roles = roleRepo.findByIdIn(userRole.getRoleIds());
                    roleNames.addAll(roles.stream().map(r -> r.getName()).collect(Collectors.toSet()));
                });
                if(!roleNames.isEmpty() || allowEmptyRoles) {
                    instanceRoleMapping.put(instance.getSyncariId(), roleNames);
                }
            });
        } finally {
            SyncariContext.restore();
        }

        return instanceRoleMapping;
    }

    public Set<String> getUserRolesForCurrentInstance(String userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
        Optional<UserRole> userRoleOpt = userRoleRepo.findByUserId(userId);
        Set<String> roleNames = new HashSet<>();
        userRoleOpt.ifPresent(userRole -> {
            Set<Role> roles = roleRepo.findByIdIn(userRole.getRoleIds());
            roleNames.addAll(roles.stream().map(r -> r.getName()).collect(Collectors.toSet()));
        });
        return roleNames;
    }

    public Map<String, Set<String>> getUserRoles(String userId) {
        return getUserRoles(userId, false);
    }

    public boolean isGhost(User user,Map<String, Set<String>> userRoles) {
    	boolean isGhost = false;
    	if (!user.isGhostUser()){
    	    return false;
        }
    	for (Entry<String, Set<String>> entry : userRoles.entrySet()) {
			if (entry.getValue().stream().anyMatch(roleName -> RoleConstants.GHOST.equalsIgnoreCase(roleName))) {
				isGhost = true;
				break;
			}
		}
    	return isGhost;
    }

    // need to get roles only for those who are ghostUser
    public boolean isGhost(User user) {
        boolean isGhost = false;
        if (!user.isGhostUser()){
            return false;
        }
        Map<String, Set<String>> userRoles = this.getUserRoles(user.getId());
        for (Entry<String, Set<String>> entry : userRoles.entrySet()) {
            if (entry.getValue().stream().anyMatch(roleName -> RoleConstants.GHOST.equalsIgnoreCase(roleName))) {
                isGhost = true;
                break;
            }
        }
        return isGhost;
    }

    public boolean isOrgAdmin(Map<String, Set<String>> userRoles) {
    	boolean isOrgAdmin = false;
    	for (Entry<String, Set<String>> entry : userRoles.entrySet()) {
    		if (entry.getValue().stream().anyMatch(roleName -> RoleConstants.ORG_ADMIN.equalsIgnoreCase(roleName))) {
    			isOrgAdmin = true;
    			break;
    		}
    	}
    	return isOrgAdmin;
    }

    public Set<String> getUserRoleForInstance(String userId, Instance instance) {
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
        Set<String> roleNames = new HashSet<>();
        try {
            SyncariContext.push();
            SyncariContext.setInstance(instance);
            Optional<UserRole> userRoleOpt = userRoleRepo.findByUserId(userId);
            if (user.isSuperAdmin()) {
                roleNames.add(RoleConstants.SUPER_ADMIN);
            }
            userRoleOpt.ifPresent(userRole -> {
                Set<Role> roles = roleRepo.findByIdIn(userRole.getRoleIds());
                roleNames.addAll(roles.stream().map(r -> r.getName()).collect(Collectors.toSet()));
            });
        } finally {
            SyncariContext.restore();
        }
        return roleNames;
    }

    public Set<String> getUserPermissionsForInstance(String userId, Instance instance) {
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
        Set<String> permissions = new HashSet<>();

        // Superadmins get all permissions
        if (user.isSuperAdmin()) {
            return new HashSet<>(Permissions.allPermissions());
        }

        try {
            SyncariContext.push();
            SyncariContext.setInstance(instance);
            Optional<UserRole> userRoleOpt = userRoleRepo.findByUserId(userId);
            userRoleOpt.ifPresent(userRole -> {
                Set<Role> roles = roleRepo.findByIdIn(userRole.getRoleIds());
                permissions.addAll(roles.stream().flatMap(r -> r.getPrivileges().stream().map(p -> p.getPrivilegeId())).collect(Collectors.toSet()));
            });
        } finally {
            SyncariContext.restore();
        }
        if(user.isGhostUser()) {
            permissions.addAll(Permissions.ghostPermissions());
        }
        return permissions;
    }

	public void assignRoleToAllInstance(Organization org, Instance toInstance, String roleName) {
		List<Instance> allInstances = org.getActiveInstances();
		List<String> orgAdmins = new ArrayList<>();
		for (Instance instance : allInstances) {
			try {
				SyncariContext.push();
				SyncariContext.setInstance(instance);
				Role role = roleRepo.findByName(roleName).get();
				List<UserRole> userRole = userRoleRepo.findByRoleIdsIn(Set.of(role.getId()));
				for (UserRole uRole : userRole) {
					for (String roleId : uRole.getRoleIds()) {
						if (role.getId().equalsIgnoreCase(roleId)) {
							orgAdmins.add(uRole.getUserId());
						}
					}
				}
			} finally {
				SyncariContext.restore();
			}
		}
		try {
			SyncariContext.push();
			SyncariContext.setInstance(toInstance);
			Role role = roleRepo.findByName(roleName).get();
			orgAdmins.forEach(u -> {
		    	userRepo.findById(u).ifPresent(user -> {
		    		assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(),user, Set.of(role.getName()));
		    	});
			});
		} finally {
			SyncariContext.restore();
		}
	}
	
	public boolean isOrgAdminInAnyInstance(User user) {
        return isOrgAdmin(getUserRoles(user.getId()));
	}

    public boolean isOrgAdminInAnyInstanceOfOrg(User user, Organization organization) {
        Map<String, Set<String>> userRoles = getUserRolesForOrg(user.getId(),false,organization);
        return isOrgAdmin(userRoles);
    }
    public List<User> list(String orgId) {
        Optional<Organization> org = organizationRepo.findById(orgId);
        Set<String> allSyncariIds = org.get().getAllSyncariIds();
        List<GhostAccessAudit> ghosts = ghostAccessAuditRepo.findActiveBySyncariIds(allSyncariIds);
        Set<String> ghostEmails = new HashSet<>();
//        ghosts.forEach(g -> {
//            if (StringUtils.isNotEmpty(g.getRequesterEmail())){
//                ghostEmails.add(g.getRequesterEmail());
//            }
//        });
        List<User> filteredAllUsers = userRepo.findUsersByAvailableInstances(allSyncariIds).stream().filter(u -> (!(u.getEmail().equalsIgnoreCase(SYNCARI_ADMIN_EMAIL))
                && (!ghostEmails.contains(u.getEmail())))).collect(Collectors.toList());
        return filteredAllUsers;
    }

    public List<User> listByInstance(String syncariId) {
        return userRepo.findUsersByAvailableInstances(Set.of(syncariId)).stream().filter(u -> !u.isSystemUser()).collect(Collectors.toList());
    }

    public User resetPassword(String userId, String currentPwd, String newPwd) {
        return resetPassword(userId, currentPwd, newPwd, true);
    }

    public User resetPassword(String userId, String currentPwd, String newPwd, boolean validateCurrentPassword) {
        if (StringUtils.isBlank(userId))
            throw new RuntimeException("User id cannot be blank");
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
        Organization userOrg = subService.getOrgBySyncariId(user.getCurrentInstanceId());
        validateCondition(userOrg.isSSOEnabled(), i18n("sso_user_password_reset_error"));
        user.validatePassword(newPwd);
        if (user.isActive()) {
            // validate user input current password
            if (validateCurrentPassword && (user.getPassword() == null || !(passwordEncoder.matches(currentPwd, user.getPassword())))) {
                throw new RuntimeException("Current password does not match the existing password.");
            }
            // validate new password is not same as current password
            if (passwordEncoder.matches(newPwd, user.getPassword())) {
                throw new RuntimeException("New password can not be same as current password.");
            }
        } else {
            user.setStatus(Status.ACTIVE);
        }
        user.removeAllLoginDetails();
        user.setPassword(encryptPassword(newPwd));
        user.setLastPasswordResetTimestamp(Instant.now());
        log.info("User password reset successfully");
        User saved = userRepo.save(user);
        Optional<Instance> userCurrentInstance = userOrg.getInstance(user.getCurrentInstanceId());
        boolean isTrial = userCurrentInstance.map(u-> u.isTrial()).orElse(false);
        sendPasswordEmail(user, "passwordLogoUrl", getPasswordLogoUrl(), isTrial);
        return saved;
    }

    public User setPassword(String invitationId, String password) {
        UserInvitation invite = invitationRepo.findByInvitationId(invitationId)
                .orElseThrow(() -> new RuntimeException("Unknown or expired invitation"));
        User user = userRepo.findById(invite.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found for invitation"));
        if (invite.hasExpired()) {
            throw new RuntimeException("Expired invitation");
        }
        Organization userOrg = subService.getOrgBySyncariId(user.getCurrentInstanceId());
        validateCondition(userOrg.isSSOEnabled(), i18n("sso_user_password_reset_error"));
        user.validatePassword(password);
        String subject;
        String body;
        user.setPassword(encryptPassword(password));
        user.setLastPasswordResetTimestamp(Instant.now());
        user.setFailedLoginAttempts(0);
        user.removeAllLoginDetails();
        String fullName = user.getEmail();
        if(!StringUtils.isBlank(user.getFirstName()) && StringUtils.isBlank(user.getLastName())) {
            fullName = user.getFirstName() + " " +user.getLastName();
        }
        Map<String, Object> context = new HashMap<>(Map.of("fullName", fullName, "accountname",
                userOrg.getName()));
        Optional<Instance> userCurrentInstance = userOrg.getInstance(user.getCurrentInstanceId());
        boolean isTrial = userCurrentInstance.map(u-> u.isTrial()).orElse(false);
        if (user.getStatus() == Status.ACTIVE) {
            sendPasswordEmail(user, "passwordLogoUrl", getPasswordLogoUrl(), isTrial);
        } else {
            user.setStatus(Status.ACTIVE);
            context.put("syncariLogoUrl", getSyncariLogoUrl());
            context.put("thumbsupUrl", String.format(GlobalConstants.THUMBS_UP_LOGO, appConfig.getCloudCdnHost()));
            context.put("loginUrl", String.format(GlobalConstants.LOGIN_URL, appConfig.getSpectrumServerHost()));
            body = renderer.render(WELCOME_TEMPLATE_PATH, context);
            subject = i18n("welcome_subject");
            if (!isTrial){
                emailService.sendHtml(List.of(user.getEmail()), subject, body);
            }else{
                plgEmailService.sendHtml(List.of(user.getEmail()), subject, body);
            }
        }
        userRepo.save(user);
        invitationRepo.deleteById(invite.getId());
        return user;
    }

    public void forgotPassword(String email) {
        User user = getUserByEmail(email)
                .orElseThrow(() -> new NotFoundException(String.format("User with email %s not found", email)));
        Organization userOrg = subService.getOrgBySyncariId(user.getCurrentInstanceId());
        validateCondition(userOrg.isSSOEnabled(), i18n("sso_user_password_reset_error"));
        UserInvitation invitation = createInvitation(user,false);
        Map<String, Object> context = new HashMap<>(Map.of("syncariLogoUrl", getSyncariLogoUrl(), "passwordLogoUrl", getPasswordLogoUrl()));
        context.put("setPasswordUrl", String.format(GlobalConstants.SET_PWD_URL + invitation.getInvitationId(), appConfig.getSpectrumServerHost()));
        String body = renderer.render(FORGOT_PWD_TEMPLATE_PATH, context);
        Optional<Instance> userCurrentInstance = userOrg.getInstance(user.getCurrentInstanceId());
        boolean isTrial = userCurrentInstance.map(u-> u.isTrial()).orElse(false);
        if (!isTrial){
            emailService.sendHtml(List.of(email), "Recover password for Syncari", body);
        }else{
            plgEmailService.sendHtml(List.of(email), "Recover password for Syncari", body);

        }
    }

    public User unlockUser(String email){
        User user = getUserByEmail(email)
                .orElseThrow(() -> new NotFoundException(String.format("User with email %s not found", email)));
        validateCondition(!user.isAccountLocked(), String.format("User with email %s not account not locked", email));
        user.setFailedLoginAttempts(0);
        User saved = userRepo.save(user);
        log.info(String.format("User with email %s unlocked", email));
        return saved;
    }

    public User saveUser(User user){
        return userRepo.save(user);
    }

    public UserInvitation createInvitation(User user, boolean isWelcomeInvitation) {
        Optional<UserInvitation> invite = userInvitationRepo.findByUserId(user.getId());
        if (user.getId() != null && invite.isPresent()) {
            userInvitationRepo.deleteById(invite.get().getId());
        }
        UserInvitation invitation = new UserInvitation(user.getId(), UUID.randomUUID().toString());
        if (StringUtils.isNotEmpty(user.getCurrentInstanceId()) & isWelcomeInvitation){
            Organization userOrg = subService.getOrgBySyncariId(user.getCurrentInstanceId());
            if (null != userOrg){
                Optional<Instance> instance = userOrg.getInstance(user.getCurrentInstanceId());
                instance.ifPresent(ins -> {
                    SyncariContext.runWithContext(userOrg, ins, user, () -> {
                        log.info("Changed Organization context to {} and instance to {}", userOrg, ins);
                        notificationService.send(new Notification(i18n("welcome_subject"), i18n("welcome_body"), NotificationType.INFO, user.getId()));
                    });
                });
            }
        }
        return userInvitationRepo.save(invitation);
    }

    public List<Instance> getUserInstances(User user){
        return user.getAvailableInstances().stream()
                .map(subService::getInstance).collect(Collectors.toList());
    }

    public List<Instance> getUserActiveInstances(User user){
    	List<Instance> instances = null;
        if (user.isSuperAdmin() && SyncariContext.isGhost()) {
        	instances = SyncariContext.getOrganziation().getInstances();
        } else {
        	instances =  user.getAvailableInstances().stream()
                    .filter(syncariId ->
                        subService.getOptionalOrgBySyncariId(syncariId).map(o -> o.isActive()).orElse(false)
                    ).map(subService::getInstance)
                    .collect(Collectors.toList());
        }
        return instances.stream().filter(i -> i.getStatus()!= Status.DELETED).collect(Collectors.toList());
    }

    public void validateUserInstances(User user, String syncariId){
        if(StringUtils.isBlank(syncariId)){
            throw new RuntimeException("Please provide a valid Instance ID");
        }
        // check if user has access to instance
        boolean hasInstance = user.getAvailableInstances().stream()
                .filter(i -> i.equals(syncariId))
                .findFirst().isPresent();
        if(!hasInstance){
            throw new RuntimeException(String.format("User doesn't have access to Instance with Syncari ID: %s", syncariId));
        }
    }

    public List<Instance> listInstancesWithPermission(User user, String permission) {
        if (user.isSuperAdmin()){
            return getUserActiveInstances(user);
        }else{
            return getUserActiveInstances(user).stream().filter(instance -> {
                return getUserRoleForInstance(user.getId(), instance).stream().
                        flatMap(role -> authzService.getPermissions(role, user.isSuperAdmin()).stream()).anyMatch(p -> p.contains(permission));
            }).collect(Collectors.toList());
        }
    }

    private String encryptPassword(String newPwd) {
        return passwordEncoder.encode(newPwd);
    }

    private void sendPasswordEmail(User user, String logoKey, String logoValue, boolean isTrial) {
    	String name = user.getEmail();
    	if(StringUtils.isNotBlank(user.getFirstName()) && StringUtils.isNotBlank(user.getLastName())) {
    		name = user.getFirstName() + " " + user.getLastName();
		} else if(StringUtils.isNotBlank(user.getFirstName())) {
    		name = user.getFirstName();
		} else if(StringUtils.isNotBlank(user.getLastName())) {
    		name = user.getLastName();
		}
    	String subject = "Syncari password reset for User " + name;
        Map<String, Object> context = Map.of("syncariLogoUrl", getSyncariLogoUrl(), logoKey, getPasswordLogoUrl(), "name", name);
        String body = renderer.render(RESET_PWD_TEMPLATE_PATH, context);
        if (!isTrial){
            emailService.sendHtml(List.of(user.getEmail()), subject, body);
        }else{
            plgEmailService.sendHtml(List.of(user.getEmail()), subject, body);
        }
    }
    private String getSyncariLogoUrl() {
        return String.format(GlobalConstants.SYNCARI_LOGO, appConfig.getCloudCdnHost());
    }
    private String getPasswordLogoUrl() {
        return String.format(GlobalConstants.PASSWORD_LOGO, appConfig.getCloudCdnHost());
    }

    public List<User>getAllActiveUsers(){
        return userRepo.findAllActive();
    }

    public List<User> getAllActiveSystemUsers(){
        return userRepo.findAllActiveSystemUser();
    }

    public List<User>getAllActiveStandardUsers(){
        return userRepo.findAllActiveStandard();
    }


    private void validateUser(String userId) {
        userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException(format("User with id %s not found", userId)));
    }

    public void removeUserFromCurrentOrg(String userId){
        User user = findUserById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, "id", userId));

        // remove user from all instances of the org
        Organization currentOrg = SyncariContext.getOrganziation();
        // remove users from all instances of the org where user has access
        currentOrg.getInstances().forEach(instance -> {
            if(user.getAvailableInstances().contains(instance.getSyncariId())) {
                SyncariContext.runWithContext(currentOrg, instance, user, () -> {
                    // disassociate instance from user
                    removeInstanceFromUser(instance.getSyncariId(), Optional.of(userId));

                    // remove all roles of user from the instance
                    Set<String> roles = getUserRoleForInstance(userId, instance);
                    removeRolesFromUser(user, roles);
                });
            }
        });

        // send email notification to the user
        Map<String, Object> context = new HashMap<>();
        context.put("syncariLogoUrl", getSyncariLogoUrl());
        context.put("fullName", user.getName());
        context.put("subName", currentOrg.getName());
        String body = renderer.render(REMOVE_USER_TEMPLATE_PATH, context);
        String subject = "Your access from Syncari has been removed";
        emailService.sendHtml(List.of(user.getEmail()), subject, body);
        log.info("User with email {} has been successfully removed from the Org {}",
                user.getEmail(), currentOrg.getName());

    }

    public void removeInstanceFromUser(String syncariId, Optional<String> userId) {
        // remove instance from all users
        List<Organization> allOrgs = subService.getAllOrg();
        Set<String> allActiveInstances = new HashSet<>();
        allOrgs.forEach(o -> allActiveInstances.addAll(o.getAllSyncariIds()));
        List<User> all = userId.isPresent() ? List.of(userRepo.findById(userId.get()).get()) : userRepo.findAll();
        all.stream().forEach(u -> u.removeAvailableInstance(syncariId));
        all.stream().forEach(u -> {
            if ((null != u.getCurrentInstanceId()) && (u.getCurrentInstanceId().equals(syncariId))){
            	log.info("Available instances {},  current instance {} and user is {}", u.getAvailableInstances(), u.getCurrentInstanceId(), u.getId());
            	u.removeAllLoginDetails();
                u.setCurrentInstanceId(null);
                // set a valid instance from available instances to current instance
                for(String instance: u.getAvailableInstances()){
                    Optional<Organization> org = subService.getOptionalOrgBySyncariId(instance);
                    if(allActiveInstances.contains(instance) && org.isPresent() && !org.get().isSSOEnabled()){
                        log.info("Setting instance {} as the currentInstanceId for user {}", instance, u.getEmail());
                        u.setCurrentInstanceId(instance);
                        break;
                    } else {
                        log.warn("Instance {} is not an active instance to set", instance);
                    }
                }
            }
        });
        userRepo.saveAll(all);
        // send only active user notifications and deactivate only those.
        /*List<User> allActiveUsers = userId.isPresent() ? List.of(userRepo.findById(userId.get()).get()) : userRepo.findAllActive();
        allActiveUsers.stream().forEach(u -> {
            if (null == u.getCurrentInstanceId() || CollectionUtils.isEmpty(u.getAvailableInstances())) {
                this.deactivateUser(u.getId());
                Map<String, Object> context = new HashMap<>(Map.of("syncariLogoUrl", getSyncariLogoUrl()));
                context.put("fullName", u.getName());
                String body = renderer.render(DEACTIVATE_USER_TEMPLATE_PATH, context);
                String subject = "User has been deactivated";
                emailService.sendHtml(List.of(u.getEmail()), subject, body);
                log.info("User with email {} has been deactivated because all associated instance {} was deleted",
                        u.getEmail(), syncariId);
            } });
        log.info("The instance {} is removed from all users", syncariId);*/
    }

    public boolean isExpiredToken(String username, String tokenId){
        Optional<com.syncari.core.model.User> userOptional = this.findActiveUserByEmail(username);
        return userOptional.map(user -> {
            Optional<UserLoginDetails> loginDetails = user.findLoginDetails(tokenId);
            return loginDetails.map(detail -> {
                if (detail.isValidLogin()) {
                    detail.setLastAccessed(Instant.now());
                    this.updateUserLoginDetails(user, detail);
                    return false;
                }else{
                    user.removeLoginDetails(detail);
                }
                return true;
            }).orElse(true);
        }).orElse(true);
    }

    public String generateRandomString(int stringSize) {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[stringSize];
        random.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String randomeString = encoder.encodeToString(bytes);
        return randomeString;
    }

    public void updateUserCurrentInstance(User user, String syncariId){
        Optional<Organization> orgInstanceTobeSwitchedTo = organizationRepo.findBySyncariId(syncariId);
        orgInstanceTobeSwitchedTo.ifPresent(org -> {
            String currentInstanceId = user.getCurrentInstanceId();
            Optional<Organization> existingCurrentInstanceOrg = organizationRepo.findBySyncariId(currentInstanceId);
            if ((!org.isSSOEnabled()) && ((!existingCurrentInstanceOrg.isPresent()) || (existingCurrentInstanceOrg.isPresent() && (!existingCurrentInstanceOrg.get().isSSOEnabled())))){
                user.setCurrentInstanceId(syncariId);
                userRepo.save(user);
            }
        });
    }

    @Deprecated
    public UserPreference updateErrorNotificationPreference(String userId, ErrorNotificationPreference pref) {
        validateUser(userId);
        UserPreference preference = userPreferenceRepo.findByUserId(userId).orElse(new UserPreference(userId));
        preference.setErrorNotification(pref);
        preference = userPreferenceRepo.save(preference);
        return preference;
    }

    public Set<String> getActiveGhostInstancesForUser(String userId, String status){
        return subService.ghostAccessRepo.findByRequesterIdAndStatus(userId, status).stream()
                .filter(a -> StringUtils.isNotEmpty(a.getSyncariId()))
                .map(a -> a.getSyncariId())
                .collect(Collectors.toSet());
    }
    
    public void clearFailedLoginAttempts(String username){
    	findActiveUserByEmail(username).ifPresent(user -> {
    		user.setFailedLoginAttempts(0);
    		userRepo.save(user);
    	});
    }
    
    public void incrementFailedLoginAttempts(String username){
    	findActiveUserByEmail(username).ifPresent(user -> {
    		user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
    		userRepo.save(user);
    	});
    }

    public Set<String> getUserOrgs(User user){
        Map<String, String> instanceToOrgMap = new HashMap<>();
        subService.getAllOrg().stream().forEach(o -> {
            o.getActiveInstances().forEach(i -> {
                instanceToOrgMap.put(i.getSyncariId(), o.getId());
            });
        });
        return user.getAvailableInstances().stream()
                .map(instance -> instanceToOrgMap.get(instance))
                .collect(Collectors.toSet());
    }

    private boolean canChangeUser(User user){
        // superadmin can delete users
        if(SyncariContext.getUser().isSuperAdmin()) return true;
        Set<String> roles = getUserRoleForInstance(user.getId(), SyncariContext.getInstance());
        canAssignRole(roles);
        // non superadmins can delete users only if they belong to single org
        var userOrgs = getUserOrgs(user);
        log.info("User is part of following {} organization: {}", userOrgs.size(), userOrgs);
        return userOrgs.size() == 1 && userOrgs.contains(SyncariContext.getOrganziation().getId());
    }
}
