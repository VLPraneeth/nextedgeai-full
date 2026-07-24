package com.syncari.core.changelogs.syncari;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

@ChangeLog(order = "0017")
public class M0017_AddBootstrapableFlagForRedshift {

	@ChangeSet(order = "001", id = "addBootstrapableFlagForRedshift", author = "varsha")
	public void dropAccessAuditFieldMappings(MongoTemplate db) {
		MongoCollection<Document> metas = db.getCollection("connectorMetadata");
		metas.findOneAndUpdate(Filters.eq("name","redshift"), new Document("$set",new Document("bootstrappable", true)));
	}
}
