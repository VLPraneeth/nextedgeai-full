package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0029")
public class M0029_NetSuiteSchemaTemplateSeed {

    // https://www.netsuite.com/help/helpcenter/en_US/srbrowser/Browser2019_2/schema/record/account.html
    @ChangeSet(order = "001", id = "netsuiteAccountAttributeSeed", author = "francis")
    public void addNetSuiteAccountAttributes(MongoTemplate db) {
        // Noop
    }

    //https://system.netsuite.com/help/helpcenter/en_US/APIs/REST_API_Browser/record/v1/2020.1/index.html#/definitions/opportunity
    @ChangeSet(order = "002", id = "netsuiteOpportunityAttributeSeed", author = "francis")
    public void addNetSuiteOpportunityAttributes(MongoTemplate db) {
        // Noop
    }

    // https://www.netsuite.com/help/helpcenter/en_US/srbrowser/Browser2019_2/schema/record/contact.html
    @ChangeSet(order = "003", id = "netsuiteContactAttributeSeed", author = "francis")
    public void addNetSuiteContactAttributes(MongoTemplate db) {
        // Noop
    }

}
