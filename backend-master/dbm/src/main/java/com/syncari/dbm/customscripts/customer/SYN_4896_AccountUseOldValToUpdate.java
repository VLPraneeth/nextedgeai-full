package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Updates.set;


@Slf4j
public class SYN_4896_AccountUseOldValToUpdate {


    @ChangeSet(order = "001", id = "revertOldValuesForAccountProductFamily", author = "rohit")
    public void revertOldValuesForAccountProductFamily(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String prod_family_c = "Product_Family__c";
        String prod_line_c = "Product_Line__c";
        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> account = template.getCollection("syncari_account");

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        MongoCursor<Document> cursor = transactionLogs.find(Filters.and(new Document("entityName", "account"),new Document("operation", "update"), Filters.gte("createdAt", Instant.parse("2021-10-01T00:00:00.000Z")))).batchSize(1000).iterator();
        log.info("Cursor has next {} ", cursor.hasNext());
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String syncariId = (String) doc.get("syncariId");
            Document changes = (Document) doc.get("changes");
            ObjectId transactionLogId = doc.getObjectId("_id");
            log.info("Account syncari id to be updated is {}",syncariId);
            if (changes.containsKey("6054ce9f3e4301000174df8e")) {
                Document change = (Document) changes.get("6054ce9f3e4301000174df8e");
                if ("6054ce9f3e4301000174df8e".equals(change.get("fieldId").toString()) && prod_family_c.equals(change.get("apiName").toString())) {
                    log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                    boolean isNewValueList = change.get("newValue").toString().startsWith("[");
                    boolean isOldValueList = change.get("oldValue").toString().startsWith("[");
                    log.info("Old value for 6054ce9f3e4301000174df8e is instance of list {} and newValue is {}", isOldValueList,isNewValueList );
                    if (!dryRunMode) {
                        if ((!isNewValueList) && (isOldValueList)){
                            String oldValues = StringUtils.strip(StringUtils.strip(change.get("oldValue").toString(),"["),"]");
                            String [] oldValArray = oldValues.split(",");
                            List<String> listOfOldvals = new ArrayList<>();
                            for (String x : oldValArray) {
                                listOfOldvals.add(x.trim());
                            }
                            account.findOneAndUpdate(new Document("_id", syncariId), set(prod_family_c, listOfOldvals));
                        }
                    }
                }
            }else{
                log.info("Attribute Id 6054ce9f3e4301000174df8e not found");
            }
            if (changes.containsKey("6054d06768b6460001dccc89")) {
                Document change = (Document) changes.get("6054d06768b6460001dccc89");
                if ("6054d06768b6460001dccc89".equals(change.get("fieldId").toString()) && prod_line_c.equals(change.get("apiName").toString())) {
                    log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                    boolean isNewValueList = change.get("newValue").toString().startsWith("[");
                    boolean isOldValueList = change.get("oldValue").toString().startsWith("[");
                    log.info("Old value for 6054d06768b6460001dccc89 is instance of list {} and newValue is {}", isOldValueList,isNewValueList );
                    if (!dryRunMode) {
                        if ((!isNewValueList) && (isOldValueList)){
                            String oldValues = StringUtils.strip(StringUtils.strip(change.get("oldValue").toString(),"["),"]");
                            String [] oldValArray = oldValues.split(",");
                            List<String> listOfOldvals = new ArrayList<>();
                            for (String x : oldValArray) {
                                listOfOldvals.add(x.trim());
                            }
                            account.findOneAndUpdate(new Document("_id", syncariId), set(prod_line_c, listOfOldvals));
                        }
                    }
                }
            }else{
                log.info("Attribute Id 6054d06768b6460001dccc89 not found in transaction");
            }

        }
    }

    @ChangeSet(order = "002", id = "revertOldValuesForAccountFewAttribs", author = "rohit")
    public void revertOldValuesForAccountFewAttribs(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String prod_family_c = "Product_Family__c";
        String prod_line_c = "Product_Line__c";
        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> account = template.getCollection("syncari_account");


        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        MongoCursor<Document> cursor = transactionLogs.find(Filters.and(new Document("entityName", "account"),new Document("operation", "update"), Filters.gte("createdAt", Instant.parse("2021-10-01T00:00:00.000Z")))).batchSize(1000).iterator();
        log.info("Cursor has next {} ", cursor.hasNext());
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String syncariId = (String) doc.get("syncariId");
            Document changes = (Document) doc.get("changes");
            ObjectId transactionLogId = doc.getObjectId("_id");
            log.info("Account syncari id to be updated is {}",syncariId);
            if (changes.containsKey("6054ce9f3e4301000174df8e")) {
                Document change = (Document) changes.get("6054ce9f3e4301000174df8e");
                if ("6054ce9f3e4301000174df8e".equals(change.get("fieldId").toString()) && prod_family_c.equals(change.get("apiName").toString())) {
                    log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                    boolean isNewValueList = change.getOrDefault("newValue","").toString().startsWith("[");
                    boolean isOldValueList = change.getOrDefault("oldValue","").toString().startsWith("[");
                    log.info("Old value for 6054ce9f3e4301000174df8e is instance of list {} and newValue is {}", isOldValueList,isNewValueList );
                    if (!dryRunMode) {
                        if ((!isNewValueList) && (isOldValueList)){
                            String oldValues = StringUtils.strip(StringUtils.strip(change.getOrDefault("oldValue","").toString(),"["),"]");
                            String [] oldValArray = oldValues.split(",");
                            List<String> listOfOldvals = new ArrayList<>();
                            for (String x : oldValArray) {
                                listOfOldvals.add(x.trim());
                            }
                            account.findOneAndUpdate(new Document("_id", syncariId), set(prod_family_c, listOfOldvals));
                        }
                    }
                }
            }else{
                log.info("Attribute Id 6054ce9f3e4301000174df8e not found");
            }
            if (changes.containsKey("6054d06768b6460001dccc89")) {
                Document change = (Document) changes.get("6054d06768b6460001dccc89");
                if ("6054d06768b6460001dccc89".equals(change.get("fieldId").toString()) && prod_line_c.equals(change.get("apiName").toString())) {
                    log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                    boolean isNewValueList = change.getOrDefault("newValue","").toString().startsWith("[");
                    boolean isOldValueList = change.getOrDefault("oldValue","").toString().startsWith("[");
                    log.info("Old value for 6054d06768b6460001dccc89 is instance of list {} and newValue is {}", isOldValueList,isNewValueList );
                    if (!dryRunMode) {
                        if ((!isNewValueList) && (isOldValueList)){
                            String oldValues = StringUtils.strip(StringUtils.strip(change.getOrDefault("oldValue","").toString(),"["),"]");
                            String [] oldValArray = oldValues.split(",");
                            List<String> listOfOldvals = new ArrayList<>();
                            for (String x : oldValArray) {
                                listOfOldvals.add(x.trim());
                            }
                            account.findOneAndUpdate(new Document("_id", syncariId), set(prod_line_c, listOfOldvals));
                        }
                    }
                }
            }else{
                log.info("Attribute Id 6054d06768b6460001dccc89 not found in transaction");
            }

        }
    }

    @ChangeSet(order = "003", id = "revertOldValuesForAccountProductAttribs", author = "rohit")
    public void revertOldValuesForAccountProductAttribs(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String prod_family_c = "Product_Family__c";
        String prod_line_c = "Product_Line__c";
        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> account = template.getCollection("syncari_account");


        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        MongoCursor<Document> cursor = transactionLogs.find(Filters.and(new Document("entityName", "account"),new Document("operation", "update"), Filters.gte("createdAt", Instant.parse("2021-10-01T00:00:00.000Z")))).batchSize(1000).iterator();
        log.info("Cursor has next {} ", cursor.hasNext());
        while (cursor.hasNext()) {
            Document doc = cursor.next();
            String syncariId = (String) doc.get("syncariId");
            Document changes = (Document) doc.get("changes");
            ObjectId transactionLogId = doc.getObjectId("_id");
            log.info("Account syncari id to be updated is {}",syncariId);
            if (changes.containsKey("6054ce9f3e4301000174df8e")) {
                Document change = (Document) changes.get("6054ce9f3e4301000174df8e");
                if ("6054ce9f3e4301000174df8e".equals(change.get("fieldId").toString()) && prod_family_c.equals(change.get("apiName").toString())) {
                    log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                    boolean isNewValueList = change.getOrDefault("newValue","").toString().startsWith("[");
                    boolean isOldValueList = change.getOrDefault("oldValue","").toString().startsWith("[");
                    log.info("Old value for 6054ce9f3e4301000174df8e is instance of list {} and newValue is {}", isOldValueList,isNewValueList );
                    if (!dryRunMode) {
                        if ((!isNewValueList) && (isOldValueList)){
                            String oldValues = StringUtils.strip(StringUtils.strip(change.getOrDefault("oldValue","").toString(),"["),"]");
                            String [] oldValArray = oldValues.split(",");
                            List<String> listOfOldvals = new ArrayList<>();
                            for (String x : oldValArray) {
                                listOfOldvals.add(x.trim());
                            }
                            log.info("List of values to be updated for 6054ce9f3e4301000174df8e is {}",listOfOldvals);
                            account.findOneAndUpdate(new Document("_id", new ObjectId(syncariId)), set(prod_family_c, listOfOldvals));
                        }
                    }
                }
            }else{
                log.info("Attribute Id 6054ce9f3e4301000174df8e not found");
            }
            if (changes.containsKey("6054d06768b6460001dccc89")) {
                Document change = (Document) changes.get("6054d06768b6460001dccc89");
                if ("6054d06768b6460001dccc89".equals(change.get("fieldId").toString()) && prod_line_c.equals(change.get("apiName").toString())) {
                    log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                    boolean isNewValueList = change.getOrDefault("newValue","").toString().startsWith("[");
                    boolean isOldValueList = change.getOrDefault("oldValue","").toString().startsWith("[");
                    log.info("Old value for 6054d06768b6460001dccc89 is instance of list {} and newValue is {}", isOldValueList,isNewValueList );
                    if (!dryRunMode) {
                        if ((!isNewValueList) && (isOldValueList)){
                            String oldValues = StringUtils.strip(StringUtils.strip(change.getOrDefault("oldValue","").toString(),"["),"]");
                            String [] oldValArray = oldValues.split(",");
                            List<String> listOfOldvals = new ArrayList<>();
                            for (String x : oldValArray) {
                                listOfOldvals.add(x.trim());
                            }
                            log.info("List of values to be updated for 6054d06768b6460001dccc89 is {}",listOfOldvals);
                            account.findOneAndUpdate(new Document("_id", new ObjectId(syncariId)), set(prod_line_c, listOfOldvals));
                        }
                    }
                }
            }else{
                log.info("Attribute Id 6054d06768b6460001dccc89 not found in transaction");
            }
        }
    }
}
