package com.syncari.core.changelogs.syncari;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;

/**
 * Adds the white-label tenant identifier without invalidating existing installations.
 * The legacy field remains readable during the staged application migration.
 */
@ChangeLog(order = "9990")
public class M9990_NextEdgeIdMigration {

    @ChangeSet(order = "001", id = "backfillNextEdgeId", author = "nextedge-ai")
    @SuppressWarnings("unchecked")
    public void backfillNextEdgeId(MongoTemplate db) {
        MongoCollection<Document> organizations = db.getCollection("organization");
        for (Document organization : organizations.find()) {
            List<Document> instances = (List<Document>) organization.get("instances");
            if (instances == null) {
                continue;
            }

            boolean changed = false;
            for (Document instance : instances) {
                if (instance.getString("nextEdgeId") == null && instance.getString("syncariId") != null) {
                    instance.put("nextEdgeId", instance.getString("syncariId"));
                    changed = true;
                }
            }

            if (changed) {
                organizations.replaceOne(eq("_id", organization.get("_id")), organization);
            }
        }
    }
}
