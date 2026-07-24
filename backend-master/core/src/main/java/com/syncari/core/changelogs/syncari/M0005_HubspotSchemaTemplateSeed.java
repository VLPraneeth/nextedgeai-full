package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order="0005")
public class M0005_HubspotSchemaTemplateSeed {

	@ChangeSet(order = "001", id = "companyAttributeSeed", author = "varsha")
	public void addAccountAttributes(MongoTemplate db) {
	    // Noop
	}

	@ChangeSet(order = "002", id = "contactAttributeSeed", author = "varsha")
	public void addContactAttributes(MongoTemplate db) {
	    // Noop
	}

   @ChangeSet(order = "003", id = "dealEntitySeed", author = "varsha")
    public void dealEntitySeed(MongoTemplate db) {
       // Noop
    }
	   
	@ChangeSet(order = "004", id = "dealAttributeSeed", author = "varsha")
	public void dealAttributeSeed(MongoTemplate db) {
	    // Noop
	}

	@ChangeSet(order = "005", id = "addAccountToDealAttributeSeed", author = "varsha")
    public void addAccountToDealAttributeSeed(MongoTemplate db) {
       // Noop
    }

}
