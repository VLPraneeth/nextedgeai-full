package com.syncari.api.alerts;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Instance;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.Organization;
import com.syncari.core.model.SyncStream;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.StreamService;

import com.syncari.utils.DateUtil;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class LagReporter {

    @Autowired
    SyncariContextHandler syncariContextHandler;

    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    StreamService streamService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    AppConfig appConfig;

    static String lockOwner;
    static {
        lockOwner = UUID.randomUUID().toString();
    }

    @Autowired
    LockRepo lockRepo;


    // We randomize so that every arcade instance runs every 1 hour randomly between 1-60 mins. 
    // Total 3 times (number of arcade nodes) within an hour.
    @Scheduled(cron = "0 0 0/12 * * ?")
    public void lagReport() {
        List<LagReport> lagReports = new ArrayList<>();
        log.info("Running scheduled lag reporter");

        try {
            // Each arcade node waits for a random time between 1-10 mins before starting the lag report.
            Thread.sleep(RandomUtils.nextInt(1, 600) * 1000);
        } catch (InterruptedException e) {
            log.error("Failed to sleep", e);
        }

        List<Organization> all = organizationRepo.findAllActiveCustomers();
        for(Organization organization : all) {
            List<Instance> instances = organization.getInstances();

            for (Instance instance : instances) {
                syncariContextHandler.setContext(instance.getSyncariId());
                var lockId = "lagReporter:"+  instance.getSyncariId();
                var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(30));
            }
        }

        //all = organizationRepo.findAllActiveCustomers();
        for(Organization organization : all) {
            List<Instance> instances = organization.getInstances();
            
            for(Instance instance : instances){
                syncariContextHandler.setContext(instance.getSyncariId());
                var lockId = "lagReporter:"+  instance.getSyncariId();
                try {
                    var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(30));
                    if(locked.isPresent()) {
                        log.info("Preparing lag report for {} instance", instance.getSyncariId());

                        List<SyncStream> streams = streamService.findLagReportStreams();
                        Map<String, SyncStream> graphToStream = streams.stream().collect(Collectors.toMap(s -> s.getGraphId(), s -> s));
                        Iterable<MappingGraph> graphs = graphService.retrieve(streams.stream().map(s -> s.getGraphId()).collect(Collectors.toList()));
                        LagReport report = new LagReport()
                                .setOrgName(organization.getName())
                                .setInstanceName(instance.getName())
                                .setSyncariId(instance.getSyncariId())
                                .setTotalStreams(streams.size());
                        graphs.forEach(graph -> {
                            SyncStream syncStream = graphToStream.get(graph.getId());
                            report.addLine(new LagReportLine()
                                    .setGraphName(graph.getName())
                                    .setLag(syncStream.lagInMillis())
                                    .setLastCheckin(syncStream.getCheckin())
                                    .setLastSync(syncStream.getLastSuccessfulSync()));
                        });
                        lagReports.add(report);
                    }
                } catch (Exception e) {
                    String lagReportFailureBody = "Failed to process lag report for instance " + instance.getSyncariId();
                    log.error(lagReportFailureBody, e);
                    lagReportFailureBody += " due to \n" + ExceptionUtils.getFullStackTrace(e);
                    emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
                        String.format("Lag report failure for instance %s", instance.getSyncariId()), lagReportFailureBody);
                }

                /*
                // TODO: This needs more work like compare against the main pipeline and be smart about when to alert for DS lag.
                List<DatastoreWatermark> dsWMs = syncService.getDatastoreWatermarks().stream()
                    .filter(x -> StringUtils.isNotEmpty(x.getEntityName())).collect(Collectors.toList());
                LagReport dsReport = new LagReport().setOrgName(o.getName()).setInstanceName(i.getName()).setSyncariId(syncariId).setTotalStreams(streams.size());
                dsWMs.forEach(dsWM -> {
                    dsReport.addLine(new DSLagReportLine()
                            .setEntityName(dsWM.getEntityName())
                            .setLag(dsWM.lagInMillis()));
                });
                //lagReports.add(dsReport);
                */
            }
        }
        Optional<String> lagReportBody = lagReports.stream().filter(l -> l.hasLags()).map(l -> l.toString()).reduce((r1, r2) -> r1 + "\n" + r2);
        lagReportBody.ifPresentOrElse(body ->{
            String subject = String.format("Lag report generated at %s", DateUtil.format(new Date()));
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, body);
            log.info("Sent lag report for {} organizations", all.size());
        },()-> log.info("No lags found for {} organizations", all.size()));

        // wait for a bit before freeing up the locks
        try {
            Thread.sleep(1000 * 60 * 30);
        } catch (InterruptedException e) {
            log.error("Failed to sleep", e);
        }

        for(Organization organization : all) {
            List<Instance> instances = organization.getInstances();

            for (Instance instance : instances) {
                syncariContextHandler.setContext(instance.getSyncariId());
                var lockId = "lagReporter:"+  instance.getSyncariId();
                var locked = lockRepo.unlock(lockId, lockOwner);
            }
        }

    }
}

@Data
@Accessors(chain = true)
class LagReport {
    protected static final long LAG_THRESHOLD =  3 * 60 * 60 * 1000;//3 hours

    public static final String REPORT_HEADER = "Report for Org: %s, Instance: %s(%s), Total Streams : %s";
    public static final String SEPERATOR = "============================================================";
    public static final String NEWLINE = "\n";

    String orgName;
    String instanceName;
    String syncariId;
    int totalStreams;
    List<LagReportLine> reportLines = new ArrayList<>();

    public boolean hasLags(){
        return this.reportLines.stream().anyMatch(l -> l.isOverThreshold(LAG_THRESHOLD));
    }
    public LagReport addLine(LagReportLine line){
        reportLines.add(line);
        return this;
    }
    public String toString() {
        Stream<LagReportLine> streamsOverThreshold = this.reportLines.stream().filter(l -> l.isOverThreshold(LAG_THRESHOLD));
        String reportLines = streamsOverThreshold.map(l -> l.toString()).reduce((l1, l2) -> l1 + NEWLINE + l2).orElse("");
        return String.format(REPORT_HEADER,orgName,instanceName,syncariId,totalStreams) + NEWLINE + SEPERATOR + NEWLINE + reportLines + NEWLINE;
    }
}

@Data
@Accessors(chain = true)
class LagReportLine {
    public static final String REPORT_LINE = "Graph : %s,  Lag: %s hours,   Last Checkin:%s,   Last Sync: %s";
    String graphName;
    long lag;
    Instant lastCheckin;
    Instant lastSync;

    public boolean isOverThreshold(long threshold) {
        return lag > threshold;
    }

    public String toString() {
        return String.format(REPORT_LINE, graphName, lag / (60 * 60 * 1000), lastCheckin, lastSync);
    }
}

@Data
@Accessors(chain = true)
class DSLagReport extends LagReport {
    public static final String REPORT_HEADER = "Datastore Report for Org: %s, Instance: %s(%s), Total datastore entities : %s";
}

@Data
@Accessors(chain = true)
class DSLagReportLine extends LagReportLine {
    public static final String REPORT_LINE = "Datastore Entity: %s,  Lag: %s seconds";
    String entityName;

    public String toString() {
        return String.format(REPORT_LINE, entityName, lag / 1000);
    }
}