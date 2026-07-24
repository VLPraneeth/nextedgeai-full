package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableId;
import com.syncari.core.MigrationContext;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.event.store.FieldDefinition;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.ProvisioningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SYN_16927_DeleteBQTable {
    public static final String MOVE_TRANSACTION_ERRORS = "moveTransactionErrors";

    @ChangeSet(order = "001", id = "deletebqmoveTransactionErrorTable", author = "rohit", runAlways = true)
    public void deletebqmoveTransactionErrorTable(MongoTemplate template) {

        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        BigQueryHelper helper = MigrationContext.getBigQueryHelper();
        List<Organization> orgs = organizationRepo.findAllCustomers();
        orgs.forEach(o -> {
            List<Instance> instances = o.getInstances();
            instances.forEach(i -> {
                try{
                    if (!dryRun){
                        Map<String, List<FieldDefinition>> tables = getTables(i.getSyncariId());
                        for (Map.Entry<String, List<FieldDefinition>> entry : tables.entrySet()) {
                            TableId tableId = TableId.of(i.getSyncariId(), entry.getKey());
                            log.info("Deleting table moveTransactionErrors for syncariid {}", i.getSyncariId());
                            helper.deleteTableIfExists(tableId);
                        }
                    }else{
                        log.info("Running in dry run mode , not deleting table moveTransactionErrors for syncariid {}", i.getSyncariId());
                    }

                }catch (Exception e){
                    log.error("Exception occurred while deleting table moveTransactionErrors for syncariid {}", i.getSyncariId());
                }

            });
        });


    }

    private static Map<String, List<FieldDefinition>> getTables(String syncariId) {
        Map<String, List<FieldDefinition>> tables = new LinkedHashMap<>();
        tables.put(MOVE_TRANSACTION_ERRORS, getMoveTransactionErrors(syncariId));
        return tables;
    }

    private static List<FieldDefinition> getMoveTransactionErrors(String syncariId) {
        List<FieldDefinition> fields = new ArrayList<>();
        fields.add(new FieldDefinition(syncariId, MOVE_TRANSACTION_ERRORS, "failedId", StandardSQLTypeName.STRING, true));
        fields.add(new FieldDefinition(syncariId, MOVE_TRANSACTION_ERRORS, "errors", StandardSQLTypeName.STRING, true));
        return fields;
    }
}
