package com.syncari.core.service;

import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.InsightsShareDashboardResponse;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedItem;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedObj;
import com.syncari.core.model.insights.sharing.SharedItemInvitationStatus;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import com.syncari.core.repositories.syncari.SharedItemRepoImpl;
import com.syncari.core.template.TemplateRenderer;
import com.syncari.core.utils.PasswordConstraintValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.passay.CharacterRule;
import org.passay.PasswordGenerator;
import org.passay.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.xml.bind.DatatypeConverter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Service
@Slf4j
public class InsightsSharingService {

    public static final String INSTANCE_CONFIG_DOMAIN_KEY = "domains";
    private static final String DASHBOARD_URL = "%s/insightssharing/user/%s/dashboard/%s";
    private static final String SHARE_INSIGHTSDASHBOARD_TEMPLATE_PATH = "/templates/insights.share.dashboard.template";
    private static final String SHARE_INSIGHTSDASHBOARD_WITHOUTPWD_TEMPLATE_PATH = "/templates/insights.share.dashboard.withoutpwd.template";

    @Autowired
    SharedItemRepo sharedItemRepo;

    @Autowired
    SharedItemRepoImpl sharedItemCustomRepo;

    @Autowired
    UserService userService;

    @Autowired
    InsightsDashboardService dashboardService;

    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    AppConfig appConfig;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    InstanceConfigurationService instanceConfigurationService;

    @Autowired
    TemplateRenderer renderer;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    InsightsService service;

    @Autowired
    private SyncariContextHandler synCtxHandler;



    public List<SharedItem> findAllSharedItemsByRecipientId(String recipientId){
        return sharedItemRepo.findAllSharedItemsByItemTypeAAndRecipientsUserId(Sharable.INSIGHTS_DASHBOARD, recipientId);
    }

    public List<SharedItem> findAllInsightsSharedItemsForGivenInstance(){
        return sharedItemRepo.findAllSharedItemsByItemTypeAAndSourceInstance(Sharable.INSIGHTS_DASHBOARD, SyncariContext.getSyncariId());
    }

    public Optional<SharedItem> findSharedItemByRecipientIdAndDashboardId(String recipientId, String dashboardId){
        return sharedItemRepo.findSharedItemByItemTypeAndRecipientsUserIdAndSourceId(Sharable.INSIGHTS_DASHBOARD, recipientId, dashboardId);
    }

    public Iterable<SharedItem> findAllSharedItemsById(List<String> sharedItemIds){
        return sharedItemRepo.findAllById(sharedItemIds);
    }

    public void deleteSharedItems(List<String> sharedItemIds){
        sharedItemRepo.deleteAllById(sharedItemIds);
    }

    public void deleteSharedItemsByDashboardId(String dashboardId){
        List<SharedItem>  sharedItems =  sharedItemRepo.findAllSharedItemsByItemTypeAAndSourceId(Sharable.INSIGHTS_DASHBOARD, dashboardId);
        List<String> sharedItemIds = sharedItems.stream().map(s -> s.getId()).collect(Collectors.toList());
        sharedItemRepo.deleteAllById(sharedItemIds);
    }

    public void deleteSharedItemsByRecipientsId(String recipientId){
        List<SharedItem>  sharedItems =  sharedItemRepo.findAllSharedItemsByItemTypeAAndRecipientsUserId(Sharable.INSIGHTS_DASHBOARD, recipientId);
        List<String> sharedItemIds = sharedItems.stream().map(s -> s.getId()).collect(Collectors.toList());
        sharedItemRepo.deleteAllById(sharedItemIds);
    }

    public void updateSharedItemsExpiry(String sharedItemId, Optional<String>  expiryDate){
        Optional<SharedItem> sharedItem = sharedItemRepo.findById(sharedItemId);
        sharedItem.ifPresentOrElse(item -> {
            InsightsDashboardSharedItem insightsDashboardSharedItem = (InsightsDashboardSharedItem)item.getItemObject();
            insightsDashboardSharedItem.setExpiredTime(getExpiryTimeFromStringDate(expiryDate));
            sharedItemRepo.save(item);
        },()-> {
            throw new SyncariValidationException("Shared Item detail is not present");
        });
    }

    public long getExpiryTimeFromStringDate(Optional<String>  expiryDate){
        return expiryDate.map(e -> {
            Calendar newExpiryDate = Calendar.getInstance();
            // UI is sending in UTC time zone, we are not converting and storing it as it is. UI is showing after conversion.
            Instant tempNewExpiryDate = LocalDateTime.parse(e).atZone(ZoneId.of("UTC")).toInstant();
            newExpiryDate.setTimeInMillis(tempNewExpiryDate.toEpochMilli());
            newExpiryDate.setTimeZone(TimeZone.getTimeZone("UTC"));
            return newExpiryDate.getTimeInMillis();
        }).orElse(-1l);

    }

    public List<InsightsShareDashboardResponse> shareDashboard(InsightsDashboardSharedObj sharedObj){
        PasswordGenerator passwordGenerator = new PasswordGenerator();
        Optional<InstanceConfiguration> instanceConfig = instanceConfigurationService.getInstanceConfigurationByKey(INSTANCE_CONFIG_DOMAIN_KEY);
        List<String> allowedDomain = new ArrayList<>();
        instanceConfig.ifPresent(ic -> allowedDomain.addAll((List)ic.getValue()));
        List<String> emails = sharedObj.getEmailIds();
        Map<String, String> userIdAndEmailMap = new HashMap<>();
        Map<String, String> userIdAndPasswordMap = new HashMap<>();
        List<InsightsShareDashboardResponse> shareDashboardResponses = new ArrayList<>();
        List<String> allowedEmailDomainsEmail = emails.stream().filter(e -> isAllowedToShare(e, allowedDomain)).collect(Collectors.toList());
        List<String> notAllowedEmailDomainsEmail = emails.stream().filter(e -> !isAllowedToShare(e, allowedDomain)).collect(Collectors.toList());

        // Out of allowed domains, emails can be inactive.
        List<String> inactiveEmailIds = new ArrayList<>();

        allowedEmailDomainsEmail.forEach(e -> {
            Optional<User> user = userService.getUserByEmail(e);
            user.ifPresentOrElse( u -> {
                if (u.isActive()){
                    userIdAndEmailMap.put(u.getId(), u.getEmail());
                    Set<String> userCurrentRoles = userService.getUserRolesForCurrentInstance(u.getId());
                    Set<String> rolesToBeAssigned = new HashSet<>();
                    rolesToBeAssigned.add(RoleConstants.DASHBOARD_LIGHT_VIEWER);
                    if (CollectionUtils.isNotEmpty(userCurrentRoles)){
                        rolesToBeAssigned.addAll(userCurrentRoles);
                    }
                    userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), u, rolesToBeAssigned);
                }else{
                    inactiveEmailIds.add(u.getId());
                }
            },()-> {
                User userTocreate = new User().setEmail(e);
                userTocreate.setStatus(Status.ACTIVE);
                String pwd = generateRandomPassword(passwordGenerator);
                userTocreate.setPassword(passwordEncoder.encode(pwd));
                userTocreate.setCurrentInstanceId(SyncariContext.getSyncariId());
                User createdUser = userService.saveUser(userTocreate);
                userIdAndPasswordMap.put(createdUser.getId(), pwd);
                userIdAndEmailMap.put(createdUser.getId(), createdUser.getEmail());
                userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), createdUser, Set.of(RoleConstants.DASHBOARD_LIGHT_VIEWER));
            });
        });
        userIdAndEmailMap.keySet().forEach(uId -> {
            Optional<SharedItem> existingSharedItem = sharedItemRepo.findSharedItemByItemTypeAndRecipientsUserIdAndSourceId(Sharable.INSIGHTS_DASHBOARD,uId,sharedObj.getDashboardId());
            existingSharedItem.ifPresentOrElse(es -> {
                String emailId = userIdAndEmailMap.get(uId);
                InsightsShareDashboardResponse response = new InsightsShareDashboardResponse();
                InsightsDashboardSharedItem itemObject = (InsightsDashboardSharedItem)es.getItemObject();
                // setExpiryDate using method setExpiryToSharedItem
                setExpiryToSharedItem(itemObject, sharedObj.getExpiryDate());
                es.setItemObject(itemObject);
                SharedItem savedSharedItem = sharedItemRepo.save(es);
                response.setSharedItem(savedSharedItem);
                response.setRecipientEmailId(emailId);
                shareDashboardResponses.add(response);

                StringBuilder body = new StringBuilder().append(sharedObj.getEmailMessage());
                shareDashboardEmail(body.toString(),Optional.empty(),emailId,sharedObj.getDashboardId(),itemObject.getDashboardDisplayName());
            },() -> {
                Optional<InsightsDashboard> dashboard = dashboardService.getDashboardById(sharedObj.getDashboardId());
                InsightsDashboardSharedItem dashboardSharedItem = new InsightsDashboardSharedItem();
                dashboard.ifPresent(d -> {
                    dashboardSharedItem.setDashboardId(d.getId());
                    dashboardSharedItem.setDashboardDescription(d.getDescription());
                    dashboardSharedItem.setDashboardDisplayName(d.getDisplayName());
                });
                dashboardSharedItem.setSenderUserId(SyncariContext.getUser().getId());
                dashboardSharedItem.setDashboardSourceInstanceId(SyncariContext.getSyncariId());
                dashboardSharedItem.setEmailMessage(sharedObj.getEmailMessage());
                // setExpiryDate using method setExpiryToSharedItem
                setExpiryToSharedItem(dashboardSharedItem, sharedObj.getExpiryDate());
                dashboardSharedItem.setInvitationStatus(SharedItemInvitationStatus.NOT_OPENED);

                InsightsShareDashboardResponse response = new InsightsShareDashboardResponse();
                SharedItem sharedItem = new SharedItem();
                sharedItem.setItemObject(dashboardSharedItem);
                sharedItem.setItemType(Sharable.INSIGHTS_DASHBOARD);
                sharedItem.setRecipientsUserId(uId);
                // UNKNOWN is not a case
                sharedItem.setSourceId(sharedObj.getDashboardId());
                sharedItem.setSourceInstance(SyncariContext.getSyncariId());
                sharedItem.setOrgId(SyncariContext.getOrganziation().getId());
                String emailId = userIdAndEmailMap.get(uId);
                sharedItem.setRecipientsEmailId(emailId);
                SharedItem savedSharedItem = sharedItemRepo.save(sharedItem);
                response.setSharedItem(savedSharedItem);
                response.setRecipientEmailId(emailId);
                shareDashboardResponses.add(response);
                shareDashboardEmail(sharedObj.getEmailMessage(),Optional.ofNullable(userIdAndPasswordMap.get(uId)),emailId,sharedObj.getDashboardId(),dashboardSharedItem.getDashboardDisplayName());
            });

        });

        // add null SharedItem for not allowed emails;
        notAllowedEmailDomainsEmail.forEach(e -> {
            InsightsShareDashboardResponse response = new InsightsShareDashboardResponse();
            response.setSharedItem(null);
            response.setRecipientEmailId(e);
            response.setErrorMessage("Not Allowed Domain, Cannot share this dashboard to this domain");
            shareDashboardResponses.add(response);
        });

        // Add error for inactive emails
        inactiveEmailIds.forEach(e -> {
            InsightsShareDashboardResponse response = new InsightsShareDashboardResponse();
            response.setSharedItem(null);
            response.setRecipientEmailId(e);
            response.setErrorMessage("User is not active user, not able to share dashboard");
            shareDashboardResponses.add(response);
        });

        return shareDashboardResponses;
    }

    private void setExpiryToSharedItem(InsightsDashboardSharedItem dashboardSharedItem, Long expiryDate){
        if (expiryDate == -1l){
            dashboardSharedItem.setExpiredTime(-1l);
        }else{
            // if es is expired and new date is not greater than now then add 7 days to current date
            if (expiryDate < Instant.now().toEpochMilli()){
                Instant expiredTime = Instant.ofEpochMilli(dashboardSharedItem.getExpiredTime());
                if (expiredTime.toEpochMilli() <= Instant.now().toEpochMilli()){
                    dashboardSharedItem.setExpiredTime(Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli());
                }
            }else if (expiryDate >= Instant.now().toEpochMilli()){ // if new date is great then now
                dashboardSharedItem.setExpiredTime(expiryDate);
            }
        }
    }

    public void shareDashboardEmail(String message, Optional<String> password, String emailId, String dashboardId, String dashboardName){
        String fromUser = String.format("%s %s", SyncariContext.getUser().getFirstName(),
                SyncariContext.getUser().getLastName());
        String syncariLogoUrl = String.format(GlobalConstants.SYNCARI_LOGO, appConfig.getCloudCdnHost());
        String userLogoUrl = String.format("%s/user-add_2X.png", appConfig.getCloudCdnHost());
        Map<String, Object> context = new HashMap<>();
        context.put("graphIconUrl", String.format(GlobalConstants.GRAPH_ICON_LOGO, appConfig.getCloudCdnHost()));
        String encodedEmailId = Base64.getEncoder().encodeToString(emailId.getBytes());
        String displayName = StringUtils.isNotEmpty(dashboardName) ? dashboardName : "";
        context.putAll(Map.of("fromUser", fromUser,"message",message, "dashboardUrl", format(DASHBOARD_URL, appConfig.getSpectrumServerHost(),encodedEmailId, dashboardId),
                "syncariLogoUrl", syncariLogoUrl, "userLogoUrl", userLogoUrl,"userEmail", emailId,"dashboardName",displayName));
        String subject = "Syncari - %s shared something with you";
        if (StringUtils.isNotEmpty(message)){
            context.put("messageExists", "true");
        }
        password.ifPresentOrElse(p -> {
            context.put("password",p);
            String body = renderer.render(SHARE_INSIGHTSDASHBOARD_TEMPLATE_PATH, context);
            emailService.sendHtml(List.of(emailId),List.of(),String.format(subject,SyncariContext.getUser().getFirstName()),body);
        },()-> {
            String body = renderer.render(SHARE_INSIGHTSDASHBOARD_WITHOUTPWD_TEMPLATE_PATH, context);
            emailService.sendHtml(List.of(emailId),List.of(),String.format(subject,SyncariContext.getUser().getFirstName()),body);
        });
    }

    private boolean isAllowedToShare(String emailId, List<String> allowedDomains){
        if (CollectionUtils.isNotEmpty(allowedDomains)){
            String [] splittedEmail = emailId.split("@");
            return allowedDomains.contains(splittedEmail[1]);
        }
        return true;
    }

    private String generateRandomPassword(PasswordGenerator passwordGenerator){
        List<Rule> rulesList = PasswordConstraintValidator.rulesList.stream().filter(r -> r instanceof CharacterRule).collect(Collectors.toList());
        List<CharacterRule> characterRules = new ArrayList<>();
        rulesList.forEach(r -> characterRules.add((CharacterRule) r));
        return passwordGenerator.generatePassword(10,characterRules);
    }

    public Page<SharedItem> query(String dashboardId, PageCursor pageCursor, Optional<Expression> predicate){
        return sharedItemCustomRepo.getSharedItems(dashboardId,pageCursor,predicate);
    }

    public SharedItem save(SharedItem sharedItem){
        return  sharedItemRepo.save(sharedItem);
    }

    // Add code to delete records for deleted domains

    public List<SharedItem> deleteDomainsSharedItem(List<String> deletedDomains, String instanceId, boolean isDelete){

        List<SharedItem> deletedSharedItemsWithRecord = new ArrayList<>();
        List<String> sharedItemIds = new ArrayList<>();
                // for given instance id find all shareditems which ends with domain for recipientsEmailId
        deletedDomains.forEach(d-> {
            List<SharedItem> sharedItemstoDelete = sharedItemRepo.findSharedItemByItemTypeAndSourceInstance(Sharable.INSIGHTS_DASHBOARD, instanceId, d);
            deletedSharedItemsWithRecord.addAll(sharedItemstoDelete);
            sharedItemIds.addAll(sharedItemstoDelete.stream().map(si -> si.getId()).collect(Collectors.toList()));
        });
        if (isDelete && CollectionUtils.isNotEmpty(sharedItemIds)){
            sharedItemRepo.deleteAllById(sharedItemIds);
        }
        return deletedSharedItemsWithRecord;

    }

    public InsightsDashboard getDashboard(String dashboardId, SharedItem s){
        User user = SyncariContext.getUser();
        String sourceInstanceId = s.getSourceInstance();
        String orgId = s.getOrgId();
        Optional<Organization> org = subscriptionService.getOrgById(orgId);
        log.info("sourceInstanceId is {} and orgid is {}", sourceInstanceId,org);
        if (!org.isPresent()){
            throw new NotFoundException(i18n("dashboardid_notpresent"));
        }
        log.info(String.format("Switching User: %s to Instance: %s", user.getEmail(), sourceInstanceId));
        synCtxHandler.setContext(sourceInstanceId);
        InsightsDashboardSharedItem item = (InsightsDashboardSharedItem)s.getItemObject();
        if ((null != item) && ((item.getExpiredTime() > Instant.now().toEpochMilli())) || (item.getExpiredTime() == -1)){
            InsightsDashboard dashboard = service.getDashboard(dashboardId);
            if (null != dashboard){
                InsightsDashboardSharedItem dashboardSharedItem = (InsightsDashboardSharedItem)s.getItemObject();
                dashboardSharedItem.setInvitationStatus(SharedItemInvitationStatus.OPENED);
                dashboardSharedItem.setLastVisitedDate(Instant.now().toEpochMilli());
                save(s);
            }
            return dashboard;
        }else{
            throw new AccessDeniedException(i18n("dashboardid_expired"));
        }

    }

}
