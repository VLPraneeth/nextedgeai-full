package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_4490_RemoveDuplicateContacts {

    @ChangeSet(order = "001", id = "removeDuplicateContacts", author = "venkat")
    public void removeDuplicateContacts(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String connectorId = "5ff49da6d0d0420001fa4f46";
        String hubContactIdCol = "hubspot_contact_id";
        int pageSize = 1000;

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);

        MongoCollection<Document> contact = template.getCollection("syncari_contact");
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCursor<Document> cursor = contact.find(Filters.exists(hubContactIdCol)).projection(new Document(hubContactIdCol, 1)).batchSize(pageSize).iterator();

        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String syncariId = doc.getObjectId("_id").toHexString();
            String hsObjectId = doc.getString(hubContactIdCol);

            Document idMap = idMapping.find(new Document("syncariId", syncariId).append("mappings.connectorId", connectorId))
                    .projection(new Document("_id" , 0).append("mappings",  new Document("$elemMatch", new Document("connectorId", connectorId))))
                    .first();

            if (idMap != null) {
                String entityId = ((List<Document>)idMap.get("mappings")).get(0).getString("entityId");
                if (!hsObjectId.equalsIgnoreCase(entityId)) {
                    log.info("Mismatch between vid {} and hs_object_id {}, syncari Id is {}", entityId, hsObjectId, syncariId);
                    // object Id not equals entityId
                    log.info("Soft Deleting id {} from syncari_contact", syncariId);
                    if (!dryRunMode) {
                        contact.findOneAndDelete(new Document("_id", new ObjectId(syncariId)));
                        log.info("Successfully Deleted id {} from syncari_contact", syncariId);
                    }

                    log.info("Deleting syncari id  {} from idMapping", syncariId);
                    if (!dryRunMode) {
                        idMapping.findOneAndDelete(new Document("syncariId", syncariId));
                        log.info("Successfully deleted syncari id  {} from idMapping", syncariId);
                    }
                }
            }
        }
    }
}