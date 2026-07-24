package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ENABLE_FEATURE;
import static com.syncari.core.security.Permissions.DISABLE_FEATURE;
import static com.syncari.core.security.Permissions.SERVICE_CREDENTIAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.ConnectorStatus;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.api.rest.controllers.data.Credential;
import com.syncari.core.Features;
import com.syncari.core.model.Feature;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.FeatureRepo;
import com.syncari.core.repositories.customer.ServiceCredentialRepo;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.ProvisioningService;
import static com.syncari.utils.I18n.i18n;

public class ProvisioningControllerTest extends AbstractSyncariTest {
	@Autowired
	ProvisioningController provisioningController;
	@Autowired
	FeatureRepo featureRepo;
	@Autowired
	OrganizationRepo orgRepo;
	@Autowired
	ProvisioningService provisioningService;
	@Autowired
	ServiceCredentialRepo serviceCredentialRepo;
	@Autowired
	ConnectorRepo connectorRepo;
	@Autowired
	FeatureService featureService;

	@After
	public void tearDown() {
		featureRepo.deleteAll();
		serviceCredentialRepo.reset();
	}

	@Test
	@WithMockUser(username = "admin", authorities = { ENABLE_FEATURE, DISABLE_FEATURE })
	public void enableDisableFeature() {
		Organization org = new Organization("test");
		Instance instance = new Instance("syncari-test", "syncari test");
		org.setInstances(List.of(instance));
		org = orgRepo.insert(org);
		assertNotNull(org.getId());
		Feature feature = new Feature(Features.Datastore.name());
		feature = featureRepo.insert(feature);
		assertNotNull(feature.getId());
		provisioningController.enableFeature(Features.Datastore.name());
		assertTrue(featureService.isEnabled(Features.Datastore));

		try {
			provisioningController.enableFeature(null);
			fail();
		} catch (Exception e) {
			assertEquals("Name is null", e.getMessage());
		}

		try {
			provisioningController.enableFeature("234");
			fail();
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("No enum"));
		}

		provisioningController.disableFeature(Features.Datastore.name());
		assertFalse(featureService.isEnabled(Features.Datastore));

		try {
			provisioningController.disableFeature(null);
			fail();
		} catch (Exception e) {
			assertEquals("Name is null", e.getMessage());
		}

		try {
			provisioningController.disableFeature("234");
			fail();
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("No enum"));
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = { SERVICE_CREDENTIAL })
	public void upsertServiceCredential() {
		// add clearbit
		Credential credentialReq = new Credential().setName("clearbit").setType("clearbit").setKey("api_key");
		provisioningController.upsertServiceCredential(credentialReq);
		List<Credential> creds = provisioningController.getCredentials();
		assertEquals(1, creds.size());
		assertEquals("clearbit", creds.get(0).getName());

		// Update clearbit
		String recordId = creds.get(0).getId();
		credentialReq = new Credential().setName("clearbit_update").setType("clearbit").setKey("api_key_update").setId(recordId);
		provisioningController.upsertServiceCredential(credentialReq);
		creds = provisioningController.getCredentials();

		assertEquals(1, creds.size());
		assertEquals("clearbit_update", creds.get(0).getName());
		assertEquals(recordId, creds.get(0).getId());

		// add zoominfo without user pass
		credentialReq = new Credential().setName("zoominfo").setType("zoominfo");
		try{
			provisioningController.upsertServiceCredential(credentialReq);
			fail();
		} catch (Exception e){
			assertEquals("Username and Password required to create ZoomInfo Service", e.getMessage());
		}

		// add correct zoominfo credentials
		credentialReq = credentialReq.setUsername("nick@syncari.com").setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
		provisioningController.upsertServiceCredential(credentialReq);
		creds = provisioningController.getCredentials();
		assertEquals(2, creds.size());
		assertEquals("clearbit_update", creds.get(0).getName());
		assertEquals("zoominfo", creds.get(1).getName());
		Optional<Connector> con = connectorService.find(creds.get(0).getId());
		con.ifPresent(c -> 	assertEquals(ConnectorStatus.ACTIVE,c.getStatus()));


		// Update zoominfo
		recordId = creds.get(1).getId();
		credentialReq = new Credential().setName("zoominfo_update").setType("zoominfo").setUsername("nick@syncari.com").setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME")).setId(recordId);
		provisioningController.upsertServiceCredential(credentialReq);
		creds = provisioningController.getCredentials();

		assertEquals(2, creds.size());
		assertEquals("zoominfo_update", creds.get(1).getName());
		assertEquals(recordId, creds.get(1).getId());

		// Delete clearbit
		recordId = creds.get(1).getId();
		provisioningController.deleteServiceCredential(recordId);
		creds = provisioningController.getCredentials();

		assertEquals(1, creds.size());

		// add zoominfo with unauthorized user/pass
		credentialReq = new Credential().setName("zoominfo2").setType("zoominfo").setUsername("invalid_user").setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
		try {
			// with invalid credentials the connector save will be successful but testConnection will fail
			provisioningController.upsertServiceCredential(credentialReq);
			fail();
		} catch (Exception e){
			assertEquals(i18n("invalid_zoominfo_credentials_create"), e.getMessage());
		}

		creds = provisioningController.getCredentials();
		assertEquals(2, creds.size());

		Credential credentialReqSlack = new Credential().setName("slack").setType("slack").setKey("api_key");
		provisioningController.upsertServiceCredential(credentialReqSlack);
		List<Credential> credslatest = provisioningController.getCredentials();
		assertEquals(3, credslatest.size());
	}

}
