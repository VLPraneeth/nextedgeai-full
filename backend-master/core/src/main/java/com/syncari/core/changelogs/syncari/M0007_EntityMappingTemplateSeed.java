package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0007")
public class M0007_EntityMappingTemplateSeed {

	@ChangeSet(order = "001", id = "addEntityMapping", author = "varsha")
	public void addEntityMapping(MongoTemplate db) {
	    // Noop
	}
	
}
