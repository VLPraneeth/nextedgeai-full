package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class UpdateInstanceDisplayName {

    private final SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();

    @ChangeSet(order = "001", id = "updateInstanceDisplayName", author = "rohit",runAlways = true)
    public void updateInstanceDisplayName(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String instanceId = System.getProperty("instanceId");
        String displayName = System.getProperty("displayName");
        boolean isReplaceChar = Boolean.valueOf(System.getProperty("isReplaceChar"));
        if (isReplaceChar){
            // this flag assumes that & is part of the displayname and needs to be replaced with |
            displayName = displayName.replace("&", "|");

        }
        assert (null != instanceId);
        log.info("display name to be changed for syncariId {}", instanceId);
        Organization organization = subscriptionService.getOrgBySyncariId(instanceId);
        if (null != organization){
            log.info("Updating org with instance of syncariId {}", instanceId);
            Optional<Instance> instanceToUpdate = organization.getInstance(instanceId);
            assert instanceToUpdate.isPresent();
            if (null != instanceToUpdate.get()){
                Instance instance = instanceToUpdate.get();
                if (!dryRunMode){
                    instance.setDisplayName(displayName);
                    Organization orgUpdated = subscriptionService.updateOrg(organization);
                    log.info("Updated result of Org is {}", orgUpdated);
                }else{
                    log.info("Instance with existing display name {} to be updated to displayName {}", instance.getDisplayName(), displayName);
                }

            }
        }else{
            log.error("Org with syncariId {} instance does not exists", instanceId);
        }
    }
}
