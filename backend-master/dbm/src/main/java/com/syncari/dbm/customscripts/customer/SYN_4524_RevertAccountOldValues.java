package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.model.Filters;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.extern.slf4j.Slf4j;

import static com.mongodb.client.model.Updates.*;

import java.time.Instant;

@Slf4j
public class SYN_4524_RevertAccountOldValues {

    @ChangeSet(order = "001", id = "revertAccountOldValues", author = "sudee")
    public void revertOldValuesForAccountNameOwnerId(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> syncariAccount = template.getCollection("syncari_account");

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);

        transactionLogs.find(Filters.and(new Document("entityName", "account"), Filters.gte("createdAt", "2021-09-01T02:43:00.000Z")))
            .forEach((Block<? super Document>) doc -> {
                ObjectId transactionLogId = doc.getObjectId("_id");
                String syncariId = (String) doc.get("syncariId");
                Document changes = (Document) doc.get("changes");
                // We are interested in reverting just two attributes. Account "Name" and "OwnerId". 
                // We hardcode the fieldIds here to revert specifically to those values.
                if (changes.containsKey("6053b21e68b6460001dcad31")) { 
                    Document change = (Document) changes.get("6053b21e68b6460001dcad31");
                    if ("6053b21e68b6460001dcad31".equals(change.get("fieldId").toString()) && "Name".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("Name", change.get("oldValue").toString()));
                        }
                    }
                } 
                if (changes.containsKey("6053b21e68b6460001dcad1e")) {
                    Document change = (Document) changes.get("6053b21e68b6460001dcad1e");
                    if ("6053b21e68b6460001dcad1e".equals(change.get("fieldId").toString()) && "OwnerId".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("OwnerId", change.get("oldValue").toString()));
                        }
                    }
                }
            });
    }

    @ChangeSet(order = "002", id = "revertOldValuesForAccountNameOwnerId2", author = "sudee")
    public void revertOldValuesForAccountNameOwnerId2(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> syncariAccount = template.getCollection("syncari_account");

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        
        transactionLogs.find(Filters.and(new Document("entityName", "account"), 
                Filters.gte("createdAt", Instant.parse("2021-09-01T02:43:00.000Z"))))
            .forEach((Block<? super Document>) doc -> {
                log.info("transaction log : {} ", doc);
                ObjectId transactionLogId = doc.getObjectId("_id");
                String syncariId = (String) doc.get("syncariId");
                Document changes = (Document) doc.get("changes");
                // We are interested in reverting just two attributes. Account "Name" and "OwnerId". 
                // We hardcode the fieldIds here to revert specifically to those values.
                if (changes.containsKey("6053b21e68b6460001dcad31")) { 
                    Document change = (Document) changes.get("6053b21e68b6460001dcad31");
                    if ("6053b21e68b6460001dcad31".equals(change.get("fieldId").toString()) && "Name".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("Name", change.get("oldValue").toString()));
                        }
                    }
                } 
                if (changes.containsKey("6053b21e68b6460001dcad1e")) {
                    Document change = (Document) changes.get("6053b21e68b6460001dcad1e");
                    if ("6053b21e68b6460001dcad1e".equals(change.get("fieldId").toString()) && "OwnerId".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("OwnerId", change.get("oldValue").toString()));
                        }
                    }
                }
            });
    }

    @ChangeSet(order = "003", id = "revertOldValuesForAccountNameOwnerId3", author = "sudee")
    public void revertOldValuesForAccountNameOwnerId3(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> syncariAccount = template.getCollection("syncari_account");

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        
        transactionLogs.find(Filters.and(new Document("entityName", "account"), 
                Filters.gte("createdAt", Instant.parse("2021-09-01T02:43:00.000Z"))))
            .forEach((Block<? super Document>) doc -> {
                log.info("transaction log : {} ", doc.getObjectId("_id"));
                ObjectId transactionLogId = doc.getObjectId("_id");
                String syncariId = (String) doc.get("syncariId");
                Document changes = (Document) doc.get("changes");
                // We are interested in reverting just two attributes. Account "Name" and "OwnerId". 
                // We hardcode the fieldIds here to revert specifically to those values.
                if (changes.containsKey("6053b21e68b6460001dcad31")) { 
                    Document change = (Document) changes.get("6053b21e68b6460001dcad31");
                    if ("6053b21e68b6460001dcad31".equals(change.get("fieldId").toString()) && "OwnerId".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("OwnerId", change.get("oldValue").toString()));
                        }
                    }
                } 
                if (changes.containsKey("6053b21e68b6460001dcad1e")) {
                    Document change = (Document) changes.get("6053b21e68b6460001dcad1e");
                    if ("6053b21e68b6460001dcad1e".equals(change.get("fieldId").toString()) && "Name".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("Name", change.get("oldValue").toString()));
                        }
                    }
                }
            });
    }

    @ChangeSet(order = "004", id = "revertOldValuesForAccountNameOwnerId4", author = "sudee")
    public void revertOldValuesForAccountNameOwnerId4(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> syncariAccount = template.getCollection("syncari_account");

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        
        transactionLogs.find(Filters.and(new Document("entityName", "account"), 
                Filters.gte("createdAt", Instant.parse("2021-09-01T02:43:00.000Z"))))
            .forEach((Block<? super Document>) doc -> {
                log.info("transaction log : {} ", doc.getObjectId("_id"));
                ObjectId transactionLogId = doc.getObjectId("_id");
                String syncariId = (String) doc.get("syncariId");
                Document changes = (Document) doc.get("changes");
                // We are interested in reverting just two attributes. Account "Name" and "OwnerId". 
                // We hardcode the fieldIds here to revert specifically to those values.
                if (changes.containsKey("6053b21e68b6460001dcad31")) { 
                    Document change = (Document) changes.get("6053b21e68b6460001dcad31");
                    if ("6053b21e68b6460001dcad31".equals(change.get("fieldId").toString()) && "OwnerId".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("OwnerId", change.get("oldValue").toString()));
                            log.info("Successfully updated syncariId {} ", syncariId);
                        }
                    }
                } 
                if (changes.containsKey("6053b21e68b6460001dcad1e")) {
                    Document change = (Document) changes.get("6053b21e68b6460001dcad1e");
                    if ("6053b21e68b6460001dcad1e".equals(change.get("fieldId").toString()) && "Name".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", syncariId), set("Name", change.get("oldValue").toString()));
                            log.info("Successfully updated syncariId {} ", syncariId);
                        }
                    }
                }
            });
    }

    @ChangeSet(order = "005", id = "revertOldValuesForAccountNameOwnerId5", author = "sudee")
    public void revertOldValuesForAccountNameOwnerId5(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        MongoCollection<Document> transactionLogs = template.getCollection("transactionLog");
        MongoCollection<Document> syncariAccount = template.getCollection("syncari_account");

        log.info("Running this tool in dryrun mode: {} ", dryRunMode);
        
        transactionLogs.find(Filters.and(new Document("entityName", "account"), 
                Filters.gte("createdAt", Instant.parse("2021-09-01T02:43:00.000Z"))))
            .forEach((Block<? super Document>) doc -> {
                log.info("transaction log : {} ", doc.getObjectId("_id"));
                ObjectId transactionLogId = doc.getObjectId("_id");
                String syncariId = (String) doc.get("syncariId");
                Document changes = (Document) doc.get("changes");
                // We are interested in reverting just two attributes. Account "Name" and "OwnerId". 
                // We hardcode the fieldIds here to revert specifically to those values.
                if (changes.containsKey("6053b21e68b6460001dcad31")) { 
                    Document change = (Document) changes.get("6053b21e68b6460001dcad31");
                    if ("6053b21e68b6460001dcad31".equals(change.get("fieldId").toString()) && "OwnerId".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", new ObjectId(syncariId)), set("OwnerId", change.get("oldValue").toString()));
                            log.info("Successfully updated syncariId {} ", syncariId);
                        }
                    }
                } 
                if (changes.containsKey("6053b21e68b6460001dcad1e")) {
                    Document change = (Document) changes.get("6053b21e68b6460001dcad1e");
                    if ("6053b21e68b6460001dcad1e".equals(change.get("fieldId").toString()) && "Name".equals(change.get("apiName").toString())) {
                        log.info("Reverting transactionLogId {}, syncariId {}, change {}", transactionLogId, syncariId, change);
                        if (!dryRunMode) {
                            syncariAccount.findOneAndUpdate(new Document("_id", new ObjectId(syncariId)), set("Name", change.get("oldValue").toString()));
                            log.info("Successfully updated syncariId {} ", syncariId);
                        }
                    }
                }
            });
    }

    /**
     * query db.transactionLog.find({"entityName":"account","createdAt":{$gte:ISODate("2021-09-01T02:43:00.000Z")}}).limit(1).pretty();
     * Sample record,
        "_id" : ObjectId("612ee98b488867000103bc91"),
        "syncariId" : "612ee5974888670001036f74",
        "entityName" : "account",
        "changes" : {
            "6053b21e68b6460001dcad31" : {
                "fieldId" : "6053b21e68b6460001dcad31",
                "incomingExternalValues" : {
                    "605a590b47281c0001da09ca" : "67928456"
                },
                "outgoingExternalValues" : {
                    
                },
                "apiName" : "OwnerId",
                "oldValue" : "6104134f37d0740001bb5d48",
                "newValue" : "605e2c7892d5550001e78969",
                "timestamp" : NumberLong("1630464395973")
            },
            "6053b21e68b6460001dcad1e" : {
                "fieldId" : "6053b21e68b6460001dcad1e",
                "incomingExternalValues" : {
                    "605a590b47281c0001da09b7" : "American Autowire"
                },
                "outgoingExternalValues" : {
                    
                },
                "apiName" : "Name",
                "oldValue" : "Kindred Bravely",
                "newValue" : "American Autowire",
                "timestamp" : NumberLong("1630464395973")
            },
            "612ec2cb1cda310001d82d7a" : {
                "fieldId" : "612ec2cb1cda310001d82d7a",
                "incomingExternalValues" : {
                    "605a590b47281c0001da099e" : NumberLong("6697019531")
                },
                "outgoingExternalValues" : {
                    
                },
                "apiName" : "Hubspot_Company_ID",
                "newValue" : "6697019531",
                "timestamp" : NumberLong("1630464395973")
            }
        },
     */

}