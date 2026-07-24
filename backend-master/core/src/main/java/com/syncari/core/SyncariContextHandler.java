package com.syncari.core;

import com.syncari.core.config.AppConfig;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.Feature;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.InstanceConfigurationService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SyncariContextHandler {
	@Autowired
	OrganizationRepo orgRepo;
	@Autowired
	UserService userService;
	@Autowired
	InstanceConfigurationService instanceConfigurationService;
	@Autowired
	FeatureService featureService;
	@Autowired
	InsightsProviderIntegrator insightsProviderIntegrator;
	@Autowired
	@Qualifier("defaultEmailService")
	EmailService emailService;
	@Autowired
	AppConfig appConfig;

	public void setContext(String syncariId) {
		setContext(syncariId, false);
	}
	public void setContext(String syncariId, boolean setSystemUserInContext) {
		Organization org = orgRepo.findBySyncariId(syncariId)
				.orElseThrow(() -> new NotFoundException(Organization.class, "syncariId", syncariId));
		org.getInstance(syncariId).ifPresentOrElse(instance -> {
			if(setSystemUserInContext) {
				SyncariContext.setUser(userService.getSystemUser());
			}
			SyncariContext.setInstance(instance);
			SyncariContext.setOrganziation(org);
			instance.setFeatures(featureService.getEnabledFeatures().stream().collect(Collectors.toMap(Feature::getName, Function.identity())));
			MDC.put("subscription",SyncariContext.getOrganziation().getName());
			MDC.put("instance",SyncariContext.getInstance().getName());
			MDC.put("syncariId",SyncariContext.getInstance().getSyncariId());
			if(SyncariContext.getUser()!=null){
				MDC.put("user",SyncariContext.getUser().getEmail());
			}
			if (instanceConfigurationService.isDebugModeEnabled()) {
				MDC.put("debugMode", "true");
				log.debug("DEBUG mode enabled");
			} else {
				MDC.put("debugMode", "false");
			}

		},() -> { throw new RuntimeException("No instance found for " + syncariId); });
	}

	public void resetSyncariContext(){
		SyncariContext.resetAll();
		MDC.remove("subscription");
		MDC.remove("instance");
		MDC.remove("syncariId");
		MDC.remove("user");
		MDC.remove("debugMode");
	}
}
