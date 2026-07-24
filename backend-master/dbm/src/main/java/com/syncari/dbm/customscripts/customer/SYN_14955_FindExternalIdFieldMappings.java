package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.ExternalIdType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SYN_14955_FindExternalIdFieldMappings {

    @ChangeSet(order = "001", id = "findExternalIdFieldMappings", author = "abhinav", runAlways = true)
    public void findExternalIdFieldMappings(MongoTemplate template) {

        SchemaService schemaService = MigrationContext.getSchemaService();
        MappingGraphService graphService = MigrationContext.getMappingGraphService();

        List<EntityDefinition> syncariEntities = schemaService.getSyncariEntities();

        syncariEntities.forEach(entity -> {

            List<AttributeDefinition> externalIdFields = schemaService.getAttributesByEntityId(entity.getId()).stream()
                    .filter(a -> ExternalIdType.VALUE.equals(a.getDataType()))
                    .collect(Collectors.toList());

            externalIdFields.forEach(a -> {
                graphService.retrieveApprovedAttributeGraph(a.getId()).ifPresent(g -> {
                    var coreNode = g.getCoreNode();
                    var inboundEdges = g.getInboundEdges(coreNode);
                    if(!inboundEdges.isEmpty()){
                        log.info("Instance: {} -> ExternalId field: {} is mapped with source(s) in FP {} of entity {}",
                                SyncariContext.getSyncariId(), a.getApiName(), a.getDisplayName(), entity.getApiName());
                    }
                });
            });
        });

    }
}
