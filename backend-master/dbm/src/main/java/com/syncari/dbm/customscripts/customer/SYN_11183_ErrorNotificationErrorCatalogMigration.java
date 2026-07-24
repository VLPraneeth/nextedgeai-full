package com.syncari.dbm.customscripts.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.model.ErrorCategory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_11183_ErrorNotificationErrorCatalogMigration {
	private int counter = 1;
	@ChangeSet(order = "001", id = "UpdateErrorCatalogMetadaSeed", author = "sibin")
	public void errorCatalogMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> catalog = template.getCollection("errorCatalog");
		catalog.findOneAndUpdate(Filters.eq("category", ErrorCategory.PIPELINE.name()), new Document("$set", new Document("title", "Pipeline")));
		catalog.findOneAndUpdate(Filters.eq("category", ErrorCategory.SYNAPSE.name()), new Document("$set", new Document("title", "Synapse")));
		catalog.findOneAndUpdate(Filters.eq("category", ErrorCategory.SYNC.name()), new Document("$set", new Document("title", "Sync")));
	}
}
