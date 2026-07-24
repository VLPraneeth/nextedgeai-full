package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ChangeLog(order = "0037")
public class M0037_CreateUserClientIdIndex {

    @ChangeSet(order = "001", id = "indexOnUserClientId", author = "jason")
    public void indexOnUserClientId(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("user");
        MongoUtils.createIndexes(template,"user", List.of(
                new Index("idx_client_id",false,
                        "clientId")
        ));
    }}
