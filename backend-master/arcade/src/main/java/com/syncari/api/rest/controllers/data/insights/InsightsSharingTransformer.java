package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.InstanceConfiguration;
import com.syncari.core.model.SharedItem;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.InsightsShareDashboardResponse;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedItem;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedObj;
import com.syncari.core.service.InsightsSharingService;
import com.syncari.core.service.InstanceConfigurationService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InsightsSharingTransformer {


    @Autowired
    InstanceConfigurationService service;

    @Autowired
    UserService userService;

    @Autowired
    InsightsSharingService insightsSharingService;


    public InstanceConfiguration toInstanceConfig(InsightsSharingAllowedDomainDTO allowedDomainDTO, String key){
        InstanceConfiguration configuration = new InstanceConfiguration();
        List<String> domains = allowedDomainDTO.getDomains();
        configuration.setKey(key);
        configuration.setValue(domains.stream().distinct().collect(Collectors.toList()));
        return  configuration;
    }

    public InsightsSharingAllowedDomainDTO toInstanceSharingAllowedDomain(InstanceConfiguration instanceConfiguration){
        InsightsSharingAllowedDomainDTO dto = new InsightsSharingAllowedDomainDTO();
        dto.setDomains((List<String>) instanceConfiguration.getValue());
        return  dto;
    }

    public List<InsightsShareDetailsDTO> toInsightsShareDetailsDtoList(List<SharedItem> sharedItems){
        return sharedItems.stream().map(sI -> toInsightsSharedDetailsDTO(sI)).collect(Collectors.toList());
    }

    public InsightsShareDetailsDTO toInsightsSharedDetailsDTO(SharedItem sharedItem){
        InsightsShareDetailsDTO dto = new InsightsShareDetailsDTO();
        Optional<User> user = userService.findUserById(sharedItem.getRecipientsUserId());
        user.ifPresentOrElse(u -> dto.setEmailId(u.getEmail()), () -> dto.setEmailId("UNKNOWN"));
        InsightsDashboardSharedItem insightsDashboardSharedItem =  (InsightsDashboardSharedItem)sharedItem.getItemObject();
        if (insightsDashboardSharedItem.getExpiredTime() > 0){
            dto.setExpiryDate(Instant.ofEpochMilli(insightsDashboardSharedItem.getExpiredTime()));
        }
        dto.setStatus(insightsDashboardSharedItem.getInvitationStatus());
        dto.setSharedItemId(sharedItem.getId());
        if (insightsDashboardSharedItem.getLastVisitedDate() > 0){
            dto.setLastVisitedDate(Instant.ofEpochMilli(insightsDashboardSharedItem.getLastVisitedDate()));
        }
        return dto;
    }

    public InsightsSharedDashboardDTO toInsightsSharedDashboardDTO(SharedItem sharedItem){
        InsightsSharedDashboardDTO dto = new InsightsSharedDashboardDTO();
        InsightsDashboardSharedItem insightsDashboardSharedItem =  (InsightsDashboardSharedItem)sharedItem.getItemObject();
        dto.setDashboardId(sharedItem.getSourceId());
        dto.setDashboardInstanceId(sharedItem.getSourceInstance());
        dto.setDashboardDiplayName(insightsDashboardSharedItem.getDashboardDisplayName());
        dto.setDashboardDescription(insightsDashboardSharedItem.getDashboardDescription());
        if (insightsDashboardSharedItem.getExpiredTime() > 0){
            dto.setExpiredTime(Instant.ofEpochMilli(insightsDashboardSharedItem.getExpiredTime()));
        }
        return dto;
    }

    public InsightsDashboardSharedObj toInsightsDashboardSharedObj(InsightsSharingDashboardDTO dto){
        InsightsDashboardSharedObj obj = new InsightsDashboardSharedObj();
        obj.setDashboardId(dto.getDashboardId());
        obj.setEmailIds(dto.getEmails());
        obj.setExpiryDate(insightsSharingService.getExpiryTimeFromStringDate(Optional.ofNullable(dto.getExpiryDate())));
        obj.setEmailMessage(dto.getMessage());
        return obj;
    }

    public InsightsShareDashboardResponseDTO insightsShareDashboardResponseDTO(InsightsShareDashboardResponse response){
        InsightsShareDashboardResponseDTO responseDTO = new InsightsShareDashboardResponseDTO();
        responseDTO.setRecipientEmailId(response.getRecipientEmailId());
        responseDTO.setSharedItem(response.getSharedItem());
        responseDTO.setErrorMessage(response.getErrorMessage());
        return responseDTO;
    }

    public InsightsSharingDashboardDTO toInsightsSharingDashboardDTO(SharedItem s){
        InsightsSharingDashboardDTO dto = new InsightsSharingDashboardDTO();
        dto.setDashboardId(s.getSourceId());
        userService.findUserById(s.getRecipientsUserId()).ifPresentOrElse(u -> dto.setEmails(List.of(u.getEmail())), ()-> dto.setEmails(List.of("UNKNOWN")));
        InsightsDashboardSharedItem sharedItemObj = (InsightsDashboardSharedItem)s.getItemObject();
        Optional.ofNullable(SyncariContext.getUser().getTimeZone()).ifPresentOrElse(tz -> {
            dto.setExpiryDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(sharedItemObj.getExpiredTime()), ZoneId.of(tz)).toString());
        },()-> {
            dto.setExpiryDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(sharedItemObj.getExpiredTime()), ZoneId.of("UTC")).toString());
        });
        dto.setMessage(sharedItemObj.getEmailMessage());
        return dto;
    }

    public List<InsightsDashboardSharedObj> toInsightsDashboardSharedObjList(List<SharedItem> sharedItems){
        return sharedItems.stream().map(s -> this.toInsightsDashboardSharedObj(this.toInsightsSharingDashboardDTO(s))).collect(Collectors.toList());
    }
}
