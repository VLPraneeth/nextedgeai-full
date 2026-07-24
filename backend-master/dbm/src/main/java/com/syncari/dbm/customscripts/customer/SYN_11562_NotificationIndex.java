package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class SYN_11562_NotificationIndex {
    @ChangeSet(order = "001", id = "createNotificationIndexes", author = "neelesh", runAlways = true)
    public void createNotificationIndexes(MongoTemplate db) {
        MongoUtils.createIndexes(db, "notification", List.of(new Index(false, Map.of(
                "userId", 1, "read", 1, "archived", 1, "_id", -1
        ), "userId", "read", "archived", "_id")));
    }
}
