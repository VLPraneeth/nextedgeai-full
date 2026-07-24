package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Slf4j
public class SYN_14770_FixImpartnerScope {

    @ChangeSet(order = "001", id = "updateImartnerScopes", author = "rohit", runAlways = true)
    public void updateImartnerScopes(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();
        Optional<Organization> organization = subscriptionService.getOrgByName("Impartner Customers");
        organization.ifPresentOrElse(o -> {
            Map<String, OAuthConfig> configs = o.getOauthConfigs();
            OAuthConfig oAuthConfig = configs.get("hubspot");
            if (null != oAuthConfig){
                List<String> additionalScopes = oAuthConfig.getAdditionalScopes();
                if (CollectionUtils.isNotEmpty(additionalScopes)){
                    log.info("additionalScopes is not empty, adding e-commerce to scopes");
                    if (!dryRunMode){
                        additionalScopes.add("e-commerce");
                        subscriptionService.updateOrg(o);
                        log.info("running not in dry run mode, scopes updated");
                    }else{
                        log.info("running in dry run mode, not updating");
                    }
                }else{
                    log.info("additionalScopes is empty, nothing is there to be added");
                }
            }else{
                log.info("OAuthConfig is null, nothing is there to be updated");
            }
        },() -> {
            log.info("No Org present with the name");
        });
    }
}
