package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.model.misc.ConnectorStatus;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Updates.*;

@Slf4j
public class SYN_15311_AddTimeTicker {
	public static final String SYNCARI_CONNECTOR_NAME="syncari";

    @ChangeSet(order = "001", id = "createTimeTickerEntity", author = "sibin", runAlways = true)
	public void addTimeTicketEntity(MongoTemplate db) {
		String entityName = "timeTicker";
		String displayName = "Time Ticker";
		boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		var timeTickerId = System.getProperty("id");
		log.info("Running in dry run mode - {}", dryRunMode);
		if (getDocument(db, entityName).isPresent()) {
			log.info("{} already exist", entityName);
			return;
		}
		if(dryRunMode) {
			log.info("{} does not exist", entityName);
			return;
		}

		MongoCollection<Document> entities = db.getCollection("entityDefinition");

		Document syncariConnector = getSyncariConnector(db);
		Document entity = new Document("apiName", entityName)
				.append("displayName", StringUtils.capitalize(displayName))
				.append("status", ConnectorStatus.ACTIVE.name())
				.append("connectorId", syncariConnector.getObjectId("_id").toHexString())
				.append("connectorTypeId", syncariConnector.get("metadataId").toString())
				.append("seeded", true)
				.append("readOnly", true)
				.append("syncariSource", true)
				.append("systemType", "syncari")
				.append("draftStatus", "APPROVED");
		if (StringUtils.isNotEmpty(timeTickerId)){
			entity.append("_id", new ObjectId(timeTickerId));
		}

		entities.insertOne(entity);

		MongoCollection<Document> attributes = db.getCollection("attributeDefinition");
		List<Document> timeTickerAttributes = new ArrayList<>();

		Document timeTicker = getDocument(db, entityName).get();
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "id")
				.append("displayName", "ID")
				.append("custom", false)
				.append("dataType", "id")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("isIdField", true)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "timestamp")
				.append("displayName", "Timestamp")
				.append("custom", false)
				.append("dataType", "timestamp")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("isWatermarkField", true)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "datetime")
				.append("displayName", "Datetime")
				.append("custom", false)
				.append("dataType", "datetime")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "date")
				.append("displayName", "Date")
				.append("custom", false)
				.append("dataType", "date")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "year")
				.append("displayName", "Year")
				.append("custom", false)
				.append("dataType", "integer")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "month")
				.append("displayName", "Month")
				.append("custom", false)
				.append("dataType", "integer")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "day")
				.append("displayName", "Day")
				.append("custom", false)
				.append("dataType", "integer")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "hour")
				.append("displayName", "Hour")
				.append("custom", false)
				.append("dataType", "integer")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "minute")
				.append("displayName", "Minute")
				.append("custom", false)
				.append("dataType", "integer")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "second")
				.append("displayName", "Second")
				.append("custom", false)
				.append("dataType", "integer")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));
		timeTickerAttributes.add(new Document("entityId", timeTicker.get("_id").toString())
				.append("apiName", "millisecond")
				.append("displayName", "Millisecond")
				.append("custom", false)
				.append("dataType", "integer")
				.append("nillable", false)
				.append("calculated", false)
				.append("unique", false)
				.append("initializable", false)
				.append("updatable", false));

		addSeededFlag(timeTickerAttributes);
		timeTickerAttributes.forEach(a -> a.append("status", ConnectorStatus.ACTIVE.name()));
		attributes.insertMany(timeTickerAttributes);
		log.info("{} created successfully", entityName);
	}
    
    private Document getSyncariConnector(MongoTemplate db){
        MongoCollection<Document> connector = db.getCollection("connector");
        Document filterDoc = new Document();
        filterDoc.append("name", SYNCARI_CONNECTOR_NAME);
        return connector.find(filterDoc).first();

    }
    
    private void addSeededFlag(List<Document> documents) {
		documents.forEach(a->{
			if(!a.containsKey("seeded")) a.append("seeded",true);
		});
	}
    
    private Optional<Document> getDocument(MongoTemplate db, String entityName) {
        MongoCollection<Document> entities = db.getCollection("entityDefinition");
        Document filterDoc = new Document();
        filterDoc.append("apiName", entityName);
        filterDoc.append("connectorId", getSyncariConnector(db).getObjectId("_id").toHexString());
        
        return Optional.ofNullable(entities.find(filterDoc).first());
    }
}
