package com.syncari.api.alerts;

import com.syncari.core.SyncariContext;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.insights.dataset.DatasetExport;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.service.DatasetExportService;
import com.syncari.core.service.ErrorNotificationService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DatasetExportFileCleanScheduler {

    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    private UserService userService;
    @Autowired
    private LockRepo lockRepo;
    @Autowired
    private DatasetExportService datasetExportService;
    @Autowired
    private GCSFileManager gcsFileManager;

    static String lockOwner;
    static {
        lockOwner = UUID.randomUUID().toString();
    }

    // Scheduled to run every day at 11pm
    @Scheduled(cron = "0 0 4 * * ?")
    public void clearDatasetExportFiles() {
        log.info("Running DatasetExportFileCleaner");
        var user = userService.getSystemUser();
        subscriptionService.getAllOrg().forEach(org -> {
            org.getActiveInstances().forEach(ins -> {
                SyncariContext.runWithContext(org, ins, user, () -> {
                    var lockId = "datasetexportfilecleaner"+ins.getSyncariId();
                    try {
                        var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(15));
                        if(locked.isPresent()) {
                            log.debug("Acquired lock {}", lockId);
                            List<DatasetExport> exportJobs = datasetExportService.findAll();
                            List<DatasetExport> expiredJobs = exportJobs.stream().filter(e -> Instant.now().minusMillis(e.getExpiredTime().toEpochMilli()).toEpochMilli() > 0).collect(Collectors.toList());
                            expiredJobs.forEach(job -> {
                                String filePath = job.getExportedFileLink();
                                if (StringUtils.isNotEmpty(filePath)){
                                    gcsFileManager.delete(filePath);
                                    log.info("Expired file {} deleted for datasetId {}", filePath, job.getDatasetId());
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.error("Exception occured while cleaing DatasetExportFiles for datasetId", org.getId(),
                                ins.getSyncariId(), e);
                    } finally {
                        lockRepo.unlock(lockId,  lockOwner);
                        log.info("No need to release lock for instance {} as db would have been deleted", lockId);
                    }
                });
            });
        });
    }
}
