package com.syncari.core.service;

import com.syncari.core.GlobalConstants;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.commands.DBMigrator;
import com.syncari.core.config.AppConfig;
import com.syncari.core.config.DatabaseConfig;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.RoleRepo;
import com.syncari.core.repositories.customer.ServiceCredentialRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.PlanRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.core.template.TemplateRenderer;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Service
public class ProvisioningService {
	private static final String NAME = "Name";
	private static final String PROVISION_TEMPLATE_PATH = "/templates/provision.admin.template";
	private static final String PROVISION_TRIAL_TEMPLATE_PATH = "/templates/provision.trial.admin.template";
	private static final String ACTIVATE_USR = "%s/invited-user/setpassword/%s";
	private static final String DEFAULT = "default";
	public static final String MAX_INSTANCES = "3";
	public static final String MAX_INSTANCES_PARTNER = "75";
	public static final String MAX_INSTANCES_TRIAL = "1";
	@Autowired
	private ApplicationContext appContext;
	@Autowired
	private OrganizationRepo orgRepo;
	@Autowired
	UserRepo userRepo;
	@Autowired
	EventStore eventStore;
	@Autowired
	PlanRepo planRepo;
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
	DBMigrator dbMigrator;
	@Autowired
	UserService userService;
	@Autowired
	AppConfig appConfig;
	@Autowired
	ServiceCredentialRepo credRepo;
	@Autowired
	SubscriptionService subService;
	@Autowired
	DatastoreService datastoreService;
	@Autowired
	CustomerMongoUtils mongoUtils;
	@Autowired
	EncryptionService encryptionService;
	@Autowired
	NotificationService notifyService;
	@Autowired
	PasswordEncoder encoder;
	@Autowired
	DatabaseConfig dbConfig;
	@Autowired
	FileDataService fileDataService;
	@Autowired
	FeatureService featureService;
	@Autowired
	AuthzService authzService;

	@Autowired
	InsightsProviderIntegrator insightsProviderIntegrator;

    public ProvisioningResponse provision(
        String instanceName,
        InstanceType instanceType,
        String displayName,
        String orgName,
        String adminEmail,
        String planName,
        String role,
        String adminFirstName,
        String adminLastName,
		OrganizationType organizationType,
		String maxNumberOfInstances
    ) {
		// TODO: set DB cluster details
		validate(instanceName, orgName, adminEmail);
		if (orgRepo.findByName(orgName).isPresent()) {
			throw new RuntimeException(i18n("organization_exists", orgName));
		}
		ProvisioningResponse provisioningResponse = new ProvisioningResponse();
		Organization org = new Organization(orgName);
		if (StringUtils.isBlank(planName)) {
			log.info("Plan is empty, using default");
			planName = DEFAULT;
		}
		org.setStatus(Status.PENDING);
		org.setOrgType(organizationType);
		org.setMaxNumberOfInstances(maxNumberOfInstances);
		org = orgRepo.save(org);
		Instance instance = provisionInstance(org, instanceName,  displayName, instanceType, planName);
		User contextUser = SyncariContext.getUser();

		// Create the user as admin and invite
		User admin = new User(adminEmail, User.generatePassword(), org.getInstances().get(0).getSyncariId());
		admin.setAdmin(true);
		admin.setFirstName(adminFirstName);
		admin.setLastName(adminLastName);
		List<String> errorMessage = new ArrayList<>();
		try{
			admin = inviteUser(admin, Map.of(instance.getSyncariId(),Set.of(role)), true, Optional.of(org));
			// the below code seems redundant since inviteUser already assigns role?
			userService.assignRolesToUser(org, instance, admin, Set.of(RoleConstants.ORG_ADMIN));

		}catch (Exception e){
			String message = i18n("org_provisioned_with_error",admin.getEmail());
			log.error(message);
			log.error("Exception occurred {}", ExceptionUtils.getStackTrace(e));
			errorMessage.add(message);
		}
		org.setStatus(Status.ACTIVE);
		org = orgRepo.save(org);
		log.info(format("Successfully provisioned %s for %s of type %s", instanceName, orgName,organizationType));
		provisioningResponse.setMessages(errorMessage);
		provisioningResponse.setOrganization(org);
		return provisioningResponse;
	}



	public Instance provisionInstance(Organization org, String instanceName, String displayName, InstanceType type, String planName, User user) {
	    Instance instance = provisionInstance(org, instanceName, displayName, type, planName);
		if (!user.isGhostUser()){
			userService.assignRolesToUser(org, instance, user, Set.of(RoleConstants.ORG_ADMIN));
		}
		return instance;
	}

	private Instance provisionInstance(Organization org, String instanceName, String displayName, InstanceType type, String planName) {
		if(!SyncariContext.getUser().isSuperAdmin() && !org.isPartner() && org.getActiveInstances().size() >= Integer.parseInt(MAX_INSTANCES)) {
			throw new SyncariValidationException(I18n.i18n("instance_cannot_create"));
		}
		if(StringUtils.isNotEmpty(org.getMaxNumberOfInstances()) && (org.getActiveInstances().size() >= Integer.valueOf(org.getMaxNumberOfInstances()))) {
			throw new SyncariValidationException(I18n.i18n("instance_cannot_create"));
		}
	    Instance instance = createInstance(instanceName, displayName, type, planName);
	    org.addInstance(instance);
	    orgRepo.save(org);
	    provisionDb(org, instance);
	    // assign access to all org admins for this instance
	    userService.assignRoleToAllInstance(org, instance, RoleConstants.ORG_ADMIN);

	    eventStore.provision(instance.getSyncariId());
	    provisionDataStore(org, instance);
	    enableFeatures(org, instance);
	    verifyProvisioned(instanceName, org.getName());
	    log.info(format("Successfully provisioned instance %s for %s", instanceName, org.getName()));
		if (type == InstanceType.trial) {
			log.info(format("Seeding trial user data %s for %s", instanceName, org.getName()));
			try {
				SyncariContext.push();
				SyncariContext.setOrganziation(org);
				SyncariContext.setInstance(instance);
				fileDataService.seedTrailUserData(instance.getSyncariId());
			} catch (Exception e) {
				log.error(format("Seeding trial user data %s for %s failed", instanceName, org.getName()), e);
				StringWriter stackTrace = new StringWriter();
				e.printStackTrace(new PrintWriter(stackTrace));
				emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
						format("Seeding trial user data %s for %s failed", instanceName, org.getName()),
						stackTrace.toString());
			} finally {
				SyncariContext.restore();
			}
		}
	    return instance;
	}

    private void provisionDataStore(Organization org, Instance instance) {
        SyncariContext.push();
        try {
            SyncariContext.setOrganziation(org);
            SyncariContext.setInstance(instance);
            // Create datastore read only user, store the jdbc endpoint/creds
            // create datastore connector, and instantiate with syncari schema
            datastoreService.provision(instance.getSyncariId());
        } finally {
            SyncariContext.restore();
        }
    }

    private void enableFeatures(Organization org, Instance instance) {
		SyncariContext.push();
		try {
			SyncariContext.setOrganziation(org);
			SyncariContext.setInstance(instance);
		} finally {
			SyncariContext.restore();
		}
	}

	public void deprovision(String orgId, boolean isDeprovInstances) {
	    Optional<Organization> organization = orgRepo.findById(orgId);
		if(organization.isEmpty()) {
			log.error("Org with id {} not found", orgId);
			return;
		}
		Organization org = organization.get();
	    log.info(format("Deleting org %s", org.getName()));
		org.setDeletedBy(SyncariContext.getUser().getId());
		org.setDeletedAt(new Date());
		if (isDeprovInstances){
			org.setStatus(Status.HARD_DELETING);
			org = orgRepo.save(org);
		}
		org.getInstances().forEach(i -> {
			if (isDeprovInstances){
				deprovisionInstance(i.getSyncariId(), true, !isDeprovInstances);
			}
			userService.removeInstanceFromUser(i.getSyncariId(), Optional.empty());
		});
		if (isDeprovInstances){
			orgRepo.delete(org);
			log.info(format("Successfully hard deleted %s", orgId));
		}else{
			org.setStatus(Status.DELETED);
			org = orgRepo.save(org);
			log.info(format("Successfully soft deleted %s", orgId));

		}

	}

	/**
	 * Use this method only if you are looking to hard delete the instance.
	 * Do not use this method from arcade or api's
	 * @param syncariId
	 * @param force
	 */

	public void deprovisionInstance(String syncariId, boolean force){
        this.deprovisionInstance(syncariId, force, false);
	}

	/**
	 * Use this method to deprovision an instance from a UI or rest API client.
	 * This method soft deletes the instance and let backend process hard deletes it.
	 * @param syncariId
	 * @param force
	 */
	public void deprovInstance(String syncariId, boolean force){
        this.deprovisionInstance(syncariId, force, true);
	}
	private void deprovisionInstance(String syncariId, boolean force, boolean isAsyncCall) {
	    // update org to remove instance
	    Organization org = subService.getOrgBySyncariId(syncariId);
	    if(!force && org.getActiveInstances().size() < 2) {
	        throw new SyncariValidationException(I18n.i18n("instance_cannot_delete"));
	    }
		Instance instance = org.getInstance(syncariId).get();
		List<User> admins = new ArrayList<>();
		SyncariContext.runWithContext(org, instance, SyncariContext.getUser(),()-> {
			// extract admins of the instance before dropping db to be used for sending notifications
			admins.addAll(userService.getAdmins());
		});


	    User contextUser = SyncariContext.getUser();
	    if ((!contextUser.isSuperAdmin()) && (!contextUser.isSystemUser())){
	    	
			if (authzService.listPrivileges().stream().map(prev -> prev.getPrivilegeId())
					.filter(p -> Permissions.DELETE_INSTANCE.equals(p)).findFirst().isEmpty()) {
				throw new SyncariValidationException(I18n.i18n("user_notadmin_cannot_delete"));
			}
			if (!contextUser.hasAccess(syncariId)){
				throw new SyncariValidationException(I18n.i18n("user_noaccess_cannot_delete"));
			}
		}


		// set instance status to DELETED before deleting db so that other process won't pickup
		instance.setStatus(Status.DELETED);
		instance.setDeletedBy(SyncariContext.getUser().getId());
		instance.setDeletedAt(new Date());
		org = orgRepo.save(org);
        if (isAsyncCall) {
            userService.removeInstanceFromUser(syncariId, Optional.empty());
            return;
        }
		// deprovision data store
		SyncariContext.runWithContext(org, instance, SyncariContext.getUser(), () -> {
			datastoreService.deprovision(syncariId);
			mongoUtils.dropDb(instance.getDbName());
		});

        eventStore.deprovision(syncariId);
		userService.removeInstanceFromUser(syncariId, Optional.empty());
		log.info(format("Deleting inst %s from org %s", syncariId, org.getName()));
        org.removeInstance(syncariId);
		org = orgRepo.save(org);
		// Clean up current context
		SyncariContext.getOrganziation().removeInstance(syncariId);
		SyncariContext.getUser().removeAvailableInstance(syncariId);
        String subject = String.format(I18n.i18n("instance_deprovisioned_subject"), syncariId);
        String message = String.format(I18n.i18n("instance_deprovisioned_body"), instance.getDisplayName(), instance.getSyncariId(), SyncariContext.getUser().getName());
        notifyService.sendToSuperAdmins(subject, message, NotificationType.INFO);
		List<String> adminEmails = admins.stream().map(user -> user.getEmail()).collect(Collectors.toList());
		emailService.sendText(adminEmails, subject, message);
		log.info("Deprovision email sent to emails and domain of emails are {} for instance name {}",StringUtils.join(",",
				adminEmails.stream().map(s -> s.split("@")[1]).collect(Collectors.toList())), instance.getName());
        log.info("Instance {} successfully deprovisioned", syncariId);
	}

    public void deprovisionEventStore(String syncariId) {
	    eventStore.deprovision(syncariId);
	}

	public void verifyProvisioned(String instanceName, String orgName) {
		// 1) Org and Instance saved with plan selected
		Organization org = orgRepo.findByName(orgName)
				.orElseThrow(() -> new NotFoundException(Organization.class, NAME, orgName));
		Instance instance = org.getInstanceByName(instanceName)
				.orElseThrow(() -> new NotFoundException(Instance.class, NAME, instanceName));
		Plan plan = planRepo.findById(instance.getPlanId())
				.orElseThrow(() -> new NotFoundException(Plan.class, "Id", instance.getPlanId()));

		// 2) Features enabled
		Collections.sort(plan.getFeatureIds());
		Collections.sort(instance.getFeatureIds());
		assert plan.getFeatureIds().equals(instance.getFeatureIds()) : "Feature not set from plan";

		// 3) Db created with index and seed

		// 4) Admin user created
		SyncariContext.runWithContext(org, instance, SyncariContext.getUser(),()-> {
			Role admin = roleRepo.findByName(RoleConstants.ORG_ADMIN)
					.orElseThrow(() -> new NotFoundException(Role.class, "Name", RoleConstants.ORG_ADMIN));
					List<User> users = userService.getUsersByRole(admin);
					assert users != null && !users.isEmpty() : "No admin user found";
				});

        // 5) Bigquery table exists
		eventStore.verifyProvisioned(instance.getSyncariId());

		// 6) db cluster details saved
		// TODO
	}

	public List<Organization> listOrg() {
		List<Organization> orgs = orgRepo.findAll().stream().collect(Collectors.toList());
		orgs.forEach(o -> {
			o.setInstances(o.getInstances().stream().collect(Collectors.toList()));
		});
		return orgs;
	}

	public List<Instance> listInstances(String orgId) {
		List<Instance> instances = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException(Organization.class, "Id", orgId))
				.getInstances();
		List<Instance> notDeletedInstances = instances.stream().filter(i -> i.getStatus()!= Status.DELETED).collect(Collectors.toList());
		return canViewAllInstances() ? notDeletedInstances
				: notDeletedInstances.stream()
						.filter(i -> SyncariContext.getUser().getAvailableInstances().contains(i.getSyncariId()))
						.collect(Collectors.toList());
	}

	private boolean canViewAllInstances() {
		Map<String, Set<String>> userRoles = userService.getUserRoles(SyncariContext.getUser().getId());
		return (SyncariContext.getUser().isSuperAdmin()
				|| userService.isOrgAdmin(userRoles) || userService.isGhost(SyncariContext.getUser(),userRoles));
	}

	public User inviteUser(User user, Map<String, Set<String>> instanceRoleMapping,boolean isWelcomeEmail, Optional<Organization> givenOrg) {
		Optional<User> existingUser = userService.getUserByEmail(user.getEmail());
		User currentUser = existingUser.orElseGet(()->userService.addUser(user));
		User saved = null;
		Organization orgTobeUsedForInviteUser = givenOrg.isPresent() ? givenOrg.get() : SyncariContext.getOrganziation();
		Set<String> currentUserInstances = SyncariContext.getUser().getAvailableInstances();
        SyncariContext.push();
        try {
            Set<String> requestedInstanceIds = instanceRoleMapping.keySet();
			Set<String> requestedRoleNames = instanceRoleMapping.values().stream().flatMap(Collection::stream)
					.collect(Collectors.toSet());
			boolean isRoleOrgAdmin = false;
            if(requestedRoleNames.contains(RoleConstants.ORG_ADMIN)) {
            	// assign access to all instances
            	requestedInstanceIds = orgTobeUsedForInviteUser.getAllSyncariIds();
				isRoleOrgAdmin = true;
            }
            for(String instanceId: requestedInstanceIds) {
            	// Loggedin user can invite new users only to the instances they have access to
				if (currentUserInstances.contains(instanceId) || SyncariContext.getUser().isSuperAdmin()) {
					Instance instance = subService.getInstance(instanceId);
                    SyncariContext.setInstance(instance);
                    Set<String> roleNames = instanceRoleMapping.get(instanceId);
                    if (isRoleOrgAdmin){
						roleNames = Set.of(RoleConstants.ORG_ADMIN);
					}
                    userService.assignRolesToUser(SyncariContext.getOrganziation(), instance,currentUser, roleNames);
                } else {
					log.warn("Logged in user {} does not have access to instance {}", SyncariContext.getUser().getId(),
							instanceId);
                }
            }
			if (currentUser.getCurrentInstanceId() == null) {
				currentUser.getAvailableInstances().stream().findFirst().ifPresent(instance -> {
					currentUser.setCurrentInstanceId(instance);
				});
			}
            String rawSecret = currentUser.getClientSecret();
            if(existingUser.isEmpty() && currentUser.isApiUser()) {
            	currentUser.setClientSecret(encoder.encode(rawSecret));
            }
			saved = userRepo.save(currentUser);
            saved.setClientSecret(rawSecret);
            if(saved.getStatus() == Status.PENDING) {
				UserInvitation invitation = userService.createInvitation(saved, isWelcomeEmail);
				log.info(format("User for org %s created successfully", orgTobeUsedForInviteUser.getName()));
				try {
					sendInviteEmail(saved, orgTobeUsedForInviteUser, invitation.getInvitationId(), isWelcomeEmail);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
					throw new RuntimeException(I18n.i18n("invite_user_error"));
				}
			}
			if (!saved.isApiUser() && !saved.isSystemUser()){
				if (StringUtils.isEmpty(saved.getInsightsProviderUserId())){
					insightsProviderIntegrator.createUserByAdmin(saved);
				}
			}
        } finally {
            SyncariContext.restore();
        }

        return saved;
	}

	public void reinviteUser(String userId) {
		User existing = userRepo.findById(userId).orElseThrow(() -> new NotFoundException(User.class, "Id", userId));
		if (!existing.canInvite()) {
			throw new RuntimeException("User cannot be re-invited, the user is probably active or deleted");
		}

		Organization org = subService.getOrgBySyncariId(existing.getCurrentInstanceId());
		UserInvitation invitation = userService.createInvitation(existing, false);
		sendInviteEmail(existing, org, invitation.getInvitationId(), false);
	}

	public ServiceCredential addServiceCredential(ServiceCredential credential) {
	    if(!StringUtils.isBlank(credential.apiKey)) {
	        credential.setApiKey(encryptionService.encrypt(credential.apiKey));
	    }
	    return credRepo.save(credential);
	}

	public List<ServiceCredential> getCredentials() {
	    List<ServiceCredential> all = credRepo.findAll();
	    all.stream().forEach(c -> {
	        if(!StringUtils.isBlank(c.apiKey)) {
	            c.setApiKey(encryptionService.decrypt(c.getApiKey()));
	        }
	    });
	    return all;
	}

	@Deprecated
	public List<ServiceCredential> getRawCredentials() {
	    return credRepo.findAll();
	}

	public Optional<ServiceCredential> getCredentials(String serviceId) {
	    Optional<ServiceCredential> byId = credRepo.findById(serviceId);
	    byId.ifPresent(s -> s.setApiKey(encryptionService.decrypt(s.apiKey)));
	    return byId;
	}

	public List<ServiceCredential> getCredentials(ServiceCredentialType type) {
	    List<ServiceCredential> all = getCredentials();
        return all.stream().filter(m -> m.getCredentialType() == type).collect(Collectors.toList());
	}

	public String getMaxInstances(String maxInstances, OrganizationType organizationType){
		return StringUtils.isNotEmpty(maxInstances) && (!maxInstances.equalsIgnoreCase("null")) ? maxInstances :
				(organizationType.equals(OrganizationType.standard) ? ProvisioningService.MAX_INSTANCES
						: organizationType.equals(OrganizationType.standard) ? ProvisioningService.MAX_INSTANCES_PARTNER : ProvisioningService.MAX_INSTANCES_TRIAL);

	}

	public void upgradeTrialInstance(){
		// check if its trial instance
		Organization org = SyncariContext.getOrganziation();
		Instance instance = SyncariContext.getInstance();
		validateCondition(!instance.isTrial() || !org.isTrial(), "Instance is not a trial instance and cannot be upgraded");
		validateCondition(!instance.isActive(), "Instance is not ACTIVE and cannot be upgraded");

		// once validated for trial instance upgrade it to standard
		log.info("Upgrading Trial instance {} to regular instance", instance.getSyncariId());
		org.setOrgType(OrganizationType.standard);
		org.setMaxNumberOfInstances(ProvisioningService.MAX_INSTANCES);
		org.getInstance(instance.getSyncariId()).ifPresent(i -> {
			i.setType(InstanceType.production);
			// set the plan id and quota appropriately
			Plan plan = planRepo.findByName(DEFAULT).orElseThrow(() -> new NotFoundException(Plan.class, NAME, DEFAULT));
			instance.setPlanId(plan.getId());
			instance.setQuota(plan.getQuota());
			instance.setFeatureIds(plan.getFeatureIds());
		});
		orgRepo.save(org);
		log.info("Trial instance {} upgraded successfully", instance.getSyncariId());
	}

	private void provisionDb(Organization newOrg, Instance newInstance) {
		Organization stashedOrg = SyncariContext.getOrganziation();
		Instance stashedInstance = SyncariContext.getInstance();
		try {
			SyncariContext.setOrganziation(newOrg);
			SyncariContext.setInstance(newInstance);
			log.info(format("Switched org from %s to %s", stashedOrg.getName(), newOrg.getName()));
			log.info(format("Switched instance from %s to %s", stashedInstance.getName(), newInstance.getName()));
			log.info(format("Provisioning instance %s in cluster %s", newInstance.getName(),
					newInstance.getResource(ResourceType.DATABASE).get().getConfiguration().get("clusterId")));

			MigrationContext.setApplicationContext(appContext);
			dbMigrator.migrateCustomer(SyncariContext.getDatabase());
			log.info(format("Migrated changes for %s successfully", SyncariContext.getDatabase()));

		} finally {
			SyncariContext.setOrganziation(stashedOrg);
			SyncariContext.setInstance(stashedInstance);
			MigrationContext.clear();
			log.info(format("Switched back org to %s, instance to %s", SyncariContext.getOrganziation().getName(),
					SyncariContext.getInstance().getName()));
		}
	}

	protected Instance createInstance(String instanceName, String displayName, InstanceType type, String planName) {
		instanceName = StringUtils.isBlank(instanceName) ? DEFAULT : instanceName;
		Instance instance;
		do{
			instance = new Instance(instanceName, displayName);
		}while(!isUniqueInstance(instance));

		instance.setCreatedAt(Calendar.getInstance().getTime());
		instance.setCreatedBy(SyncariContext.getUser().getId());
		instance.setType(type);
		instance.setStatus(Status.ACTIVE);
		Plan plan = planRepo.findByName(planName).orElseThrow(() -> new NotFoundException(Plan.class, NAME, planName));
		instance.setPlanId(plan.getId());
		instance.setQuota(plan.getQuota());
		instance.setFeatureIds(plan.getFeatureIds());
		Resource database = new Resource(ResourceType.DATABASE);
		Map config = new HashMap<>();
		config.put("database", generateDbName(instanceName, instance.getSyncariId()));
		database.setConfiguration(config);
		instance.addResource(database);
		dbConfig.setDbConfig(instance);
		log.info(format("Instance %s created successfully", instanceName));
		return instance;
	}

	public Instance updateInstance(String syncariId, String displayName, InstanceType type) {
		Organization orgBySyncariId = orgRepo.findBySyncariId(syncariId).orElseThrow();
		Instance instance = orgBySyncariId.getInstance(syncariId).orElseThrow();
		instance.setDisplayName(displayName);
		if(instance.getType() == InstanceType.trial && type != InstanceType.trial) {
			upgradeTrialInstance();
		} else {
			instance.setType(type);
		}
		orgRepo.save(orgBySyncariId);
		return instance;
	}


	private String generateDbName(String instanceName, String syncariId) {
		return String.format("%s_%s_db", normalize(instanceName).toLowerCase(), syncariId);
	}

	private String normalize(String instanceName) {
		return instanceName.replace(" ", "_").replaceAll("[^A-Za-z0-9_]", "");
	}

	private void validate(String instanceName, String orgName, String adminEmail) {
		if (StringUtils.isEmpty(instanceName))
			throw new RuntimeException("Instance name is required");
		if (StringUtils.isEmpty(orgName))
			throw new RuntimeException("Organization name is required");
		if (StringUtils.isEmpty(adminEmail))
			throw new RuntimeException("Admin email is required");
	}

	private void sendInviteEmail(User user, Organization org, String inviteId, boolean isWelcomeEmail) {
		String fromUser = String.format("%s %s", SyncariContext.getUser().getFirstName(),
				SyncariContext.getUser().getLastName());
		String syncariLogoUrl = String.format(GlobalConstants.SYNCARI_LOGO, appConfig.getCloudCdnHost());
		String userLogoUrl = String.format("%s/user-add_2X.png", appConfig.getCloudCdnHost());
		Map<String, Object> context = Map.of("fromUser", fromUser, "uname", user.getEmail(), "accountname",
				org.getName(), "url", format(ACTIVATE_USR, appConfig.getSpectrumServerHost(), inviteId),
				"syncariLogoUrl", syncariLogoUrl, "userLogoUrl", userLogoUrl);
		String currentInstanceId = user.getCurrentInstanceId();
		Optional<Boolean> isTrialInstance = Optional.empty();
		if (StringUtils.isNotEmpty(currentInstanceId)){
			Optional<Organization> userCurrentOrg = orgRepo.findBySyncariId(currentInstanceId);
			Optional<Instance> instance = userCurrentOrg.flatMap(x -> x.getInstance(currentInstanceId));
			isTrialInstance = instance.map(x -> x.isTrial());
		}
		isTrialInstance.ifPresentOrElse(isTrial -> {
			String body = null;
			String subject = "Welcome to Syncari";
			if (isTrial && isWelcomeEmail){
				body = renderer.render(PROVISION_TRIAL_TEMPLATE_PATH, context);
				subject = "Your Syncari trial is ready!";
				plgEmailService.sendHtml(List.of(user.getEmail()),subject , body);
			}else{
				body = renderer.render(PROVISION_TEMPLATE_PATH, context);
				emailService.sendHtml(List.of(user.getEmail()), "Welcome to Syncari", body);
			}
		}, () -> {
			String body = renderer.render(PROVISION_TEMPLATE_PATH, context);
			emailService.sendHtml(List.of(user.getEmail()), "Welcome to Syncari", body);
		});
	}

	private boolean isUniqueInstance(Instance instance){
		return orgRepo.findBySyncariId(instance.getSyncariId()).isEmpty();
	}
}
