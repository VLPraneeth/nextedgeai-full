package com.syncari.core.event;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.AckMode;
import org.springframework.cloud.gcp.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.cloud.gcp.pubsub.integration.outbound.PubSubMessageHandler;
import org.springframework.cloud.gcp.pubsub.support.GcpPubSubHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.syncari.core.SyncariContextHandler;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.notification.PagerDutyNotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Deprecated
@Profile("!dbm")
public class GCPEventPubSub {
	private static final String PUBSUB_OUTPUT_CHANNEL = "pubsubOutputChannel";
	private static final String PUBSUB_INPUT_CHANNEL = "pubsubInputChannel";
	@Autowired
	AppConfig appConfig;
	@Autowired
	SyncariContextHandler contextHandler;
	@Autowired
	EventStore eventStore;
	@Autowired
	PagerDutyNotificationService pdService;
	@Autowired
	ObjectMapper mapper;

	@Bean
	public PubSubInboundChannelAdapter messageChannelAdapter(
			@Qualifier(PUBSUB_INPUT_CHANNEL) MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {
		// TODO change the subscription
		PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate, appConfig.getEventLogSubscription());
		adapter.setOutputChannel(inputChannel);
		adapter.setAckMode(AckMode.MANUAL);

		return adapter;
	}

	@Bean
	public MessageChannel pubsubInputChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel pubsubOutputChannel() {
		return new DirectChannel();
	}

	@Bean
	@ServiceActivator(inputChannel = PUBSUB_INPUT_CHANNEL)
	public MessageHandler messageReceiver() {
		return message -> {
			String content = new String((byte[]) message.getPayload());
			try {
				log.info(String.format("Read Message: %s", content));
				Message msg = mapper.readValue(content, Message.class);
				contextHandler.setContext(msg.getSyncariId());
				eventStore.insert(List.of(msg.getEvent()));
				
				if(EventTypes.ERROR.equalsIgnoreCase(msg.getEvent().getType())) {
					pdService.notify(msg.getEvent());
				}
			} catch (Exception e) {
				log.error(String.format("Error while processing event %s", content));
			}
			AckReplyConsumer consumer = (AckReplyConsumer) message.getHeaders().get(GcpPubSubHeaders.ACKNOWLEDGEMENT);
			consumer.ack();
		};
	}

	@Bean
	@ServiceActivator(inputChannel = PUBSUB_OUTPUT_CHANNEL)
	public MessageHandler messageSender(PubSubTemplate pubsubTemplate) {
		return new PubSubMessageHandler(pubsubTemplate, appConfig.getEventLogTopicName());
	}

	@MessagingGateway(defaultRequestChannel = PUBSUB_OUTPUT_CHANNEL)
	public interface PubsubOutboundGateway {
		void sendToPubsub(String eventString);
	}
}
