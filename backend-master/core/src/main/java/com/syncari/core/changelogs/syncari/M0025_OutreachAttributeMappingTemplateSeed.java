package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0025")
public class M0025_OutreachAttributeMappingTemplateSeed {

    @ChangeSet(order = "001", id = "addMapping", author = "varsha")
    public void addOutreachEntityMapping(MongoTemplate db) {
        // Noop
	}

}
