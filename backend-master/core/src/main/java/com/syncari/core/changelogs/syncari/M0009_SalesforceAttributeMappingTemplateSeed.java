package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0009")
public class M0009_SalesforceAttributeMappingTemplateSeed {

	@ChangeSet(order = "001", id = "addSalesforceMapping", author = "varsha")
	public void addSalesforceMapping(MongoTemplate db) {
	    // Noop
	}

}
