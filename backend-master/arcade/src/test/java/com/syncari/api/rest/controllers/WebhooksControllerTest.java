package com.syncari.api.rest.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.Connector;
import com.syncari.core.model.Event;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.EventDataService;
import com.syncari.utils.TextUtil;

public class WebhooksControllerTest extends AbstractSyncariTest {

    @Autowired
    WebhookController controller;
    @Autowired
    ConnectorService connService;
    @Autowired
    EventDataService service;
    @Autowired
    GlobalConfigurationRepo repo;
	String data = "[\n"
			+ "  {\n"
			+ "    \"eventId\": 2270875301,\n"
			+ "    \"subscriptionId\": 1238319,\n"
			+ "    \"portalId\": 6196729,\n"
			+ "    \"appId\": 204106,\n"
			+ "    \"occurredAt\": 1632175988258,\n"
			+ "    \"subscriptionType\": \"deal.deletion\",\n"
			+ "    \"attemptNumber\": 0,\n"
			+ "    \"objectId\": 6261424409,\n"
			+ "    \"changeFlag\": \"DELETED\",\n"
			+ "    \"changeSource\": \"API\"\n"
			+ "  }\n"
			+ "]";
	
    @Override
    public void tearDown() {
    	repo.deleteAll();
    }

    @Test
    @WithMockUser(username = "test@email.com")
    public void processSavesData() throws IOException {
    	Publisher mockService = mock(Publisher.class);
		controller.publisher = mockService;
		doNothing().when(mockService).publishToWebhookQueue(any(Event.class), any(Boolean.class));
    	
	    Connector connector = new Connector("hubtest", connService.describe(Constants.HUBSPOT).getId(),
	            "http://test.com");
	    connector.setMetaConfig(Map.of("portalId", "6196729"));
	    connector.setAuthConfig(new AuthConfig().setClientSecret("secret"));
	    connector = connService.save(connector);
	    connService.createWebhookConfig(connector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-hubspot-signature", Hex.encodeHexString(TextUtil.getSha("secret".concat(data))));
		request.setRequestURI("http://test.com/hubspot?param1=value1&param2=value2");

        controller.process("hubspot", data, request);

		// Verify params
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(controller.publisher).publishToWebhookQueue(captor.capture(), any(Boolean.class));
		assertTrue(captor.getValue().getDetails().containsKey("params"));
		assertEquals(List.of("value1"),((Map) captor.getValue().getDetails().get("params")).get("param1"));
		assertEquals(List.of("value2"),((Map) captor.getValue().getDetails().get("params")).get("param2"));

        verify(controller.publisher, times(1)).publishToWebhookQueue(any(Event.class), any(Boolean.class));
    }
    
}
