package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0075")
public class M0075_CreateErrorCatalogMetadaSeed {

	@ChangeSet(order = "001", id = "ErrorCatalogMetadataSeed", author = "sibin")
	public void errorCatalogMetadataSeed(MongoTemplate template) {
		MongoCollection<Document> catalog = template.getCollection("errorCatalog");

		catalog.insertOne(new Document("category", ErrorCategory.PIPELINE.name())
				.append("title", "Pipeline")
				.append("helpText", "Errors that prevent pipelines from running successfully")
				.append("priority", ErrorPriority.P1.name())
				.append("seeded", true)
				.append("active", true));
		
		catalog.insertOne(new Document("category", ErrorCategory.SYNAPSE.name())
				.append("title", "Synapse")
				.append("helpText", "Errors that prevent synapses from running successfully")
				.append("priority", ErrorPriority.P1.name())
				.append("seeded", true)
				.append("active", true));
		catalog.insertOne(new Document("category", ErrorCategory.SYNC.name())
				.append("title", "Sync")
				.append("helpText", "Errors that prevent data sync from running successfully")
				.append("priority", ErrorPriority.P1.name())
				.append("seeded", true)
				.append("active", true));
	}
}
