package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@Slf4j
public class SYN_11246_FetchLoserRecordsPerPredicate {

    @ChangeSet(order = "001", id = "fetchLoserRecordsPerPredicate", author = "blesson", runAlways = true)
    public void fetchLoserRecordsPerPredicate(MongoTemplate template) throws IOException {
        String predicateId = System.getProperty("predicateId");
        String operation = System.getProperty("operation");
        String entityName = System.getProperty("entityName");
        MongoCollection<Document> transactionLog = template.getCollection("transactionLog");

        MongoCursor<Document> cursor = transactionLog.find(
                new Document("additionalInfo.mergeDetails.mergeInfo.duplicateSelector.predicates", new Document("$elemMatch", new Document("predicateId", predicateId))).append("operation", operation).append("entityName", entityName)).batchSize(1000).iterator();
        File csvFile = new File("dbm/src/main/resources/SYN_11246_Loser_Records.csv");
        FileWriter csvWriter = new FileWriter(csvFile);
        List<Map<String, Object>> rows = new ArrayList<>();
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String winnerSyncariId = doc.getString("syncariId");
            Document mergeDetails = doc.get("additionalInfo", Document.class).get("mergeDetails", Document.class);
            List<Document> losingRecords = (List<Document>) mergeDetails.get("losingRecords");
            losingRecords.forEach(loser -> {
                String loserSyncariId = loser.getString("syncariEntityId");
                Map<String, Object> map = new HashMap<>();
                map.put("WINNING RECORD", winnerSyncariId);
                map.put("LOSING RECORD", loserSyncariId);
                Map<String, Object> values = (Map<String, Object>)loser.get("values");
                map.putAll(values);
                rows.add(map);
            });
        }

        Set<String> HEADERS = new LinkedHashSet<>();
        rows.forEach(row -> {
            row.forEach((key, value) -> HEADERS.add(key));
        });
        HEADERS.remove("WINNING RECORD");
        HEADERS.remove("LOSING RECORD");
        List<String> headerList = new ArrayList<>(HEADERS);
        headerList.add(0, "LOSING RECORD");
        headerList.add(0, "WINNING RECORD");
        CSVPrinter printer = new CSVPrinter(csvWriter, CSVFormat.DEFAULT.withHeader(headerList.toArray(String[]::new)));
        rows.forEach(row -> {
            List<Object> list = new ArrayList();
            headerList.forEach(header -> {
                list.add(row.getOrDefault(header, ""));
            });
            try {
                printer.printRecord(list);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        printer.flush();
        printer.close();
    }
}
