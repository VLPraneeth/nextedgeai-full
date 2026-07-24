package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0023")
public class M0023_FixMarketoSeed {

    @ChangeSet(order = "001", id = "removeCompanyEntityMapping", author = "abhinav")
    public void removeCompanyEntityMapping(MongoTemplate db) {
        // Noop
    }

    @ChangeSet(order = "002", id = "addProgramMembershipReferenceFields", author = "abhinav")
    public void addProgramMembershipReferenceFields(MongoTemplate db) {
        // Noop
    }

}
