package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.ErrorNotificationFrequency;
import com.syncari.core.model.ErrorNotificationWebhookConfig;
import com.syncari.core.model.errornotification.WebhookRequestBodyNotification;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.repositories.customer.ErrorCatalogRepo;
import com.syncari.core.repositories.customer.ErrorNotificationConfigRepo;

public class ErrorNotificationWebhookHelperTest extends AbstractSyncariTest {

	@Autowired
	ErrorNotificationConfigRepo configRepo;
	@Autowired
    ObjectMapper objectMapper;
	@Autowired
	ErrorCatalogRepo catalogRepo;

    @Override
    public void tearDown() {
        super.tearDown();
    }
    
    @Test
    public void testExecute() {
    	var webhookHelper = new ErrorNotificationWebhookHelper() {
    		@Override
    		public RestTemplate getTemplate() {
    			return Mockito.mock(RestTemplate.class);
    		}
    	};
    	webhookHelper.configRepo = configRepo;
    	webhookHelper.objectMapper = objectMapper;
    	
    	ErrorNotificationWebhookConfig config = new ErrorNotificationWebhookConfig();
    	config.setName("Test wh group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setHttpMethod("POST");
    	config.setUrl("http://example.com");
    	config.setHeaders(Map.of());
    	config.setProcessing(true);
    	config = configRepo.save(config);
    	
    	var notif =  WebhookRequestBodyNotification.builder()
				.timestamp(new Date())
				.summary("test subject")
				.message("test message body")
				.build();
    	
    	webhookHelper.execute(config, List.of(notif), 1, new Date());
    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	assertNotNull(updatedConfig.get().getLastNotificationTimestamp());
    	assertNull(updatedConfig.get().getLastErrorTimestamp());
    	assertFalse(updatedConfig.get().isProcessing());
    	configRepo.deleteAll();
    }
    
    @Test
    public void testExecuteException() {
    	var webhookHelper = new ErrorNotificationWebhookHelper() {
    		@Override
    		public RestTemplate getTemplate() {
    			RestTemplate template = Mockito.mock(RestTemplate.class);
    			Mockito.when(
    			template.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class))).thenThrow(new RuntimeException("test"));
    			return template;
    		}
    	};
    	webhookHelper.configRepo = configRepo;
    	webhookHelper.objectMapper = objectMapper;
    	
    	ErrorNotificationWebhookConfig config = new ErrorNotificationWebhookConfig();
    	config.setName("Test wh group");
    	config.setCadence(ErrorNotificationFrequency.IMMEDIATE);
    	config.setNotificationTypes(catalogRepo.findAll().stream().map(cat -> cat.getId()).collect(Collectors.toList()));
    	config.setStatus(ErrorNotificationConfigStatus.Active);
    	config.setHttpMethod("POST");
    	config.setUrl("http://example.com");
    	config.setHeaders(Map.of());
    	config.setProcessing(true);
    	config = configRepo.save(config);
    	
    	var notif =  WebhookRequestBodyNotification.builder()
				.timestamp(new Date())
				.summary("test subject")
				.message("test message body")
				.build();
    	try {
	    	webhookHelper.execute(config, List.of(notif), 1, new Date());
	    	fail();
    	} catch (Exception e) {
			assertEquals("test", e.getMessage());
		}
    	var updatedConfig = configRepo.findById(config.getId());
    	assertFalse(updatedConfig.isEmpty());
    	configRepo.deleteAll();
    }
}
