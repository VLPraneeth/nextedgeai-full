package com.syncari.dbm.customscripts.customer;

import com.amazonaws.services.dynamodbv2.xspec.L;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.connector.EntityData;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.User;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class SYN_15781_FixMultivaluedFieldsFromTxnlog {

    @ChangeSet(order = "001", id = "fixMultivaluedfields", author = "rohit", runAlways = true)
    public void fixMultivaluedfields(MongoTemplate template) throws IOException{
        String entity = System.getProperty("entityName");
        String operation = System.getProperty("operation");
        MongoCollection<Document> txnLog = template.getCollection("transactionLog");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        int pageSize = 1000;
        List<String> operations = Arrays.asList("update", "create"); // Add your desired operations here

        var txnIterator = txnLog.find(new Document("entityName", entity).append("operation", new Document("$in", operations)).append("updatedAt",new Document("$lte", Instant.parse("2024-04-21T19:00:00.000Z")))).sort(new Document("_id", 1)).batchSize(pageSize).iterator();
        log.info("Input entity {} operation {}", entity, operations);

        SchemaService schemaService = MigrationContext.getSchemaService();
        EntityRepoService repoService = MigrationContext.getRepoService();
        UserService userService = MigrationContext.getUserService();
        Optional<EntityDefinition> edef = schemaService.getSyncariEntityByName(entity);


        LinkedHashMap<String, Map<String, Object>> values = new LinkedHashMap<>();
        while(txnIterator.hasNext()) {
            var doc = txnIterator.next();
            Document changesDoc = (Document) doc.get("changes");

            var syncariId = doc.getString("syncariId");

            if (changesDoc != null) {
                for (Map.Entry<String, Object> changes : changesDoc.entrySet()) {
                    String fieldId = changes.getKey();
                    Optional<AttributeDefinition> attribDef = schemaService.findAttribute(fieldId);
                    attribDef.ifPresentOrElse(a -> {
                        if (a.isMultiValueField()){
                            if (changes.getValue() instanceof Document) {
                                Document fieldChanges = (Document)changes.getValue();
                                if (fieldChanges.containsKey("incomingExternalValues") && fieldChanges.containsKey("newValue")) {
                                    var incomingDoc = (Document)fieldChanges.get("incomingExternalValues");
                                    log.info("Incoming doc is {} for attribute {} of syncariid {}",incomingDoc.toString(), a.getApiName(), syncariId);
                                    try{
                                        List<Document> incomingDocs = incomingDoc.values().stream().map(d -> (Document)d).collect(Collectors.toList());
                                        if (incomingDocs.size() > 0) {
                                            var fieldChange = (Document)incomingDocs.get(0);
                                            if ((fieldChange.get("value") instanceof List) && ((fieldChanges.get("newValue") instanceof List))){
                                                // iterate on transaction again and find the latest transaction changes for these fields
                                                var txnIteratorInternal = txnLog.find(new Document("entityName", entity).append("operation", new Document("$in", operations))
                                                        .append("syncariId",syncariId).append("updatedAt",new Document("$lte",  Instant.parse("2024-04-21T00:00:00.000Z")))).sort(new Document("_id", -1)).batchSize(pageSize).iterator();

                                                while(txnIteratorInternal.hasNext()) {
                                                    var internalDoc = txnIteratorInternal.next();
                                                    Document internalChangesDoc = (Document) internalDoc.get("changes");
                                                    boolean isLatestTransactionFoundWithChanges = false;
                                                    if (internalChangesDoc != null){
                                                        for (Map.Entry<String, Object> internalLoopChanges : internalChangesDoc.entrySet()) {
                                                            String internalFieldId = internalLoopChanges.getKey();
                                                            Document latestFieldChanges = (Document)internalLoopChanges.getValue();

                                                            if ((internalFieldId.equalsIgnoreCase(fieldId))
                                                                    && (internalLoopChanges.getValue() instanceof Document) && (latestFieldChanges.containsKey("incomingExternalValues")
                                                                    && latestFieldChanges.containsKey("newValue"))){
                                                                var latestIncomingDoc = (Document)latestFieldChanges.get("incomingExternalValues");
                                                                log.info("Latest Incoming doc is {} for attribute {} of syncariid {}",latestIncomingDoc, a.getApiName(), syncariId);
                                                                List<Document> latestIncomingDocs = latestIncomingDoc.values().stream().map(d -> (Document)d).collect(Collectors.toList());
                                                                if (CollectionUtils.isNotEmpty(latestIncomingDocs)){
                                                                    var latestFieldChange = (Document)latestIncomingDocs.get(0);
                                                                    // validate if this fieldChange is same in entity data or not if it is not then add it into changes
                                                                    Iterable<EntityData> edI = repoService.findRecordsByIds(edef.get(), Set.of(syncariId));
                                                                    if (null != edI){
                                                                        Optional<EntityData> eData = IteratorUtils.toList(edI.iterator()).stream().findFirst();
                                                                        eData.ifPresentOrElse(e -> {
                                                                            var valueFromLatestTxn = latestFieldChanges.get("newValue");
                                                                            Object value = e.getValue(a.getApiName());
                                                                            log.info("Value from e data is {} and from latest txn is {} for api name {} and syncariId {}", value, valueFromLatestTxn,
                                                                                    a.getApiName(), syncariId);
                                                                            if ((value instanceof List) && (valueFromLatestTxn instanceof List) && (!isTwoValSame(value, valueFromLatestTxn))){
                                                                                if (values.containsKey(syncariId)){
                                                                                    Map<String, Object> existingValues = values.get(syncariId);
                                                                                    if (existingValues.containsKey(fieldChange.getString("apiName"))){
                                                                                        Set<Object> existing = new HashSet<>((List)existingValues.get(fieldChange.getString("apiName")));
                                                                                        existing.addAll((List)valueFromLatestTxn);
                                                                                        existingValues.put(fieldChange.getString("apiName"),new ArrayList<>(existing));
                                                                                    }else{
                                                                                        existingValues.put(fieldChange.getString("apiName"),valueFromLatestTxn);
                                                                                    }
                                                                                }else{
                                                                                    Map<String, Object> apiVals = new HashMap<>();
                                                                                    apiVals.put(fieldChange.getString("apiName"), valueFromLatestTxn);
                                                                                    values.put(syncariId, apiVals);
                                                                                    log.info("Adding value {} in map for syncariid {}", apiVals, syncariId);
                                                                                }
                                                                            }else{
                                                                                log.info("Not adding value {} in map for syncariid {}", valueFromLatestTxn, syncariId);
                                                                            }
                                                                        },() -> log.info("Could not find entity data for syncariid {}", syncariId));
                                                                    }
                                                                    isLatestTransactionFoundWithChanges = true;
                                                                    break;
                                                                }
                                                            }
                                                        }

                                                        if (isLatestTransactionFoundWithChanges){
                                                            break;
                                                        }
                                                    }
                                                }

                                            }
                                        }
                                    }catch (Exception e){
                                        log.error("Error occurred {}  of syncariid {}", ExceptionUtils.getStackTrace(e), syncariId);
                                    }
                                }
                            }
                        }
                    },() -> log.info("Attribute Definition is not present id is {}  of syncariid {}", fieldId,syncariId));
                }
            }
        }


        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        File csvFile = new File(String.format("/tmp/SYN_15781_multivalued_%s_%s.csv",entity, System.currentTimeMillis()));
        FileWriter csvWriter = new FileWriter(csvFile);

        if (!dryRunMode){
            if (MapUtils.isNotEmpty(values)){
                for (Map.Entry<String, Map<String, Object>> e : values.entrySet()){
                    log.info("SyncariId {} to be updated to values {}",e.getKey(),e.getValue());
                    Iterable<EntityData> ed = repoService.findRecordsByIds(edef.get(), Set.of(e.getKey()));
                    if (null != ed){
                        Optional<EntityData> entityData = IteratorUtils.toList(ed.iterator()).stream().findFirst();
                        entityData.ifPresentOrElse(edata -> {
                            if (MapUtils.isNotEmpty(e.getValue())){
                                if (!edata.isDeleted()){
                                    EntityData entityDataToUpdate = new EntityData().withId(e.getKey());
                                    boolean updateEntity = false;
                                    for (Map.Entry<String, Object> val : e.getValue().entrySet()){
                                        String key = val.getKey();
                                        Object newVal = val.getValue();
                                        Object oldVal = edata.getValue(key);
                                        if ((newVal instanceof List) && (!isTwoValSame(newVal, oldVal))){
                                            updateEntity = true;
                                            entityDataToUpdate.addValue(val.getKey(), newVal);
                                        }
                                    }
                                    log.info("Entity data to be updated is {}",entityDataToUpdate);
                                    if (updateEntity){
                                        repoService.update(entityDataToUpdate,edef.get());
                                    }
                                }else{
                                    log.info("Syncari id {} is deleted, no need of this record", e.getKey());
                                }
                            }
                        },() -> {log.info("Entity data for syncariid {} is not present", e.getKey());});
                    }
                }
            }

        }else{
            log.info("Impacted number of records is {}", values.size());
            List<String> headerList = List.of("hsid", "apiname", "oldValue", "newValue");
            //log.info("Syncari ID,{}", headerList.stream().collect(Collectors.joining(",")));
            CSVPrinter printer = new CSVPrinter(csvWriter,CSVFormat.DEFAULT);
            if (MapUtils.isNotEmpty(values)){
                for (Map.Entry<String, Map<String, Object>> e : values.entrySet()){
                    log.info("Dry run mode, SyncariId {} to be updated to values {}",e.getKey(),e.getValue());
                    Iterable<EntityData> ed = repoService.findRecordsByIds(edef.get(), Set.of(e.getKey()));
                    if (null != ed){
                        Optional<EntityData> entityData = IteratorUtils.toList(ed.iterator()).stream().findFirst();
                        List<Object> list = new ArrayList();
                        entityData.ifPresentOrElse(edata -> {
                            if (!edata.isDeleted()){
                                boolean reportEntity = false;
                                list.add(e.getKey());
                                if (entity.equalsIgnoreCase("company__c") && (null != edata.getValue("syncari_hubspot_Hubspot_ACG_company_hs_object_id"))){
                                    list.add(edata.getValue("syncari_hubspot_Hubspot_ACG_company_hs_object_id"));
                                }else if (null != edata.getValue("syncari_hubspot_Hubspot_Lawyerist_contact_hs_object_id")){
                                    list.add(edata.getValue("syncari_hubspot_Hubspot_Lawyerist_contact_hs_object_id"));
                                }
                                for (Map.Entry<String, Object> val : e.getValue().entrySet()){
                                    String key = val.getKey();
                                    Object newVal = val.getValue();
                                    Object oldVal = edata.getValue(key);
                                    if ((newVal instanceof List) && (!isTwoValSame(newVal, oldVal))){
                                        list.add(key);
                                        list.add(newVal);
                                        list.add(oldVal);
                                        reportEntity = true;
                                    }
                                }
                                try {
                                    if (reportEntity){
                                        printer.printRecord(list);
                                    }
                                } catch (IOException exception) {
                                    exception.printStackTrace();
                                }
                            }else{
                                log.info("Dry run: Syncari id {} is deleted, no need of this record", e.getKey());
                            }
                        },()->log.info("Entity data with id {} is not present", e.getKey()));
                    }else{
                        log.info("Iterable is not present");
                    }
                }
            }
            printer.flush();
            printer.close();
        }
    }

    private boolean isTwoValSame(Object newVal, Object oldVal){
        if ((newVal instanceof List) && !(oldVal instanceof List)){
            return false;
        }else{
            if ((newVal instanceof List) && (oldVal instanceof List) && CollectionUtils.isEqualCollection(((List)newVal), ((List)oldVal))){
                return true;
            }
        }
        return false;
    }
}
