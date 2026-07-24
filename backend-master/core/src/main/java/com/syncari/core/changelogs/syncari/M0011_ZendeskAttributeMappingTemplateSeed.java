package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0011")
public class M0011_ZendeskAttributeMappingTemplateSeed {

	@ChangeSet(order = "001", id = "addZendeskMapping", author = "varsha")
	public void addZendeskMapping(MongoTemplate db) {
	    // Noop
	}

   @ChangeSet(order = "002", id = "updateZendeskMapping", author = "varsha")
    public void updateZendeskMapping(MongoTemplate db) {
       // Noop
    }

}
