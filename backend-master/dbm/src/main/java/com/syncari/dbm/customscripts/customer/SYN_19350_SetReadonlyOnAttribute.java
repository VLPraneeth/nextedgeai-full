package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_19350_SetReadonlyOnAttribute {

    @ChangeSet(order = "001", id = "setReadonlyOnAttribute", author = "varsha", runAlways = true)
    public void setReadonlyOnAttribute(MongoTemplate db) {

        String attributeId = System.getProperty("attributeId");
        boolean value = Boolean.parseBoolean(System.getProperty("value"));
        SchemaService schemaService = MigrationContext.getSchemaService();
        AttributeDefinition attribute = schemaService.getAttribute(attributeId);
        attribute.setUpdatable(value);
        schemaService.upsertField(attribute);
    }
}
