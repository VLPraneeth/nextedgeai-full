package com.syncari.dbm.customscripts.customer;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.syncari.core.SyncariContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_13981_ChangeStringToTextSetValue {

    @ChangeSet(order = "001", id = "changeStringToTextSetValue", author = "sibin", runAlways = true)
    public void changeStringToTextSetValue(MongoTemplate template) {
    	boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		log.info("Running in dry run mode - {}", dryRunMode);
		MongoCollection<Document> mappingNode = template.getCollection("mappingNode");
		Bson filter = and(or(eq("apiName", "setValueOnEntity"), eq("apiName", "setValue")),
				eq("configuration.functionCall.config.setValueField.type", "temporary"),
				eq("configuration.functionCall.config.setValueField.dataType", "string"));
		var docs = mappingNode.find(filter);
		docs.forEach((Block<? super Document>) doc -> {
			Bson update = new Document()
					.append("configuration.functionCall.config.setValueField.dataType", "text");
			Bson set = new Document().append("$set", update);
			log.info("Updating Set value. Syncari instance id {}, instance name {}, custom action node id - {}, node name is {} and node apiname is {}"
					, SyncariContext.getSyncariId(),SyncariContext.getInstance().getName(), doc.get("_id"), doc.get("name"), doc.get("apiName"));
	        if(!dryRunMode) {
	        	mappingNode.updateOne(doc, set);
	        }
		});

    }
}
