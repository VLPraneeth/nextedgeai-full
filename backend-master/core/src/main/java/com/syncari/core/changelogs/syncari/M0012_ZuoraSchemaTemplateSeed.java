package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order="0012")
public class M0012_ZuoraSchemaTemplateSeed {

	@ChangeSet(order = "001", id = "zuoraAccountAttributeSeed", author = "varsha")
	public void addAccountAttributes(MongoTemplate db) {
    	// Noop
	}

	@ChangeSet(order = "002", id = "zuoraContactAttributeSeed", author = "varsha")
	public void contactAttributeSeed(MongoTemplate db) {
	    // Noop
	}

	@ChangeSet(order = "003", id = "productAttributeSeed", author = "varsha")
	public void addUserAttributes(MongoTemplate db) {
	    // Noop
	}
	
	@ChangeSet(order = "004", id = "paymentAttributeSeed", author = "varsha")
	public void addPaymentAttributes(MongoTemplate db) {
	    // Noop
	}

}
