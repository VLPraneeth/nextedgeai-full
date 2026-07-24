package com.syncari.api.alerts;

import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.model.GhostAccessAudit;
import com.syncari.core.model.Role;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.GhostAccessAuditRepo;
import com.syncari.core.service.UserService;
import com.syncari.core.service.authz.AuthzService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;


@Component
@Slf4j
public class GhostAccessManager {
    @Autowired
    GhostAccessAuditRepo repo;
    @Autowired
    UserService userService;
    @Autowired
    AuthzService authzService;
    @Autowired
    SyncariContextHandler syncariContextHandler;

    // Run every 5 mins
    @Scheduled(cron = "0 0/5 * * * *")
    public void check() {
        log.debug("Running ghost manager");
        List<GhostAccessAudit> findByStatus = repo.findByStatus(Status.ACTIVE.name());

        findByStatus.forEach(req -> {
        	if(req.getExpireAt().isBefore(Instant.now())) {
                try {
                    syncariContextHandler.setContext(req.getSyncariId());
                    SyncariContext.setUser(userService.getUserById(req.getApproverId()));
                    userService.removeInstanceFromUser(req.getSyncariId(), Optional.of(req.getRequesterId()));
                    Optional<Role> role = authzService.getRoleByName(req.getRoleName());
                    role.ifPresentOrElse(r -> {
                        userService.removeRoleFromUser(req.getRequesterId(), r.getId());
                        req.setStatus(Status.COMPLETED);
                        req.setAuditTrail(new StringBuilder(req.getAuditTrail())
                                .append(format(i18n("ghost_user_revoked"), "CRONJOB",
                                        Instant.now())).toString());
                        repo.save(req);
                        log.info("Successfully removed ghost role from user {} for instance {}", req.getRequesterId(), req.getSyncariId());
                    },() -> {
                        log.error("Role {} in instance {} is not present, requested for user {}",req.getRoleName(), req.getSyncariId(),req.getRequesterId());
                        req.setStatus(Status.ERROR);
                        req.setAuditTrail(new StringBuilder(req.getAuditTrail())
                                .append(format(i18n("ghost_user_revoked"), "CRONJOB",
                                        Instant.now())).toString());
                        repo.save(req);
                    });

                } catch(Exception e){
                    log.error("Removing ghost access from user {} for instance {} failed", req.getRequesterId(), req.getSyncariId());
                    log.error(e.getMessage(), e);
                    req.setStatus(Status.ERROR);
                    req.setAuditTrail(new StringBuilder(req.getAuditTrail())
                            .append(format(i18n("ghost_user_revoked"), "CRONJOB",
                                    Instant.now())).toString());
                    repo.save(req);
                } finally {
                    SyncariContext.resetAll();
                    MigrationContext.clear();
                }
        	} else {
                log.debug("No expired ghost requests found");
            }
        });
    }
}