package com.syncari.api.pubsub.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.dfiv2.DFIResponse;
import com.syncari.core.dfiv2.DFIResultManager;
import com.syncari.core.dfiv2.DFIRuleMetric;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.store.BigQueryTempStore;
import com.syncari.core.event.store.DFIEventStore;
import com.syncari.core.event.store.StoreSchema;
import com.syncari.core.event.store.repo.DFIResultCountRepo;
import com.syncari.core.event.store.repo.DFIResultRepo;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.DataQualityRule;
import com.syncari.core.model.Feature;
import com.syncari.core.model.misc.FeatureStage;
import com.syncari.core.model.misc.FeatureStatus;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.pubsub.PubSubChannelConfig;
import com.syncari.core.repositories.customer.DataQualityRuleRepo;
import com.syncari.core.repositories.customer.FeatureRepo;
import com.syncari.core.service.*;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.AckMode;
import org.springframework.cloud.gcp.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DFIResultProcessor {

    @Autowired
    private SyncariContextHandler contextHandler;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    FeatureService featureService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;

    @Autowired
    NotificationService notifyService;

    @Autowired
    FeatureRepo featureRepo;

    @Autowired
    private DFIEventStore dfiEventStore;

    @Autowired
    private DFIResultRepo dfiResultRepo;

    @Autowired
    private DFIResultCountRepo dfiResultCountRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    DataQualityRuleRepo dataQualityRuleRepo;


    @Autowired
    BigQueryTempStore bigQueryTempStore;

    private static final String TEMP_TABLE_FORMAT = "tmp_dfi_result_count_%s_%s";

    @PostConstruct
    public void init() {
        log.info("********** Started DFI result notification processor **********");
    }

    @Bean
    public PubSubInboundChannelAdapter dfiResultNotificationChannelAdapter(
            @Qualifier(PubSubChannelConfig.DFI_RESULT_NOTIFICATION_INPUT_CHANNEL) MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
                appConfig.getDfiResultNotificationSubscription());
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    Function<String, Boolean> isValidRule = ruleId -> dataQualityRuleRepo.findByRuleId(ruleId).isPresent();

    public DFIResponse removeDeletedRules(DFIResponse original, Function<String, Boolean> condition) {
        DFIResponse copy = new DFIResponse();
        copy.setEntityId(original.getEntityId());
        copy.setEntityName(original.getEntityName());
        copy.setEvaluatedAt(original.getEvaluatedAt());

        Map<String, DFIResponse.Result> results = new HashMap<>();
        original.getResults().forEach((key, value) -> {
            if (condition.apply(key)) {
                results.put(key, deepCopyResult(value));
            }
        });
        copy.setResults(results);
        return copy;
    }

    private DFIResponse.Result deepCopyResult(DFIResponse.Result original) {
        DFIResponse.Result copy = new DFIResponse.Result();
        copy.setCategoryId(original.getCategoryId());
        copy.setCategoryName(original.getCategoryName());
        copy.setRuleName(original.getRuleName());
        copy.setPassed(deepCopyIdentifiers(original.getPassed()));
        copy.setFailed(deepCopyIdentifiers(original.getFailed()));
        return copy;
    }

    private List<DFIResponse.Identifier> deepCopyIdentifiers(List<DFIResponse.Identifier> original) {
        return original.stream()
                .map(identifier -> {
                    DFIResponse.Identifier copy = new DFIResponse.Identifier();
                    copy.setSyncariRecordId(identifier.getSyncariRecordId());
                    copy.setSyncariAttributeId(identifier.getSyncariAttributeId());
                    return copy;
                })
                .collect(Collectors.toList());
    }

    private void updateRuleMetrics(List<DFIRuleMetric> metrics) {
        for (DFIRuleMetric metric : metrics) {
           Optional<DataQualityRule> ruleInfo = dataQualityRuleRepo.findById(metric.getRuleId());
           if(ruleInfo.isPresent() && !ruleInfo.get().getIsDeleted()){
               DataQualityRule rule = ruleInfo.get();
               rule.setFailed(metric.getFailedCount());
               rule.setPassed(metric.getSuccessCount());
               dataQualityRuleRepo.save(rule);
           } else {
               log.info("rule id : {} does not exist", metric.getRuleId());
           }
        }
    }

    private void softDeleteRule(String ruleId, String entityId, String timestamp) {
        try {
            dfiResultRepo.softDelete(ruleId, entityId, timestamp);
            dfiResultCountRepo.softDelete(ruleId, entityId, timestamp);
        } catch (Exception e) {
            log.error("Error deleting dfi rule for ruleId {}, entityId {}", ruleId, entityId);
        }
    }

    private Feature setStatus(Features feature, FeatureStatus status){
        Optional<Feature> byName = featureRepo.findByName(feature.name());
        Feature f;
        if(byName.isEmpty()) {
            f = new Feature(feature.name(), FeatureStage.GA, status);
        } else {
            f = byName.get();
            f.setStatus(status);
        }
        return featureRepo.save(f);
    }

    private Feature setDFIStatusToDisabled() {
        return setStatus(Features.DfiV2Provisioning, FeatureStatus.inactive);
    }

    private Feature setDFIStatusToInProgress() {
        return setStatus(Features.DfiV2Provisioning, FeatureStatus.activating);
    }

    private Feature setDFIStatusToEnabled() {
        return setStatus(Features.DfiV2Provisioning, FeatureStatus.active);
    }

    private void deleteRule(String ruleId, String entityId) {
        try {
            dataQualityRuleRepo.deleteById(ruleId);
            dfiResultRepo.deleteByRuleId(ruleId, entityId);
            dfiResultCountRepo.deleteByRuleId(ruleId, entityId);
        } catch (Exception e) {
            log.error("Error deleting dfi rule for ruleId {}, entityId {}", ruleId, entityId);
        }
    }

    private boolean enableInsights() {
        log.info("Enabling Insights as part of DFI provisioning");
        try {
            if (featureService.getOrCreateFeatureByName(Features.InsightsProvider).getStatus().equals(FeatureStatus.active)) {
                log.info("Insights is already enabled");
                return true;
            }
            if (!featureService.isEnabled(Features.Datastore)) {
                log.info("Enabling datastore as part of DFI provisioning");
                featureService.enableFeature(Features.Datastore);
                try {
                    datastoreService.provision(SyncariContext.getSyncariId());
                } catch (Exception e) {
                    log.error("Error while provisioning datastore : ", e);
                    featureService.disableFeature(Features.Datastore);
                    return false;
                }
            }
            if (!featureService.isEnabled(Features.InsightsProvider) && featureService.isEnabled(Features.Datastore)) {
                log.info("Enabling insights provider as part of DFI provisioning");
                boolean result = insightsProviderIntegrator.provisionTSOrganization();
                if (result) {
                    String body = String.format(I18n.i18n("insightsprovider_enabled"), SyncariContext.getSyncariId());
                    notifyService.broadcast(I18n.i18n("insightsprovider_enabled_sub"), body, NotificationType.ANNOUNCEMENT);
                } else {
                    String body = String.format(I18n.i18n("insightsprovider_enabled_failed"), SyncariContext.getSyncariId());
                    notifyService.broadcast(I18n.i18n("insightsprovider_enabled_sub_failed"), body, NotificationType.ANNOUNCEMENT);
                }
            } else {
                String body = String.format(I18n.i18n("insightsprovider_enabled_failed"), SyncariContext.getSyncariId());
                notifyService.broadcast(I18n.i18n("insightsprovider_enabled_sub_failed"), body, NotificationType.ANNOUNCEMENT);
            }
        } catch (Exception e) {
            log.error("Error while provisioning insights as part of DFI provisioning. Error : ",e);
            return false;
        }
        boolean provisioningResult = featureService.getOrCreateFeatureByName(Features.InsightsProvider).getStatus().equals(FeatureStatus.active);
        log.info("insights provisioning completed as part of DFI povisioning. status : {}", provisioningResult);
        return provisioningResult;
    }

    @Bean
    @ServiceActivator(inputChannel = PubSubChannelConfig.DFI_RESULT_NOTIFICATION_INPUT_CHANNEL)
    public MessageHandler dfiResultNotificationMessageReceiver() {

        return message -> {
            String content = new String((byte[]) message.getPayload());
            try {
                log.info("Received Message");
                Message msg = objectMapper.readValue(content, Message.class);

                if (msg.getSyncariId() != null) {
                    contextHandler.setContext(msg.getSyncariId(), true);
                } else {
                    log.error("SyncariId not set on message {}", msg.getEvent().getType());
                    return;
                }
                String syncariId = msg.getSyncariId();

                switch (msg.getEvent().getType()) {
                    case EventTypes.DFI_RESULT_NOTIFICATION:
                        log.info("Received event type {}", msg.getEvent().getType());
                        String tempCountTable = String.format(TEMP_TABLE_FORMAT, msg.getSyncariId(), UUID.randomUUID().toString().replace("-", ""));
                        DFIResponse response = objectMapper.readValue(objectMapper.writeValueAsString(msg.getEvent().getDetails()), DFIResponse.class);
                        DFIResponse validResults = removeDeletedRules(response, isValidRule);
                        boolean insertionResult = dfiResultRepo.insertDFIResults(validResults);
                        if (!insertionResult) {
                            log.error("persisting dfi results to database failed");
                            return;
                        }
                        log.info("inserted dfi results to dfi results table");
                        bigQueryTempStore.provision(syncariId, StoreSchema.DFI_RESULTS_COUNT_TABLE_NAME, tempCountTable);
                        try {
                            dfiResultCountRepo.insertDFIResults(validResults, tempCountTable);
                            log.info("inserted dfi results to temp count table");
                        } catch (Exception e) {
                            log.error("error while inserting to temp table {}. error : ", tempCountTable, e);
                            bigQueryTempStore.deprovision(syncariId, StoreSchema.DFI_RESULTS_COUNT_TABLE_NAME, tempCountTable);
                            throw e;
                        }
                        dfiResultCountRepo.mergeFromTempTable(tempCountTable);
                        log.info("merged table {} to dfi count table successfully", tempCountTable);
                        bigQueryTempStore.deprovision(syncariId, StoreSchema.DFI_RESULTS_COUNT_TABLE_NAME, tempCountTable);
                        updateRuleMetrics(dfiResultCountRepo.getEntitymetric(validResults.getEntityId()));
                        log.debug("complete dfi result processing");
                        break;
                    case EventTypes.PROVISION_DFI:
                        log.info("Success event type {}, acking", msg.getEvent().getType());
                        FeatureStatus dfiStatus = featureService.getOrCreateFeatureByName(Features.DfiV2Provisioning).getStatus();
                        if (dfiStatus.equals(FeatureStatus.inactive)) {
                            setDFIStatusToInProgress();
                            try {
                                dfiEventStore.provision(syncariId);
                            } catch (Exception e) {
                                log.error("Error while provisioning dfi event store. error : ", e);
                                String body = String.format(I18n.i18n("dfi_enabled_failed"), SyncariContext.getSyncariId());
                                notifyService.broadcast(I18n.i18n("dfi_enabled_sub_failed"), body, NotificationType.ANNOUNCEMENT);
                                setDFIStatusToDisabled();
                                break;
                            }
                            if (!enableInsights()) {
                                log.error("DFI provisioning failed since enabling insights failed");
                                String body = String.format(I18n.i18n("dfi_enabled_failed"), SyncariContext.getSyncariId());
                                notifyService.broadcast(I18n.i18n("dfi_enabled_sub_failed"), body, NotificationType.ANNOUNCEMENT);
                                setDFIStatusToDisabled();
                                break;
                            }
                            try {
                                insightsProviderIntegrator.provisionBQforDFI();
                            } catch (Exception e) {
                                log.error("Error occurred while enabling BQ connection for insights. Error : ", e);
                                setDFIStatusToDisabled();
                                break;
                            }
                            setDFIStatusToEnabled();
                            log.info("Dfi event store provisioning completed");
                            String body = String.format(I18n.i18n("dfi_enabled"), SyncariContext.getSyncariId());
                            notifyService.broadcast(I18n.i18n("dfi_enabled_sub"), body, NotificationType.ANNOUNCEMENT);
                        } else
                            log.info("skipping dfi provisioning as status is {}", dfiStatus);
                        break;
                    case EventTypes.DFI_RULE_DELETED:
                        log.info("Success event type {}, acking", msg.getEvent().getType());
                        String entityId = (String) msg.getEvent().getDetails().get("entityId");
                        String ruleId = (String) msg.getEvent().getDetails().get("ruleId");
                        String timestamp = (String) msg.getEvent().getDetails().get("deletedAt");
                        if (StringUtils.isBlank(entityId) || StringUtils.isBlank(ruleId) || !DFIResultManager.isValidTimestamp(timestamp)) {
                            log.error("invalid payload for rule deletion. key ruleId / entityId is missing. payload : {}", msg.getEvent().getDetails());
                            return;
                        }
                        softDeleteRule(ruleId, entityId, timestamp);
                        break;
                    default:
                        log.error("Unknown event type {}, Ignored.", msg.getEvent().getType());
                }

            } catch(Exception e){
                log.error(String.format("Error while processing event %s", content), e);
            } finally{
                contextHandler.resetSyncariContext();
            }

            BasicAcknowledgeablePubsubMessage consumer = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
            consumer.ack().addCallback( s -> {
                log.info("Processed message successfully");
            }, e -> {
                log.error("Error processing {}, error: ", content, e);
            });
        };
    }

}
