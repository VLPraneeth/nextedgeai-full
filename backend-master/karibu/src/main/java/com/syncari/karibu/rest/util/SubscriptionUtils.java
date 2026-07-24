package com.syncari.karibu.rest.util;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.QuotaType;
import com.syncari.core.repositories.syncari.PlanRepo;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.request.InstanceRequest;
import com.syncari.karibu.rest.response.InstanceResponse;
import com.syncari.karibu.rest.response.OrgResponse;
import com.syncari.karibu.rest.response.TrialInstanceResponse;
import com.syncari.utils.DateUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Component
public class SubscriptionUtils {

    @Autowired
    PlanRepo planRepo;

    public OrgResponse toOrgResponse(ProvisioningResponse provisioningResponse){
        Organization org = provisioningResponse.getOrganization();
        OrgResponse response = new OrgResponse();
        response.setId(org.getId());
        response.setName(org.getName());
        response.setInstances(toInstanceResponses(org.getInstances(), org.getName()));
        response.setErrorMessages(provisioningResponse.getMessages());
        return response;
    }

    public OrgResponse toOrgResponse(Organization org){
        OrgResponse response = new OrgResponse();
        response.setId(org.getId());
        response.setName(org.getName());
        response.setInstances(toInstanceResponses(org.getInstances(), org.getName()));
        return response;
    }

    private List<InstanceResponse> toInstanceResponses(List<Instance> instances, String subscriptionName) {
        List<InstanceResponse> response = new ArrayList<>();
        instances.stream().forEach(i -> {response.add(getTrialInstance(i, subscriptionName)); });
        return response;
    }


    public InstanceResponse getInstance(Instance instance, String subscriptionName) {
        InstanceResponse instanceResponse = new InstanceResponse();

        if (StringUtils.isNotEmpty(instance.getPlanId())){
            Plan plan = planRepo.findById(instance.getPlanId()).get();
            instanceResponse.setPlanName(plan.getName());
        }
        instanceResponse.setSubscriptionName(subscriptionName);
        instanceResponse.setName(instance.getName());
        instanceResponse.setDisplayName(instance.getDisplayName());
        instanceResponse.setSyncariId(instance.getSyncariId());
        instanceResponse.setType(instance.getType());
        instanceResponse.setStatus(instance.getStatus());
        if (CollectionUtils.isNotEmpty(instance.getQuota())){
            instanceResponse.setQuota(instance.getQuota());
        }
        return instanceResponse;
    }

    public TrialInstanceResponse getTrialInstance(Instance instance, String subscriptionName) {
        TrialInstanceResponse response = new TrialInstanceResponse();
        Plan plan = planRepo.findById(instance.getPlanId()).get();
        response.setPlanName(plan.getName());
        response.setSubscriptionName(subscriptionName);
        response.setName(instance.getName());
        response.setDisplayName(instance.getDisplayName());
        response.setSyncariId(instance.getSyncariId());
        response.setType(instance.getType());
        response.setStatus(instance.getStatus());
        response.setQuota(instance.getQuota());
        response.setCreatedAt(instance.getCreatedAt());
        List<Quota> daysLimit = instance.getQuota().stream().filter(q -> q.getType().equals(QuotaType.TRIAL_DAYS_LIMIT)).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(daysLimit) && daysLimit.size() > 0) {
            response.setEndDate(DateUtil.addDaysToDate(instance.getCreatedAt(),Integer.valueOf(daysLimit.get(0).getValue())));
        }
        return response;
    }

    public void validateCreateInstanceRequest(InstanceRequest request){
        if(!SyncariContext.getOrganziation().getName().equals(request.getSubscriptionName()))
            throw new BadRequestException(i18n("subscription_name_mismatch", request.getSubscriptionName(), SyncariContext.getOrganziation().getName()));
    }
}
