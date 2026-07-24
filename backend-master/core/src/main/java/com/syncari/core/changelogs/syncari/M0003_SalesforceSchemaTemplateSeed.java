package com.syncari.core.changelogs.syncari;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;

@ChangeLog(order="0003")
public class M0003_SalesforceSchemaTemplateSeed {
	@ChangeSet(order = "001", id = "entitySeed", author = "varsha")
	public void addEntities(MongoTemplate db) {
		MongoCollection<Document> entities = db.getCollection("entityDefinition");
		Map<String, List<String>> entityMap = Map.of(
				Constants.SYNCARI, Arrays.asList("account", "contact", "lead", "opportunity", Constants.TICKET, "activity", "user")
				);
		entityMap.forEach((k , v) -> {
			MongoCollection<Document> metadata = db.getCollection("connectorMetadata");
			Document filterDoc = new Document();
		    filterDoc.append("name", k);
			Document connectorMeta = metadata.find(filterDoc).first();

			v.forEach(entityName -> {
				Document entity = new Document("apiName", entityName)
						.append("displayName", StringUtils.capitalize(entityName))
						.append("connectorTypeId", connectorMeta.get("_id").toString())
						.append("systemType", k);
				entities.insertOne(entity);
			});
		});
	}

	@ChangeSet(order = "002", id = "sfdcAccountAttributeSeed", author = "varsha")
	public void addSfdcAccountAttributes(MongoTemplate db) {
		// Noop
	}

	@ChangeSet(order = "003", id = "sfdcContactAttributeSeed", author = "varsha")
	public void addSfdcContactAttributes(MongoTemplate db) {
	    // Noop
	}

	@ChangeSet(order = "004", id = "sfdcLeadAttributeSeed", author = "varsha")
	public void addSfdcLeadAttributes(MongoTemplate db) {
	    // Noop
	}

	@ChangeSet(order = "005", id = "sfdcUserAttributeSeed", author = "varsha")
	public void addSfdcUserAttributes(MongoTemplate db) {
	    // Noop
	}
	
	@ChangeSet(order = "006", id = "sfdcOpportunityAttributeSeed", author = "varsha")
	public void addSfdcOpportunityAttributes(MongoTemplate db) {
	    // Noop
	}
	
	@ChangeSet(order = "007", id = "sfdcCaseAttributeSeed", author = "varsha")
	public void addSfdcCaseAttributes(MongoTemplate db) {
	    // Noop
	}
	
	@ChangeSet(order = "008", id = "sfdcActivityHistoryAttributeSeed", author = "varsha")
	public void addSfdcActivityHistoryAttributes(MongoTemplate db) {
	    // Noop
	}
	
}
