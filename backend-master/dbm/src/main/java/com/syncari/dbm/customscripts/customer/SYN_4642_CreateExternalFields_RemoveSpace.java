package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.datatype.ExternalIdType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SYN_4642_CreateExternalFields_RemoveSpace {

    @ChangeSet(order = "001", id = "createExternalIdFieldsRemoveSpace", author = "varsha", runAlways = true)
    public void createExternalIdFieldsRemoveSpace(MongoTemplate db) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        AttributeRepo attrRepo = MigrationContext.getAttributeRepo();
        SchemaService schemaService = MigrationContext.getSchemaService();

        List<AttributeDefinition> all = attrRepo.findAllByDataType(ExternalIdType.NAME);
        all.forEach(a -> {
            if(a.getApiName().contains(" ")) {
                String newName = schemaService.toApiName(a.getApiName());
                String newDsName = schemaService.toApiName(a.getDataStoreName());
                log.info("Renaming {} to {}", a.getApiName(), newName);
                a.setApiName(newName);
                a.setDataStoreName(newDsName);
                attrRepo.save(a);
            }
        });

    }

}
