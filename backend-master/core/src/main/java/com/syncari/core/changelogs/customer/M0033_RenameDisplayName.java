package com.syncari.core.changelogs.customer;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0033")
public class M0033_RenameDisplayName {

    @ChangeSet(order = "001", id = "renameDisplayName", author = "neelesh")
    public void renameDisplayName(MongoTemplate db) {
        // Noop
    }
}
