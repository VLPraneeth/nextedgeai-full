package com.syncari.connector.service.def;

import java.util.List;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.EventData;
import com.syncari.connector.data.WebhookRequest;

public interface WebhookService {
	
	String PREFIX = "webhook";
	
	// This api is used to extract the unique identifier for the endsystem account
	String extractIdentifier(WebhookRequest request);

	// The unique identifier setup in the synapse (ex: munchkinid, portalId, etc)
	String getIdentifier(ConnectorInfo config);

	// Synapse specific webhook
	String getEndpoint();

	// Api that takes the raw JSON and parses the data into EntityData
	List<EventData> parseEventData(WebhookRequest request);

	default boolean webhookCreatable() {
		return false;
	}

	default String createWebhook(ConnectorInfo config, String spectrumHost) {
		return "";
	}

	default void deleteWebhook(ConnectorInfo config) {
	}
}
