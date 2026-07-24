package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.Util;
import com.syncari.api.rest.config.security.SecurityConstants;
import com.syncari.api.rest.controllers.data.insights.*;
import com.syncari.core.SyncariContext;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.InsightsShareDashboardResponse;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedItem;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedObj;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.*;
import com.syncari.restutils.utils.DataQueryUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping("/api/v1/insightssharing")
public class InsightsSharingController {

    @Autowired
    InsightsSharingService insightsSharingService;

    @Autowired
    InsightsSharingTransformer sharingTransformer;

    @Autowired
    InstanceConfigurationService configurationService;

    @Autowired
    UserService userService;

    @Autowired
    InsightsTransformer transformer;

    @Autowired
    InsightsService service;

    @Autowired
    DatacardService datacardService;

    @Autowired
    DataQueryUtils dataUtils;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    private Util util;

    @Secured(CREATE_ALLOWED_DOMAINS)
    @RequestMapping(method = RequestMethod.POST, value = "/allowedDomains")
    public InsightsSharingAllowedDomainDTO addOrUpdateAllowedDomains(@RequestBody InsightsSharingAllowedDomainDTO allowedDomain) {
        InstanceConfiguration instanceConfiguration = sharingTransformer.toInstanceConfig(allowedDomain,InsightsSharingService.INSTANCE_CONFIG_DOMAIN_KEY);
        InstanceConfiguration savedConfig = configurationService.saveDomainsinInstanceConfiguration(instanceConfiguration, InsightsSharingService.INSTANCE_CONFIG_DOMAIN_KEY);
        return sharingTransformer.toInstanceSharingAllowedDomain(savedConfig);
    }


    @Secured(READ_ALLOWED_DOMAINS)
    @RequestMapping(method = RequestMethod.POST, value = "/deletedDomainsRecords")
    public Set<String> deletedDomainsRecords(@RequestBody InsightsSharingAllowedDomainDTO allowedDomain) {
        InstanceConfiguration instanceConfiguration = sharingTransformer.toInstanceConfig(allowedDomain, InsightsSharingService.INSTANCE_CONFIG_DOMAIN_KEY);
        Optional<InstanceConfiguration> instanceConfigurationOptional = configurationService.getInstanceConfigurationByKey(InsightsSharingService.INSTANCE_CONFIG_DOMAIN_KEY);
        List<String> domainsToBeDeleted = configurationService.getDeletedDomains(instanceConfiguration,instanceConfigurationOptional);
        List<SharedItem> sharedItems =  insightsSharingService.deleteDomainsSharedItem(domainsToBeDeleted, SyncariContext.getSyncariId(), false);
        List<String> domainsToBeDeletedFromRecord = sharedItems.stream().map(s -> s.getRecipientsEmailId().split("@")[1]).collect(Collectors.toList());
        return domainsToBeDeletedFromRecord.stream().filter(d -> StringUtils.isNotEmpty(d)).collect(Collectors.toSet());
    }

    @Secured(READ_ALLOWED_DOMAINS)
    @RequestMapping(method = RequestMethod.GET, value = "/listDomains")
    public InsightsSharingAllowedDomainDTO listAllowedDomains() {
        Optional<InstanceConfiguration> instanceConfiguration = configurationService.getInstanceConfigurationByKey(InsightsSharingService.INSTANCE_CONFIG_DOMAIN_KEY);
        return instanceConfiguration.map(ic -> sharingTransformer.toInstanceSharingAllowedDomain(ic))
                .orElse(null);
    }

    @Secured(DELETE_ALLOWED_DOMAINS)
    @RequestMapping(method = RequestMethod.DELETE, value = "/deleteAllAllowedDomains")
    public void deleteAllAllowedDomains() {
        Optional<InstanceConfiguration> instanceConfiguration = configurationService.getInstanceConfigurationByKey(InsightsSharingService.INSTANCE_CONFIG_DOMAIN_KEY);
        instanceConfiguration.ifPresent(ic -> configurationService.delete(ic));
    }

    // api to share dashboard
    @Secured(SHARE_DASHBOARD)
    @RequestMapping(method = RequestMethod.POST)
    public List<InsightsShareDashboardResponseDTO>  sharedashboard(@RequestBody InsightsSharingDashboardDTO sharingDashboardDTO) {
        InsightsDashboardSharedObj sharingObj = sharingTransformer.toInsightsDashboardSharedObj(sharingDashboardDTO);
        List<InsightsShareDashboardResponse> shareDashboardResponses =  insightsSharingService.shareDashboard(sharingObj);
        return shareDashboardResponses.stream().map(sd -> sharingTransformer.insightsShareDashboardResponseDTO(sd)).collect(Collectors.toList());
    }

    // api to list all shared dashboard with context user
    @Secured(READ_ALL_SHARED_DASHBOARD)
    @RequestMapping(method = RequestMethod.GET, value = "/allSharedDashboard")
    public List<InsightsSharedDashboardDTO> findAllSharedDashboard(){
        Optional<User> user = userService.getUserByEmail(SyncariContext.getUser().getEmail());
        List<InsightsSharedDashboardDTO> sharingDashboardDTOs = new ArrayList<>();
        user.ifPresent(u -> {
            List<SharedItem> sharedItems = insightsSharingService.findAllSharedItemsByRecipientId(u.getId());
            // filter expired ones
            List<SharedItem> unexpiredItems = sharedItems.stream().filter(s -> (((InsightsDashboardSharedItem)s.getItemObject()).getExpiredTime() > Instant.now().toEpochMilli() || (((InsightsDashboardSharedItem)s.getItemObject()).getExpiredTime()==-1) )).collect(Collectors.toList());
            unexpiredItems.forEach(sI -> {
                sharingDashboardDTOs.add(sharingTransformer.toInsightsSharedDashboardDTO(sI));
            });
        });
        return sharingDashboardDTOs;
    }

    @Secured(READ_ALL_SHARED_DASHBOARD)
    @RequestMapping(method = RequestMethod.GET, value = "/dashboard/{dashboardId}")
    public  DashboardDTO getDashboard(@PathVariable String dashboardId, @RequestHeader(SecurityConstants.TOKEN_HEADER) String previousToken, HttpServletResponse response){
        User user = SyncariContext.getUser();
        Optional<SharedItem> sharedItem = insightsSharingService.findSharedItemByRecipientIdAndDashboardId(user.getId(), dashboardId);
        return sharedItem.map(s -> {
            String sourceInstanceId = s.getSourceInstance();
            InsightsDashboard dashboard = insightsSharingService.getDashboard(dashboardId,s);
            List<Datacard> datacards = dashboard.getDataCardIds().stream().map(d -> datacardService.getSeededOrFromDataset(d)).collect(Collectors.toList());
            List<String> perms = user.isSuperAdmin() ? Permissions.allPermissions()
                    : new ArrayList<>(userService.getUserPermissionsForInstance(user.getId(),
                    subscriptionService.getInstance(sourceInstanceId)));
            // create new JWT token and set in response header
            String token = util.parseJWTTokenAndUpdateUserWithNewLoginDetails(previousToken.replace("Bearer ", ""),
                    user, perms,false);
            response.addHeader(SecurityConstants.TOKEN_HEADER, SecurityConstants.TOKEN_PREFIX + token);
            return transformer.toDashboardDTO(dashboard, datacards);
        }).orElseThrow( () -> new AccessDeniedException(i18n("dashboardid_notpresent")));
    }

    // api to list sharing details for given dashboardId
    @Secured(READ_SHARED_DASHBOARD_DETAILS)
    @RequestMapping(method = RequestMethod.POST, value = "/details/{dashboardId}")
    public InsightsShareDetailsResponse getSharingDetails(@PathVariable String dashboardId,
                                                           @RequestParam(required = false) String cursor,
                                                           @RequestParam String direction,
                                                           @RequestParam int pageSize,
                                                           @RequestBody(required = false) String predicate){
        if(StringUtils.isBlank(cursor) && PageDirection.valueOf(direction) == PageDirection.previous) {
            throw new SyncariValidationException(i18n("cursor_needed_for_prev"));
        }
        Optional<Expression> input = StringUtils.isNotEmpty(predicate) ? dataUtils.getExpression(predicate) : Optional.empty();
        Page<SharedItem> page = insightsSharingService.query(dashboardId, new PageCursor(cursor, PageDirection.valueOf(direction), pageSize), input);
        List<InsightsShareDetailsDTO> sharedDetails = sharingTransformer.toInsightsShareDetailsDtoList(page.getRecords());
        InsightsShareDetailsResponse response = new InsightsShareDetailsResponse();
        response.setShareDetailsRecords(sharedDetails);
        response.setPageInfo(page.getPageInfo());
        return response;
    }


    // api to delete shared dashboards
    @Secured(DELETE_SHARED_DASHBOARD_DETAILS)
    @RequestMapping(method = RequestMethod.DELETE, value = "/details")
    public void deleteSharingDetails(@RequestParam List<String> sharedItemIds){
        insightsSharingService.deleteSharedItems(sharedItemIds);
    }

    // api to reshare dashboards
    @Secured(SHARE_DASHBOARD)
    @RequestMapping(method = RequestMethod.POST, value = "/reshare")
    public List<InsightsShareDashboardResponseDTO> reshareDashboard(@RequestBody List<String> sharedItemIds){
        List<InsightsDashboardSharedObj> insightsShareDetailsDTOS = sharingTransformer.toInsightsDashboardSharedObjList(IterableUtils.toList(insightsSharingService.findAllSharedItemsById(sharedItemIds)));
        List<List<InsightsShareDashboardResponse>> sharedItemsResponse = insightsShareDetailsDTOS.stream().map(ist -> insightsSharingService.shareDashboard(ist)).collect(Collectors.toList());
        List<InsightsShareDashboardResponse> flattenList =  sharedItemsResponse.stream().flatMap(f -> f.stream()).collect(Collectors.toList());
        return flattenList.stream().map(sd -> sharingTransformer.insightsShareDashboardResponseDTO(sd)).collect(Collectors.toList());

    }


    // api to update expiry date of dashboard
    @Secured(UPDATE_SHARED_DASHBOARD_EXPIRY)
    @RequestMapping(method = RequestMethod.PUT, value = "/details")
    public void updateExpiry(@RequestParam String sharedItemId,@RequestParam(required = false) String expiryDate){
        insightsSharingService.updateSharedItemsExpiry(sharedItemId,Optional.ofNullable(expiryDate));
    }
}
