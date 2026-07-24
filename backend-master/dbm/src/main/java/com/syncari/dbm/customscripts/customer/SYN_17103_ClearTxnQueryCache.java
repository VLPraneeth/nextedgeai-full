package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_17103_ClearTxnQueryCache {

    @ChangeSet(order = "001", id = "clearTxnQueryCache", author = "rohit", runAlways = true)
    public void clearTxnQueryCache(MongoTemplate db) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var valueClass = System.getProperty("value");
        log.info("valueClass is {}", valueClass);
        if (StringUtils.isEmpty(valueClass)){
            log.error("valueClass is mandatory param");
            return;
        }
        MongoCollection<Document> queryCache = db.getCollection("queryCache");
        queryCache.find(Filters.eq("value._class", valueClass))
                .forEach((Block<? super Document>) doc -> {
                    ObjectId fId = doc.getObjectId("_id");
                    log.info("Deleting queryCache _id {} ", fId);
                    if (!dryRunMode){
                        queryCache.deleteOne(new Document("_id", fId));
                    }
                });
    }
}
