package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order="0008")
public class M0008_ZendeskSchemaTemplateSeed {

	@ChangeSet(order = "001", id = "orgAttributeSeed", author = "varsha")
	public void addAccountAttributes(MongoTemplate db) {
	    // Noop
	}

	@ChangeSet(order = "002", id = "ticketAttributeSeed", author = "varsha")
	public void addticketAttributes(MongoTemplate db) {
	    // Noop
	}

	@ChangeSet(order = "003", id = "userAttributeSeed", author = "varsha")
	public void addUserAttributes(MongoTemplate db) {
	    // Noop
	}

   @ChangeSet(order = "004", id = "updateTicketAttributeSeed", author = "varsha")
    public void updateTicketAttributeSeed(MongoTemplate db) {
       // Noop
    }

}
