package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order = "0010")
public class M0010_HubspotAttributeMappingTemplateSeed {

	@ChangeSet(order = "001", id = "addHubspotMapping", author = "varsha")
	public void addHubspotMapping(MongoTemplate db) {
	    // Noop
	}

    @ChangeSet(order = "002", id = "addHubspotDealMapping", author = "varsha")
    public void addHubspotDealMapping(MongoTemplate db) {
        // Noop
    }
    
    @ChangeSet(order = "003", id = "addHubspotDealAccountIdMapping", author = "varsha")
    public void addHubspotDealAccountIdMapping(MongoTemplate db) {
        // Noop
    }

}
