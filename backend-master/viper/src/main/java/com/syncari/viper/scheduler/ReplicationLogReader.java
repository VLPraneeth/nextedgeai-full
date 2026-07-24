package com.syncari.viper.scheduler;

import com.google.common.collect.Lists;
import com.syncari.connector.Constants;
import com.syncari.connector.database.PostgresService;
import com.syncari.core.DataTransformer;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EventData;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.repositories.customer.LockRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.*;
import com.syncari.viper.InstanceUtil;
import com.syncari.viper.ViperContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Component
@Slf4j
public class ReplicationLogReader {

    private static final int REPLICATION_LOG_PAGE_SIZE = 5000;
    private static final int EVENT_BATCH_SIZE = 1000;

    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    UserService userService;
    @Autowired
    LockRepo lockRepo;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    AppConfig appConfig;
    @Autowired
    InstanceUtil instanceUtil;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService connectorMetaService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    DataServiceFactory dataServiceFactory;
    @Autowired
    DataTransformer transformer;
    @Autowired
    EventDataService eventDataService;

    @Autowired
    InstanceConfigurationService instanceConfigurationService;

    private static int LOCK_EXPIRY_TIMEOUT_MINUTES = 60;

    static String lockOwner;
    static {
        lockOwner = UUID.randomUUID().toString();
    }


    @Scheduled(fixedRate = 60000)
    public void process() {
        instanceUtil.forEachInstance((context -> {
            readWAL(context);
        }));
    }

    private void readWAL(ViperContext context) {
        try {
            context.setDebugMode(instanceConfigurationService.isDebugModeEnabled());
            log.debug("Reading from replication log for {}", context.getInstance().getSyncariId());
            var lockId = "walreader_"+context.getInstance().getSyncariId();

            // get list of synapses for this instance
            var postgresSynapses = connectorService.list().stream().filter(connector -> {
                var postgres = connectorMetaService.findById(connector.getMetadataId()).filter(cm -> cm.getName().equals(Constants.POSTGRESQL));
                return connector.isActive() && postgres.isPresent();
            }).collect(Collectors.toList());

            // get a list of entities which do not have watermark and have published pipelines
            var noWatermarkEntities = postgresSynapses.stream().map(conn -> {
                var approvedEntityGraphs = schemaService.getAllPublishedEntities(conn.getId()).stream()
                        .filter(e -> e.getWatermarkField().isEmpty())
                        .map(e -> Pair.of(e, mappingGraphService.findEntityGraphsWithSource(e.getId()).stream().filter(graph -> graph.isApproved()).collect(Collectors.toList())))
                        .collect(Collectors.toList());
                return Pair.of(conn, approvedEntityGraphs);
            }).collect(Collectors.toList());

            log.debug("Number of entities to read from replication log {} for instance {}", noWatermarkEntities.size(), context.getInstance().getSyncariId());
            if (CollectionUtils.isNotEmpty(noWatermarkEntities)){
                try {
                    var locked = lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(LOCK_EXPIRY_TIMEOUT_MINUTES));
                    if(locked.isPresent()) {
                        log.debug("Acquired lock {}", lockId);
                        readWALHelper(noWatermarkEntities, context.getInstance().getSyncariId());
                    }
                } catch (Exception e) {
                    log.error(String.format("Error processing replication reader for instance %s, error : %s", context.getInstance().getSyncariId(), e.getMessage()), e);
                } finally {
                    lockRepo.unlock(lockId, lockOwner);
                    log.debug("Releasing lock {}", lockId);
                }
            }
        } catch (Exception e) {
            log.error(String.format("Error processing replication reader for instance %s, error : %s", context.getInstance().getSyncariId(), e.getMessage()), e);
        }
    }

    private void readWALHelper(List<Pair<Connector, List<Pair<EntityDefinition, List<MappingGraph>>>>> noWatermarkEntities, String syncariId){
        // for each connector
        noWatermarkEntities.stream().forEach(synpaseEntities -> {

            var connector = synpaseEntities.getFirst();
            try {
                var entityMap = synpaseEntities.getSecond().stream().filter(p -> !p.getSecond().isEmpty()).map(Pair::getFirst).collect(Collectors.toMap(
                        ed -> ed.getApiName(), ed -> transformer.toEntitySchema(ed, connector)));

                var entityGraphs = synpaseEntities.getSecond().stream().collect(Collectors.toMap(ed -> ed.getFirst().getApiName(), ed -> ed.getSecond()));

                if (!entityMap.isEmpty()) {
                    var dataService = dataServiceFactory.getDataService(connector.getMetadata());
                    if (dataService instanceof PostgresService) {
                        PostgresService postgresService = ((PostgresService) dataService);
                        var eventPair = postgresService.getByWAL(transformer.toConnectorInfo(connector), entityMap, REPLICATION_LOG_PAGE_SIZE);
                        var events = eventPair.y;
                        var readEvents = eventPair.x;
                        var walEvents = compactEvents(events);
                        log.info("Number of events read from replication log {} for Connector {} read events {}", walEvents.size(), connector.getId(), events.size());
                        logIds(walEvents, connector);
                        //  for each event/connector, find the graphs
                        var copiedEvents = walEvents.stream().flatMap(wevent -> entityGraphs.getOrDefault(wevent.getData().getName(), List.of()).stream().map(graph -> {

                            return new EventData().setData(wevent.getData()).setConnectorId(connector.getId()).setGraphId(graph.getId());
                        })).collect(Collectors.toList());
                        var batchedEvents = Lists.partition(copiedEvents, EVENT_BATCH_SIZE);
                        batchedEvents.stream().forEach(batch -> {
                            log.info("Save events, batch size : {}", batch.size());
                            eventDataService.save(batch);
                        });
                        // drain WAL of same number of records we have read
                        postgresService.drainWAL(transformer.toConnectorInfo(connector), entityMap, readEvents);
                    } else {
                        log.error("Connector {} is not a Postgres Service", connector.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error reading from replication slot for connector {}, Error: ", connector.getName(), e.getMessage(), e);
                emailService.sendErrorEmail(List.of(), userService.getInternalAdminEmailList(),
                        String.format(i18n("replication_reader_subject"), syncariId, connector.getName()),
                        e + ExceptionUtils.getStackTrace(e));
            }
        });

    }

    private List<com.syncari.connector.data.EventData> compactEvents(List<com.syncari.connector.data.EventData> events) {
        return events.stream()
                .filter(e -> !StringUtils.isBlank(e.getData().getName() + e.getData().getId()))
                .collect(Collectors.groupingBy(e -> e.getData().getName() + e.getData().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream().map(l -> l.get(l.size() -1)).collect(Collectors.toList());
    }

    private void logIds(List<com.syncari.connector.data.EventData> walEvents, Connector connector) {
        Lists.partition(walEvents, 100).stream()
                .map(eventList -> eventList.stream().filter(ev -> ev.getData() != null))
                .map(partition -> {
                    return partition.map(event -> {
                        return String.format("(%s, %s, %s)", event.getData().getName(), event.getData().getId(), event.getOperation());
                    });
                }).forEach(partitionSteam -> {
                    log.debug("Retrieved Event Data for connector {} (Entity Name, Id, Operation): {}", connector.getId(), partitionSteam.collect(Collectors.joining(",")));
        });
    }
}
