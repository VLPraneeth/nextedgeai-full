package com.syncari.core.changelogs.syncari;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.model.Indexes;
import com.syncari.core.utils.MongoUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;

@ChangeLog(order = "0002")
public class M0002_CreateIndexes {
	@ChangeSet(order = "001", id = "createUniqueIndexesSyncari", author = "varsha")
	public void createUniqueIndexesSyncari(MongoTemplate db) {
		Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
		indexMap.put("plan", List.of(new Index("name")));
		indexMap.put("user", List.of(new Index("email")));
		indexMap.put("organization", List.of(new Index("name")));
		indexMap.put("globalConfiguration", List.of(new Index("key")));

		indexMap.forEach((k, v) -> {
			v.stream().forEach(index -> {
				MongoCollection<Document> collection = db.getCollection(k);
				IndexOptions keyOpts = new IndexOptions().unique(true);
				Map map = new HashMap<>();
				index.fields.stream().forEach(f -> map.put(f, 1));
				collection.createIndex(new BasicDBObject(map), keyOpts);
			});
		});
	}
	@ChangeSet(order = "002", id = "updateUserUniqueIndex", author = "varsha")
	public void updateUserUniqueIndex(MongoTemplate db) {
	    Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
	    indexMap.put("user", List.of(new Index("email", "orgId")));

	    indexMap.forEach((k, v) -> {
	        v.stream().forEach(index -> {
	            MongoCollection<Document> collection = db.getCollection(k);
	            collection.dropIndexes();
	            IndexOptions keyOpts = new IndexOptions().unique(true);
	            Map map = new HashMap<>();
	            index.fields.stream().forEach(f -> map.put(f, 1));
	            collection.createIndex(new BasicDBObject(map), keyOpts);
	        });
	    });
	}
    @ChangeSet(order = "003", id = "noOpTestingMultiClusterSyncaridb", author = "varsha")
    public void noOpTestingMultiClusterSyncaridb(MongoTemplate db) {
        // No-op
    }

	@ChangeSet(order = "004", id = "indexOnConnectorMetaDataName", author = "durga")
	public void indexOnConnectorMetaDataName(MongoTemplate db) {
		MongoCollection<Document> collection = db.getCollection("connectorMetadata");
		IndexOptions keyOpts = new IndexOptions().unique(true);
		BasicDBObject dbObj = new BasicDBObject();
		dbObj.append("name",1);
		dbObj.append("draftStatus", 1);
		collection.createIndex(Indexes.compoundIndex(dbObj), keyOpts);
	}

	@ChangeSet(order = "005", id = "createGhostAccessAuditIndex", author = "rohit")
	public void createGhostAccessAuditIndex(MongoTemplate db) {
		MongoUtils.createIndexes(db, "ghostAccessAudit", List.of(new com.syncari.core.Index(false, "requesterId", "status")));
	}

	@ChangeSet(order = "006", id = "indexOnSharedItem", author = "blesson")
	public void indexOnSharedItem(MongoTemplate db) {
		MongoCollection<Document> collection = db.getCollection("sharedItem");
		IndexOptions keyOpts = new IndexOptions().unique(true);
		BasicDBObject dbObj = new BasicDBObject();
		dbObj.append("itemType",1);
		dbObj.append("sourceInstance", 1);
		dbObj.append("sourceId", 1);
		dbObj.append("recipientsUserId", 1);
		collection.createIndex(Indexes.compoundIndex(dbObj), keyOpts);
	}
}

class Index {
	List<String> fields = new ArrayList<>();
	
	public Index(String ... fieldName) {
		fields.addAll(List.of(fieldName));
	}
}
