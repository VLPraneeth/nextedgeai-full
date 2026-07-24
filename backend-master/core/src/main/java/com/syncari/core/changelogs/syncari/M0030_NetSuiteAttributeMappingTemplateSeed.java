package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0030")
public class M0030_NetSuiteAttributeMappingTemplateSeed {

    @ChangeSet(order = "001", id = "netSuiteAccountAttributeMappings", author = "francis")
    public void netSuiteAccountAttributeMappings(MongoTemplate db) {
        // Noop
    }

    @ChangeSet(order = "002", id = "netSuiteOpportunityAttributeMappings", author = "neelesh")
    public void netSuiteOpportunityAttributeMappings(MongoTemplate db) {
        // Noop
    }

    @ChangeSet(order = "003", id = "netSuiteContactAttributeMappings", author = "francis")
    public void netSuiteContactAttributeMappings(MongoTemplate db) {
        // Noop
    }

    @ChangeSet(order = "003", id = "netSuiteCustomerAttributeMappings", author = "francis")
    public void netSuiteCustomerAttributeMappings(MongoTemplate db) {
        // Noop
    }

}
