package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

@Slf4j
public class SYN_12503_UpdateOpportunitySyncariEntityDataType {
    public static final String SYNCARI_CONNECTOR_NAME = "syncari";

    @ChangeSet(order = "001", id = "updateOpportunitySyncariEntityDataTypeValue", author = "armando")
    public void updateOpportunitySyncariEntityDataTypeValue(MongoTemplate db) {
        MongoCollection<Document> attributeDefinition = db.getCollection("attributeDefinition");
        List<Document> syncariOpportunities = getOpportunityDocuments(db);

        List<String> fieldsIds = syncariOpportunities.stream().map(document -> document.get("_id").toString()).collect(Collectors.toList());

        Bson filter = and(
                in("entityId", fieldsIds),
                eq("apiName", "LastModifiedById"),
                eq("dataType", 32)
        );

        List<Document> foundOpportunities = attributeDefinition.find(filter).into(new ArrayList<>());

        log.info("Total number of documents found with wrong Opportunity.dataType: " + foundOpportunities.size());

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        if (!dryRunMode && !foundOpportunities.isEmpty()) {
            List<WriteModel<Document>> updateOperations = new ArrayList<>();
            Bson updateQuery = Updates.set("dataType", "reference");

            for (Document attribute : foundOpportunities) {
                ObjectId attributeId = attribute.getObjectId("_id");
                updateOperations.add(new UpdateOneModel<>(eq("_id", attributeId), updateQuery));
            }

            BulkWriteResult bulkWriteResult = attributeDefinition.bulkWrite(updateOperations);
            log.info("Total number of documents updated: " + bulkWriteResult.getModifiedCount());
        }
    }

    private Document getSyncariConnector(MongoTemplate db) {
        MongoCollection<Document> connector = db.getCollection("connector");
        Document filterDoc = new Document();
        filterDoc.append("name", SYNCARI_CONNECTOR_NAME);
        return connector.find(filterDoc).first();

    }

    private ArrayList<Document> getOpportunityDocuments(MongoTemplate db) {
        MongoCollection<Document> entities = db.getCollection("entityDefinition");
        Document filterDoc = new Document();
        filterDoc.append("apiName", "opportunity");
        filterDoc.append("connectorId", getSyncariConnector(db).getObjectId("_id").toHexString());
        return entities.find(filterDoc).into(new ArrayList<>());
    }
}
