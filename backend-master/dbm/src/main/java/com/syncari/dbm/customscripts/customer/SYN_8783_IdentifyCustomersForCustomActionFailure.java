package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_8783_IdentifyCustomersForCustomActionFailure {

    @ChangeSet(order = "001", id = "identifyCustomersForCustomActionFailure", author = "Santosh", runAlways = true)
    public void identifyCustomersForCustomActionFailure(MongoTemplate db){
        var mappingGraph = db.getCollection("mappingGraph");
        var mappingNode = db.getCollection("mappingNode");
        var syncariId = MigrationContext.getSyncariId();
        var orgRepo = MigrationContext.getOrganizationRepo();
        var graphs = mappingGraph.find(eq("draftStatus", "APPROVED")).into(new ArrayList<>());

        graphs.forEach(graph -> {
            var graphId = graph.getObjectId("_id").toHexString();
            var entityName = (null == graph.getString("apiName")) ? graph.getString("name") : graph.getString("apiName");

            var lookupDocuments = mappingNode.find(and(eq("mappingGraphId",graphId)
                    ,eq("configuration.type","CUSTOM")))
                    .into(new ArrayList<>());

            lookupDocuments.forEach(customAction -> {
                Optional<Organization> org = orgRepo.findBySyncariId(syncariId);
                String instanceName = org.isPresent() ? org.get().getName() : "";
                var scope = customAction.get("scope");
                scope = ObjectUtils.isEmpty(scope) ? "" : scope;
                log.info("For InstanceId  "+syncariId +
                        " Having Instance Name "+instanceName +
                        " with Name "+entityName+" having scope "+scope.toString()+" has customAction defined with name "+customAction.get("name"));
            });


        });
    }
}
