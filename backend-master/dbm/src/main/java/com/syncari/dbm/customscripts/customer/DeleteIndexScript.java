package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class DeleteIndexScript {

    @ChangeSet(order = "001", id = "deleteIndex", author = "abhinav", runAlways = true)
    public void deleteIndex(MongoTemplate template) {

        var collectionName = System.getProperty("collectionName");
        var indexName = System.getProperty("indexName");

        if (StringUtils.isBlank(collectionName) || StringUtils.isBlank(indexName)) {
            log.error("Either one of collectionName or indexName cannot be blank");
            return;
        }

        log.info("Deleting index {} from collection {}", indexName, collectionName);
        try {
            MongoCollection<Document> collection = template.getCollection(collectionName);
            collection.dropIndex(indexName);
        }catch(Exception e){
            log.error("Failed to delete index {} in collection {}. Error: {}", indexName, collectionName, e.getMessage());
            e.printStackTrace();
        }
    }
}
