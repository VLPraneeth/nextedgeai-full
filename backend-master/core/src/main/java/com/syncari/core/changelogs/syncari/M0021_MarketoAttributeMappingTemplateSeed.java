package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0020")
public class M0021_MarketoAttributeMappingTemplateSeed {

    @ChangeSet(order = "001", id = "marketoLeadAttributeMappings", author = "abhinav")
    public void marketoLeadAttributeMappings(MongoTemplate db) {
        // Noop
    }

}
