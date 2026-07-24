package com.syncari.dbm.customscripts.customer;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_8590_RemoveDoubleQuotesImportFiles {

	@ChangeSet(order = "001", id = "removeDoubleQuotesImportFiles", author = "sibin")
	public void removeDoubleQuotesImportFiles(MongoTemplate db) {
		boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		log.info("Running this tool in dryrun mode: {} ", dryRunMode);
		Document importFilesConnector = getImportedFilesConnector(db);
		if (importFilesConnector != null) {
			log.info("Imported Files connector found: {} ", importFilesConnector.get("_id").toString());
			var entityDef = db.getCollection("entityDefinition");
			var entities = entityDef
					.find(new Document().append("connectorId", importFilesConnector.get("_id").toString()));
			List<String> entityIds = entities.into(new ArrayList<>()).stream().map(e -> e.get("_id").toString())
					.collect(Collectors.toList());
			log.info("Entities to be updated: {} ", entityIds);
			var attributeDefinition = db.getCollection("attributeDefinition");

			var attribs = attributeDefinition.find(in("entityId", entityIds)).iterator();
			while (attribs.hasNext()) {
				var attribute = attribs.next();
				var displayName = (String) attribute.get("displayName");
				if (displayName != null && (displayName.startsWith("\"") && displayName.endsWith("\""))) {
					displayName = displayName.replaceAll("^\"|\"$", "");
					log.info("Attribute display name updated to {} ", displayName);
					if (!dryRunMode) {
						attributeDefinition.findOneAndUpdate(new Document("_id", attribute.getObjectId("_id")),
								set("displayName", displayName));
					}
				}
			}
		}

	}
    
    private Document getImportedFilesConnector(MongoTemplate db){
        MongoCollection<Document> connector = db.getCollection("connector");
        Document filterDoc = new Document().append("name", "Imported Files");
        return connector.find(filterDoc).first();

    }
}
