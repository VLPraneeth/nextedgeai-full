package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0019")
public class M0020_MarketoSchemaTemplateSeed {

    @ChangeSet(order = "001", id = "marketoLeadAttributeSeed", author = "abhinav")
    public void addMarketoLeadAttributes(MongoTemplate db) {
        //Noop
    }

    @ChangeSet(order = "002", id = "marketoCompanyAttributeSeed", author = "abhinav")
    public void addMarketoAccountAttributes(MongoTemplate db) {
      //Noop
    }

    @ChangeSet(order = "003", id = "marketoProgramAttributeSeed", author = "abhinav")
    public void addMarketoProgramAttributes(MongoTemplate db) {
      //Noop
    }

    @ChangeSet(order = "004", id = "marketoOpportunityAttributeSeed", author = "abhinav")
    public void addMarketoOpportunityAttributes(MongoTemplate db) {
      //Noop
    }

    @ChangeSet(order = "005", id = "marketoActivityAttributeSeed", author = "abhinav")
    public void addMarketoActivityAttributes(MongoTemplate db) {
      //Noop
    }

    @ChangeSet(order = "006", id = "marketoProgramMembershipAttributeSeed", author = "abhinav")
    public void addMarketoProgramMembershipAttributes(MongoTemplate db) {
      //Noop
    }

    @ChangeSet(order = "007", id = "fixAttributeFlagsInSeed", author = "abhinav")
    public void fixAttributeFlagsInSeed(MongoTemplate db) {
      //Noop
    }

}
