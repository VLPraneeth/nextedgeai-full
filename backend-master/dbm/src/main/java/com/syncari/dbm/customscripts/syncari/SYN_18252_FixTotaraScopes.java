package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;

@Slf4j
public class SYN_18252_FixTotaraScopes {

    @ChangeSet(order = "001", id = "fixTotaraScopes", author = "rohit", runAlways = true)
    public void fixTotaraScopes(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();
        Optional<Organization> organization = subscriptionService.getOrgByName("Totara Learning");
        List<String> scopes = getScopesList();
        organization.ifPresentOrElse(o -> {
            Map<String, OAuthConfig> configs = o.getOauthConfigs();
            OAuthConfig oAuthConfig = configs.get("hubspot");
            if (null != oAuthConfig){
                List<String> additionalScopes = oAuthConfig.getAdditionalScopes();
                log.info("additionalScopes is not empty, adding to scopes");
                if (!dryRunMode) {
                    Set<String> existingScopes = new HashSet<>(additionalScopes);
                    scopes.removeAll(existingScopes);
                    additionalScopes.addAll(scopes);
                    subscriptionService.updateOrg(o);
                    log.info("running not in dry run mode, scopes updated");
                }
            }else{
                log.info("OAuthConfig is null, Adding config for hubspot");
                if (!dryRunMode) {
                    oAuthConfig = new OAuthConfig("", "", ", ");
                    oAuthConfig.setAdditionalScopes(scopes);
                    configs.put("hubspot", oAuthConfig);
                    subscriptionService.updateOrg(o);
                    log.info("running not in dry run mode, scopes updated");
                }
            }
        },() -> {
            log.info("No Org present with the name");
        });
    }

    public static List<String> getScopesList() {
        List<String> scopes = new ArrayList<>();

        scopes.add("account-info.security.read");
        scopes.add("accounting");
        scopes.add("actions");
        scopes.add("analytics.behavioral_events.send");
        scopes.add("behavioral_events.event_definitions.read_write");
        scopes.add("business-intelligence");
        scopes.add("collector.graphql_query.execute");
        scopes.add("collector.graphql_schema.read");
        scopes.add("content");
        scopes.add("crm.dealsplits.read_write");
        scopes.add("crm.export");
        scopes.add("crm.import");
        scopes.add("crm.lists.read");
        scopes.add("crm.lists.write");
        scopes.add("crm.objects.appointments.read");
        scopes.add("crm.objects.appointments.sensitive.read");
        scopes.add("crm.objects.appointments.sensitive.write");
        scopes.add("crm.objects.appointments.write");
        scopes.add("crm.objects.carts.read");
        scopes.add("crm.objects.carts.write");
        scopes.add("crm.objects.commercepayments.read");
        scopes.add("crm.objects.companies.read");
        scopes.add("crm.objects.companies.sensitive.read");
        scopes.add("crm.objects.companies.sensitive.write");
        scopes.add("crm.objects.companies.write");
        scopes.add("crm.objects.contacts.read");
        scopes.add("crm.objects.contacts.sensitive.read");
        scopes.add("crm.objects.contacts.sensitive.write");
        scopes.add("crm.objects.contacts.write");
        scopes.add("crm.objects.courses.read");
        scopes.add("crm.objects.courses.write");
        scopes.add("crm.objects.custom.read");
        scopes.add("crm.objects.custom.sensitive.read");
        scopes.add("crm.objects.custom.sensitive.write");
        scopes.add("crm.objects.custom.write");
        scopes.add("crm.objects.deals.read");
        scopes.add("crm.objects.deals.sensitive.read");
        scopes.add("crm.objects.deals.sensitive.write");
        scopes.add("crm.objects.deals.write");
        scopes.add("crm.objects.feedback_submissions.read");
        scopes.add("crm.objects.goals.read");
        scopes.add("crm.objects.invoices.read");
        scopes.add("crm.objects.leads.read");
        scopes.add("crm.objects.leads.write");
        scopes.add("crm.objects.line_items.read");
        scopes.add("crm.objects.line_items.write");
        scopes.add("crm.objects.listings.read");
        scopes.add("crm.objects.listings.write");
        scopes.add("crm.objects.marketing_events.read");
        scopes.add("crm.objects.marketing_events.write");
        scopes.add("crm.objects.orders.read");
        scopes.add("crm.objects.orders.write");
        scopes.add("crm.objects.owners.read");
        scopes.add("crm.objects.partner-clients.read");
        scopes.add("crm.objects.partner-clients.write");
        scopes.add("crm.objects.quotes.read");
        scopes.add("crm.objects.quotes.write");
        scopes.add("crm.objects.services.read");
        scopes.add("crm.objects.services.write");
        scopes.add("crm.objects.subscriptions.read");
        scopes.add("crm.objects.subscriptions.write");
        scopes.add("crm.objects.users.read");
        scopes.add("crm.objects.users.write");
        scopes.add("crm.pipelines.orders.read");
        scopes.add("crm.pipelines.orders.write");
        scopes.add("crm.schemas.appointments.read");
        scopes.add("crm.schemas.appointments.write");
        scopes.add("crm.schemas.carts.read");
        scopes.add("crm.schemas.carts.write");
        scopes.add("crm.schemas.commercepayments.read");
        scopes.add("crm.schemas.companies.read");
        scopes.add("crm.schemas.companies.write");
        scopes.add("crm.schemas.contacts.read");
        scopes.add("crm.schemas.contacts.write");
        scopes.add("crm.schemas.courses.read");
        scopes.add("crm.schemas.courses.write");
        scopes.add("crm.schemas.custom.read");
        scopes.add("crm.schemas.custom.write");
        scopes.add("crm.schemas.deals.read");
        scopes.add("crm.schemas.deals.write");
        scopes.add("crm.schemas.invoices.read");
        scopes.add("crm.schemas.line_items.read");
        scopes.add("crm.schemas.listings.read");
        scopes.add("crm.schemas.listings.write");
        scopes.add("crm.schemas.orders.read");
        scopes.add("crm.schemas.orders.write");
        scopes.add("crm.schemas.quotes.read");
        scopes.add("crm.schemas.services.read");
        scopes.add("crm.schemas.services.write");
        scopes.add("crm.schemas.subscriptions.read");
        scopes.add("crm.schemas.subscriptions.write");
        scopes.add("ctas.read");
        scopes.add("e-commerce");
        scopes.add("external_integrations.forms.access");
        scopes.add("files");
        scopes.add("files.ui_hidden.read");
        scopes.add("forms");
        scopes.add("forms-uploaded-files");
        scopes.add("hubdb");
        scopes.add("integration-sync");
        scopes.add("marketing-email");
        scopes.add("media_bridge.read");
        scopes.add("media_bridge.write");
        scopes.add("oauth");
        scopes.add("sales-email-read");
        scopes.add("scheduler.meetings.meeting-link.read");
        scopes.add("settings.billing.write");
        scopes.add("settings.currencies.read");
        scopes.add("settings.currencies.write");
        scopes.add("settings.security.security_health.read");
        scopes.add("settings.users.read");
        scopes.add("settings.users.teams.read");
        scopes.add("settings.users.teams.write");
        scopes.add("settings.users.write");
        scopes.add("social");
        scopes.add("tickets");
        scopes.add("tickets.sensitive");
        scopes.add("timeline");
        scopes.add("transactional-email");

        return scopes;
    }
}