package com.syncari.api.pubsub.receiver;

import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.apache.commons.collections.CollectionUtils;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorNotification;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.pubsub.PubSubChannelConfig;
import com.syncari.core.repositories.customer.ErrorCatalogRepo;
import com.syncari.core.service.ErrorNotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ErrorNotificationProcessor {
    @Autowired
    private SyncariContextHandler contextHandler;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private AppConfig appConfig;
    @Autowired
    private ErrorNotificationService notificationService;
    @Autowired
    private ErrorCatalogRepo catalogRepo;

    private final String id = UUID.randomUUID().toString();
    public String getId() {
        return id;
    }

    @PostConstruct
    public void init() {
        log.info("********** Started error notification processor with id {} **********", getId());
    }

    @Bean
	public PubSubInboundChannelAdapter errorNotificationChannelAdapter(
			@Qualifier(PubSubChannelConfig.ERROR_NOTIFICATION_INPUT_CHANNEL) MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {
		PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
				appConfig.getErrorNotificationSubscription());
		adapter.setOutputChannel(inputChannel);
		adapter.setAckMode(AckMode.MANUAL);
		return adapter;
	}

    @Bean
    @ServiceActivator(inputChannel = PubSubChannelConfig.ERROR_NOTIFICATION_INPUT_CHANNEL)
    public MessageHandler errorNotificationMessageReceiver() {
        return message -> {
            String content = new String((byte[]) message.getPayload());
            try {
                log.debug(String.format("Read Message: %s", content));
                Message msg = mapper.readValue(content, Message.class);

                if(msg.getSyncariId() != null) {
                	contextHandler.setContext(msg.getSyncariId(),true);
                } else {
                	log.warn("SyncariId not set on message {}", content);
                }
				switch (msg.getEvent().getType()) {
					case EventTypes.ERROR_NOTIFICATION:
						log.info("Success event type {}, acking", msg.getEvent().getType());
						var details = msg.getEvent().getDetails();
					ErrorCategory category = details.get("category") != null
							? ErrorCategory.valueOf(details.get("category").toString())
							: null;
					String componentId = details.get("componentId") != null ? details.get("componentId").toString()
							: null;
					ErrorPriority priority = details.get("priority") != null
							? ErrorPriority.valueOf(details.get("priority").toString())
							: null;
					String key = details.get("key") != null ? details.get("key").toString() : null;
					String subject = details.get("subject") != null ? details.get("subject").toString() : null;
					String body = details.get("body") != null ? details.get("body").toString() : null;
					Map<String, String> moreInfo = (Map<String, String>) details.get("details");
					if (category != null && priority != null && componentId != null) {
						var catalogs = catalogRepo.findByCategoryAndPriority(category, priority);
						if(CollectionUtils.isNotEmpty(catalogs)) {
							ErrorNotification notif = ErrorNotification.builder()
									.category(category)
									.priority(priority).key(key)
									.catalogId(catalogs.get(0).getId())
									.componentId(componentId)
									.subject(subject)
									.body(body)
									.details(moreInfo).build();
							notificationService.processErrorNotification(notif);
						} else {
							log.error("Error catalog not present {}", details);
						}
					} else {
						log.error("Not processable error notification {}", details);
					}
						break;
					default:
						log.error("Unknown event type {}, acking", msg.getEvent().getType());
				}
            } catch (Exception e) {
                log.error(String.format("Error while processing event %s", content),e);
            } finally {
                // All done reset syncari context
                contextHandler.resetSyncariContext();
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
