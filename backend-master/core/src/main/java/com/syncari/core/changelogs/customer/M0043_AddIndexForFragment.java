package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@ChangeLog(order="0043")
public class M0043_AddIndexForFragment {

    @ChangeSet(order = "001", id = "createUniqueIndexOnFragmentName", author = "abhinav")
    public void createUniqueIndexOnFragmentName(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("fragment");
        collection.dropIndexes();

        MigrationUtil.createIndex(db, Map.of("fragment", List.of(new Index("name"))));
    }
}
