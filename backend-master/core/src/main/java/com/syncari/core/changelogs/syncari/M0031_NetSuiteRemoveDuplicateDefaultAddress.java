package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0031")
public class M0031_NetSuiteRemoveDuplicateDefaultAddress {
    @ChangeSet(order = "001", id = "netsuiteContactRemoveDuplicateDefaultAddress", author = "francis")
    public void removeDuplicateDefaultAddress(MongoTemplate db) {
        //Noop
    }

}
