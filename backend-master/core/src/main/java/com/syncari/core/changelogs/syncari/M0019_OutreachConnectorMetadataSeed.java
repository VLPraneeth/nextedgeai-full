package com.syncari.core.changelogs.syncari;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;

@ChangeLog(order = "0019")
public class M0019_OutreachConnectorMetadataSeed {

	@ChangeSet(order = "001", id = "outreachConnectorMetadataSeed", author = "varsha")
	public void outreachConnectorMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
        
		meta.insertOne(new Document("name", Constants.OUTREACH)
                .append("defaultApiLimit", 1000));
	}

}
