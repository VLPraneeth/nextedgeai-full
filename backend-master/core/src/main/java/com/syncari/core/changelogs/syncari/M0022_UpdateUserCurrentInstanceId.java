package com.syncari.core.changelogs.syncari;

import static com.mongodb.client.model.Filters.eq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

@ChangeLog(order = "0022")
public class M0022_UpdateUserCurrentInstanceId {

    @ChangeSet(order = "001", id = "addSuperAdminCurrentInstance", author = "varsha")
    public void addSuperAdminCurrentInstance(MongoTemplate db) {
        MongoCollection<Document> users = db.getCollection("user");
        Map<String, Document> orgMap = new HashMap<>();
        Consumer<Document> consumer = document -> {
            orgMap.put(document.getObjectId("_id").toHexString(), document);
        };
        db.getCollection("organization").find().forEach(consumer);
        users.find().forEach(new Consumer<Document>() {
            @Override
            public void accept(Document usr) {
                Document org = orgMap.get(usr.get("orgId"));
                if(org == null)return;
                List<Document> instances = (List<Document>) org.get("instances", List.class);
                if(instances == null || instances.isEmpty()) return;
                String syncariId = instances.get(0).getString("syncariId");
                Bson query = eq("_id", usr.getObjectId("_id"));
                Document updated = users.findOneAndUpdate(query,
                        new Document("$set", new Document("currentInstanceId", syncariId)));
                assert updated != null;
            }
        });
    }
    
    @ChangeSet(order = "002", id = "addUsersAvailableInstances", author = "varsha")
    public void addUsersAvailableInstances(MongoTemplate db) {
        MongoCollection<Document> users = db.getCollection("user");
        Map<String, Document> orgMap = new HashMap<>();
        Consumer<Document> consumer = document -> {
            orgMap.put(document.getObjectId("_id").toHexString(), document);
        };
        db.getCollection("organization").find().forEach(consumer);
        users.find().forEach(new Consumer<Document>() {
            @Override
            public void accept(Document usr) {
                Document org = orgMap.get(usr.get("orgId"));
                if(org == null)return;
                List<Document> instances = (List<Document>) org.get("instances", List.class);
                if(instances == null || instances.isEmpty()) return;
                String syncariId = instances.get(0).getString("syncariId");
                Bson query = eq("_id", usr.getObjectId("_id"));
                Document updated = users.findOneAndUpdate(query,
                        new Document("$set", new Document("availableInstances", Set.of(syncariId))));
                assert updated != null;
            }
        });
    }

}
