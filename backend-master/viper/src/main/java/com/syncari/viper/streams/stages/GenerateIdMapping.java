package com.syncari.viper.streams.stages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.event.EventTypes;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.UnresolvedReference;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.viper.ViperContext;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GenerateIdMapping {
    @Autowired
    IdMappingRepo idMappingRepo;
    @Autowired
    UnresolvedReferenceRepo unresolvedReferenceRepo;

    public GraphContext execute(ViperContext context, GraphContext graphContext) {
        var entityBatch = graphContext.getCurrentBatch();
        var counter = new AtomicInteger();
        entityBatch.newRecordsIterator().forEachRemaining(recordPage -> {

            counter.addAndGet(recordPage.getContent().size());
            List<IdMapping> mappings = new ArrayList<>();
            List<UnresolvedReference> lookupList = new ArrayList<>();
            recordPage.getContent().stream().forEach(record -> {
                String connectorId = entityBatch.lookupConnectorIdByBatchId(record.getStagedBatchId()).getConnectorId();
                mappings.add(new IdMapping().addMapping(connectorId, record.getEntityData().getId(),record.getExternalEntityDefinitionId())
                        .setSyncariId(record.getSyncariId()).setEntityName(entityBatch.getSyncariEntityName()));
                UnresolvedReference unresolvedReference = new UnresolvedReference(connectorId,
                        record.getEntityData().getName(), record.getEntityData().getId());
                unresolvedReference.setResolvedSyncariValue(record.getSyncariId());
                lookupList.add(unresolvedReference);
            });
            idMappingRepo.saveAll(mappings);
            // update unresolved records for this batch with the syncari ids
            unresolvedReferenceRepo.updateSyncariValues(lookupList);
        });
        log.info("{} stage:GenerateIdMapping, component:viper ,newRecords:{}", EventTypes.PIPELINE_RUNTIME, counter.toString());
        return graphContext;
    }

}
