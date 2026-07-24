package com.syncari.core.service;

import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.cloudfunctions.CloudFunctionManager;
import com.syncari.core.cloudfunctions.SyncariCloudFunctionStatus;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.http.source.HttpSourceConfig;
import com.syncari.core.http.source.HttpSourceMetadataDTO;
import com.syncari.core.http.source.HttpSourcesHelper;
import com.syncari.core.http.source.HttpSourcesService;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.repositories.syncari.ConnectorMetadataRepo;
import com.syncari.core.webhook.receiver.WebhookConfig;
import com.syncari.core.webhook.receiver.WebhookReceiverMetadataDTO;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@Slf4j
public class ConnectorMetadataServiceTest extends AbstractSyncariTest {

    private static final String BASE_RESOURCE_PATH = "src/test/resources/connectormetadata/";

    @Autowired
    ConnectorMetadataService connectorMetadataService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataRepo connectorMetadataRepo;
    @Autowired
    CloudFunctionManager cloudFunctionManager;
    @Autowired
    HttpSourcesService httpSourcesService;
    @Mock
    HttpSourcesHelper httpSourcesHelper;

    private InputStream synapseFileStream;
    private InputStream requirementsFileStream;

    @Override
    public void setUp() {
        super.setUp();
        deleteCustomSynapses();
        try {
            synapseFileStream = new FileInputStream(BASE_RESOURCE_PATH + "main.py");
            requirementsFileStream = new FileInputStream(BASE_RESOURCE_PATH + "requirements.txt");
        } catch (FileNotFoundException e) {
            fail("Failed to read synapse files.");
        }
    }

    private void deleteCustomSynapses() {
        List<ConnectorMetadata> customSynapses = connectorMetadataRepo.findIsCustom();
        List<String> ids = customSynapses.stream().map(x -> x.getId()).collect(Collectors.toList());
        if (ids.size() > 0) {
            connectorMetadataRepo.deleteAllById(ids);
        }
        
        List<ConnectorMetadata> httpSynapses = connectorMetadataRepo.findHttpSources();
        ids = httpSynapses.stream().map(x -> x.getId()).collect(Collectors.toList());
        if (ids.size() > 0) {
            connectorMetadataRepo.deleteAllById(ids);
        }
    }

    @After
    public void tearDown() {
        super.tearDown();
        deleteCustomSynapses();
    }

    @Test
    public void deleteConnectorHttpSource(){

        // case 1: delete approved metadata - passes
        ConnectorMetadata metadata1 = new ConnectorMetadata();
        metadata1.setName("test");
        metadata1.setDisplayName("test");
        metadata1.setHttpSource(true);
        metadata1.setDraftStatus(DraftStatus.APPROVED);

        metadata1 = connectorMetadataRepo.save(metadata1);
        connectorMetadataService.delete(metadata1.getId());

        // case 2: delete draft metadata - success
        metadata1.setDraftStatus(DraftStatus.NEW);
        metadata1 = connectorMetadataRepo.save(metadata1);
        connectorMetadataService.delete(metadata1.getId());

        // case 3: delete approved metadata used in pipeline - fails
        metadata1.setDraftStatus(DraftStatus.APPROVED);
        Connector con = new Connector();
        con.setAuthConfig(new AuthConfig());
        con.setMetadata(metadata1);
        con.setName("Test");
        con.setMetadataId(metadata1.getId());
        con.setStatus(ConnectorStatus.ACTIVE);
        connectorService.save(con);
        metadata1 = connectorMetadataRepo.save(metadata1);
        assertTrue(connectorService.isInUse(metadata1.getId()));
    }
    
    @Test
    public void deleteConnector(){

        // case 1: delete approved metadata - passes
        ConnectorMetadata metadata1 = new ConnectorMetadata();
        metadata1.setName("test");
        metadata1.setDisplayName("test");
        metadata1.setCustom(true);
        metadata1.setCustomSynapseIdentifier("test");
        metadata1.setDraftStatus(DraftStatus.APPROVED);

        metadata1 = connectorMetadataRepo.save(metadata1);
        connectorMetadataService.delete(metadata1.getId());

        // case 2: delete draft metadata - success
        metadata1.setDraftStatus(DraftStatus.NEW);
        metadata1 = connectorMetadataRepo.save(metadata1);
        connectorMetadataService.delete(metadata1.getId());

        // case 3: delete approved metadata used in pipeline - fails
        metadata1.setDraftStatus(DraftStatus.APPROVED);
        Connector con = new Connector();
        con.setAuthConfig(new AuthConfig());
        con.setMetadata(metadata1);
        con.setName("Test");
        con.setMetadataId(metadata1.getId());
        con.setStatus(ConnectorStatus.ACTIVE);
        connectorService.save(con);
        metadata1 = connectorMetadataRepo.save(metadata1);
        assertTrue(connectorService.isInUse(metadata1.getId()));
    }

    @Test
    @Ignore("Flaky, fix me ASAP!")
    public void connectorMetadataCRUD() {
        String custSynapseIdentifier = String.format("custom_%s_%s", SyncariContext.getSyncariId(), "sample").toLowerCase();
        try {

            // Nothing to begin with
            List<ConnectorMetadata> definitions = connectorMetadataService.list();
            assertTrue(definitions.isEmpty());

            MultipartFile synapseFile = new MockMultipartFile("main", 
                "main.py", "text/plain", IOUtils.toByteArray(synapseFileStream));
            MultipartFile requirementsFile = new MockMultipartFile("requirements", 
                "requirements.txt", "text/plain", IOUtils.toByteArray(requirementsFileStream));
            ConnectorMetadata created = connectorMetadataService.createDraft("sample", "Sample", synapseFile, requirementsFile, null);
            assertNotNull(created);
            assertNotNull(created.getId());
            assertNull(created.getParentId());
            assertEquals(DraftStatus.NEW, created.getDraftStatus());

            Thread.sleep(5000);

            // verify deployment
            verifyFunctionStatus(created, SyncariCloudFunctionStatus.CODE.ACTIVE);

            definitions = connectorMetadataService.list();
            assertFalse(definitions.isEmpty());

            SyncariCloudFunctionStatus status = connectorMetadataService.getCustomConnectorMetadataStatus(created.getId());
            assertNotNull(status);
            
            // Update the draft. Not typical flow.
            ConnectorMetadata updated = connectorMetadataService.updateDraft(created.getId(), "Sample", synapseFile, requirementsFile, null, false);
            assertNotNull(updated);
            assertNotNull(updated.getId());
            assertEquals(DraftStatus.NEW, updated.getDraftStatus());

            // Update the draft without any files should just work.
            updated = connectorMetadataService.updateDraft(created.getId(), "Sample Test", null, null, null, false);
            assertNotNull(updated);
            assertNotNull(updated.getId());
            assertEquals("Sample Test", updated.getDisplayName());
            assertEquals(DraftStatus.NEW, updated.getDraftStatus());

            Thread.sleep(5000);
            // verify deployment
            verifyFunctionStatus(updated, SyncariCloudFunctionStatus.CODE.ACTIVE);


            // Directly publishing should fail.
            try {
                ConnectorMetadata tryMe = connectorMetadataService.approve(updated.getId());
            } catch (SyncariValidationException e) {
                assertEquals(i18n("connector_meta_invalid_state_for_approval"), e.getMessage());
            }

            // Trying to withdraw a non-submitted draft should fail
            try {
                ConnectorMetadata tryMe = connectorMetadataService.withdrawApproval(updated.getId());
            } catch (SyncariValidationException e) {
                assertEquals(i18n("connector_meta_invalid_state_for_withdrawal"), e.getMessage());
            }

            // Submission for aproval should be the next step for the draft
            ConnectorMetadata submitted = connectorMetadataService.submitForApproval(updated.getId());
            assertEquals(DraftStatus.SUBMIT_FOR_APPROVAL, submitted.getDraftStatus());

            // Trying to submitting again should fail.
            try {
                ConnectorMetadata tryMe = connectorMetadataService.submitForApproval(submitted.getId());
            } catch (SyncariValidationException e) {
                assertEquals(i18n("connector_meta_invalid_state_for_submission"), e.getMessage());
            }

            // Withdraw approval.
            ConnectorMetadata withdrawn = connectorMetadataService.withdrawApproval(submitted.getId());
            withdrawn = connectorMetadataService.findDraft(withdrawn.getId()).get();
            assertNotNull(withdrawn);
            assertNotNull(withdrawn.getId());
            assertEquals(DraftStatus.NEW, withdrawn.getDraftStatus());

            // Submit again for approval, should work.
            submitted = connectorMetadataService.submitForApproval(withdrawn.getId());
            
            // approve should be the next step for the draft
            ConnectorMetadata approved = connectorMetadataService.approve(submitted.getId());
            assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());

            Thread.sleep(5000);

            // verify deployment
            verifyFunctionStatus(approved, SyncariCloudFunctionStatus.CODE.ACTIVE);

            // Create a draft
            ConnectorMetadata newDraft = connectorMetadataService.createDraftFor(approved.getId());
            assertNotNull(newDraft);
            assertNotNull(newDraft.getId());
            assertNotNull(newDraft.getParentId());
            assertEquals(DraftStatus.NEW, newDraft.getDraftStatus());

            definitions = connectorMetadataService.list();
            assertTrue(definitions.size() == 2);

            newDraft = connectorMetadataService.findDraft(newDraft.getId()).get();
            assertNotNull(newDraft);
            assertNotNull(newDraft.getId());
            assertNotNull(newDraft.getParentId());
            assertEquals(DraftStatus.NEW, newDraft.getDraftStatus());
            verifySynapseInfo(newDraft);
            // TODO
            verifySynapseFunctionality(newDraft);

            // Update the new draft from the approved version without any files should just work.
            updated = connectorMetadataService.updateDraft(newDraft.getId(), "Sample Test updated", null, null, null, false);
            assertNotNull(updated);
            assertNotNull(updated.getId());
            assertEquals("Sample Test updated", updated.getDisplayName());
            assertEquals(DraftStatus.NEW, updated.getDraftStatus());

            // Submit it again and approved it, lets verify the display name of the approved version.
            submitted = connectorMetadataService.submitForApproval(updated.getId());
            assertEquals(DraftStatus.SUBMIT_FOR_APPROVAL, submitted.getDraftStatus());
            approved = connectorMetadataService.approve(submitted.getId());
            assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
            assertEquals("Sample Test updated", approved.getDisplayName());

            Thread.sleep(5000);
            // Yield until the function has been deployed.
            verifyFunctionStatus(approved, SyncariCloudFunctionStatus.CODE.ACTIVE);

            verifySynapseInfo(connectorMetadataService.findById(approved.getId()).get());
            // TODO
            verifySynapseFunctionality(approved);

            // Ensure the files are downloadable
            InputStream stream = connectorMetadataService.getCustomSynapseFiles(updated.getId());
            assertFalse(Objects.isNull(stream));
            stream.close();

            // Discard the draft version of an approved one, should fail.
            try {
                connectorMetadataService.discardDraft(updated.getId());
            } catch (Exception e) {
                assertEquals("Draft cannot be discarded as it is approved", e.getMessage());
            }

            // Discard the new draft version should now work.
            newDraft = connectorMetadataService.createDraftFor(approved.getId());
            connectorMetadataService.discardDraft(newDraft.getId());

            // Archived should not be returned.
            definitions = connectorMetadataService.list();
            assertTrue(definitions.size() == 1);

            // Just test some scenarios where we dont process cross org custom synapses.
            newDraft = connectorMetadataService.createDraftFor(approved.getId());
            newDraft.setOrgId("SOME_RANDOM_ORGID");
            connectorMetadataRepo.save(newDraft);

            // Another org's custom synapse draft will not show up.
            definitions = connectorMetadataService.list();
            assertTrue(definitions.size() == 1);

            // Make sure published custom synapse from another Org is not visible.
            submitted = connectorMetadataService.submitForApproval(newDraft.getId());
            approved = connectorMetadataService.approve(submitted.getId());
            definitions = connectorService.describe();
            final String approvedId = approved.getId();
            assertFalse(definitions.stream().filter(c -> c.getId() == approvedId).findAny().isPresent());
        } catch (InterruptedException | IOException e) {
            log.error("failed to read file", e);
            fail("Failed to read files into multipart files");
        } finally {
            try {
                // Deleting a just updated cloud function does not cleanup the cloud function instead throws an error.
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }
            cloudFunctionManager.delete(custSynapseIdentifier, CloudFunctionManager.DEFAULT_REGION, ConnectorMetadataService.getDraftFileName(custSynapseIdentifier));
            cloudFunctionManager.delete(custSynapseIdentifier + "_draft", CloudFunctionManager.DEFAULT_REGION, ConnectorMetadataService.getDraftFileName(custSynapseIdentifier));
        }
    }

    private void verifySynapseInfo(ConnectorMetadata connectormd) {
        assertNotNull(connectormd.getSupportedAuthTypes());
        assertNotNull(connectormd.getConfigureFields());
        List<AuthMetadata> supportedAuthTypes = connectormd.getSupportedAuthTypes();
        assertTrue(supportedAuthTypes.size() == 1);
        AuthMetadata supportedType = supportedAuthTypes.get(0);
        assertEquals(AuthType.UserPassword, supportedType.getAuthType());
        assertTrue(supportedType.getFields().size() == 2);
        assertEquals("userName", supportedType.getFields().get(0).getName());
        assertEquals("string", supportedType.getFields().get(0).getDataType());
        assertEquals("password", supportedType.getFields().get(1).getName());
        assertEquals("password", supportedType.getFields().get(1).getDataType());

        List<AuthField> configuredFields = connectormd.getConfigureFields();
        assertTrue(configuredFields.size() == 2);
        assertEquals("endpoint", configuredFields.get(0).getName());
        assertEquals("string", configuredFields.get(0).getDataType());
    }

    private void verifySynapseFunctionality(ConnectorMetadata connectormd) {
        // TODO:
    }


    private void verifyFunctionStatus(ConnectorMetadata connectorMetaDefinition, SyncariCloudFunctionStatus.CODE expectedStatus) {
        int retries = 20;
        SyncariCloudFunctionStatus status = null;
        while(status.getCode() != expectedStatus && retries > 0) {
            try {
                Thread.sleep(10000);
                status = connectorMetadataService.getCustomConnectorMetadataStatus(connectorMetaDefinition.getId());
            } catch (Exception e) {
                // Do nothing
            }
            retries--;
        }
        assertEquals(expectedStatus, status);
    }
    
	@Test
	public void connectorMetadataHttpSourceCRUD() {
		try {

			// Create http source valid request
			var req = new HttpSourceMetadataDTO().setName("test_http").setDisplayName("Test HTTP")
					.setAuthType(AuthType.None).setAuthConfig(null)
					.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
							.set("required", false).set("multivalued", false)))
					.setVariableValues(List.of(new KeyValue().set("name", "variable1").set("value", "value1")))
					.setMethod("GET").setEndpoint("http://www.example.com").setHeaders(Map.of("h1", "header1"))
					.setBody("{\"success\":true}").setIcon(null);
			ConnectorMetadata created = connectorMetadataService.createHttpSourceDraft(req);
			assertNotNull(created);
			assertNotNull(created.getId());
			assertNull(created.getParentId());
			assertEquals(DraftStatus.NEW, created.getDraftStatus());

			// Update http source valid request
			var req1 = new HttpSourceMetadataDTO().setName("test_http").setDisplayName("Test HTTP updated")
					.setAuthType(AuthType.ApiSecretKey).setAuthConfig(new AuthConfig().setAccessToken("accessToken1"))
					.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
							.set("required", false).set("multivalued", false)))
					.setVariableValues(List.of(new KeyValue().set("name", "variable1").set("value", "value1")))
					.setMethod("GET").setEndpoint("http://www.example.com").setHeaders(Map.of("h1", "header1"))
					.setBody("{\"success\":true}").setIcon(null);
			ConnectorMetadata updated = connectorMetadataService.updateHttpSourceDraft(created.getId(), req1);
			assertNotNull(updated);
			assertEquals(created.getId(), updated.getId());
			assertNull(updated.getParentId());
			assertEquals(DraftStatus.NEW, updated.getDraftStatus());
			assertEquals("Test HTTP updated", updated.getDisplayName());
			assertEquals(AuthType.ApiSecretKey, updated.getAuthType());
			assertEquals("accessToken1", updated.getAuthConfig().getAccessToken());

			// Discard http source valid request
			assertFalse(connectorMetadataService.listAllCustomSynapses().isEmpty());
			connectorMetadataService.discardDraft(updated.getId());
//			assertTrue(connectorMetadataService.listAllCustomSynapses().isEmpty());

			// Create draft http source invalid
			created = connectorMetadataService.createHttpSourceDraft(req);
			try {
				created = connectorMetadataService.createHttpSourceDraftFor(created.getId());
				fail("Should fail due to meta data in new state");
			} catch (Exception e) {
				e.printStackTrace();
			}

			// Approve draft http source valid
			var approved = connectorMetadataService.approveHttpSource(created.getId());
			assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
			
			// Create draft http source valid
			created = connectorMetadataService.createHttpSourceDraftFor(approved.getId());
			assertEquals(DraftStatus.NEW, created.getDraftStatus());
			
			// Approve draft http source invalid
			try {
				approved = connectorMetadataService.approveHttpSource(approved.getId());
				fail("Should fail due to meta data in approved state");
			} catch (Exception e) {
				e.printStackTrace();
			}
			

		} catch (Exception e) {
			log.error("Failed http source crud", e);
			fail(e.getMessage());
		}
	}
	
	@Test
	public void connectorMetadataHttpSourceTest() {
	  httpSourcesService.setTokenHandler(new DefaultAuthTokenHandler() {
          public AuthConfig getAccessToken(String endpoint, Map<String, String> map) {
              throw new IllegalArgumentException("Needs OAuth headers");
          }

          public AuthConfig getAccessToken(String endpoint, java.util.Map<String, String> dataMap, java.util.Map<String, String> headersMap) {
	        assertEquals("http://localhost:80/oauth/token", endpoint);
	        assertEquals("Basic " + Base64.getEncoder().encodeToString("c1:c2".getBytes()), headersMap.get("Authorization"));
	        return new AuthConfig().setAccessToken("token1");
	      };
	    });
		var oldHelper = httpSourcesService.getHelper();
		httpSourcesService.setHelper(httpSourcesHelper);
		HTTPSourceResult result = new HTTPSourceResult().setBodyString("{\"success\":true}");
		when(httpSourcesHelper.execute(any(), any(), any(), anyBoolean())).thenReturn(result);
		var res = connectorMetadataService.testHttpSource(null, AuthType.None,
				new AuthConfig().setClientId("c1").setClientSecret("c2"),
				new HttpSourceConfig()
				.setMethod("GET")
				.setEndpoint("http://www.example.com")
				.setHeaders(Map.of("h1", "header1"))
				.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
						.set("required", false).set("multivalued", false))),
				List.of(new KeyValue().set("name", "variable1").set("value", "value1")));
		assertNotNull(res);
		assertEquals("{\"success\":true}", result.getBodyString());
		httpSourcesService.setHelper(oldHelper);
		
	}
	
	@Test
	public void connectorMetadataHttpSourceEntityValidations() {
		try {

			// Create http source valid request
			var req = new HttpSourceMetadataDTO().setName("test_http").setDisplayName("Test HTTP")
					.setAuthType(AuthType.None).setAuthConfig(null)
					.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
							.set("required", false).set("multivalued", false)))
					.setVariableValues(List.of(new KeyValue().set("name", "variable1").set("value", "value1")))
					.setMethod("GET").setEndpoint("http://www.example.com").setHeaders(Map.of("h1", "header1"))
					.setBody("{\"success\":true}").setIcon(null);
			ConnectorMetadata created = connectorMetadataService.createHttpSourceDraft(req);
			assertNotNull(created);
			assertNotNull(created.getId());
			assertNull(created.getParentId());
			assertEquals(DraftStatus.NEW, created.getDraftStatus());
			
			//List Entities initially empty
			assertTrue(connectorMetadataService.findAllHttpSource(created.getId()).isEmpty());
			
			//Create Entity validations
			var entity1 = new HttpSourceConfig();
			try {
				connectorMetadataService.saveHttpSource(entity1, created.getId());
				fail();
			} catch (SyncariValidationException e) {
				assertEquals("Api Name cannot be empty", e.getMessage());
			}
			
			entity1.setApiName("test");
			try {
				connectorMetadataService.saveHttpSource(entity1, created.getId());
				fail();
			} catch (SyncariValidationException e) {
				assertEquals("Invalid 'Endpoint' in http source configuration", e.getMessage());
			}
			entity1.setEndpoint("xyz");
			try {
				connectorMetadataService.saveHttpSource(entity1, created.getId());
				fail();
			} catch (SyncariValidationException e) {
				assertEquals("Invalid 'HTTP Method' in http source configuration", e.getMessage());
			}
			
			entity1.setMethod("GET");
			try {
				connectorMetadataService.saveHttpSource(entity1, created.getId());
				fail();
			} catch (SyncariValidationException e) {
				assertEquals("Invalid 'Endpoint' in http source configuration", e.getMessage());
			}
			
			entity1.setEndpoint("http://example.com");
			entity1.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
					.set("required", false).set("multivalued", false), new KeyValue().set("name", "").set("dataType", "string")
					.set("required", false).set("multivalued", false)));
			try {
				connectorMetadataService.saveHttpSource(entity1, created.getId());
				fail();
			} catch (SyncariValidationException e) {
				assertEquals("Variable(s) configured without valid Name.", e.getMessage());
			}
			
			entity1.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
					.set("required", false).set("multivalued", false), new KeyValue().set("name", "variable1").set("dataType", "string")
					.set("required", false).set("multivalued", false)));
			try {
				connectorMetadataService.saveHttpSource(entity1, created.getId());
				fail();
			} catch (SyncariValidationException e) {
				assertEquals("Duplicate variable names found variable1", e.getMessage());
			}

		} catch (Exception e) {
			e.printStackTrace();
			log.error("Failed http source crud", e);
			fail(e.getMessage());
		}
	}
	
	@Test
	public void connectorMetadataHttpCRUDSourceEntity() {
		try {

			// Create http source valid request
			var req = new HttpSourceMetadataDTO().setName("test_http").setDisplayName("Test HTTP")
					.setAuthType(AuthType.None).setAuthConfig(null)
					.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
							.set("required", false).set("multivalued", false)))
					.setVariableValues(List.of(new KeyValue().set("name", "variable1").set("value", "value1")))
					.setMethod("GET").setEndpoint("http://www.example.com").setHeaders(Map.of("h1", "header1"))
					.setBody("{\"success\":true}").setIcon(null);
			ConnectorMetadata created = connectorMetadataService.createHttpSourceDraft(req);
			assertNotNull(created);
			assertNotNull(created.getId());
			assertNull(created.getParentId());
			assertEquals(DraftStatus.NEW, created.getDraftStatus());
			
			//List Entities initially empty
			assertTrue(connectorMetadataService.findAllHttpSource(created.getId()).isEmpty());
			
			//Create Entity success
			var entity1 = new HttpSourceConfig();
			entity1.setId(ObjectId.get().toHexString());
			entity1.setApiName("test");
			entity1.setMethod("GET");
			entity1.setEndpoint("http://example.com");
			entity1.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
					.set("required", false).set("multivalued", false)));
			var entityCreated = connectorMetadataService.saveHttpSource(entity1, created.getId());
			assertNotNull(entityCreated);
			assertEquals("test", entityCreated.getApiName());
			assertFalse(connectorMetadataService.findAllHttpSource(created.getId()).isEmpty());
			
			//Create Entity fail duplicate api name
			var entity2 = new HttpSourceConfig();
			entity2.setId(ObjectId.get().toHexString());
			entity2.setApiName("test");
			entity2.setMethod("GET");
			entity2.setEndpoint("http://example.com");
			entity2.setVariables(List.of(new KeyValue().set("name", "variable1").set("dataType", "string")
					.set("required", false).set("multivalued", false)));
			try {
				connectorMetadataService.saveHttpSource(entity2, created.getId());
				fail();
			}catch (SyncariValidationException e) {
				assertEquals("Entity with api name test already exists", e.getMessage());
				assertEquals(1, connectorMetadataService.findAllHttpSource(created.getId()).size());
			}
			
			//Update entity success
			entityCreated.setApiName("test1");
			entityCreated.setMethod("POST");
			var entityUpdated = connectorMetadataService.saveHttpSource(entity1, created.getId());
			assertEquals(1, connectorMetadataService.findAllHttpSource(created.getId()).size());
			assertEquals(entityCreated.getId(), entityUpdated.getId());
			assertEquals("test1", entityUpdated.getApiName());
			assertEquals("POST", entityUpdated.getMethod());
			
			//Find entity success
			var entityReturned = connectorMetadataService.findHttpSource(created.getId(), entityCreated.getId());
			assertNotNull(entityReturned);
			assertEquals("test1", entityReturned.getApiName());
			assertEquals("POST", entityReturned.getMethod());
			
			//Find entity failure
			entityReturned = connectorMetadataService.findHttpSource(created.getId(), ObjectId.get().toHexString());
			assertNull(entityReturned);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Failed http source crud", e);
			fail(e.getMessage());
		}
	}
	
	@Test
    public void connectorMetadataCRUDWebhookEntity() {
	  try {

        // Nothing to begin with
        List<ConnectorMetadata> definitions = connectorMetadataService.listAllCustomSynapses();
        assertTrue(definitions.isEmpty());

        // Create webhook valid request
        var req = new WebhookReceiverMetadataDTO().setName("test_wh").setDisplayName("Test WH")
                .setAuthType(AuthType.None).setAuthConfig(null).setIdSelector("id").setRecordSelector("")
                .setSchema( "{\n  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n  \"title\": \"Person\",\n  \"type\": \"object\",\n  \"properties\": {\n    \"id\": {\n      \"type\": \"integer\"\n    },\n    \"name\": {\n      \"type\": \"string\"\n    },\n    \"age\": {\n      \"type\": \"integer\"\n    },\n    \"address\": {\n      \"type\": \"object\",\n      \"properties\": {\n        \"street\": {\n          \"type\": \"string\"\n        },\n        \"city\": {\n          \"type\": \"string\"\n        },\n        \"state\": {\n          \"type\": \"string\"\n        },\n        \"country\": {\n          \"type\": \"string\"\n        }\n      }\n    },\n    \"updatedAt\": {\n      \"type\": \"string\",\n      \"format\": \"date-time\"\n    }\n  }\n}\n")
                .setIcon(null);
        ConnectorMetadata created = connectorMetadataService.createWebhookReceiverDraft(req);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertNull(created.getParentId());
        assertEquals(DraftStatus.NEW, created.getDraftStatus());

        // Update webhook valid request
        var req1 = new WebhookReceiverMetadataDTO().setName("test_wh").setDisplayName("Test WH Updated")
            .setAuthType(AuthType.ApiSecretKey).setAuthConfig(new AuthConfig().setAccessToken("authToken1")).setIdSelector("id").setRecordSelector("")
            .setSchema( "{\n  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n  \"title\": \"Person\",\n  \"type\": \"object\",\n  \"properties\": {\n    \"id\": {\n      \"type\": \"integer\"\n    },\n    \"name\": {\n      \"type\": \"string\"\n    },\n    \"age\": {\n      \"type\": \"integer\"\n    },\n    \"address\": {\n      \"type\": \"object\",\n      \"properties\": {\n        \"street\": {\n          \"type\": \"string\"\n        },\n        \"city\": {\n          \"type\": \"string\"\n        },\n        \"state\": {\n          \"type\": \"string\"\n        },\n        \"country\": {\n          \"type\": \"string\"\n        }\n      }\n    },\n    \"updatedAt\": {\n      \"type\": \"string\",\n      \"format\": \"date-time\"\n    }\n  }\n}\n")
            .setIcon(null);
        ConnectorMetadata updated = connectorMetadataService.updateWebhookReceiverDraft(created.getId(), req1);
        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertNull(updated.getParentId());
        assertEquals(DraftStatus.NEW, updated.getDraftStatus());
        assertEquals("Test WH Updated", updated.getDisplayName());
        assertEquals(AuthType.ApiSecretKey, updated.getAuthType());
        assertEquals("authToken1", updated.getAuthConfig().getAccessToken());

        // Discard webhook valid request
        assertFalse(connectorMetadataService.listAllCustomSynapses().isEmpty());
        connectorMetadataService.discardDraft(updated.getId());
        assertTrue(connectorMetadataService.listAllCustomSynapses().isEmpty());

        // Create draft webhook invalid
        created = connectorMetadataService.createWebhookReceiverDraft(req);
        try {
            created = connectorMetadataService.createWebhookReceiverDraftFor(created.getId());
            fail("Should fail due to meta data in new state");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Approve draft webhook valid
        var approved = connectorMetadataService.approveWebHookReceiver(created.getId());
        assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
        
        // Create draft webhook valid
        created = connectorMetadataService.createWebhookReceiverDraftFor(approved.getId());
        assertEquals(DraftStatus.NEW, created.getDraftStatus());
        
        // Approve draft webhook invalid
        try {
            approved = connectorMetadataService.approveWebHookReceiver(approved.getId());
            fail("Should fail due to meta data in approved state");
        } catch (Exception e) {
            e.printStackTrace();
        }
        

      } catch (Exception e) {
          log.error("Failed http source crud", e);
          fail(e.getMessage());
      }
    }
	
	@Test
    public void connectorMetadataWebhookTest() {
        var res = connectorMetadataService.testWebhookReceiver(AuthType.ApiSecretKey,
                new AuthConfig().setAccessToken("authToken1"),
                new WebhookConfig()
                .setIdSelector("id")
                .setRecordSelector("")
                .setSchema("{\n  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n  \"title\": \"Person\",\n  \"type\": \"object\",\n  \"properties\": {\n    \"id\": {\n      \"type\": \"integer\"\n    },\n    \"name\": {\n      \"type\": \"string\"\n    },\n    \"age\": {\n      \"type\": \"integer\"\n    },\n    \"address\": {\n      \"type\": \"object\",\n      \"properties\": {\n        \"street\": {\n          \"type\": \"string\"\n        },\n        \"city\": {\n          \"type\": \"string\"\n        },\n        \"state\": {\n          \"type\": \"string\"\n        },\n        \"country\": {\n          \"type\": \"string\"\n        }\n      }\n    },\n    \"updatedAt\": {\n      \"type\": \"string\",\n      \"format\": \"date-time\"\n    }\n  }\n}\n"),
                "[{\"id\": 1,\"name\": \"Name 1\",\"age\": 21,\"address\": {\"street\": \"1 Example St\",\"city\": \"City 1\",\"state\": \"State 1\",\"country\": \"Country 1\"},\"updatedAt\": \"2024-10-10T09:38:19.2175439+05:30\"},{\"id\": 2,\"name\": \"Name 2\",\"age\": 22,\"address\": {\"street\": \"2 Example St\",\"city\": \"City 2\",\"state\": \"State 2\",\"country\": \"Country 2\"},\"updatedAt\": \"2024-10-10T09:38:19.2175439+05:30\"}]", Map.of());
        assertNotNull(res);
        assertEquals(2, res.getRecords().size());
        
    }
}
