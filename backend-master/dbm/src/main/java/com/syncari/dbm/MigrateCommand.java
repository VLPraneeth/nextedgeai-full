package com.syncari.dbm;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.commands.DBMigrator;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.syncari.OrganizationRepo;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
@Command(name = "migrate", header = "Migrate Syncari DB or one or more Customer DBs")
public class MigrateCommand implements Runnable {
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
            "-s", "--sids" }, required = false, arity = "1..*")
    private String[] sid;

    @Autowired
    DBMigrator migrator;

    @Override
    public void run() {

        if (isSyncariDB()) {
            MigrationContext.setApplicationContext(applicationContext);
            migrator.migrateSyncari();
            log.info("Migrated " + target);
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
            log.info("Migrated " + target + ":" + sids + ":" + sid.length);
        }
    }

    private void doMigrate(Stream<Pair<Organization, Instance>> instances) {
        List<MigrationResult> failures = instances.parallel().map(pair -> doMigrate(pair.getLeft(), pair.getRight())).filter(r->r.hasFailed()).collect(Collectors.toList());
        if(!failures.isEmpty()){
            failures.forEach(failure -> log.error("Migration failed {}",failure));
            throw new RuntimeException("Some migrations have failed.");
        }
    }

    private MigrationResult doMigrate(Organization organization, Instance instance) {
        try {
            log.info("Migrating Instance {}:{} in Organization {}", instance.getName(), instance.getSyncariId(), organization.getName());
            syncariContextHandler.setContext(instance.getSyncariId());
            MigrationContext.setApplicationContext(applicationContext);
            MigrationContext.setSyncariId(instance.getSyncariId());
            migrator.migrateCustomers();
        }catch(Exception e){
            log.error(e.getMessage(),e);
            log.error("Migration failed for Instance {}:{} in Organization {}", instance.getName(), instance.getSyncariId(), organization.getName());
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

class MigrationResult {

    final Organization organization;
    final Instance instance;
    final Exception exception;

    MigrationResult(Organization organization, Instance instance, Exception exception) {
        this.organization = organization;
        this.instance = instance;
        this.exception = exception;
    }

    public boolean hasFailed(){
        return exception!= null;
    }

    @Override
    public String toString() {
        return "MigrationResult{" +
                "organization=" + organization.getName() +
                ", instance=" + instance.getSyncariId() + ":"+instance.getName() +
                ", exception=" + (exception==null? "": ExceptionUtils.getStackTrace(exception))+
                '}';
    }
}
