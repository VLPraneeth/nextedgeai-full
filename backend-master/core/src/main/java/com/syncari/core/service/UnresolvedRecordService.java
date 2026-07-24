package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.UnresolvedRecord;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.UnresolvedRecordRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UnresolvedRecordService {
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    SchemaService schemaService;
    @Autowired
    UnresolvedRecordRepo unresolvedDestinationEntityRepo;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    AppConfig appConfig;

    public Iterable<EntityData> getUnresolvedEntities(String syncariEntityDefinitionId, String externalEntityDefinitionId) {
        EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityDefinitionId);
        List<UnresolvedRecord> unresolved = unresolvedDestinationEntityRepo.findUnresolved(externalEntityDefinitionId);
        markPermanentlyUnresolved(unresolved);
        return entityRepo.findByIds(syncariEntity.getApiName(), unresolved.stream().map(u -> u.getSyncariId()).collect(Collectors.toSet()));
    }

    public List<UnresolvedRecord> getUnresolvedRecords(String externalEntityDefinitionId) {
        return  unresolvedDestinationEntityRepo.findUnresolved(externalEntityDefinitionId);
    }

    public void delete(List<UnresolvedRecord> unresolvedEntities) {
        unresolvedDestinationEntityRepo.delete(unresolvedEntities);
    }

    public void upsert(List<UnresolvedRecord> unresolvedEntities) {
        unresolvedDestinationEntityRepo.upsert(unresolvedEntities);
    }

    protected void markPermanentlyUnresolved(List<UnresolvedRecord> unresolvedEntities) {
        List<UnresolvedRecord> permanentError = unresolvedEntities.stream().filter(u -> u.exceedsErrorThreshold() && u.hasUnresolvedFields()).collect(Collectors.toList());
        // We simply purge the permanently unresolved records to avoid forever growth of this collection.
        unresolvedDestinationEntityRepo.delete(permanentError);
        logWarning(permanentError, false);
    }

    private void logWarning(List<UnresolvedRecord> unresolvedEntities, boolean sendEmail) {
        Optional<String> body = unresolvedEntities.stream().filter(u -> u.exceedsWarningThreshold() && u.hasUnresolvedFields()).map(u ->
                String.format("SyncariId : %s, Syncari EntityDefinition Id: %s, External Entity Definition Id: %s, Unresolved Fields: %s, Time Elapsed in seconds : %s",
                        u.getSyncariId(), u.getSyncariEntityDefinitionId(), u.getExternalEntityDefinitionId(), u.getUnresolvedFieldIds(), u.elapsedTimeInMillis() / 1000
                )
        ).reduce((a, b) -> String.format("%s\n%s", a, b));

        body.ifPresent(b -> {
            log.error("Unresolved References {} ", b);
            if (sendEmail) {
                String instance = SyncariContext.getInstance().getName();
                String instanceId = SyncariContext.getInstance().getSyncariId();
                String org = SyncariContext.getOrganziation().getName();
                String subject = String.format("ERROR:[%s:%s:%s] Unresolved References", org, instance, instanceId);
                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, b);
            }
        });
    }

}
