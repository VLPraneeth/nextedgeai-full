package com.syncari.core.service.messagingservice;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.messagingservice.SlackService;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Connector;

public class SlackServiceTest extends AbstractSyncariTest {
	@Autowired
    ConnectorService connectorService;
	@Autowired
	AppConfig appConfig;

    @Autowired
    SlackService service;

	@After
	public void tearDown() {
		super.tearDown();
	}
	
	@Test
	public void createSlack() {
	    Connector connector = new Connector("slack", connectorService.describe(Constants.SLACK).getId(),
                SlackService.END_POINT);
        connector.setAuthConfig(new AuthConfig(appConfig.getSlackClientId(), appConfig.getSlackClientSecret()));
        connector = connectorService.save(connector);
        String redirectUrl = String.format(SlackService.INIT_OAUTH_URL, appConfig.getSlackClientId(),
                connector.getOAuthRedirectUrl());
        assertNotNull(redirectUrl);
	}
	
    @Test
	public void sendMessageError() {
        try {
            Connector connector = new Connector("slack", connectorService.describe(Constants.SLACK).getId(), SlackService.END_POINT);
            connector.setAuthConfig(new AuthConfig(appConfig.getSlackClientId(), appConfig.getSlackClientSecret()));
            connector = connectorService.save(connector);
            service.sendMessage("Test block", "", "Test message", "#dummy", connector.getId());
            fail();
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to send slack message. Received error response: " +
                "{\"ok\":false,\"error\":\"invalid_auth\",\"warning\":\"missing_charset\",\"response_metadata\":{\"warnings\":[\"missing_charset\"]}}"));
        }
	    
	}


}
