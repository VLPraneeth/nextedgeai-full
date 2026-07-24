package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0028")
public class M0028_NetSuiteEntityMappingTemplateSeed {
    @ChangeSet(order = "001", id = "netsuiteEntityMappingSeed", author = "francis")
    public void addNetSuiteEntities(MongoTemplate db) {
        // Noop
    }

    @ChangeSet(order = "002", id = "netSuiteEntityMappingSeed", author = "francis")
    public void addNetSuiteEntityMappingSeed(MongoTemplate db) {
        // Noop
    }



}
