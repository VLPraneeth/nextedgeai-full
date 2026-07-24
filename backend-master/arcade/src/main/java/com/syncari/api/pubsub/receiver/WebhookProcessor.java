package com.syncari.api.pubsub.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.pubsub.PubSubChannelConfig;
import com.syncari.core.quickstart.QuickStartRunService;
import com.syncari.core.quickstart.v2.QuickStartV2Service;
import com.syncari.core.service.*;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.exception.ExceptionUtils;
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
import java.util.UUID;

@Slf4j
@Component
public class WebhookProcessor {
    @Autowired
    SyncariContextHandler contextHandler;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AppConfig appConfig;
    @Autowired
    Publisher publisher;
    @Autowired
    EventDataService eventDataService;

    private final String id = UUID.randomUUID().toString();
    public String getId() {
        return id;
    }

    @PostConstruct
    public void init() {
        log.info("********** Started webhook processor with id {} **********", getId());
    }

    @Bean
    public PubSubInboundChannelAdapter webhookChannelAdapter(
            @Qualifier(PubSubChannelConfig.WEBHOOK_INPUT_CHANNEL) MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {
        PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
                appConfig.getWebhookSubscription());
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = PubSubChannelConfig.WEBHOOK_INPUT_CHANNEL)
    public MessageHandler webhookMessageReceiver() {
        return message -> {
            String content = new String((byte[]) message.getPayload());
            Message msg = null;
            try {
                log.debug(String.format("Read Message: %s", content));
                msg = mapper.readValue(content, Message.class);
                if(msg.getSyncariId() != null) {
                    contextHandler.setContext(msg.getSyncariId(),true);
                } else {
                    log.debug("SyncariId not set on message {}", content);
                }
                switch (msg.getEvent().getType()) {
                    case EventTypes.ACCEPT_WEBHOOK:
                        eventDataService.handleEvent(msg);
                        break;
                    default:
                        log.error("Unknown event type {}, acking", msg.getEvent().getType());
                }
            } catch (Exception e) {
                log.error(String.format("Error while processing event %s", content),e);
                log.error(ExceptionUtils.getStackTrace(e));
                if (msg == null || msg.getSyncariId() != null) {
                    throw new RuntimeException(e);
                }
            } finally {
                contextHandler.resetSyncariContext();
            }
            BasicAcknowledgeablePubsubMessage consumer = message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
            consumer.ack().addCallback( s -> {
                log.debug("Processed message {}", content);
            }, e -> {
                log.error("Error processing {}, error: {}", content, e);
            });
        };
    }
}
