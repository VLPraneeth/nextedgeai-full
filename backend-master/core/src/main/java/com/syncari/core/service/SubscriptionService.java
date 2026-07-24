package com.syncari.core.service;

import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.QuotaType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.misc.StreamInfo;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.syncari.CustomOrganizationRepo;
import com.syncari.core.repositories.syncari.GhostAccessAuditRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.authz.AuthzService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component
public class SubscriptionService {
    @Autowired
    OrganizationRepo orgRepo;
    @Autowired
    GCSFileManager fileManager;
    @Autowired
    EncryptionService encryptionService;

    @Autowired
    EntityRepoService repoService;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    ConnectorService connectorService;

    @Autowired
    SyncStatusService syncStatusService;

    @Autowired
    ReferenceDataService referenceDataService;
    
    @Autowired
    GhostAccessAuditRepo ghostAccessRepo;
    
    @Autowired
    UserService userService;

    @Autowired
    DateUtil dateutil;
    @Autowired
    AuthzService authzService;

    @Autowired
    SyncariContextHandler synCtxHandler;

    @Autowired
    CustomOrganizationRepo customOrganizationRepo;

    public static final String SYNCARI_ADMIN_INSTANCE = "syncari_admin";
    public static Integer TRIAL_PERIOD = 28;

    public Organization getSyncariMasterOrg() {
        return getOrgBySyncariId(SYNCARI_ADMIN_INSTANCE);
    }

    public Organization getOrgBySyncariId(String syncariId) {
        return orgRepo.findBySyncariId(syncariId)
                .orElseThrow(() -> new OrgNotFound("Org with syncari id " + syncariId + " not found"));
    }

    public Optional<Organization> getOptionalOrgBySyncariId(String syncariId){
        return orgRepo.findBySyncariId(syncariId);
    }

    public List<Organization> findDeletedCustomers() {
        return orgRepo.findDeletedCustomers();
    }

    public List<Organization> findDeletedInstancesCustomers() {
        return orgRepo.findDeletedInstancesOrg();
    }


    public List<Organization> findAndModifyDeletedInstancesCustomersToDeletingStatus(int numberOfDays) {
        List<Organization> orgs = orgRepo.findDeletedInstancesOrg();
        Map<String, Organization> orgIdToOrgMap = new HashMap<>();
        orgs.forEach(o -> {
            List<Instance> instances = o.getInstances();
            instances.forEach(i -> {
                if ((null != i.getDeletedAt()) && (ChronoUnit.DAYS.between(i.getDeletedAt().toInstant(), new Date().toInstant()) > numberOfDays)) {
                    Optional<Organization> org = customOrganizationRepo.findAndModifyInstanceStatus(i.getSyncariId(), Status.DELETED, Status.HARD_DELETING);
                    org.ifPresent(or -> orgIdToOrgMap.put(or.getId(), or));
                }
            });
        });
        return orgIdToOrgMap.values().stream().collect(Collectors.toList());
    }

    public Long getTimeDiffInDays(Instance instance, InstanceState instanceState){
        Date instanceCreatedAt = instance.getCreatedAt();
        log.info("Checking trial subscription timeline for {} instance, its createdAt is {} and isExpired is {}", instance.getSyncariId(), instanceCreatedAt, instanceState.isTrialExpired());
        return ChronoUnit.DAYS.between(instanceCreatedAt.toInstant(), new Date().toInstant());
    }


    public List<Organization> findAndModifyTrialExpiredCustomersToDeletingStatus() {
        List<Organization> orgs = this.findAllCustomersByInstanceType(OrganizationType.trial);
        Map<String, Organization> orgIdToOrgMap = new HashMap<>();

        orgs.forEach(o -> {
            List<Instance> instances = o.getInstances();
            instances.forEach(i -> {
                if (i.isTrial() ){
                    InstanceState instanceState = this.getInstanceState(i.getSyncariId());
                    Long timeDifferenceInDays = this.getTimeDiffInDays(i,instanceState);
                    if ((instanceState.isTrialExpired()) && (timeDifferenceInDays >= SubscriptionService.TRIAL_PERIOD)){
                        Optional<Organization> org = customOrganizationRepo.findAndModifyInstanceStatus(i.getSyncariId(),Status.ACTIVE, Status.HARD_DELETING);
                        org.ifPresent(or -> orgIdToOrgMap.put(or.getId(), or));
                    }
                }
            });
        });
        return orgIdToOrgMap.values().stream().collect(Collectors.toList());
    }

    public List<Organization> findAllCustomersByInstanceType(OrganizationType type) {
        return orgRepo.findAllCustomersByInstanceType(type.name());
    }

    public List<Organization> getAllOrg(){
        List<Organization> orgs = orgRepo.findAllActiveCustomers();
        orgs.add(getSyncariMasterOrg());
        return orgs;
    }
    
    public Optional<Organization> getOrgById(String orgId) {
        return orgRepo.findById(orgId).filter(o -> o.getStatus() != Status.DELETED);
    }

    public Optional<Organization> getOrgByName(String orgName) {
        return orgRepo.findByName(orgName).filter(o -> o.getStatus() != Status.DELETED);
    }

    public List<Instance> getInstances() {
        List<Organization> allCustomers = orgRepo.findAllCustomers();
        List<Instance> allInstances = new ArrayList<>();
        allCustomers.stream().forEach(sub -> {
            allInstances.addAll(sub.getInstances());
        });
        return allInstances;
    }

    public Instance getInstance(String syncariId) {
        Organization customer = getOrgBySyncariId(syncariId);
        return customer.getInstance(syncariId)
                .orElseThrow(() -> new InstanceNotFound("Instance with syncari id " + syncariId + " not found"));
    }

    public List<Instance> getInstanceFromGivenOrgAndRole(Organization customer, String syncariId, String roleName) {
        if (roleName.equals(RoleConstants.ORG_ADMIN)){
            return customer.getInstances();
        }
        return List.of(customer.getInstance(syncariId)
                    .orElseThrow(() -> new InstanceNotFound("Instance with syncari id " + syncariId + " not found")));
    }
    
    public void grantAccess(GhostAccessAudit request, Organization organization) {
    	// The ghost access is provided for the duration requested. An async cron job will remove expired access
        // Get the target user who will receive the ghost access
        User targetUser = userService.getUserById(request.getRequesterId());
    	Role role = authzService.getRoleByName(request.getRoleName()).orElseThrow(() -> new InstanceNotFound("Role with name " + request.getRoleName() + " not found"));
    	Instance i = organization.getInstance(request.getSyncariId()).get();
    	// if ORG Admin then un assign other roles
        if (role.getName().equals(RoleConstants.ORG_ADMIN)){
            List<GhostAccessAudit> audit = ghostAccessRepo.findByRequesterIdAndSyncariIdAndStatus(request.getRequesterId(), request.getSyncariId(), request.getStatus().toString());
            if (CollectionUtils.isNotEmpty(audit)){
                audit.forEach(a -> {
                    a.setStatus(Status.COMPLETED);
                    a.setAuditTrail(new StringBuilder(a.getAuditTrail())
                            .append(format(i18n("ghost_user_revoked"), "API",
                                    Instant.now())).toString());
                    ghostAccessRepo.save(a);
                    userService.unAssignAllRolesFromUser(organization, i, targetUser);
                });
            }
        }else{
            List<GhostAccessAudit> audit = ghostAccessRepo.findByRequesterIdAndSyncariIdAndStatus(request.getRequesterId(), request.getSyncariId(), "ACTIVE");
            // there is already an existing Active entry, we should not process another entry.
            if (CollectionUtils.isNotEmpty(audit)){
                log.info("There is already active ghost entry exists for syncariId {} from user {}",request.getSyncariId(), request.getRequesterId());
                return;
            }
        }
        userService.assignRolesToUser(organization, i, targetUser, Set.of(role.getName()));
        request.setApprovedAt(Instant.now());
        ghostAccessRepo.save(request);
        log.info("Ghost access provided to {} successfully for instance {}", request.getRequesterId(), request.getSyncariId());

    }

    public void revokeAccess(String userId, String syncariId){
        List<GhostAccessAudit> ghostAccessAudit = ghostAccessRepo.findByRequesterIdAndSyncariIdAndStatus(userId, syncariId, Status.ACTIVE.name());
        if(CollectionUtils.isEmpty(ghostAccessAudit)){
            throw new InstanceNotFound("Ghost User with syncari Id "+syncariId+" and User Id "+ userId+" Not Found ");
        }
        List<String> errors = new ArrayList<>();
        ghostAccessAudit.forEach(gAA -> {
            Role role = authzService.getRoleByName(gAA.getRoleName()).get();
            Organization organization = getOrgBySyncariId(syncariId);
            // Get only the specific instance being revoked
            Instance instance = organization.getInstance(syncariId)
                    .orElseThrow(() -> new InstanceNotFound("Instance " + syncariId + " not found"));

            try{
                synCtxHandler.setContext(instance.getSyncariId());
                List<GhostAccessAudit> ghostAccessAuditToUpdate = ghostAccessRepo.findByRequesterIdAndSyncariIdAndStatus(userId, instance.getSyncariId(), Status.ACTIVE.name());
                Role roleinRightInstance = authzService.getRoleByName(gAA.getRoleName()).get();
                userService.removeInstanceFromUser(instance.getSyncariId(), Optional.of(userId));
                userService.removeRoleFromUser(userId,roleinRightInstance.getId());
                ghostAccessAuditToUpdate.forEach(gAATU -> {
                    gAATU.setStatus(Status.COMPLETED);
                    gAATU.setAuditTrail(new StringBuilder(gAATU.getAuditTrail())
                            .append(format(i18n("ghost_user_revoked"), "API",
                                    Instant.now())).toString());
                    ghostAccessRepo.save(gAATU);
                });
                log.info("Successfully removed ghost role from user {} for instance {}", instance.getSyncariId(), userId);
            }catch (Exception e){
                log.error("Failure happened for ghost role from user {} for instance {}, exception occurred is {}", instance.getSyncariId(), userId, ExceptionUtils.getStackTrace(e));
                errors.add("for syncariId: " + instance.getSyncariId() + " message is : "+ e.getMessage() );
            }
        });

        if (CollectionUtils.isNotEmpty(errors)){
            throw new RuntimeException(errors.toString());
        }
    }
    
    public void addResource(String syncariId, Resource resource) {
        Organization org = getOrgBySyncariId(syncariId);
        Instance instance = org.getInstance(syncariId)
                .orElseThrow(() -> new InstanceNotFound("Instance with syncari id " + syncariId + " not found"));
        instance.addResource(resource);
        orgRepo.save(org);
    }

    public InputStream getOrgLogo(Organization org) {
        var existingOrg = orgRepo.findById(org.getId()).get();
        if (existingOrg.getLogoLocation() == null) {
            return fileManager.readFile(GlobalConstants.BUSINESS_LOGO);
        }
        return fileManager.readFile(existingOrg.getLogoLocation());
    }

    public ProvisioningResponse updateOrg(Organization org, InputStream photoStream, String fileName) {
        ProvisioningResponse wrapper = new ProvisioningResponse();
        Organization existing = orgRepo.findById(org.getId()).get();
        if (photoStream != null) {
            existing.setLogoLocation(existing.getId() + "_" + fileName.replace(" ", "_"));
            fileManager.uploadFile(photoStream, existing.getLogoLocation());
        }
        existing.setName(org.getName());
        if (SyncariContext.getUser().isSuperAdmin()) {
            existing.setOrgType(org.getOrgType());
            existing.setMaxNumberOfInstances(org.getMaxNumberOfInstances());
        }
        log.info(format("Successfully updated org profile for %s", existing.getId()));
        wrapper.setOrganization(orgRepo.save(existing));
        return wrapper;
    }

    public SSOAuthConfig updateSSOForOrg(Organization org, SSOAuthConfig ssoConfig) {
        validateCondition(ssoConfig == null, i18n("sso_config_null_error"));
        ssoConfig.validate();
        ssoConfig.setX509Key(encryptionService.encrypt(ssoConfig.getX509Key()));
        org.setSsoConfig(ssoConfig);
        org = orgRepo.save(org);
        return org.getSsoConfig();
    }
    
    public void disableSSO(Organization org) {
    	org.setSsoConfig(null);
    	org = orgRepo.save(org);
    	log.info("Successfully disabled SSO for org {}", org.getId());
    }

    public void updateStatus(Organization org, Status status) {
        org.setStatus(status);
        org = orgRepo.save(org);
        log.info("Successfully update status for org {} to status {}", org.getId(), status);
    }

    public Map<String, OAuthConfig> updateOauthConfigForOrg(Organization org, Map<String, OAuthConfig> oauthConfigs) {
        validateCondition(oauthConfigs == null || oauthConfigs.size() == 0, i18n("oauth_config_null_error"));
        oauthConfigs.values().forEach(x -> {
            x.validate();
            x.setClientId(encryptionService.encrypt(x.getClientId()));
            x.setClientSecret(encryptionService.encrypt(x.getClientSecret()));
        });
        org.setOauthConfigs(oauthConfigs);
        org = orgRepo.save(org);
        return org.getOauthConfigs();
    }
    
    public Organization updateOrg(Organization org) {
    	return orgRepo.save(org);
    }

    public void disableOAuthConfigs(Organization org) {
    	org.setOauthConfigs(null);
    	org = orgRepo.save(org);
    	log.info("Successfully disabled Oauth configs for org {}", org.getId());
    }

    public boolean isActiveInstance(String syncariId){
        Optional<Organization> org = getOptionalOrgBySyncariId(syncariId);
        if(org.isEmpty()) return false;
        return org.get().isActive() && org.get().getActiveInstance(syncariId).isPresent();
    }

    public boolean extendTrialInstance(String instanceId, Optional<String> extendedTrialDate, Optional<Integer> extendRecordLimitAddition){
        Organization organization = this.getOrgBySyncariId(instanceId);
        assert null != organization : "Organization with syncariid does not exist";
        Instance instance = organization.getInstance(instanceId)
                .orElseThrow(() -> new InstanceNotFound("Instance with syncari id " + instanceId + " not found"));
        assert null != instance : "Instance with syncariid does not exist";
        List<Quota> quotaList = instance.getQuota();
        extendedTrialDate.ifPresent(exdate -> {
            Calendar newExpiryDate = Calendar.getInstance();
            Optional.ofNullable(SyncariContext.getUser().getTimeZone()).ifPresentOrElse(tz -> {
                        Instant tempNewExpiryDate = LocalDateTime.parse(exdate).atZone(ZoneId.of(tz)).toInstant();
                        newExpiryDate.setTimeInMillis(tempNewExpiryDate.toEpochMilli());
                        newExpiryDate.setTimeZone(TimeZone.getTimeZone(tz));
                    },
                    () -> {
                        Instant tempNewExpiryDate = LocalDateTime.parse(exdate).atZone(ZoneId.of("UTC")).toInstant();
                        newExpiryDate.setTimeInMillis(tempNewExpiryDate.toEpochMilli());
                        newExpiryDate.setTimeZone(TimeZone.getTimeZone("UTC"));
                    });
            Instant createdDate =  Instant.ofEpochMilli(instance.getCreatedAt().getTime()).atZone(ZoneId.of(newExpiryDate.getTimeZone().getID())).toInstant();

            // Number od days to extend with new limits
            Long numberOfDaysToExtend = TimeUnit.MILLISECONDS.toDays(newExpiryDate.getTimeInMillis() - createdDate.toEpochMilli());;
            List<Quota> trialDaysQuota = quotaList.stream().filter(q -> q.getType() == QuotaType.TRIAL_DAYS_LIMIT).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(trialDaysQuota)){
                assert(trialDaysQuota.size()==1);
                Integer currentLimit = Integer.valueOf(trialDaysQuota.get(0).getValue());
                Long newlimit =  numberOfDaysToExtend;
                trialDaysQuota.get(0).setValue(String.valueOf(newlimit));
            }else{
                Quota quota = new Quota(QuotaType.TRIAL_DAYS_LIMIT,String.valueOf(numberOfDaysToExtend) , null);
                quotaList.add(quota);
            }
        });
        extendRecordLimitAddition.ifPresent(addRecord -> {
            List<Quota> limitQuota = quotaList.stream().filter(q -> q.getType() == QuotaType.RECORDS_LIMIT).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(limitQuota)){
                assert(limitQuota.size()==1);
                Integer currentLimit = Integer.valueOf(limitQuota.get(0).getValue());
                Integer newlimit = currentLimit + addRecord;
                limitQuota.get(0).setValue(String.valueOf(newlimit));
            }else{
                Quota quota = new Quota(QuotaType.RECORDS_LIMIT,String.valueOf(addRecord) , null);
                quotaList.add(quota);
            }
        });
        instance.setUpdatedBy(SyncariContext.getUser().getId());
        instance.setUpdatedAt(Calendar.getInstance().getTime());
        instance.setQuota(quotaList);
        orgRepo.save(organization);
        return true;
    }

    public InstanceState getInstanceState(String syncariId) {
        Instance instance = this.getInstance(syncariId);
        InstanceState instanceState = new InstanceState();
        instanceState.setInstance(instance);

        List<Quota> quotas = instance.getQuota();
        quotas.forEach(q -> {
            if (q.getType() == QuotaType.TRIAL_DAYS_LIMIT) {
                isTrialDaysExpiredForTrial(q, instance.getCreatedAt(), instanceState);
            }
        });
        instanceState.setNumberofSynapses(connectorService.countConnectors());
        instanceState.setNumberofPipelines(syncStatusService.countAllDraftAndPublishedPipelines());

        return instanceState;
    }


    public boolean isTrialDaysExpiredForTrial(Quota quota, Date createdAt, InstanceState instanceStateToBeSet){
        boolean result = false;
        Integer trialDays = Integer.valueOf(quota.getValue());
        Calendar expiryDate = Calendar.getInstance();
        expiryDate.setTime(createdAt);
        expiryDate.add(Calendar.DAY_OF_MONTH,trialDays);

        Calendar calendar = Calendar.getInstance();
        Date currentDate = calendar.getTime();
        long diffDays = 0l;
        if (expiryDate.getTimeInMillis() > currentDate.getTime() ){
            long diff = expiryDate.getTimeInMillis() - currentDate.getTime() ;
            diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
            instanceStateToBeSet.setTrialDaysLeft(diffDays);
        }else{
            long diff = currentDate.getTime() - expiryDate.getTimeInMillis();
            diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
            result = true;
            instanceStateToBeSet.setTrialExpired(result);
        }
        // Convert date to user timezone
        Optional.ofNullable(SyncariContext.getUser().getTimeZone()).ifPresentOrElse(tz -> expiryDate.setTimeZone(TimeZone.getTimeZone(tz)),
                () -> expiryDate.setTimeZone(TimeZone.getTimeZone("UTC")));
        instanceStateToBeSet.setExpiryDate(expiryDate.getTime());
        return result;
    }

    public boolean isPublishLimitExpiredForTrial(Quota quota, InstanceState instanceStateToBeSet) {
        boolean result = false;
        List<StreamInfo> syncStreamInfo = syncStatusService.getAllPipelineStreamStatus();
        List<StreamInfo> runningStreams = syncStreamInfo.stream().filter(streamInfo -> (streamInfo.getStatus() == StreamInfo.Status.RUNNING
                || streamInfo.getStatus() == StreamInfo.Status.QUEUED || streamInfo.getStatus() == StreamInfo.Status.RESYNCING)).collect(Collectors.toList());
        long publishLimit = Long.valueOf(quota.getValue());
        if (CollectionUtils.isNotEmpty(runningStreams) && publishLimit <= runningStreams.size()) {
            result = true;
            instanceStateToBeSet.setPublishLimitExpired(result);
        }
        return result;
    }

    public boolean isRecordLimitReachedForTrial(Quota quota, InstanceState instanceStateToBeSet) {
        List<EntityDefinition> entityDefinitions = entityProxyRepo.findActiveEntities(connectorService.getSyncariConnector().getId());
        List<String> entityApiNames = entityDefinitions.stream().map(edef -> edef.getApiName()).collect(Collectors.toList());
        boolean result = false;
        long totalCount = 0;
        long val = Long.valueOf(quota.getValue());
        for (String apiname : entityApiNames){
            totalCount += repoService.getCount(apiname);
        }
        if (val < totalCount) {
            instanceStateToBeSet.setRecordLimitExpired(true);
            result = true;
        }
        instanceStateToBeSet.setNumberOfRecordsLeft(val-totalCount);
        return result;
    }

    public boolean isRefDatasetLimitReachedForTrial(Quota quota, InstanceState instanceStateToBeSet){
        boolean result = false;
        long val = Long.valueOf(quota.getValue());
        long count = referenceDataService.countActiveRefData();
        if (count >= val){
            result = true;
            instanceStateToBeSet.setRefDataLimitExpired(result);
        }else{
            instanceStateToBeSet.setNumberOfRefDataLeft(val-count);
        }
        return result;
    }
}