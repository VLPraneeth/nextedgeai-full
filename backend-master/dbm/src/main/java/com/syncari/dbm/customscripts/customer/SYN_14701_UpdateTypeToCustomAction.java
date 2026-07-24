package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_14701_UpdateTypeToCustomAction {

	@ChangeSet(order = "001", id = "findCorruptedCustomAction", author = "sibin", runAlways = true)
	public void findCorruptedCustomAction(MongoTemplate template) {
		log.info("Running in dry run mode - {}", false);
		MongoCollection<Document> mappingNode = template.getCollection("mappingNode");
		Bson filter = and(eq("configuration.name", "httpAction"), or(eq("configuration.type", null),
				eq("configuration.actionProperties._class", "com.syncari.core.DefaultActionProperties")));
		var docs = mappingNode.find(filter);
		log.info("Listing all Affected custom action");
		docs.forEach((Block<? super Document>) doc -> {
			log.info("Affected Syncari instance id {}, instance name {}, custom action node id - {}, node name is {} and node apiname is {}"
					, SyncariContext.getSyncariId(),SyncariContext.getInstance().getName(), doc.get("_id"), doc.get("name"), doc.get("apiName"));
		});
	}
	
	@ChangeSet(order = "002", id = "updateCorruptedCustomAction", author = "sibin", runAlways = true)
	public void updateCorruptedCustomAction(MongoTemplate template) {
		boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		log.info("Running in dry run mode - {}", dryRunMode);
		MongoCollection<Document> mappingNode = template.getCollection("mappingNode");
		MongoCollection<Document> actionDefinition = template.getCollection("actionDefinition");
		Bson filter = and(eq("configuration.name", "httpAction"), or(eq("configuration.type", null),
				eq("configuration.actionProperties._class", "com.syncari.core.DefaultActionProperties")));
		var docs = mappingNode.find(filter);
		log.info("Updating all Affected custom action");
		docs.forEach((Block<? super Document>) doc -> {
			log.info("Affected Syncari instance id {}, instance name {}, custom action node id - {}, node name is {} and node apiname is {}"
					, SyncariContext.getSyncariId(),SyncariContext.getInstance().getName(), doc.get("_id"), doc.get("name"), doc.get("apiName"));
			var defId = doc.get("configuration", Document.class).get("configMap", Document.class).get("definition");
			if(defId == null) {
				log.error("Skipping custom action update. No action definition id found in node {}", doc.get("_id"));
			} else {
				Bson actionFilter = eq("_id", new ObjectId(defId.toString()));
				var actionDoc = actionDefinition.find(actionFilter).first();
				if(actionDoc == null) {
					log.error("Skipping custom action update. No action definition {} found", defId.toString());
				} else {
					var actionProperties = actionDoc.get("properties");
					if(actionProperties == null ) {
						log.error("Skipping custom action update. No action properties {} found", defId.toString());
					} else {
						Bson update = new Document()
								.append("configuration.actionProperties", actionProperties)
								.append("configuration.type", "CUSTOM");
				        Bson set = new Document().append("$set", update);
				        log.info("Updating Syncari instance id {}, instance name {}, custom action node id - {}, node name is {} and node apiname is {}"
								, SyncariContext.getSyncariId(),SyncariContext.getInstance().getName(), doc.get("_id"), doc.get("name"), doc.get("apiName"));
				        if(!dryRunMode) {
				        	mappingNode.updateOne(doc, set);
				        }
					}
				}
			}
		});
	}
}
