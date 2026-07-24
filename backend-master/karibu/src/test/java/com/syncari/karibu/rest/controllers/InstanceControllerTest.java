package com.syncari.karibu.rest.controllers;

import com.jayway.jsonpath.JsonPath;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.ProvisioningResponse;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.service.ProvisioningService;
import com.syncari.karibu.rest.util.OauthUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class InstanceControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Autowired
    ProvisioningService provisioningService;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void createAsyncInstanceTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String instanceRequest = "{\"name\": \"asyncTestInstance1\", \"displayName\": \"async test instance 1\", \"subscriptionName\": \"Test Org\", \"planName\" : \"default\", \"type\" : \"production\" }";

            ResultActions resultAsyncCreateInstance = mockMvc.perform(post("/api/v1/instances")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.jobDetails.name", is("asyncTestInstance1")))
                    .andExpect(jsonPath("$.result.jobDetails.displayName", is("async test instance 1")))
                    .andExpect(jsonPath("$.result.jobDetails.subscriptionName", is("Test Org")))
                    .andExpect(jsonPath("$.result.jobDetails.planName", is("default")))
                    .andExpect(jsonPath("$.result.jobDetails.type", is("production")))
                    .andExpect(jsonPath("$.result.status", is("queued")))
                    .andExpect(status().isAccepted());

            MvcResult createInstanceAsyncResult = resultAsyncCreateInstance.andReturn();
            String jobId = JsonPath.read(createInstanceAsyncResult.getResponse().getContentAsString(), "$.result.jobId");

            ResultActions resultGetJobStatus = mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.jobDetails.name", is("asyncTestInstance1")))
                    .andExpect(jsonPath("$.result.jobDetails.displayName", is("async test instance 1")))
                    .andExpect(jsonPath("$.result.jobDetails.subscriptionName", is("Test Org")))
                    .andExpect(jsonPath("$.result.jobDetails.planName", is("default")))
                    .andExpect(jsonPath("$.result.jobDetails.type", is("production")))
                    .andExpect(jsonPath("$.result.status", isIn(Arrays.asList("queued", "processing", "completed"))))
                    .andExpect(status().isOk());
        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createAsyncInstanceTestForTrialType() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String instanceRequest = "{\"name\": \"asyncTestInstance2\", \"displayName\": \"async test instance 2\", \"subscriptionName\": \"Test Org\", \"planName\" : \"default\", \"type\" : \"trial\" }";

            ResultActions resultAsyncCreateInstance = mockMvc.perform(post("/api/v1/instances")
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(instanceRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.jobDetails.name", is("asyncTestInstance2")))
                    .andExpect(jsonPath("$.result.jobDetails.displayName", is("async test instance 2")))
                    .andExpect(jsonPath("$.result.jobDetails.subscriptionName", is("Test Org")))
                    .andExpect(jsonPath("$.result.jobDetails.planName", is("trial")))
                    .andExpect(jsonPath("$.result.jobDetails.type", is("trial")))
                    .andExpect(jsonPath("$.result.status", is("queued")))
                    .andExpect(status().isAccepted());

            MvcResult createInstanceAsyncResult = resultAsyncCreateInstance.andReturn();
            String jobId = JsonPath.read(createInstanceAsyncResult.getResponse().getContentAsString(), "$.result.jobId");

            ResultActions resultGetJobStatus = mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId)
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8).content(instanceRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.jobDetails.name", is("asyncTestInstance2")))
                    .andExpect(jsonPath("$.result.jobDetails.displayName", is("async test instance 2")))
                    .andExpect(jsonPath("$.result.jobDetails.subscriptionName", is("Test Org")))
                    .andExpect(jsonPath("$.result.jobDetails.planName", is("trial")))
                    .andExpect(jsonPath("$.result.jobDetails.type", is("trial")))
                    .andExpect(jsonPath("$.result.status", isIn(Arrays.asList("queued", "processing", "completed"))))
                    .andExpect(status().isOk());
        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createInstanceNegativeTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String instanceRequestBlankName = "{\"name\": \"\", \"displayName\": \"test instance 1\", \"subscriptionName\": \"Test Org\", \"planName\" : \"default\", \"type\" : \"production\" }";

            ResultActions resultCreateInstanceBlankName = mockMvc.perform(post("/api/v1/instances")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequestBlankName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Instance name is empty. Please verify these request parameters")))
                    .andExpect(status().isBadRequest());

            String instanceRequestLongName = "{\"name\": \"12345678901234567890123456789012345\", \"displayName\": \"test instance 1\", \"subscriptionName\": \"Test Org\", \"planName\" : \"default\", \"type\" : \"production\" }";

            ResultActions resultCreateInstanceLongName = mockMvc.perform(post("/api/v1/instances")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequestLongName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Length of Instance name is more than 30 characters. Please reduce the length to process the request")))
                    .andExpect(status().isBadRequest());

            String instanceRequestMismatchName = "{\"name\": \"testInstance2\", \"displayName\": \"test instance 2\", \"subscriptionName\": \"Test Bad Org\", \"planName\" : \"default\", \"type\" : \"production\" }";

            ResultActions resultCreateInstanceMismatchName = mockMvc.perform(post("/api/v1/instances")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequestMismatchName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("This user cannot create instances in org 'Test Bad Org'")))
                    .andExpect(status().isForbidden());

            String instanceRequestMissingName = "{\"displayName\": \"test instance 2\", \"subscriptionName\": \"Test Org\", \"planName\" : \"default\", \"type\" : \"production\" }";

            ResultActions resultCreateInstanceMisssingName = mockMvc.perform(post("/api/v1/instances")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequestMissingName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Instance name is empty. Please verify these request parameters")))
                    .andExpect(status().isBadRequest());

            String instanceRequestMissingNamePlanName = "{\"displayName\": \"test instance 2\", \"subscriptionName\": \"Test Bad Org\", \"type\" : \"production\" }";

            ResultActions resultCreateInstanceMisssingNamePlanName = mockMvc.perform(post("/api/v1/instances")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequestMissingNamePlanName))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            String instanceRequestBadPlanName = "{\"name\": \"testInstance2\", \"displayName\": \"test instance 2\", \"subscriptionName\": \"Test Org\", \"planName\" : \"bad name\", \"type\" : \"production\" }";

            ResultActions resultCreateInstanceBadPlanName = mockMvc.perform(post("/api/v1/instances")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequestBadPlanName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Invalid plan name. plan must one of default | trial")))
                    .andExpect(status().isBadRequest());

        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    @Ignore
    public void getInstanceProfile() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String instanceRequest = "{\"name\": \"asyncTestInstance2\", \"displayName\": \"async test instance 2\", \"subscriptionName\": \"Test Org\", \"planName\" : \"default\", \"type\" : \"trial\" }";

            ResultActions resultAsyncCreateInstance = mockMvc.perform(post("/api/v1/instances/profile/"+ SyncariContext.getSyncariId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(instanceRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.jobDetails.name", is("asyncTestInstance2")))
                    .andExpect(jsonPath("$.result.jobDetails.displayName", is("async test instance 2")))
                    .andExpect(jsonPath("$.result.jobDetails.subscriptionName", is("Test Org")))
                    .andExpect(jsonPath("$.result.jobDetails.planName", is("trial")))
                    .andExpect(jsonPath("$.result.jobDetails.type", is("trial")))
                    .andExpect(jsonPath("$.result.status", is("queued")))
                    .andExpect(status().isAccepted());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void deleteInstanceEmtpySyncariId() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            mockMvc.perform(post("/api/v1/instances/")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            assertTrue(false);
        }

    }

    @Test
    public void deleteInstance() {
        try {
            String adminEmail = "test@testemail.com";
            ProvisioningResponse response = provisioningService.provision("instance1", InstanceType.production, "testorg", "testorg",
                    adminEmail, null, RoleConstants.ORG_ADMIN, "testfn", "testln", OrganizationType.standard, "2");
            Organization sub = response.getOrganization();
            Instance provisionedInstance = sub.getInstances().stream().filter(i -> i.getName().equalsIgnoreCase("instance1")).findFirst().get();

            String accessToken = oauthUtil.getTestAccessToken();
            mockMvc.perform(delete("/api/v1/instances/" + provisionedInstance.getSyncariId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/instances/profile/" + provisionedInstance.getSyncariId())
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(status().isOk());
        } catch (Exception e) {
            assertTrue(false);
        }

    }
}
