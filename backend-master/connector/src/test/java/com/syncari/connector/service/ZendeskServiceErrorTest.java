package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import com.sforce.ws.ConnectionException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.exception.UnknownException;
import com.syncari.connector.zendesk.ZendeskRestClient;
import com.syncari.connector.zendesk.ZendeskService;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class ZendeskServiceErrorTest {

	@Test
	public void internalServerErrorIsUnknown() throws ConnectionException {
		ZendeskService service = getMockService("Internal error", HttpStatus.INTERNAL_SERVER_ERROR);

		try {
			service.getFirstCreatedTime(getRequest());
			fail();
		} catch (UnknownException e) {
			assertEquals("UNKNOWN_EXCEPTION", e.getErrorCode());
			assertTrue(e.getMessage().contains("Internal error"));
		}
	}

	@Test
	public void gatewayTimeoutIsRetriable() throws ConnectionException {
		ZendeskService service = getMockService("Gateway Time out", HttpStatus.GATEWAY_TIMEOUT);

		try {
			service.getFirstCreatedTime(getRequest());
			fail();
		} catch (RetriableException e) {
			assertEquals("GATEWAY_TIMEOUT", e.getErrorCode());
			assertTrue(e.getMessage().contains("Gateway Time out"));
		}
	}
	
	@Test
	public void bandwidthExceededIsRetriable() throws ConnectionException {
		ZendeskService service = getMockService("Bandwidth limit exceeded", HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);
		
		try {
			service.getFirstCreatedTime(getRequest());
			fail();
		} catch (RetriableException e) {
			assertEquals("BANDWIDTH_LIMIT_EXCEEDED", e.getErrorCode());
			assertTrue(e.getMessage().contains("Bandwidth limit exceeded"));
		}
	}
	
	@Test
	public void requestTimeoutIsRetriable() throws ConnectionException {
		ZendeskService service = getMockService("Request Time out", HttpStatus.REQUEST_TIMEOUT);
		
		try {
			service.getFirstCreatedTime(getRequest());
			fail();
		} catch (RetriableException e) {
			assertEquals("REQUEST_TIMEOUT", e.getErrorCode());
			assertTrue(e.getMessage().contains("Request Time out"));
		}
	}
	
	@Test
	public void unavailableServiceIsRetriable() throws ConnectionException {
		ZendeskService service = getMockService("Service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
		
		try {
			service.getFirstCreatedTime(getRequest());
			fail();
		} catch (RetriableException e) {
			assertEquals("SERVICE_UNAVAILABLE", e.getErrorCode());
			assertTrue(e.getMessage().contains("Service unavailable"));
		}
	}
	
	@Test
	public void tooManyRequestsIsRetriable() throws ConnectionException {
		ZendeskService service = getMockService("Too many requests", HttpStatus.TOO_MANY_REQUESTS);
		
		try {
			service.getFirstCreatedTime(getRequest());
			fail();
		} catch (RetriableException e) {
			assertEquals("TOO_MANY_REQUESTS", e.getErrorCode());
			assertTrue(e.getMessage().contains("Too many requests"));
		}
	}
	
	@Test
	public void unauthorizedIsNonRetriable() throws ConnectionException {
		ZendeskService service = getMockService("Not authorized", HttpStatus.UNAUTHORIZED);
		
		try {
			service.getFirstCreatedTime(getRequest());
			fail();
		} catch (NonRetriableException e) {
			assertEquals("UNAUTHORIZED", e.getErrorCode());
			assertTrue(e.getMessage().contains("Not authorized"));
		}
	}

	private ZendeskService getMockService(String errorMsg, HttpStatus status) {
		ZendeskService service = Mockito.spy(ZendeskService.class);
		ZendeskRestClient connection = Mockito.spy(ZendeskRestClient.class);
		RestTemplate template = Mockito.mock(RestTemplate.class);
		ResponseEntity<String> response = new ResponseEntity<>(errorMsg, status);
		doReturn(response).when(template).exchange(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
				ArgumentMatchers.any(), ArgumentMatchers.any(Class.class));
		doReturn(template).when(connection).getTemplate();
		doReturn(connection).when(service).getClient(ArgumentMatchers.any());
		return service;
	}

	private SyncRequest getRequest() {
		return new SyncRequest()
				.Builder(new ConnectorInfo("123", "zendesk", "https://syncaridevhelp.zendesk.com","instance1"),
						new EntitySchema("ticket"))
				.setWatermark(new WatermarkInfo());
	}
}
