package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0018")
public class  M0018_FixConnectorMetadataSeed {

    @ChangeSet(order = "001", id = "fixConnectorMetadataSeed", author = "francis")
    public void fixConnectorMetadataSeed(MongoTemplate template) {
        // Noop
    }

    @ChangeSet(order = "002", id = "activateMarketoAndSetAuthType", author = "abhinav")
    public void activateMarketoAndSetAuthType(MongoTemplate template) {
        //Noop
    }
}
