package com.syncari.dbm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.dbm.dbclient.CustomScriptExecutor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Slf4j
@Command(name = "customscript", header = "Apply custom scripts on Syncari DB or one or more customer DBs")
public class CustomScriptCommand implements Runnable {
    private static final String ALL = "all";

    private static final String SYNCARI = "syncari";

    private static final String CUSTOMER = "customer";
    @Autowired
    SyncariContextHandler syncariContextHandler;
    @Autowired
    OrganizationRepo orgRepo;
    @Autowired
    ApplicationContext applicationContext;

    @Option(required = true, description = "Target system. Specifiy 'syncari' or 'customer'", names = { "--target",
            "-t" })
    String target;

    @Option(description = "Help", names = { "-h", "--help", "-?", "-help" }, required = false)
    private boolean helpRequested;

    @Option(description = "Comma separated list of syncari ids, or the special value 'all'. Required when target is 'customer'", names = {
            "-s", "--sids" }, required = false, arity = "0..*")
    private String[] sid;

    @Option(description = "Custom script class name", names = { "-sn", "--scriptname" }, required = true)
    private String scriptClassName;

    @Option(description = "Dry run mode", names = { "-d", "--dryrun" }, required = false)
    private String dryRun;

    @Option(description = "Turn off parallel processing", names = { "-np", "--parallel" }, required = false)
    private String parallel;

    @Option(description = "Additional Parameters", names = { "-p", "--params" }, split = "\\#", required = false)
    private Map<String,String> paramMap;

    @Autowired
    CustomScriptExecutor customScriptExecutor;

    @Override
    public void run() {

        if (StringUtils.isEmpty(scriptClassName)) {
            throw new SyncariValidationException("'scriptClassName' parameter cannot be empty.");
        }

        if (!StringUtils.isEmpty(dryRun)) {
            System.setProperty("dryRun", dryRun);
        }

        // set additional parameters as system properties
        if (paramMap != null) {
            paramMap.entrySet().stream().forEach(p -> System.setProperty(p.getKey(), p.getValue()));
        }

        if (isSyncariDB()) {
            MigrationContext.setApplicationContext(applicationContext);
            customScriptExecutor.migrateSyncari(scriptClassName);
            log.info("Custom script applied for " + target);
            return;
        } else if (isCustomerDB()) {
            if (ObjectUtils.isEmpty(sid)) {
                log.error("ERROR: At least one sid required when target is 'customer'.");
                return;
            }
            List<String> sids = Arrays.asList(sid);
            sids = sids.stream().map(x -> x.trim()).filter(x -> !StringUtils.isEmpty(x)).collect(Collectors.toList());
            // Proceed here if its to migrate customer via mongobee.
            if (sids.contains(ALL)) {
                Stream<Pair<Organization, Instance>> instances = orgRepo.findAllActiveCustomers().stream()
                    .flatMap(org -> org.getActiveInstances().stream().map(instance -> Pair.of(org, instance)));
                doMigrate(instances);
            } else {
                for (String syncariId : sid) {
                    var instances = orgRepo.findBySyncariId(syncariId).stream().filter(org->org.isActive()).flatMap(
                        org -> org.getActiveInstance(syncariId).stream().map(instance -> Pair.of(org, instance)));
                    doMigrate(instances);
                }
            }
            log.info("Applied custom script for " + target + ":" + sids + ":" + sid.length);
        }
    }

    private void doMigrate(Stream<Pair<Organization, Instance>> instances) {
        Stream<Pair<Organization, Instance>> stream = instances.parallel();
        if(!StringUtils.isBlank(parallel) && "false".equalsIgnoreCase(parallel)) {
            stream = instances;
        }
        List<MigrationResult> failures = stream.map(pair -> doMigrate(pair.getLeft(), pair.getRight())).filter(r->r.hasFailed()).collect(Collectors.toList());
        if(!failures.isEmpty()){
            failures.forEach(failure -> log.error("Custom script failed {}",failure));
            throw new RuntimeException("Some custom scripts have failed.");
        }
    }

    private MigrationResult doMigrate(Organization organization, Instance instance) {
        try {
            log.info("Applying custom script on Instance {}:{} in Organization {}", instance.getName(), instance.getSyncariId(), organization.getName());
            syncariContextHandler.setContext(instance.getSyncariId());
            MigrationContext.setApplicationContext(applicationContext);
            MigrationContext.setSyncariId(instance.getSyncariId());
            customScriptExecutor.migrateCustomers(scriptClassName);
        }catch(Exception e){
            log.error(e.getMessage(),e);
            log.error("Custom script failed for Instance {}:{} in Organization {}", instance.getName(), instance.getSyncariId(), organization.getName());
            return new MigrationResult(organization, instance, e);
        } finally {
            SyncariContext.resetAll();
            MigrationContext.clear();
        }
        return new MigrationResult(organization, instance, null);
    }

    private boolean isSyncariDB() {
        return SYNCARI.equals(target);
    }

    private boolean isCustomerDB() {
        return CUSTOMER.equals(target);
    }
}