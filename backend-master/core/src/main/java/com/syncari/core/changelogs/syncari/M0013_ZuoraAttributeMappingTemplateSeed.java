package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0013")
public class M0013_ZuoraAttributeMappingTemplateSeed {

	@ChangeSet(order = "001", id = "addZuoraMapping", author = "varsha")
	public void addZuoraMapping(MongoTemplate db) {
	    // Noop
	}

}
