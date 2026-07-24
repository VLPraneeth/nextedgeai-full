package com.syncari.viper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.pubsub.PubSubChannelConfig;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.EntityRepoService;

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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TestPipelineProcessorPubSub {
    @Autowired
    SyncariContextHandler contextHandler;
    @Autowired
    GraphRunner service;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    SimulationRunner simulationGraphRunner;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AppConfig appConfig;
    @Autowired
    Publisher publisher;
    @Autowired
    EntityRepoService entityRepoService;
    
    private final String id = UUID.randomUUID().toString();
    public String getId() {
        return id;
    }

    @PostConstruct
    public void init() {
        log.info("********** Started viper processor with id {} **********", getId());
    }

    @Bean
    public PubSubInboundChannelAdapter viperChannelAdapter(
            @Qualifier(PubSubChannelConfig.VIPER_INPUT_CHANNEL) MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
                appConfig.getViperSubscription());
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = PubSubChannelConfig.VIPER_INPUT_CHANNEL)
    public MessageHandler testPipelineMessageReceiver() {
        return message -> {
            String content = new String((byte[]) message.getPayload());
            try {
                log.info(String.format("Read viper Message: %s", content));
                Message msg = mapper.readValue(content, Message.class);
                contextHandler.setContext(msg.getSyncariId());
                switch (msg.getEvent().getType()) {
                    case EventTypes.TEST_PIPELINE:
                        String pipelineTestId = msg.getEvent().getDetails().get("testPipelineId").toString();
                        service.test(pipelineTestId, getId());
                        log.info(String.format("Testing pipeline for %s", pipelineTestId));
                        break;

                    case EventTypes.SIMULATE_PIPELINE:
                        String simulationRunId = msg.getEvent().getDetails().get("simulationRunId").toString();
                        simulationGraphRunner.simulate(simulationRunId);
                        log.info(String.format("Simulating TestRun with id %s", simulationRunId));
                        break;

                    case EventTypes.TEST_PIPELINE_DONE:
                        log.info("Test pipeline done event, skipping. Event: {}", msg.getEvent());
                        break;

                    // TODO: rename this class to handle all Viper events and not just 'TestPipeline' related.
                    case EventTypes.RECALCULATE_DFI_SCORES:
                        log.info("Received {} event. Full Event: {}", EventTypes.RECALCULATE_DFI_SCORES, msg.getEvent());
                        entityRepoService.initializeScoreForEntityById(msg.getEvent().getDetails().get("entityId").toString());
                        break;
                    case EventTypes.SYNC_SUCCESS:
                    case EventTypes.PIPELINE_EVENT:
                        // Noop. Spectrum proxy is the main recipient of this message and its on a different subscription
                        break;
                    case EventTypes.UPDATE_FK_REFERENCES:
                        log.info("Received {} event. Full Event: {}", EventTypes.UPDATE_FK_REFERENCES, msg.getEvent());
                        var syncariIds = (List<String>) msg.getEvent().getDetails().get("syncariIds");
                        entityRepoService.updateReferringEntities(msg.getEvent().getDetails().get("entityId").toString(), syncariIds);
                        break;
                    default:
                        log.error("Unknown event type {}, acking this", msg.getEvent().getType());
                }
            } catch (com.syncari.core.exception.NotFoundException e){
                //skip dead subs and instances
                log.error(String.format("Error while processing event %s", content));
            } catch (JsonParseException e) {
                log.error(String.format("Error while processing event %s", content));
                // Throwing a runtimeexception keeps reprocessing the same error event.
                // We also do not know the test pipeline id, since we could not decode the message, so alert and skip.
                // throw new RuntimeException(e);
                String subject = "Json parse failed for test pipeline event";
                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, content);
            } catch (IOException e) {
                log.error(String.format("Error while processing event %s", content));
                throw new RuntimeException(e);
            }
            BasicAcknowledgeablePubsubMessage consumer = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
            consumer.ack().addCallback( s -> {
                log.info("Processed message {}", content);
            }, e -> {
                log.error("Error processing {}, error: {}", content, e);
            });
        };
    }

}
