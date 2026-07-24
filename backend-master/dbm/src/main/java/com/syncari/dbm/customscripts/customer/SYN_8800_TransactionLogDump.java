package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class SYN_8800_TransactionLogDump {
    @ChangeSet(order = "001", id = "transactionLogDump", author = "venkat", runAlways = true)
    public void transactionLogDump(MongoTemplate template) {
        MongoCollection<Document> txnLog = template.getCollection("transactionLog");
        String entity = System.getProperty("entityName");
        String operation = System.getProperty("operation");
        String connectorId = System.getProperty("connectorId");
        int pageSize = 1000;
        var txnIterator = txnLog.find(new Document("entityName", entity).append("operation", operation)).batchSize(pageSize).iterator();
        boolean printHeader = true;

        log.info("Input entity {} operation {} connectorid {}", entity, operation, connectorId);
        var headerList = List.of("dealstage","pipeline");

        while(txnIterator.hasNext()) {
            var doc = txnIterator.next();
            Document changesDoc = (Document) doc.get("changes");

            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            var syncariId = doc.getString("syncariId");

            if (changesDoc != null) {
                for (Map.Entry<String, Object> changes : changesDoc.entrySet()) {
                    if (changes.getValue() instanceof Document) {
                        Document fieldChanges = (Document)changes.getValue();
                        if (fieldChanges.containsKey("incomingExternalValues")) {
                            var incomingDoc = (Document)fieldChanges.get("incomingExternalValues");
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
                if (printHeader) {
                    printHeader = false;
                    log.info("Syncari ID,{}", headerList.stream().collect(Collectors.joining(",")));
                }
                if (values.size() > 0) {
                    log.info("{},{}", syncariId, headerList.stream().map(header -> {
                        return values.containsKey(header) ? values.get(header).toString() : "";
                    }).collect(Collectors.joining(",")));
                }
            }
        }
    }
}
