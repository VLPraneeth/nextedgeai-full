package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SYN_16615_UpdateReferenceField {

    @ChangeSet(order = "001", id = "updateReferenceField", author = "rohit", runAlways = true)
    public void updateReferenceField(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        ConnectorService connectorService = MigrationContext.getConnectorService();
        EntityDefinitionRepo entityDefinitionRepo = MigrationContext.getEntityDefinitionRepo();
        SchemaService schemaService = MigrationContext.getSchemaService();
        ConnectorRepo connectorRepo = MigrationContext.getConnectorRepo();
        AttributeRepo attributeRepo = MigrationContext.getAttributeRepo();
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<Connector> sourceFlowConnector = connectorRepo.findByName("SourceFlow");
        if (null != syncariConn){

            Optional<EntityDefinition> entityDefinition =  schemaService.findEntity(syncariConn.getId(),"job_bullhorn");
            Optional<EntityDefinition> sourceflowEntityDefinition =  schemaService.findEntity(sourceFlowConnector.get().getId(), "job");
            log.info("Syncari connector is present");
            entityDefinition.ifPresentOrElse(e -> {
                log.info("Entity definition is present");
                List<AttributeDefinition> attributes = e.getAttributes();
                List<AttributeDefinition> filterattribs = attributes.stream().filter(a -> a.getApiName().equalsIgnoreCase("syncari_sourceflow_SourceFlow_job_id")).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(filterattribs)){
                    AttributeDefinition attributeDef = filterattribs.get(0);
                    sourceflowEntityDefinition.ifPresentOrElse(sfe -> {
                        log.info("Source flow Entity definition is present");
                        attributeDef.setReferenceTo(sfe.getId());
                        List<AttributeDefinition> sfeAttributes =  sfe.getAttributes();
                        List<AttributeDefinition> sfeFilterattribs = sfeAttributes.stream().filter(a -> a.getApiName().equalsIgnoreCase("id")).collect(Collectors.toList());
                        if (CollectionUtils.isNotEmpty(sfeFilterattribs)){
                            String id = sfeFilterattribs.get(0).getId();
                            attributeDef.setReferenceTargetField(id);
                            if (!dryRunMode){
                                attributeRepo.save(attributeDef);
                            }else{
                                log.info("Running in dry run mode , Not Saving attribute id {} with right reference entity id {} and field id {}",
                                        attributeDef.getId(),sfe.getId(), id);
                            }
                        }else{
                            log.info("Sourceflow id attribute is not present");
                        }
                        },() -> log.info("Sourceflow connector entity job is not present"));

                }else{
                    log.info("Could not find the external id field in attributes of job_bullhorn");
                }

            },() -> log.info("Entity with api name job_bullhorn is not present"));
        }
    }
}
