package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SYN_14760_ImpartnerConnector {

    @ChangeSet(order = "001", id = "getSynapseStatus", author = "venkat", runAlways = true)
    public void getSynapseStatus(MongoTemplate template) {

        String metadataId = System.getProperty("metadataId");
        MongoCollection<Document> connectorColl = template.getCollection("connector");
        List<Document> synapses = connectorColl.find(new Document("metadataId", metadataId))
                .projection(new Document("errorMessage", 1).append("errorDetail", 1).append("name", 1).append("status", 1)).into(new ArrayList<>());

        String synapseString = synapses.stream().map(synapse -> {
            StringBuilder sb = new StringBuilder();
            sb.append(synapse.get("name")).append(",");
            String status = synapse.getString("status");
            sb.append(status).append(",");
            if (status.equals("ERROR")) {
                sb.append(synapse.get("errorMessage")).append(",").append("\"" + synapse.get("errorDetail") + "\"");
            }
            return sb.toString();
        }).collect(Collectors.joining(","));

        log.info("Instance Status {},{}", SyncariContext.getSyncariId(), synapseString);
    }
}
