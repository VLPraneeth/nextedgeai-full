package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.Constants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0041")
public class M0041_AddOracleRDSConnector {

	@ChangeSet(order = "001", id = "oracle", author = "armando")
	public void bigquery(MongoTemplate template) {
		MongoCollection<Document> meta = template.getCollection("connectorMetadata");
		meta.insertOne(new Document("name", Constants.ORACLE));
	}

}
