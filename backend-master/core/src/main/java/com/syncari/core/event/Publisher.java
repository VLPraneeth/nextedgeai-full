package com.syncari.core.event;

import static java.lang.String.format;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.GCPEventPubSub.PubsubOutboundGateway;
import com.syncari.core.pubsub.PubSubChannelConfig.ErrorNotificationPubsubOutboundGateway;
import com.syncari.core.pubsub.PubSubChannelConfig.DFIResultNotificationPubsubOutboundGateway;
import com.syncari.core.model.Event;
import com.syncari.core.pubsub.PubSubChannelConfig.GenericPubsubOutboundGateway;
import com.syncari.core.pubsub.PubSubChannelConfig.ViperPubsubOutboundGateway;
import com.syncari.core.pubsub.PubSubChannelConfig.WebhookPubsubOutboundGateway;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Publisher {
    @Autowired
    private PubsubOutboundGateway messagingGateway;
    @Autowired
    private GenericPubsubOutboundGateway genericGateway;
    @Autowired
    private ViperPubsubOutboundGateway viperGateway;
    @Autowired
    private WebhookPubsubOutboundGateway webhookGateway;
    @Autowired
    private ErrorNotificationPubsubOutboundGateway errorNotificationGateway;

    @Autowired
    private DFIResultNotificationPubsubOutboundGateway dfiResultNotificationPubsubOutboundGateway;

    @Autowired
    ObjectMapper mapper;

    public void publishToEventLog(String message) {
        try {
            messagingGateway.sendToPubsub(message);
            log.debug(format("logged : %s", message));
        } catch (Exception e) {
            log.error("Error while logging %s", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void publishToGenericQueue(String message) {
        try {
            genericGateway.sendToPubsub(message);
            log.debug(format("Sent message to genericGateway : %s", message));
        } catch (Exception e) {
            log.error("Error while sending message to genericGateway ", e);
            throw new RuntimeException(e);
        }
    }

    public void publishToGenericQueue(Event event) {
    	publishToGenericQueue(event, true);
    }
    
    public void publishToGenericQueue(Event event, boolean setContext) {
    	Message message = new Message();
    	message.setEvent(event);
    	if(setContext) {
    		message.setSyncariId(SyncariContext.getSyncariId());
    	}
    	try {
    		publishToGenericQueue(mapper.writeValueAsString(message));
    	} catch (Exception e) {
    		log.error("Error while sending message to genericGateway ", e);
    		throw new RuntimeException(e);
    	}
    }

    public void publishToWebhookQueue(String message) {
        try {
            webhookGateway.sendToPubsub(message);
            log.debug(format("Sent message to webhookGateway : %s", message));
        } catch (Exception e) {
            log.error("Error while sending message to webhookGateway ", e);
            throw new RuntimeException(e);
        }
    }

    public void publishToWebhookQueue(Event event) {
        publishToWebhookQueue(event, true);
    }

    public void publishToWebhookQueue(Event event, boolean setContext) {
        Message message = new Message();
        message.setEvent(event);
        if(setContext) {
            message.setSyncariId(SyncariContext.getSyncariId());
        }
        try {
            publishToWebhookQueue(mapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("Error while sending message to webhookGateway ", e);
            throw new RuntimeException(e);
        }
    }
    
    public void publishToViperQueue(String message) {
        try {
            viperGateway.sendToPubsub(message);
            log.debug(format("Sent message to viperGateway : %s", message));
        } catch (Exception e) {
            log.error("Error while sending message to viperGateway ", e);
            throw new RuntimeException(e);
        }
    }

    public void publishToViperQueue(Event event) {
        Message message = new Message(SyncariContext.getSyncariId(), event);
        try {
            publishToViperQueue(mapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("Error while sending message to viperGateway ", e);
            throw new RuntimeException(e);
        }
    }
    
    public void publishToErrorNotificationQueue(String message) {
        try {
            errorNotificationGateway.sendToPubsub(message);
            log.debug(format("Sent message to errorNotificationGateway : %s", message));
        } catch (Exception e) {
            log.error("Error while sending message to errorNotificationGateway ", e);
            throw new RuntimeException(e);
        }
    }

    public void publishToDFIResultNotificationQueue(String message) {
        try {
            dfiResultNotificationPubsubOutboundGateway.sendToPubsub(message);
            log.info("Sent message to DFIResultNotificationGateway");
        } catch (Exception e) {
            log.error("Error while sending message to DFIResultNotificationGateway ", e);
            throw new RuntimeException(e);
        }
    }

    public void publishToErrorNotificationQueue(Event event) {
        Message message = new Message(SyncariContext.getSyncariId(), event);
        try {
        	publishToErrorNotificationQueue(mapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("Error while sending message to errorNotificationGateway ", e);
            throw new RuntimeException(e);
        }
    }

    public void publishToDFIResultQueue(Event event) {
        Message message = new Message(SyncariContext.getSyncariId(), event);
        try {
            publishToDFIResultNotificationQueue(mapper.writeValueAsString(message));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
