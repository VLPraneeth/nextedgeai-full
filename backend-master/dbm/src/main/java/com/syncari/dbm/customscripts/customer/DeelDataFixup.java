package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOneModel;
import com.syncari.core.model.UnresolvedReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableInt;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class DeelDataFixup {

    @ChangeSet(order = "001", id = "updateRecordValues", author = "venkat", runAlways = true)
    public void updateRecordValues(MongoTemplate template) throws Exception {

        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String updateFile = System.getProperty("updateFile");
        String entity = System.getProperty("entity");
        String srcEntityDefId = System.getProperty("srcEntityDefId");
        String srcConnectorId = System.getProperty("srcConnectorId");

        //MongoCollection<Document> entityDefinition = template.getCollection("entityDefinition");
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        String collection = "syncari_" + entity.toLowerCase();
        MongoCollection<Document> entityColl = template.getCollection(collection);

        var url = this.getClass().getClassLoader().getResource(updateFile);

        log.info("Entity : {} Src EntityId {} Src Connector ID {}", entity, srcEntityDefId, srcConnectorId);

        try (Stream<String> stream = Files.lines(Paths.get(url.toURI()), Charset.forName("utf-8"))) {

            var header = stream.findFirst();
            header.ifPresent(h -> {
                String[] apiNames = h.split(",");

                List<UpdateOneModel<Document>> updates = new ArrayList<>();

                log.info("Loading IdMapping for entity {}", entity);
                List<Document> ids = idMapping.find(new Document("entityName", entity).append("mappings.connectorId", srcConnectorId)
                        .append("mappings.entityDefinitionId", srcEntityDefId)).projection(new Document("syncariId", 1).append("mappings.entityId", 1)).into(new ArrayList<>());

                Map<String, String> externalId2SyncariId = ids.stream().collect(Collectors.toMap(d -> {
                    var arrDocs = (List<Document>)d.get("mappings");
                    return arrDocs.get(0).getString("entityId");
                }, d -> d.getString("syncariId")));

                log.info("Loaded IdMapping for entity {} Size {}", entity, externalId2SyncariId.size());

                try (Stream<String> stream1 = Files.lines(Paths.get(url.toURI()), Charset.forName("utf-8"))) {

                    stream1.skip(1).forEach(s -> {
                        String[] values = s.split(",");
                        String id = values[0];

                        if (externalId2SyncariId.containsKey(id)) {
                            String syncariId = externalId2SyncariId.get(id);
                            Document findDoc = new Document("_id", new ObjectId(syncariId));
                            Document updateDoc = new Document();
                            for (int i = 1; i < values.length; i++) {
                                if (i < apiNames.length)
                                    updateDoc.append(apiNames[i], values[i]);
                            }
                            updates.add(new UpdateOneModel<>(findDoc, new Document("$set", updateDoc)));

                            if (updates.size() > 1000) {
                                log.info("Updating collection {} Syncari Id {} values {}", collection, syncariId, updateDoc.toString());
                                if (!dryRun) {
                                    entityColl.bulkWrite(updates);
                                    updates.clear();
                                }
                            }
                        } else {
                            log.error("Did not find Syncari Id for SFDC ID {}", id);
                        }

                    });

                    if (updates.size() > 0) {
                        if (!dryRun) {
                            entityColl.bulkWrite(updates);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
