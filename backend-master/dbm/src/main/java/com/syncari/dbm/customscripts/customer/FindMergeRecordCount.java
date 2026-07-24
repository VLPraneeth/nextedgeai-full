package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.MergeRequest;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.CollectionUtils.map;

@Slf4j
public class FindMergeRecordCount {

    @ChangeSet(order = "001", id = "findMappingAdvanceDedupeConfigPredicate", author = "rohit", runAlways = true)
    public void findMappingAdvanceDedupeConfigPredicate(MongoTemplate template) {
        /*String instanceId = SyncariContext.getSyncariId();
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        //"2023-02-22T00:00:00.000Z";"2023-03-8T17:49:55.151Z";//"merge_report_only";//
        var startDate = System.getProperty("sd");
        var endDate = System.getProperty("ed");
        var operation = "merge";
        Map<String, List<String>> instanceAndEntity = new HashMap<>();

        instanceAndEntity.putAll(Map.of("JPIQWE", List.of("contact", "account","lead"),
                "2OG73P", List.of("contact"), "HGNWQW", List.of("mkto_lead", "contact", "account"), "JMDO6I", List.of("contact"),
                "CJMWVY", List.of("contact", "lead"),"8FMWLM", List.of("Lead__c"),
                "YZYP5F", List.of("contact","lead","account"),"NWKOIT", List.of("contact"),
                "ZLN8YB", List.of("contact","lead"),"PVZKNG", List.of("account")));
        instanceAndEntity.putAll(Map.of("A0QYKX", List.of("leaddedupetest"),"X73QQV", List.of("stage")));

        instanceAndEntity.putAll(Map.of("RF5ANT", List.of("contact", "account","lead"),
                "O6Y4HR", List.of("account"),
                "LWPM9N", List.of("contact", "lead"),"FILVMO", List.of("contact__c"),
                "QZQLYX", List.of("contact","lead","account"),"6IFND6", List.of("contact"),
                "BNGQ8L", List.of("contact","lead"),"S9X0XV", List.of("dedupe_and_merge"),"KLVIGE", List.of("contact__c"), "syncari_admin", List.of("contact__c2")));

        TransactionLogRepo transactionLogRepo = MigrationContext.getTransactionLogRepo();
        EntityRepoService repoService = MigrationContext.getRepoService();
        SchemaService schemaService = MigrationContext.getSchemaService();
        EntityDatabaseRepo entityRepo = MigrationContext.getEntityDatabaseRepo();
        ConnectorService connectorService = MigrationContext.getConnectorService();
        UserService userService = MigrationContext.getUserService();

        Date start = DateUtil.parse(startDate, DateUtil.dateFormatMillis);
        Date end = DateUtil.parse(endDate, DateUtil.dateFormatMillis);
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });
        if (instanceAndEntity.containsKey(instanceId)){
            List<String> listOfEntities = instanceAndEntity.get(instanceId);
            listOfEntities.forEach(entityName -> {
                Pageable nextPage = PageRequest.of(0, 1000);
                Page<TransactionLog> transactionLogs = transactionLogRepo.findEntityOperationByRange(nextPage, start, end, entityName, operation);
                long alwaysCounter = 0;
                long whenBlankCounter = 0;
                Set<String> listOfSyncariIdsImpacted = new HashSet<>();
                Map<String,Map<String, String>> fieldsAndOperatorToCheckForEntity = new HashMap<>();
                Map<String,TransactionLog> impactedSyncariIdMergeTransaction = new HashMap<>();

                Set<String> whenBlankFields = new HashSet<>();
                Set<String> alwaysFields = new HashSet<>();
                Map<String, String> fieldIdsMap = new HashMap<>();
                Map<String, String> fields = new HashMap<>();
                while (transactionLogs.hasContent()) {
                    log.info("Found operation {} Logs size {}",operation,transactionLogs.getSize());
                    nextPage = nextPage.next();
                    for (TransactionLog tlog : transactionLogs.getContent()){
                        MergeOperation mergeOperation = tlog.getMergeOperation();
                        var record = mergeOperation.getWinningRecord();
                        Map<String, Object> winningRecordValues = record.getValues();
                        String syncarIdToCheck = record.getId();


                        var mergeInfo = mergeOperation.getMergeInfo();
                        Map<String, Object> fmpolicies = mergeInfo.getFieldMergePolicies();
                        boolean alwaysCounterIncreased = false;
                        boolean whenBlankCounterIncreased = false;
                        for (String key : fmpolicies.keySet()){
                            Object fieldValue = winningRecordValues.getOrDefault(key, "");
                            boolean hasNoFieldValue = Objects.isNull(fieldValue) || (fieldValue instanceof String && StringUtils.isBlank(fieldValue.toString()));
                            if (hasNoFieldValue){
                                listOfSyncariIdsImpacted.add(syncarIdToCheck);
                                impactedSyncariIdMergeTransaction.put(syncarIdToCheck, tlog);

                                Object value = fmpolicies.get(key);
                                Map<String, String> overridePolicy = (Map)((Map)value).get("overridePolicy");
                                String overridePolicyVal = overridePolicy.get("value");
                                if (overridePolicyVal.equalsIgnoreCase("ALWAYS")){
                                    if (!alwaysCounterIncreased){
                                        alwaysCounter += 1;
                                        alwaysCounterIncreased = true;
                                    }
                                    alwaysFields.add(key);
                                }
                                if (overridePolicyVal.equalsIgnoreCase("WHEN_BLANK")){
                                    if (!whenBlankCounterIncreased){
                                        whenBlankCounter += 1;
                                        whenBlankCounterIncreased = true;
                                    }
                                    whenBlankFields.add(key);
                                }
                                Map<String, Object> fieldMergePredicate = (Map)((Map)value).getOrDefault("expressionMap", Map.of());
                                List<Object> predicates = (List)fieldMergePredicate.getOrDefault("predicates", List.of());
                                for(Object p : predicates){
                                    {
                                        String operator = (String)((Map)p).getOrDefault("operator","");
                                        if (StringUtils.isNotEmpty(operator)){
                                            if ( (operator.equalsIgnoreCase("latest_with_value")) || (operator.equalsIgnoreCase("latest_created_with_value")) ||
                                                    (operator.equalsIgnoreCase("oldest_created_with_value")) || (operator.equalsIgnoreCase("earliest_with_value"))){
                                                log.info("Customer with operator in field merge policy is {} with entity name {} and fieldName {} ", MigrationContext.getSyncariId(), entityName, key);
                                                Map<String, String> left = (Map)((Map)p).getOrDefault("left",Map.of());
                                                String fieldId = left.get("value");
                                                fields.put(key, operator);
                                                fieldIdsMap.put(key, fieldId);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        fieldsAndOperatorToCheckForEntity.put(entityName, fields);

                    }
                    transactionLogs = transactionLogRepo.findEntityOperationByRange(nextPage, start, end, entityName, operation);
                }
                log.info("Number of merge records {} for entity {} for customer {}", listOfSyncariIdsImpacted.size(), entityName, instanceId);
                log.info("Number of merge records with always for field merge policy {} for entity {} for customer {}", alwaysCounter, entityName, instanceId);
                log.info("Number of merge records with when blank for field merge policy {} for entity {} for customer {} with whenblankfields {}", whenBlankCounter, entityName, instanceId);
                log.info("When blank fields in all transactions for field merge policy {} for entity {} for customer {}", whenBlankFields, entityName, instanceId);
                log.info("Always fields in all transactions for field merge policy {} for entity {} for customer {}", alwaysFields, entityName, instanceId);
                log.info("Field Ids impacted {}", fieldIdsMap);
                log.info("fieldsAndOperatorToCheckForEntity {}", fieldsAndOperatorToCheckForEntity);
                log.info("fields {}", fields);


                Map<String, String> fieldsImpactedWithOperator = fieldsAndOperatorToCheckForEntity.getOrDefault(entityName, Map.of());

                Optional<EntityDefinition> edef = schemaService.getSyncariEntityByName(entityName);
                Set<String> syncariIdToBeChanged = new HashSet<>();
                listOfSyncariIdsImpacted.forEach(impactedSyncariId -> {
                    // get winning record and all losing records
                    // look for operator to be used based on operator find the winning record for respective field from losers or winners, if loser is the record then just update value from loser
                    // If winner is the record then look for latest transaction with that field which has value and update to that value

                    TransactionLog transactionLog = impactedSyncariIdMergeTransaction.getOrDefault(impactedSyncariId, null);
                    Optional<EntityData> entityDataInSyncariDb = entityRepo.findById(edef.get(), impactedSyncariId);
                    List<Boolean> isAnythingChanged = new ArrayList<>();
                    entityDataInSyncariDb.ifPresentOrElse(ed -> {
                        // Assuming that Object in value is string
                        Map<String, Object> values = ed.getValues();
                        EntityData entityDataToUpdate = new EntityData().withId(ed.getSyncariEntityId());
                        if (null != transactionLog){
                            MergeOperation mergeOperation = transactionLog.getMergeOperation();
                            EntityData winningRecord = mergeOperation.getWinningRecord();
                            List<EntityData> allLosingRecords = mergeOperation.getLosingRecords();
                            List<EntityData> allRecords = new ArrayList<>();
                            allRecords.add(winningRecord);allRecords.addAll(allLosingRecords);

                            fieldsImpactedWithOperator.forEach((k,v) -> {
                                if (MapUtils.isNotEmpty(values)){
                                    Object impactFieldValue = values.get(k);
                                    boolean hasNoValue = Objects.isNull(impactFieldValue) || (impactFieldValue instanceof String && StringUtils.isBlank(impactFieldValue.toString()));
                                    if (hasNoValue){
                                        Optional<EntityData> winningRecordForField = getWinningRecord(allRecords,v,k);
                                        String fieldIdToCheck = fieldIdsMap.get(k);
                                        winningRecordForField.ifPresentOrElse(wR -> {
                                            if (wR.getId() != winningRecord.getId()){
                                                // If wR is one of the losers , add loser value in a record to update Value,
                                                Object valueToBeAdded = wR.getValue(k);
                                                entityDataToUpdate.addValue(k,valueToBeAdded);
                                                isAnythingChanged.add(true);
                                                syncariIdToBeChanged.add(impactedSyncariId);
                                                log.info("Impacted Syncari Id {} for entity {} and field is {}, winning record is loser",impactedSyncariId, entityName, k);
                                            }else{
                                                // If wR is winning record of merge
                                                // Identify value for field in latest transaction and update that to entity data
                                                Pageable transactionPage = PageRequest.of(0, 1000);
                                                Page<TransactionLog> otherTransactions = transactionLogRepo.findBySyncariId(transactionPage, impactedSyncariId);
                                                while (otherTransactions.hasContent()) {
                                                    log.info("Found transactions with size {} for syncariId {}", otherTransactions.getSize(), impactedSyncariId);
                                                    transactionPage = transactionPage.next();
                                                    for (TransactionLog othertlog : otherTransactions.getContent()) {
                                                        Map<String, FieldChange> fieldChangeMap = othertlog.getChanges();
                                                        if (MapUtils.isNotEmpty(fieldChangeMap)){
                                                            // Get fieldId of k
                                                            FieldChange change = fieldChangeMap.get(fieldIdToCheck);
                                                            if ((null != change) && (null != change.getNewValue())){
                                                                Object newValue = change.getNewValue();
                                                                log.info("Impacted Syncari Id {} for entity {} and field is {}, winning record is one from transactions and new value is {}",impactedSyncariId, entityName, k, newValue);
                                                                entityDataToUpdate.addValue(k,newValue);
                                                                isAnythingChanged.add(true);
                                                                syncariIdToBeChanged.add(impactedSyncariId);
                                                                break;
                                                            }
                                                        }
                                                    }
                                                    otherTransactions = transactionLogRepo.findBySyncariId(transactionPage, impactedSyncariId);
                                                }
                                            }
                                        }, () ->log.info("Could not identify winning record for field {} for syncariId {} and entity {}",k, impactedSyncariId, entityName));

                                    }else{
                                        log.info("Not updating as field value for field {} is present in syncari record for syncariId {} and entity {}",k, impactedSyncariId, entityName);
                                    }
                                }else{
                                    log.info("Not updating anything as no other value present for syncariId {} and entity {}", impactedSyncariId, entityName);
                                }
                            });
                        }else{
                            log.info("Transaction is null");
                        }
                        List<Boolean> filteredIsAnythingChanged = isAnythingChanged.stream().filter(v -> (v == true)).collect(Collectors.toList());
                        if ((!dryRunMode) && (CollectionUtils.isNotEmpty(filteredIsAnythingChanged))){
                            // Save data and create transaction repoService update should do that
                            repoService.update(entityDataToUpdate,edef.get());
                        }
                    },()-> log.info("Entity data for syncariId {} is not present, no fixup",impactedSyncariId));
                });
                log.info("Impacted SyncariIds count {} for instance {} and entity is {} ", syncariIdToBeChanged.size(), instanceId, entityName);
            });
        }else{
            log.info("SyncariId {} is not the one we are looking for so no op for this instance", instanceId);
        }
         */
    }


    private Optional<EntityData> getWinningRecord(List<EntityData> entityDataList, String operator, String field){
        if (operator.equalsIgnoreCase("latest_with_value")){
            return entityDataList.stream()
                    .filter(e -> StringUtils.isNotEmpty(getValue(field, e)))
                    .max(Comparator.comparingLong(EntityData::getLastModified));
        }else if (operator.equalsIgnoreCase("latest_created_with_value")){
            return entityDataList.stream()
                    .filter(e -> StringUtils.isNotEmpty(getValue(field, e)))
                    .max(Comparator.comparingLong(EntityData::getCreatedAt));
        }else if (operator.equalsIgnoreCase("oldest_created_with_value")){
            return entityDataList.stream()
                    .filter(e -> StringUtils.isNotEmpty(getValue(field, e)))
                    .min(Comparator.comparingLong(EntityData::getCreatedAt));
        }else if (operator.equalsIgnoreCase("earliest_with_value")){
            return entityDataList.stream()
                    .filter(e -> StringUtils.isNotEmpty(getValue(field, e)))
                    .min(Comparator.comparingLong(EntityData::getLastModified));
        }
        return null;
    }

    private String getValue(String field, EntityData data){
        if ((null != data) && (MapUtils.isNotEmpty(data.getValues())) && (null != data.getValues().getOrDefault(field, ""))){
            return data.getValues().getOrDefault(field, "").toString();
        }
        return null;
    }
}