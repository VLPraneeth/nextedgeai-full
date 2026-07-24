package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;


@Slf4j
public class SYN_17026_Instances_Record_Count {

    @ChangeSet(order = "001", id = "getInstancesRecordCount", author = "varsha")
    public void getInstancesRecordCount(MongoTemplate template) {
        String orgId = System.getProperty("orgId");
        Organization organization = MigrationContext.getOrganizationRepo().findById(orgId).get();
        AtomicLong counter = new AtomicLong(0);

        List<Instance> instances = organization.getInstances();
        for (Instance instance: instances) {
            SyncariContext.runWithContext(organization, instance, MigrationContext.getUserService().getSystemUser(), () -> {
                List<String> entities = MigrationContext.getSchemaService().getSyncariSchema().getEntities().stream().map(e -> e.getApiName()).collect(Collectors.toList());
                for (String entity: entities) {
                    counter.getAndAdd(MigrationContext.getRepoService().getCount(entity));
                }
            });
            log.info("Total instance count for {}, {} : {}", instance.getSyncariId(), instance.getName(), counter.get());
        }
        log.info("Total org count for {} : {}", orgId, counter.get());
    }

}
