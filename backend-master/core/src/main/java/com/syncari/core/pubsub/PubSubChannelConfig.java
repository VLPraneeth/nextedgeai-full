package com.syncari.core.pubsub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.integration.outbound.PubSubMessageHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import com.syncari.core.config.AppConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("!dbm")
public class PubSubChannelConfig {
	public static final String GENERIC_OUTPUT_CHANNEL = "genericOutputChannel";
	public static final String GENERIC_INPUT_CHANNEL = "genericInputChannel";
    public static final String VIPER_OUTPUT_CHANNEL = "viperOutputChannel";
    public static final String VIPER_INPUT_CHANNEL = "viperInputChannel";
    public static final String WEBHOOK_INPUT_CHANNEL = "webhookInputChannel";
    public static final String WEBHOOK_OUTPUT_CHANNEL = "webhookOutputChannel";
    public static final String ERROR_NOTIFICATION_INPUT_CHANNEL = "errorNotificationInputChannel";
    public static final String ERROR_NOTIFICATION_OUTPUT_CHANNEL = "errorNotificationOutputChannel";
    public static final String DFI_RESULT_NOTIFICATION_INPUT_CHANNEL = "dfiResultNotificationInputChannel";
    public static final String DFI_RESULT_NOTIFICATION_OUTPUT_CHANNEL = "dfiResultNotificationOutputChannel";

	@Autowired
	AppConfig appConfig;

	@Bean
	public MessageChannel genericInputChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel genericOutputChannel() {
		return new DirectChannel();
	}

    @Bean
    public MessageChannel webhookInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel webhookOutputChannel() {
        return new DirectChannel();
    }

	
    @Bean
    @ServiceActivator(inputChannel = VIPER_OUTPUT_CHANNEL)
    public MessageHandler viperMessageSender(PubSubTemplate pubsubTemplate) {
        return new PubSubMessageHandler(pubsubTemplate, appConfig.getViperTopicName());
    }
    
    @MessagingGateway(defaultRequestChannel = VIPER_OUTPUT_CHANNEL)
    public interface ViperPubsubOutboundGateway {
        void sendToPubsub(String eventString);
    }

    @Bean
    public MessageChannel viperInputChannel() {
        return new DirectChannel();
    }
    
    @Bean
    public MessageChannel viperOutputChannel() {
        return new DirectChannel();
    }
    
    @Bean
    @ServiceActivator(inputChannel = GENERIC_OUTPUT_CHANNEL)
    public MessageHandler genericMessageSender(PubSubTemplate pubsubTemplate) {
        return new PubSubMessageHandler(pubsubTemplate, appConfig.getGenericTopicName());
    }

    @MessagingGateway(defaultRequestChannel = GENERIC_OUTPUT_CHANNEL)
    public interface GenericPubsubOutboundGateway {
        void sendToPubsub(String eventString);
    }

    @Bean
    @ServiceActivator(inputChannel = WEBHOOK_OUTPUT_CHANNEL)
    public MessageHandler webhookMessageSender(PubSubTemplate pubsubTemplate) {
        return new PubSubMessageHandler(pubsubTemplate, appConfig.getWebhookTopicName());
    }

    @MessagingGateway(defaultRequestChannel = WEBHOOK_OUTPUT_CHANNEL)
    public interface WebhookPubsubOutboundGateway {
        void sendToPubsub(String eventString);
    }
    
    @Bean
    @ServiceActivator(inputChannel = ERROR_NOTIFICATION_OUTPUT_CHANNEL)
    public MessageHandler errorNotificationMessageSender(PubSubTemplate pubsubTemplate) {
        return new PubSubMessageHandler(pubsubTemplate, appConfig.getErrorNotificationTopicName());
    }

    @Bean
    @ServiceActivator(inputChannel = DFI_RESULT_NOTIFICATION_OUTPUT_CHANNEL)
    public MessageHandler dfiResultNotificationMessageSender(PubSubTemplate pubsubTemplate) {
        return new PubSubMessageHandler(pubsubTemplate, appConfig.getDfiResultNotificationTopicName());
    }

    @MessagingGateway(defaultRequestChannel = ERROR_NOTIFICATION_OUTPUT_CHANNEL)
    public interface ErrorNotificationPubsubOutboundGateway {
        void sendToPubsub(String eventString);
    }

    @MessagingGateway(defaultRequestChannel = DFI_RESULT_NOTIFICATION_OUTPUT_CHANNEL)
    public interface DFIResultNotificationPubsubOutboundGateway {
        void sendToPubsub(String eventString);
    }

}
