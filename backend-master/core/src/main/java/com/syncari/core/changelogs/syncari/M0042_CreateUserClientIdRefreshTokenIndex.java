package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@ChangeLog(order = "0042")
public class M0042_CreateUserClientIdRefreshTokenIndex {

    @ChangeSet(order = "001", id = "indexOnUserClientId", author = "venkat")
    public void indexOnUserClientId(MongoTemplate template) {
        MongoCollection<Document> functions = template.getCollection("user");
        MongoUtils.createIndexes(template,"user", List.of(
                new Index("idx_client_id",false,
                        "oauthDetails.clientId")
        ));
    }}
