package com.syncari.karibu.rest.controllers;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.syncari.core.service.ConnectorService;
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.karibu.rest.util.SynapseTestUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public class SynapseControllerTest extends AbstractSyncariTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Autowired
    SynapseTestUtil synapseTestUtil;

    @Autowired
    ConnectorService connectorService;

    @Override
    public void setUp() {super.setUp();}

    String createSalesforeSynapseName = "Create Salesforce Synapse Test";
    String createHubspotSynapseName = "Create Hubspot Synapse Test";
    String createGoogleSheetSynapseName = "Create GoogleSheet Synapse Test";
    String createSalesforeOauthSynapseName = "Create Salesforce Oauth Synapse Test";
    String createBadSalesforeSynapseName = "Create Bad Salesforce Synapse Test";
    String createImpartnerSynapseName = "Create Impartner Synapse Test";
    String createMarketoSynapseName = "Create Marketo Synapse Test";
    String createFullActivateSynapseName = "Create Full Activate Synapse Test";
    String createFullDeleteSynapseName = "Create Full Delete Synapse Test";
    String updateSalesforeSynapseName = "Update Salesforce Synapse Test";
    String updateMarketoSynapseName = "Update Marketo Synapse Test";
    String updateImpartnerSynapseName = "Update Impartner Synapse Test";

    private static final List<String> allSynapseNames = List.of("salesforce", "hubspot", "zendesk", "marketo", "gainsightcs",
            "redshift", "zuora", "snowflake", "airtable", "amplitude", "xero", "salesloft",
            "msdynamics", "eloqua", "intacct", "intercom", "jira", "exacttarget", "pardot",
            "s3", "jiraservicedesk", "freshsales", "postgresql", "zoominfosynapse", "bigquery", "kafka",
            "mysql", "zoho", "impartner", "dynamodb", "slacksynapse", "oraclesalescrm",
            "chargebee", "stripe", "filedata", "sftp", "sap", "pendo", "msdynamicsbizcentral",
            "pendofeedback", "azuresql", "outreach", "googlesheets", "netsuite", "oracle", "azureblobstore", "oraclepim", "oracleerpsales", "databricks",
            "oracleerpreceivables", "oracleerprocurement", "mongodb");

    @Test
    public void describeSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/synapses/describe")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.result", hasSize(52)))
                    .andExpect(jsonPath("$.result[*].name",
                            containsInAnyOrder(allSynapseNames.toArray())));

            resultSynapses.andReturn();
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void describeSynapsesPaginationTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            List<String> synapseNamesCollectedSoFar = new ArrayList<>();
            String cursorToken = null;
            int pageNum = 0;
            do {
                pageNum++;
                String requestUrl = "/api/v1/synapses/describe?limit=20";
                if (cursorToken != null) {
                    requestUrl += "&cursorToken=" + cursorToken;
                }
                ResultActions resultSynapses = mockMvc.perform(get(requestUrl)
                                .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success", is(true)));
                MvcResult mvcResult = resultSynapses.andReturn();
                String responseContent = mvcResult.getResponse().getContentAsString();

                try {
                    cursorToken = JsonPath.read(responseContent, "$.cursorToken");
                } catch (PathNotFoundException e) {
                    // cursorToken is not returned in the last page. Each page loads 20 synapses, & since we have 45 synapses, we expect 3 pages.
                    assertThat(pageNum, is(4));
                    cursorToken = null;
                }

                List<String> currentPageSynapseNames = JsonPath.read(responseContent, "$.result[*].name");
                // verify all synapses returned in current page are different than the synapses returned in previous pages
                assertThat(synapseNamesCollectedSoFar, not(containsInAnyOrder(currentPageSynapseNames.toArray())));
                synapseNamesCollectedSoFar.addAll(currentPageSynapseNames);
            } while (cursorToken != null);
            // verify all synapses gave been fetched
            assertThat(synapseNamesCollectedSoFar, containsInAnyOrder(allSynapseNames.toArray()));
        } catch (Exception e) {
            fail();
        }
    }

    // ------------------------------------- createSynapses ---------------------------------------------------------------
    @Test
    public void createSalesforceSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getSalesforceSynapseRequestString(createSalesforeSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            assertEquals(createSalesforeSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("UserPasswordToken", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("https://syncariinc--unittests.sandbox.my.salesforce.com", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.endpoint"));
            assertEquals("mary+pbo@syncari.com.unittests", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.userName"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.token"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.password"));


            ResultActions resultDuplicateSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getSalesforceSynapseRequestString(createSalesforeSynapseName)))
                    .andDo(print())
                    .andExpect(status().isConflict());

            MvcResult resultDuplicate = resultDuplicateSynapses.andReturn();
            assertEquals("Synapse with name "+createSalesforeSynapseName+" already exists", JsonPath.read(resultDuplicate.getResponse().getContentAsString(), "$.error.message"));

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createHubspotSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getHubspotSynapseRequestString(createHubspotSynapseName)))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.configuration.authType", is("Oauth")))
                    .andExpect(jsonPath("$.result.name", is(createHubspotSynapseName)))
                    .andExpect(jsonPath("$.result.configuration.clientId", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.clientSecret", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.accessToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.refreshToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.oAuthRedirectUrl", is("http://localhost:3000/oauth/authorize?client_id=a5dd557c-6967-4f23-8589-ae624c6d32c0")))
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");

            ResultActions resultDuplicateSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getSalesforceSynapseRequestString(createHubspotSynapseName)))
                    .andDo(print())
                    .andExpect(status().isConflict());

            MvcResult resultDuplicate = resultDuplicateSynapses.andReturn();
            assertEquals("Synapse with name "+createHubspotSynapseName+" already exists", JsonPath.read(resultDuplicate.getResponse().getContentAsString(), "$.error.message"));

            ResultActions resultUpdateSynapses = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getHubspotSynapseUpdateRequestString()))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.configuration.authType", is("Oauth")))
                    .andExpect(jsonPath("$.result.name", is(createHubspotSynapseName)))
                    .andExpect(jsonPath("$.result.configuration.clientId", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.clientSecret", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.accessToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.refreshToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.oAuthRedirectUrl", is("http://localhost:3000/oauth/authorize?client_id=a5dd557c-6967-4f23-8589-ae624c6d32c0")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createAndActivateGoogleSheetSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getGoogleSheetSynapseRequestString(createGoogleSheetSynapseName)))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.configuration.authType", is("Oauth")))
                    .andExpect(jsonPath("$.result.name", is(createGoogleSheetSynapseName)))
                    .andExpect(jsonPath("$.result.configuration.clientId", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.clientSecret", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.accessToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.refreshToken", is("*****")))
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");

            //assertEquals(ConnectorStatus.NEW, connectorService.findLite(synapseId).getStatus());

            ResultActions resultDuplicateSynapses = mockMvc.perform(post("/api/v1/synapses")
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getGoogleSheetSynapseRequestString(createGoogleSheetSynapseName)))
                    .andDo(print())
                    .andExpect(status().isConflict());

            MvcResult resultDuplicate = resultDuplicateSynapses.andReturn();
            assertEquals("Synapse with name "+createGoogleSheetSynapseName+" already exists", JsonPath.read(resultDuplicate.getResponse().getContentAsString(), "$.error.message"));

            // deactivate synapse

            ResultActions resultValidDeactivate = mockMvc.perform(post("/api/v1/synapses/{synapseId}/deactivate", synapseId)
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk());
            //assertEquals(ConnectorStatus.INACTIVE, connectorService.findLite(synapseId).getStatus());

            ResultActions resultUpdateSynapses = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", synapseId)
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getGoogleSheetSynapseUpdateRequestString_INVALID()))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.configuration.authType", is("Oauth")))
                    .andExpect(jsonPath("$.result.name", is(createGoogleSheetSynapseName)))
                    .andExpect(jsonPath("$.result.configuration.clientId", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.clientSecret", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.accessToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.refreshToken", is("*****")))
                    .andExpect(status().isOk());


            // activate synapse with bad creds - testConnection should fail
            ResultActions resultInValidActivate = mockMvc.perform(post("/api/v1/synapses/{synapseId}/activate", synapseId)
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message", is(String.format("Error authenticating Synapse with synapseId %s", synapseId))));


            ResultActions getSynapse = mockMvc.perform(get("/api/v1/synapses/{synapseId}", synapseId)
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status", is("ERROR")));


        } catch (Exception e) {
            log.error(e.getMessage(), e);
            assertTrue(false);
        }
    }

    @Test
    public void createImpartnerSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getImpartnerSynapseRequestString(createImpartnerSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            assertEquals(createImpartnerSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("UserPassword", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("https://prod.impartner.live", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.endpoint"));
            assertEquals("eng@syncari.com.dev", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.userName"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.password"));
            assertEquals("America/Los_Angeles", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.timeZoneId"));
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createMarketoSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getMarketoSynapseRequestString(createMarketoSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            assertEquals(createMarketoSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("SimpleOAuth", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.clientId"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.clientSecret"));
            assertEquals("183-LYQ-451", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.munchkin"));
            assertEquals("*", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.staticListId"));
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createSynapsesTestMissingHeader() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getSalesforceSynapseRequestString(createBadSalesforeSynapseName)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createSynapsesTestMissingAuthType() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getBadSalesforceSynapseRequestString(createBadSalesforeSynapseName, "authType")))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            MvcResult result = resultSynapses.andReturn();
            assertEquals("Missing required fields", JsonPath.read(result.getResponse().getContentAsString(), "$.error.message"));
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createSynapsesTestMissingPassword() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getBadSalesforceSynapseRequestString(createBadSalesforeSynapseName, "password")))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            MvcResult result = resultSynapses.andReturn();
            assertEquals("The following fields are required for AuthType =  UserPasswordToken : [userName, password, token, endpoint, authType]", JsonPath.read(result.getResponse().getContentAsString(), "$.error.message"));
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createSynapsesTestAddedTimezone() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getBadSalesforceSynapseRequestString(createBadSalesforeSynapseName, "timeZoneId")))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            MvcResult result = resultSynapses.andReturn();
            assertEquals("The following fields are required for AuthType =  UserPasswordToken : [userName, password, token, endpoint, authType]", JsonPath.read(result.getResponse().getContentAsString(), "$.error.message"));
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createSynapsesTestBadSynapseTypeId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getBadSalesforceSynapseRequestString(createBadSalesforeSynapseName, "synapseTypeId")))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            MvcResult result = resultSynapses.andReturn();
            assertEquals("Invalid synapseTypeId", JsonPath.read(result.getResponse().getContentAsString(), "$.error.message"));
        } catch (Exception e) {
            assertTrue(false);
        }
    }



    // ------------------------------------- getSynapses ---------------------------------------------------------------
    @Test
    public void getSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk());
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    // ------------------------------------- getSynapseById ------------------------------------------------------------
    @Test
    public void testGetSynapseById() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/synapses/{synapseId}", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }

    }


    @Test
    public void getSynapseByIdBadSynapseId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(get("/api/v1/synapses/{synapseId}", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    // ------------------------------------- updateSynapse -------------------------------------------------------------
    @Test
    public void testUpdateSynapseBadId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultUpdateSynapseActions = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getUpdateSalesforceSynapseRequestString("bad id name")))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

            ResultActions resultUpdateSynapseActionsMissingClientId = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", "badSynapseId")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getUpdateSalesforceSynapseRequestString("bad id name")))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Missing request header 'clientRequestId' for method parameter of type String")))
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }

    }


    // ------------------------------------- deleteSynapse -------------------------------------------------------------

    @Test
    public void deleteSynapseByIdBadSynapseId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(delete("/api/v1/synapses/{synapseId}", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    // ------------------------------------- activateSynapse -----------------------------------------------------------

    @Test
    public void activateSynapseByIdBadSynapseId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses/{synapseId}/activate", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    // ------------------------------------- deactivateSynapse ---------------------------------------------------------

    @Test
    public void deactivateSynapseByIdBadSynapseId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses/{synapseId}/deactivate", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    // ------------------------------------- testSynapseConnection --------------------------------------------------

    @Test
    public void testSynapseConnectionByIdBadSynapseId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses/{synapseId}/connection", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    // ------------------------------------- testFullActivateSynapse ---------------------------------------------------

    @Ignore
    @Test
    public void createFullActivateSynapsesTest() {
        try {
            // create synapse to activate ------------------------------------------------------------------------------
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getMarketoSynapseRequestString(createFullActivateSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
            assertEquals(createFullActivateSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("NEW", JsonPath.read(result.getResponse().getContentAsString(), "$.result.status"));
            assertEquals("SimpleOAuth", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.clientId"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.clientSecret"));
            assertEquals("183-LYQ-451", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.munchkin"));
            assertEquals("*", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.staticListId"));

            // refreshSchema -------------------------------------------------------------------------------------------
            ResultActions resultRefreshSchemaSynncariSynapse = mockMvc.perform(post("/api/v1/synapses/{synapseId}/refreshSchema", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id "+synapseId+" is not active")))
                    .andExpect(status().isConflict());

            // test synapse to activate --------------------------------------------------------------------------------

            ResultActions resultTestSynapseActions = mockMvc.perform(post("/api/v1/synapses/{synapseId}/connection", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultTestSynapse = resultTestSynapseActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultTestSynapse.getResponse().getContentAsString(), "$.result.id"));

            // get synapse to activate ---------------------------------------------------------------------------------

            ResultActions resultGetSynapseByIdActions = mockMvc.perform(get("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultGetSynapseById = resultGetSynapseByIdActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.id"));
            assertEquals(createFullActivateSynapseName, JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("AUTHENTICATED", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.status"));
            assertEquals("SimpleOAuth", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("*****", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.clientId"));
            assertEquals("*****", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.clientSecret"));
            assertEquals("183-LYQ-451", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.munchkin"));
            assertEquals("*", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.staticListId"));

            // activate synapse ----------------------------------------------------------------------------------------

            ResultActions resultActivateSynapseActions = mockMvc.perform(post("/api/v1/synapses/{synapseId}/activate", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isAccepted());

            MvcResult resultActivateSynapse = resultActivateSynapseActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.id"));
            assertEquals(createFullActivateSynapseName, JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("ACTIVATING", JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.status"));
            assertEquals("SimpleOAuth", JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("*****", JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.configuration.clientId"));
            assertEquals("*****", JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.configuration.clientSecret"));
            assertEquals("183-LYQ-451", JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.configuration.munchkin"));
            assertEquals("*", JsonPath.read(resultActivateSynapse.getResponse().getContentAsString(), "$.result.configuration.staticListId"));

            ResultActions resultRefreshSchema = mockMvc.perform(post("/api/v1/synapses/{synapseId}/refreshSchema", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id "+synapseId+" is activating")))
                    .andExpect(status().isConflict());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    // ------------------------------------- testFullDeleteSynapse -----------------------------------------------------
    @Ignore
    @Test
    public void createFullDeleteSynapsesTest() {
        try {
            // create synapse to delete --------------------------------------------------------------------------------
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getMarketoSynapseRequestString(createFullDeleteSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
            assertEquals(createFullDeleteSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("NEW", JsonPath.read(result.getResponse().getContentAsString(), "$.result.status"));
            assertEquals("SimpleOAuth", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.clientId"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.clientSecret"));
            assertEquals("183-LYQ-451", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.munchkin"));
            assertEquals("*", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.staticListId"));


            // test synapse --------------------------------------------------------------------------------------------

            ResultActions resultTestSynapseActions = mockMvc.perform(post("/api/v1/synapses/{synapseId}/connection", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultTestSynapse = resultTestSynapseActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultTestSynapse.getResponse().getContentAsString(), "$.result.id"));

            // get synapse ---------------------------------------------------------------------------------------------

            ResultActions resultGetSynapseByIdActions = mockMvc.perform(get("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultGetSynapseById = resultGetSynapseByIdActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.id"));
            assertEquals(createFullDeleteSynapseName, JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("AUTHENTICATED", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.status"));
            assertEquals("SimpleOAuth", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("*****", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.clientId"));
            assertEquals("*****", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.clientSecret"));
            assertEquals("183-LYQ-451", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.munchkin"));
            assertEquals("*", JsonPath.read(resultGetSynapseById.getResponse().getContentAsString(), "$.result.configuration.staticListId"));

            // refreshSchema -------------------------------------------------------------------------------------------
            ResultActions resultRefreshSchemaSynncariSynapse = mockMvc.perform(post("/api/v1/synapses/{synapseId}/refreshSchema", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id "+synapseId+" is not active")))
                    .andExpect(status().isConflict());

            // delete synapse ------------------------------------------------------------------------------------------

            ResultActions resultDeleteSynapseActions = mockMvc.perform(delete("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultDeleteSynapse = resultDeleteSynapseActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultDeleteSynapse.getResponse().getContentAsString(), "$.result.id"));

            ResultActions resultDeleteAgainSynapseActions = mockMvc.perform(delete("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id "+synapseId+" is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }
    }


    // ------------------------------------- testUpdateSalesforceSynapse ------------------------------------------------
    @Test
    public void updateSalesforceSynapsesTest() {
        try {
            // create synapse to update --------------------------------------------------------------------------------
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getSalesforceSynapseRequestString(updateSalesforeSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
            assertEquals(updateSalesforeSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));

            // update synapse change synapse name and endpoint ---------------------------------------------------------
            ResultActions resultUpdateSynapseActions = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getUpdateSalesforceSynapseRequestString("new name")))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultUpdateSynapse = resultUpdateSynapseActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.id"));
            assertEquals("new name", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("https://syncariinc--unittests-updated.sandbox.my.salesforce.com", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.endpoint"));
            assertEquals("mary+pbo@syncari.com.unittests", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.userName"));

        } catch (Exception e) {
            assertTrue(false);
        }
    }


    // ------------------------------------- testUpdateMarketoSynapse --------------------------------------------------
    @Ignore
    @Test
    public void updateMarketoSynapsesTest() {
        try {
            // create synapse to update --------------------------------------------------------------------------------
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getMarketoSynapseRequestString(updateMarketoSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
            assertEquals(updateMarketoSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));

            // update synapse change munchkin --------------------------------------------------------------------------
            ResultActions resultUpdateSynapseActions = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getUpdateMarketoSynapseRequestString(updateMarketoSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultUpdateSynapse = resultUpdateSynapseActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.id"));
            assertEquals(updateMarketoSynapseName, JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("SimpleOAuth", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("*****", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.clientId"));
            assertEquals("*****", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.clientSecret"));
            assertEquals("183-LYQ-451", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.munchkin"));
            assertEquals("*", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.staticListId"));

            // test synapse connection ---------------------------------------------------------------------------------
            ResultActions resultTestConnection = mockMvc.perform(post("/api/v1/synapses/{synapseId}/connection", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Authentication failed. java.lang.RuntimeException: Invalid client credentials")))
                    .andExpect(jsonPath("$.error.errorDetail", is("Invalid client credentials")))
                    .andExpect(status().isConflict());

        } catch (Exception e) {
            assertTrue(false);
        }
    }


    // ------------------------------------- testUpdateImpartnerSynapse ------------------------------------------------
    @Test
    public void updateImpartnerSynapsesTest() {
        try {
            // create synapse to update --------------------------------------------------------------------------------
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getImpartnerSynapseRequestString(updateImpartnerSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");
            assertEquals(updateImpartnerSynapseName, JsonPath.read(result.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("UserPassword", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals("https://prod.impartner.live", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.endpoint"));
            assertEquals("eng@syncari.com.dev", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.userName"));
            assertEquals("*****", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.password"));
            assertEquals("America/Los_Angeles", JsonPath.read(result.getResponse().getContentAsString(), "$.result.configuration.timeZoneId"));

            // update synapse change authType --------------------------------------------------------------------------
            ResultActions resultUpdateSynapseActions = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getUpdateImpartnerSynapseRequestString(updateImpartnerSynapseName)))
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult resultUpdateSynapse = resultUpdateSynapseActions.andReturn();
            assertEquals(synapseId, JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.id"));
            assertEquals("ApiKey", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.authType"));
            assertEquals(updateImpartnerSynapseName, JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.name"));
            assertEquals("*****", JsonPath.read(resultUpdateSynapse.getResponse().getContentAsString(), "$.result.configuration.accessToken"));


        } catch (Exception e) {
            assertTrue(false);
        }
    }
    // ------------------------------------- testNegativeSynapseRefreshSchema ------------------------------------------

    @Test
    public void negativeSynapsesRefreshSchemaTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultRefreshSchemaBadId = mockMvc.perform(post("/api/v1/synapses/{synapseId}/refreshSchema", "badSynapseId")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Synapse with Id badSynapseId is not found")))
                    .andExpect(status().isNotFound());

        } catch (Exception e) {
            assertTrue(false);
        }

    }

    // ------------------------------------- testSalesForceOauth ------------------------------------------
    @Test
    public void createSalesforceOauthSynapsesTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            ResultActions resultSynapses = mockMvc.perform(post("/api/v1/synapses")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getSalesforceOauthSynapseRequestString(createSalesforeOauthSynapseName)))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.name", is(createSalesforeOauthSynapseName)))
                    .andExpect(jsonPath("$.result.configuration.authType", is("Oauth")))
                    .andExpect(jsonPath("$.result.configuration.accessToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.refreshToken", is("*****")))
                    .andExpect(status().isOk());

            MvcResult result = resultSynapses.andReturn();
            String synapseId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.id");

            ResultActions resultUpdateSynapse = mockMvc.perform(patch("/api/v1/synapses/{synapseId}", synapseId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(synapseTestUtil.getSalesforceOauthSynapseRequestString(createSalesforeOauthSynapseName)))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.name", is(createSalesforeOauthSynapseName)))
                    .andExpect(jsonPath("$.result.configuration.authType", is("Oauth")))
                    .andExpect(jsonPath("$.result.configuration.accessToken", is("*****")))
                    .andExpect(jsonPath("$.result.configuration.refreshToken", is("*****")))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            assertTrue(false);
        }
    }

}
