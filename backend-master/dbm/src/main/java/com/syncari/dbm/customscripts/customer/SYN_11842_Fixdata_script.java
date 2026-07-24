package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.EntityData;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FieldChange;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.repositories.customer.EntityDatabaseRepo;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;

@Slf4j
public class SYN_11842_Fixdata_script {
    @ChangeSet(order = "001", id = "findMappingAdvanceDedupeConfigPredicate", author = "rohit", runAlways = true)
    public void findMappingAdvanceDedupeConfigPredicate(MongoTemplate template) {
        /*TransactionLogRepo transactionLogRepo = MigrationContext.getTransactionLogRepo();
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));


        // Create Map of entityName -> Map(fieldName, fieldId)
        Map<String, List<String>> entityField = Map.of("phone__c", List.of("PHONEID","617c088170133400013253d0"), "compphone",List.of("PHONEID",
                "617c087170133400013251c5"), "address",List.of("ADDRESSID","617c08637013340001325093"),"compaddr",List.of("ADDRESSID",
                "617c08707013340001325182"));


        //Map<String, List<String>> entityField = Map.of("contact__c1", List.of("last_name","62b1110046d945c9b73bbd4b"));
        // Create another Map between fieldName -> constant
        Map<String, String> prefixMap = Map.of("PHONEID","PHO","ADDRESSID", "ADD");
        //Map<String, String> prefixMap = Map.of("last_name","Lname");

//        String startDate = "2023-03-24T00:00:00.000Z";
//        String endDate = "2023-03-31T17:49:55.151Z";
        var startDate = System.getProperty("sd");
        var endDate = System.getProperty("ed");
        Date start = DateUtil.parse(startDate, DateUtil.dateFormatMillis);
        Date end = DateUtil.parse(endDate, DateUtil.dateFormatMillis);
        String operation="create";
        Map<String, List<String>> impactedEntityAndSyncariIdsMap = new HashMap<>();
        Map<String, TransactionLog> impactedSyncariIdTxn = new HashMap<>();

        // For each entity in Map search for transaction between dates (find dates)
        entityField.forEach((k,v) -> {
            Pageable nextPage = PageRequest.of(0, 1000);
            List<String> impactedSynacriIds = new ArrayList<>();
            List<String> fieldNameAndId = entityField.get(k);
            String fieldId = fieldNameAndId.get(1);
            String fieldName = fieldNameAndId.get(0);
            Page<TransactionLog> transactionLogs = transactionLogRepo.findEntityOperationByRange(nextPage, start, end, k, operation);
            while (transactionLogs.hasContent()) {
                log.info("Found operation {} Logs size {}",operation,transactionLogs.getSize());
                nextPage = nextPage.next();
                for (TransactionLog tlog : transactionLogs.getContent()){
                    Map<String, FieldChange> changes = tlog.getChanges();
                    if (changes.containsKey(fieldId)){
                        FieldChange fieldChange = changes.get(fieldId);
                        if ((null != fieldChange.getNewValue()) && (fieldChange.getNewValue() instanceof String)){
                            if (((String)fieldChange.getNewValue()).contains(".0")){
                                impactedSynacriIds.add(tlog.getSyncariId());
                                impactedSyncariIdTxn.put(tlog.getSyncariId(),tlog);
                                log.info("Syncari Id {} impacted for field {}", tlog.getSyncariId(), fieldName);
                            }else{
                                log.info("Syncari Id {} not impacted for field {}", tlog.getSyncariId(), fieldName);
                            }
                        }
                    }else{
                        log.info("Syncari Id {} not impacted for field {}", tlog.getSyncariId(), fieldName);
                    }
                }
                transactionLogs = transactionLogRepo.findEntityOperationByRange(nextPage, start, end, k, operation);
            }
            impactedEntityAndSyncariIdsMap.put(k, impactedSynacriIds);
            log.info("For entity {} Number of impacted syncariIds is {}", k, impactedSynacriIds.size());
            });

        SchemaService schemaService = MigrationContext.getSchemaService();
        EntityDatabaseRepo entityRepo = MigrationContext.getEntityDatabaseRepo();
        EntityRepoService repoService = MigrationContext.getRepoService();

        impactedEntityAndSyncariIdsMap.forEach((k,v)-> {
            Optional<EntityDefinition> edef = schemaService.getSyncariEntityByName(k);
            v.forEach(sId -> {
                Optional<EntityData> entityDataInSyncariDb = entityRepo.findById(edef.get(), sId);
                entityDataInSyncariDb.ifPresentOrElse(ed -> {
                    // update syncari id  and log transaction
                    // get new value from transaction log and remove .0 out of it and add lpad 7
                    List<String> fields = entityField.get(k);
                    String fieldName = fields.get(0);
                    String prefix = prefixMap.get(fieldName);
                    Map<String, Object> values = ed.getValues();
                    if (values.containsKey(fieldName)){
                        Object val = values.get(fieldName);
                        String[] result = ((String)val).split(prefix);
                        if ((null != result) && (result.length > 1)){
                            String lpadString = result[1].substring(0, result[1].length()-2);
                            String newValue = prefix + StringUtils.leftPad(lpadString, 7, "0");
                            if (!dryRunMode){
                                EntityData entityDataToUpdate = new EntityData().withId(sId);
                                entityDataToUpdate.addValue(fieldName, newValue);
                                repoService.update(entityDataToUpdate,edef.get());
                            }else{
                                log.info("Running in dry run mode not updating syncariId {} for entity {} for field {}, new value would be {}",sId, k, fieldName, newValue);
                            }
                        }else{
                            log.info("Not update for syncariId {} as no record exists in syncari collection with same prefix {} ", sId, prefix);
                        }
                    }
                },() -> {
                    log.info("Not update for syncariId {} as no record exists in syncari ", sId);
                });
            });
        });
*/
    }
/*
    public static void main(String args []){
        String val = "PHO0567.0";
        String[] result = ((String)val).split("PHO");
        String lpadString = result[1].substring(0, result[1].length()-2);
        String newValue = "PHO" + StringUtils.leftPad(lpadString, 7, "0");
        System.out.println(newValue);
    }*/
}
