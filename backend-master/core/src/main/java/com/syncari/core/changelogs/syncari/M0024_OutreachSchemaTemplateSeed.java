package com.syncari.core.changelogs.syncari;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

@ChangeLog(order="0024")
public class M0024_OutreachSchemaTemplateSeed {

    @ChangeSet(order = "001", id = "outreachEntitySeed", author = "varsha")
    public void outreachEntitySeed(MongoTemplate db) {
        // Noop
    }
    
	@ChangeSet(order = "002", id = "outreachAccountAttributeSeed", author = "varsha")
	public void addAccountAttributes(MongoTemplate db) {
		// Noop
	}

	@ChangeSet(order = "003", id = "outreachProspectAttributeSeed", author = "varsha")
	public void outreachProspectAttributeSeed(MongoTemplate db) {
	    // Noop
	}

   @ChangeSet(order = "003", id = "outreachOpptyAttributeSeed", author = "varsha")
    public void outreachOpptyAttributeSeed(MongoTemplate db) {
        // Noop
    }

   @ChangeSet(order = "004", id = "outreachTaskAttributeSeed", author = "varsha")
   public void outreachTaskAttributeSeed(MongoTemplate db) {
       // Noop
   }

}
