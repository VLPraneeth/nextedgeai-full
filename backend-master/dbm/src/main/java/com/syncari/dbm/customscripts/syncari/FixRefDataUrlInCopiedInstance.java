package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.model.AsyncJob;
import com.syncari.core.model.Event;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.util.Status;
import com.syncari.core.service.AsyncJobService;
import com.syncari.core.service.ReferenceDataService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FixRefDataUrlInCopiedInstance {

    @ChangeSet(order = "001", id = "fixRefDataUrlInCopiedInstance", author = "abhinav")
    public void fixRefDataUrlInCopiedInstance(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        AsyncJobService asyncJobService = MigrationContext.getAsyncJobService();
        UserService userService = MigrationContext.getUserService();
        SubscriptionService subscriptionService = MigrationContext.getSubscriptionService();
        ReferenceDataService refDataService = MigrationContext.getReferenceDataService();
        List<AsyncJob> allCopyInstanceJobs = asyncJobService.findByType(EventTypes.COPY_INSTANCE);

        allCopyInstanceJobs.forEach(job -> {
            if(Status.COMPLETED.equals(job.getStatus())) {
                Event event = job.getEvent();
                String fromInstance = event.getDetails().get("fromSyncariId").toString();
                String toInstance = event.getDetails().get("toSyncariId").toString();
                log.info("Found completed job to copy from {} to {}", fromInstance, toInstance);
                if (subscriptionService.isActiveInstance(toInstance)) {
                    Organization org = subscriptionService.getOrgBySyncariId(toInstance);
                    if(org.getInstance(toInstance).isPresent()){
                        log.info("Checking instance {}", toInstance);
                        Instance instance = org.getInstance(toInstance).get();
                        SyncariContext.runWithContext(org, instance, userService.getSystemUser(), () -> {
                            // get all ref data urls
                            List<ReferenceDataMeta> refMetas = refDataService.listMeta(0);
                            List<ReferenceDataMeta> toUpdate = new ArrayList<>();
                            refMetas.forEach(ref -> {
                                // check if location starts with from syncariId and update it to toSycnariId
                                if (ref.getSource().getLocation().startsWith(fromInstance)) {
                                    String updatedLocation = ref.getSource().getLocation().replaceFirst(fromInstance, toInstance);
                                    log.info("Changing location of refData {} from {} to {}", ref.getName(), ref.getSource().getLocation(), updatedLocation);
                                    ref.getSource().setLocation(updatedLocation);
                                    toUpdate.add(ref);
                                }
                            });

                            if (!dryRunMode) {
                                refDataService.updateMeta(toUpdate);
                            }
                        });

                    } else {
                        log.error("Instance with syncari id " + toInstance + " not found");
                    }
                } else {
                    log.error("Instance with syncari id " + toInstance + " not found");
                }
            }
        });

    }
}
