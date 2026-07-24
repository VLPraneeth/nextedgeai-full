package com.syncari.core.service;

import static java.lang.String.format;
import java.util.List;
import java.util.Map;
import com.syncari.core.model.misc.PhoneHome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.template.TemplateRenderer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ApplicationService {
    private static final String PHONE_HOME_TEMPLATE_PATH = "/templates/phone.home.template";
    private static final String PHONE_HOME_SUBJECT = "[PhoneHome][%s][%s][%s] %s";
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    AppConfig appConfig;
    @Autowired
    TemplateRenderer renderer;

    public PhoneHome sendPhoneHome(PhoneHome phoneHome) {
        String fromUser = format("%s %s", SyncariContext.getUser().getFirstName(),
            SyncariContext.getUser().getLastName());
        String email = SyncariContext.getUser().getEmail();
        String orgName = SyncariContext.getOrganziation().getName();
        String instanceName = SyncariContext.getInstance().getName();
        String environmentName = appConfig.getEnvironmentName();

        String subject = format(PHONE_HOME_SUBJECT, environmentName, orgName, instanceName, phoneHome.getErrorMessage());

        Map<String, Object> context = Map.ofEntries(
            Map.entry("fromUser", fromUser),
            Map.entry("userId", SyncariContext.getUser().getId()),
            Map.entry("browserInfo", phoneHome.getBrowserInfo().toString()),
            Map.entry("url", phoneHome.getUrl()),
            Map.entry("orgId", SyncariContext.getOrganziation().getId()),
            Map.entry("orgName", orgName),
            Map.entry("instanceName", instanceName),
            Map.entry("instanceId", SyncariContext.getInstance().getSyncariId()),
            Map.entry("host", appConfig.getSpectrumServerHost()),
            Map.entry("environment", environmentName),
            Map.entry("errorMessage", phoneHome.getErrorMessage()),
            Map.entry("errorStack", phoneHome.getErrorStack()),
            Map.entry("actions", phoneHome.getActions()),
            Map.entry("state", phoneHome.getState()),
            Map.entry("originalStack", phoneHome.getOriginalStack()),
            Map.entry("blackboxUrl", phoneHome.getBlackboxUrl())
        );

        String body = renderer.render(PHONE_HOME_TEMPLATE_PATH, context);
        if (environmentName.equalsIgnoreCase("dev")) {
            log.info(format("Subject: %s\nBody: %s", subject, body));
        } else {
            emailService.sendText(appConfig.getSupportEmail(), subject, body);
        }
        return phoneHome;
    }
}
