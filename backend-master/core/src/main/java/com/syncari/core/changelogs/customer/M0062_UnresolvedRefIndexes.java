package com.syncari.core.changelogs.customer;

import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;

@ChangeLog(order = "0062")
public class M0062_UnresolvedRefIndexes {

    @ChangeSet(order = "001", id = "unresolvedRefIndexes", author = "neelesh")
    public void unresolvedRefIndexes(MongoTemplate template) {
    	MongoUtils.createIndexes(template,"unresolvedReference", List.of(
                new Index("unresolved_ref_all_fld_idx",false,
                        "connectorId","externalRefEntityName","externalRefRecordId","syncariEntityDefId","syncariRecordId","syncariAttributeName")
        ));

    	MongoUtils.createIndexes(template,"unresolvedReference", List.of(
                new Index("unresolved_ref_entity_record_idx",false,
                        "syncariEntityDefId","syncariRecordId")
        ));
    }
    @ChangeSet(order = "002", id = "unresolvedRefIndexesForResolvedValue", author = "neelesh")
    public void unresolvedRefIndexesForResolvedValue(MongoTemplate template) {
    	MongoUtils.createIndexes(template,"unresolvedReference", List.of(
                new Index("unresolved_ref_resolved_value_idx",false,
                        "resolvedSyncariValue")
        ));
    }
}
