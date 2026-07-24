package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
public class SYN_10258_DestinationDump {
    @ChangeSet(order = "001", id = "transactionLogDump", author = "venkat", runAlways = true)
    public void transactionLogDump(MongoTemplate template) {
        MongoCollection<Document> txnLog = template.getCollection("transactionLog");
        MongoCollection<Document> idMapping = template.getCollection("idMapping");

        String entity = System.getProperty("entityName");
        //String operation = System.getProperty("operation");
        String connectorId = System.getProperty("connectorId");
        String startDate = System.getProperty("startDate");
        String endDate = System.getProperty("endDate");
        String mysqlConnectorId = System.getProperty("mysqlConnectorId");

        int pageSize = 1000;
        var filter = Filters.and(new Document("entityName", entity), Filters.gte("updatedAt", Instant.parse(startDate)), Filters.lt("updatedAt", Instant.parse(endDate)));

        log.info("Number of documents found {}", txnLog.countDocuments(filter));

        var txnIterator = txnLog.find(filter).batchSize(pageSize).iterator();
        boolean printHeader = true;

        log.info("Syncari Id, Values");
        while(txnIterator.hasNext()) {
            var doc = txnIterator.next();
            Document changesDoc = (Document) doc.get("changes");

            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            var syncariId = doc.getString("syncariId");

            // check if this syncari
            var idMappingFilter = Filters.and(new Document("syncariId", syncariId),
                    Filters.gte("createdAt", Instant.parse(startDate)),
                    Filters.lt("createdAt", Instant.parse(endDate)), Filters.eq("mappings.connectorId", mysqlConnectorId));

            Document mapping = idMapping.find(idMappingFilter).first();
            if (mapping != null) {
                if (changesDoc != null) {
                    for (Map.Entry<String, Object> changes : changesDoc.entrySet()) {
                        if (changes.getValue() instanceof Document) {
                            Document fieldChanges = (Document)changes.getValue();
                            if (fieldChanges.containsKey("outgoingExternalValues")) {
                                var incomingDoc = (Document)fieldChanges.get("outgoingExternalValues");
                                List<Document> incomingDocs = incomingDoc.values().stream().map(d -> (Document)d).collect(Collectors.toList());
                                if (incomingDocs.size() > 0) {
                                    var fieldChange = (Document)incomingDocs.get(0);
                                    if (fieldChange.containsKey("connectorId") && fieldChange.getString("connectorId").equals(connectorId)) {
                                        values.put(fieldChange.getString("apiName"), fieldChange.get("value"));
                                    }
                                }
                            }
                        }
                    }
                    if (values.size() > 0) {
                        log.info("{},{}", syncariId, values.keySet().stream().collect(Collectors.joining(",")));
                    }
                }
            }
        }
    }
}
