package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.connector.Constants;
import com.syncari.core.Index;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.schema.Schema;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

@Slf4j
@ChangeLog(order = "0044")
public class M0044_CustomerDataFixUp {

    @ChangeSet(order = "001", id = "setReferenceLengthTo32", author = "varsha")
    public void setReferenceLengthTo32(MongoTemplate db) {
        //No Op
    }
    
    @ChangeSet(order = "002", id = "sanitizeGoogleSheetApiName", author = "varsha")
    public void sanitizeGoogleSheetApiName(MongoTemplate db) {
        MongoDatabase syncariDb = MigrationContext.getSyncariDB();
        MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
        Document gsMeta = metadata.find(eq("name", Constants.GOOGLESHEETS)).first();
        MongoCollection<Document> connector = db.getCollection("connector");
        FindIterable<Document> gsConnector = connector.find(eq("metadataId", gsMeta.get("_id").toString()));
       
        gsConnector.forEach((Block<? super Document>) c -> {
            List<EntityDefinition> schema = MigrationContext.getSchemaService().getAllEntities(c.getObjectId("_id").toHexString());
            schema.stream().forEach(e -> {
                e.getAttributes().stream().forEach(attr -> attr.setApiName(MigrationContext.getTextUtil().createApiName(attr.getApiName())));
                MigrationContext.getAttributeRepo().saveAll(e.getAttributes());
            });
        });
    }
    
    @ChangeSet(order = "003", id = "fixDfiSnapshotComputedDay", author = "varsha")
    public void fixDfiSnapshotComputedDay(MongoTemplate db) {
        updateValues(db.getCollection("entityDataScoreSnapshot"));
        updateValues(db.getCollection("fieldDataScoreSnapshot"));
    }
    
    @ChangeSet(order = "004", id = "computeScore", author = "varsha")
    public void computeScore(MongoTemplate db) {
    }
    
    @ChangeSet(order = "005", id = "recomputeScore", author = "varsha")
    public void recomputeScore(MongoTemplate db) {
    }
    
    @ChangeSet(order = "006", id = "recomputeScore1", author = "varsha")
    public void recomputeScore1(MongoTemplate db) {
        MigrationContext.getRepoService().initializeScore();
    }
    
    @ChangeSet(order = "007", id = "renameScore", author = "varsha")
    public void renameScore(MongoTemplate db) {
        Set<String> collectionNames = db.getCollectionNames();
        List<String> toFix = List.of("syncari_account", "syncari_lead", "syncari_contact");
        toFix.forEach(c -> {
            if(collectionNames.contains(c)) {
                Document filter = new Document("syncariScore", new Document("$exists", false));
                Document update = new Document("$rename", new Document("score", "syncariScore"));
                db.getCollection(c).updateMany(filter, update, new UpdateOptions().upsert(false));
            }
        });
    }
    
    @ChangeSet(order = "008", id = "recomputeScore2", author = "varsha")
    public void recomputeScore2(MongoTemplate db) {
        MigrationContext.getRepoService().initializeScore();
    }
    
    @ChangeSet(order = "009", id = "changeReferenceDatatypeLength", author = "varsha")
    public void changeReferenceDatatypeLength(MongoTemplate db) {
        Schema syncariSchema = MigrationContext.getSchemaService().getSyncariSchema();
        List<ObjectId> attrIds = new ArrayList<>();
        if (syncariSchema == null) {
            return;
        }
        syncariSchema.getEntities().forEach(e -> {
            e.getFields().stream().forEach(f -> {
                if (f.isReference() && f.getLength() < 32) {
                    attrIds.add(new ObjectId(f.getId()));
                }
            });
        });
        if (attrIds.isEmpty()) return;
        MongoCollection<Document> attributeDefs = db.getCollection("attributeDefinition");
        attributeDefs.updateMany(in("_id", attrIds), new Document("$set", new Document("length", 32)),
                new UpdateOptions().upsert(false)
        );
    }
    
	@ChangeSet(order = "010", id = "changeIdMappingIndex", author = "varsha")
	public void changeIdMappingIndex(MongoTemplate db) {
		MongoCollection<Document> collection = db.getCollection("idMapping");
		try {
			collection.dropIndex(new BasicDBObject().append("entityName", 1).append("mappings.connectorId", 1)
					.append("mappings.entityId", 1));
		} catch (Exception e) {
			log.error(ExceptionUtils.getStackTrace(e));
		}
		Index index = new Index(true, 1, "entityName", "mappings.entityDefinitionId", "mappings.entityId");
		IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
		BasicDBObject dbObj = new BasicDBObject();
		index.getFields().stream().forEach(f -> dbObj.append(f, index.getAscending()));
		collection.createIndex(dbObj, keyOpts);
	}

    private void updateValues(MongoCollection<Document> collection) {
        DateUtil dateUtil = new DateUtil();
        FindIterable<Document> docs = collection.find();
        for (Document document : docs) {
            Object computedOn = document.get("computedOn");
            if(computedOn == null) continue;
            String formatDate = dateUtil.formatDate(((Date)computedOn).toInstant(), DateUtil.dateOnlyFormat2);
            try {
                log.info("Updating computedDay from {} to {}", document.get("computedDay"), formatDate);
                document.append("computedDay", formatDate);
                collection.replaceOne(new Document("_id", document.get("_id")), document);
            } catch (Exception e) {
                log.error("Error", ExceptionUtils.getStackTrace(e));
            }
        }
    }
}
