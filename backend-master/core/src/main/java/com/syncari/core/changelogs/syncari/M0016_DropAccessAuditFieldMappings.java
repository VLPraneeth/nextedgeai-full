package com.syncari.core.changelogs.syncari;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

@ChangeLog(order = "0016")
public class M0016_DropAccessAuditFieldMappings {

	@ChangeSet(order = "001", id = "dropAccessAuditFieldMappings", author = "neelesh")
	public void dropAccessAuditFieldMappings(MongoTemplate db) {
		MongoCollection<Document> entityMappings = db.getCollection("attributeMappingTemplate");
		entityMappings.deleteMany(Filters.eq("externalAttributeName","LastViewedDate"));
		entityMappings.deleteMany(Filters.eq("externalAttributeName","LastReferencedDate"));
		entityMappings.deleteMany(Filters.eq("externalAttributeName","LastActivityDate"));
	}
}
