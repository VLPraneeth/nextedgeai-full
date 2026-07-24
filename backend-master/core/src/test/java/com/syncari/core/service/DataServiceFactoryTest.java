package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.connector.database.RedshiftService;
import com.syncari.connector.service.HubspotService;
import com.syncari.connector.service.SalesforceService;
import com.syncari.connector.zendesk.ZendeskService;
import com.syncari.core.AbstractSyncariTest;

public class DataServiceFactoryTest extends AbstractSyncariTest {
	@Autowired
	DataServiceFactory factory;
	@Autowired
	ConnectorService service;

	@Test
	public void getDataService() {
		assertTrue(SalesforceService.class.isAssignableFrom(factory.getDataService(service.describe("salesforce")).getClass()));
		assertTrue(HubspotService.class.isAssignableFrom(factory.getDataService(service.describe("hubspot")).getClass()));
		assertTrue(ZendeskService.class.isAssignableFrom(factory.getDataService(service.describe("zendesk")).getClass()));
		assertTrue(RedshiftService.class.isAssignableFrom(factory.getDataService(service.describe("redshift")).getClass()));
	}

	@Test
	public void getSchemaDataService() {
		assertTrue(SalesforceService.class.isAssignableFrom(factory.getSchemaService(service.describe("salesforce")).getClass()));
		assertTrue(HubspotService.class.isAssignableFrom(factory.getSchemaService(service.describe("hubspot")).getClass()));
		assertTrue(ZendeskService.class.isAssignableFrom(factory.getSchemaService(service.describe("zendesk")).getClass()));
		assertTrue(RedshiftService.class.isAssignableFrom(factory.getSchemaService(service.describe("redshift")).getClass()));
	}

	@Test
	public void getAuthenticationService() {
		assertTrue(SalesforceService.class.isAssignableFrom(factory.getAuthenticationService(service.describe("salesforce")).getClass()));
		assertTrue(HubspotService.class.isAssignableFrom(factory.getAuthenticationService(service.describe("hubspot")).getClass()));
		assertTrue(ZendeskService.class.isAssignableFrom(factory.getAuthenticationService(service.describe("zendesk")).getClass()));
		assertTrue(RedshiftService.class.isAssignableFrom(factory.getAuthenticationService(service.describe("redshift")).getClass()));
	}
}
