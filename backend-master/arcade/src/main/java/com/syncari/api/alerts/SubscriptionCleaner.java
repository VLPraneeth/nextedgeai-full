package com.syncari.api.alerts;

import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Instance;
import com.syncari.core.model.InstanceState;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.service.*;
import com.syncari.utils.I18n;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@Data
public class SubscriptionCleaner {
    public static final String DEV_SYNCARI_COM = "dev@syncari.com";
    @Autowired
    SyncariContextHandler syncariContextHandler;

    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    ProvisioningService provisioningService;

    @Autowired
    UserService userService;

    @Autowired
    AppConfig appConfig;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    LockRepo lockRepo;

    private final static Integer NOTIFY_PERIOD = 7;
    private final static Integer INSTANCE_DELETE_DAYS_PERIOD = 7;
    private final static Integer SUBSCRIPTION_DELETE_DAYS_PERIOD = 30;


    static String lockOwner;

    static {
        lockOwner = UUID.randomUUID().toString();
    }

    // Scheduled to run every day at 10pm
    @Scheduled(cron = "0 0 3 * * ?")
    //@Scheduled(cron = "0 0 */1 * * *")
    public void removeExpiredInstance() {
        log.info("Running PLG Instance Cleaner");
        removeTrialSubscriptions();
        oneWeekNoticeTrialSubscriptions();
        removeDeletedSubscriptions();
        removeDeletedInstances();
    }

    @Transactional("customerTransactionManager")
    public void removeTrialSubscriptions() {
        if (SubscriptionService.TRIAL_PERIOD < 0) {
            throw new RuntimeException(I18n.i18n("trial_period_error"));
        }
        List<Organization> trialOrgs = subscriptionService.findAndModifyTrialExpiredCustomersToDeletingStatus();

        for (Organization organization : trialOrgs) {
            List<Instance> instances = organization.getInstances();
            List<String> syncariIds = instances.stream().map(i -> i.getSyncariId()).collect(Collectors.toList());
            for (String instanceId : syncariIds) {
                Optional<Instance> instanceOptional = organization.getInstance(instanceId);
                instanceOptional.ifPresent(instance -> {
                    User systemUser = userService.getSystemUser();
                    SyncariContext.runWithContext(organization, instance, systemUser , () -> {
                        var lockId = "plgcleaner"+instance.getSyncariId();
                        try {
                            if (instance.isTrial() && instance.getStatus().equals(Status.HARD_DELETING)) {
                                var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(15));
                                if(locked.isPresent()) {
                                    log.debug("Acquired lock {}", lockId);
                                    try {
                                        InstanceState instanceState = subscriptionService.getInstanceState(instanceId);
                                        long timeDifferenceInDays = subscriptionService.getTimeDiffInDays(instance, instanceState);

                                        //Check 1: If the instance is expiring today and it is more than or equal to 60 days old
                                        if ((timeDifferenceInDays >= SubscriptionService.TRIAL_PERIOD) && (instanceState.isTrialExpired())) {
                                            log.info("Deprovisioning instance {}", instance.getSyncariId());
                                            provisioningService.deprovisionInstance(instance.getSyncariId(), true);
                                            log.info("Deprovisioned instance: {}", instance.getSyncariId());
                                            Optional<Organization> orgAfterDeletingInstance = subscriptionService.getOrgByName(organization.getName());
                                            orgAfterDeletingInstance.ifPresentOrElse(o -> {
                                                if (o.isTrial() && CollectionUtils.isEmpty(o.getInstances())){
                                                    provisioningService.deprovision(o.getId(), true);
                                                }else{
                                                    log.info("Organization {} is not trial or still instances exists in org", o.getName());
                                                }
                                            },()-> log.info("Organization {} is not present", organization.getName()));
                                        }
                                    } catch (Exception e) {
                                        if(e instanceof OrgNotFound || e instanceof InstanceNotFound) {
                                            try {
                                                userService.removeInstanceFromUser(instanceId, Optional.empty());
                                                log.debug("Removed instance {} from all users", instanceId);
                                            }catch (Exception ee) {
                                                log.error("Error while remove user instance ", ee);
                                            }
                                        }
                                        String PLGInstanceCleanerFailureBody = "Failed to deprovision trial instance " + instance.getSyncariId();
                                        log.error(PLGInstanceCleanerFailureBody, e);
                                        PLGInstanceCleanerFailureBody += " due to \n" + ExceptionUtils.getFullStackTrace(e);
                                        try{
                                            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
                                                    String.format("Deprovision Trial Instance failure for instance %s", instance.getSyncariId()), PLGInstanceCleanerFailureBody);
                                        }catch (Exception ex){
                                            log.error("Send error email failed {}", ExceptionUtils.getStackTrace(ex));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // eating exception if not able to aquire lock and throw exception
                            log.error(String.format("Error acquiring lock for instance %s, error : %s", instance.getSyncariId(), e.getMessage()), e);
                        } finally {
                            log.info("No need to release lock for instance {} as db would have been deleted", lockId);
                        }
                    });
                });
            }
        }
    }

    @Transactional("customerTransactionManager")
    public void oneWeekNoticeTrialSubscriptions() {
        if (SubscriptionService.TRIAL_PERIOD < 0) {
            throw new RuntimeException(I18n.i18n("trial_period_error"));
        }
        List<Organization> trialOrgs = subscriptionService.findAllCustomersByInstanceType(OrganizationType.trial);

        //List of instances being deprovisioned in one week
        List<Instance> instancesDeprovisioningInOneWeek = new ArrayList<>();
        Map<String, List<String>> adminEmailsMap = new HashMap<>();

        for (Organization organization : trialOrgs) {
            List<Instance> instances = organization.getInstances();
            List<String> syncariIds = instances.stream().map(i -> i.getSyncariId()).collect(Collectors.toList());
            for (String instanceId : syncariIds) {
                Optional<Instance> instanceOptional = organization.getInstance(instanceId);
                instanceOptional.ifPresent(instance -> {
                    User systemUser = userService.getSystemUser();
                    SyncariContext.runWithContext(organization, instance, systemUser , () -> {
                        var lockId = "plgcleanerweeknotice"+instance.getSyncariId();
                        try {
                            var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(15));
                            if(locked.isPresent()) {
                                log.debug("Acquired lock {}", lockId);
                                if (instance.isTrial()) {
                                    try {
                                        InstanceState instanceState = subscriptionService.getInstanceState(instanceId);
                                        long timeDifferenceInDays = subscriptionService.getTimeDiffInDays(instance, instanceState);

                                        long timeDifferenceInDaysForNotfication = (instanceState.getExpiryDate() != null) ? ChronoUnit.DAYS.between(Calendar.getInstance().toInstant(),instanceState.getExpiryDate().toInstant()) : (60-timeDifferenceInDays);
                                        //Check 2: If the instance is expiring in a week
                                        if (timeDifferenceInDaysForNotfication == NOTIFY_PERIOD) {
                                            List<String> adminEmails = userService.getAllUsersFromInstance().stream().map(u -> u.getEmail()).collect(Collectors.toList());
                                            adminEmailsMap.put(instance.getSyncariId(), adminEmails);
                                            instancesDeprovisioningInOneWeek.add(instance);
                                        }
                                    } catch (Exception e) {
                                     log.error("Exception occurred while sending 1 week notification {}", ExceptionUtils.getStackTrace(e));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // eating exception if not able to aquire lock and throw exception
                            log.error(String.format("Error acquiring lock for instance %s, error : %s", instance.getSyncariId(), e.getMessage()), e);
                        } finally {
                            lockRepo.unlock(lockId, lockOwner);
                            log.info("Releasing lock for instance {} and lockId {}", instance.getSyncariId(), lockId);
                        }
                    });
                });

            }
        }
        // notify support and dev teams  and admins of instance about instances deprovisioning in 1 week
        for (Instance i : instancesDeprovisioningInOneWeek) {
            StringBuilder body = new StringBuilder("Deprovisioning the following instances in 1 week \n");
            body.append(String.format("Instance name : "+ i.getName()+ " syncariId : " + i.getSyncariId() + "\n"));
            List<String> emailIds = new ArrayList<>();
            if (adminEmailsMap.containsKey(i.getSyncariId())){
                emailIds.addAll(adminEmailsMap.get(i.getSyncariId()));
            }
            emailIds.addAll(List.of(DEV_SYNCARI_COM,
                    appConfig.getSupportEmail().stream().findFirst().get()));
            emailService.sendText(emailIds, "Deprovisioning Trial Instances in 1 week", body.toString());
        }

    }


    @Transactional("customerTransactionManager")
    public void removeDeletedSubscriptions() {
        List<Organization> deletedOrgs = subscriptionService.findDeletedCustomers();
        List<String> orgsToBeDeleted = new ArrayList<>();
        for (Organization organization : deletedOrgs) {
            if((null != organization.getDeletedAt()) && ChronoUnit.DAYS.between(organization.getDeletedAt().toInstant(), new Date().toInstant()) > SUBSCRIPTION_DELETE_DAYS_PERIOD) {
                log.info("Deprovisioning org {}", organization.getName());
                try{
                    List<String> instances = organization.getInstances().stream().map(i -> String.format("Instance name : %s, syncariId : %s \n", i.getName(), i.getSyncariId())).collect(Collectors.toList());
                    String body = "Deprovisioning the following instances \n" + String.join(",", instances);
                    provisioningService.deprovision(organization.getId(), true);
                    emailService.sendText(List.of(DEV_SYNCARI_COM,
                            appConfig.getSupportEmail().stream().findFirst().get()), "Deprovisioned Org " + organization.getName(), body);
                }catch (Exception e){
                    log.error(String.format("Error deleting for org %s, error : %s", organization.getName(), e.getMessage()));
                }

            }

            if((null != organization.getDeletedAt()) && ChronoUnit.DAYS.between(organization.getDeletedAt().toInstant(), new Date().toInstant()) == 23) {
                orgsToBeDeleted.add(organization.getName());
            }
        }
        if(!orgsToBeDeleted.isEmpty()) {
            String body = "Deprovisioning the following orgs in a week \n" + String.join(",", orgsToBeDeleted);
            emailService.sendText(List.of(DEV_SYNCARI_COM,
                    appConfig.getSupportEmail().stream().findFirst().get()), "Subscription Deprovision Notice ", body);
        }
    }

    @Transactional("customerTransactionManager")
    public void removeDeletedInstances() {
        List<Organization> orgsWithDeletedInstance = subscriptionService.findAndModifyDeletedInstancesCustomersToDeletingStatus(INSTANCE_DELETE_DAYS_PERIOD);
        orgsWithDeletedInstance.stream().forEach(o -> {
            List<String> syncariIds = o.getInstances().stream().filter(i -> i.getStatus()==Status.HARD_DELETING).map(x -> x.getSyncariId()).collect(Collectors.toList());
            syncariIds.forEach(instanceId -> {
                Optional<Instance> instanceOptional = o.getInstance(instanceId);
                log.info("Deprovisioning instance {}", instanceId);
                User systemUser = userService.getSystemUser();
                instanceOptional.ifPresent(instance -> {SyncariContext.runWithContext(o, instance, systemUser , () -> {
                    try{
                        provisioningService.deprovisionInstance(instance.getSyncariId(), true);
                        log.info("Hard Deleted instance {}", instanceId);
                    }catch (Exception e){
                        log.error(String.format("Error deleting for instance %s, error : %s", instance.getSyncariId(), e.getMessage()));
                    }
                });});
            });
        });
    }
}
