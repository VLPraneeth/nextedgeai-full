package com.syncari.karibu.rest.controllers;

import com.syncari.karibu.rest.util.OauthUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ErrorControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Override
    public void setUp() {super.setUp();}

    @Test
    public void listSynapseErrorTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            // test that the api return 200 and there may or may not be synapse created by other tests which can have error.
            ResultActions resultGetSynapseErrors = mockMvc.perform(get("/api/v1/errors")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("errorType", "synapseError"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result").exists())
                    .andExpect(jsonPath("$.cursorToken").doesNotExist())
                    .andExpect(status().isOk());

        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void listSyncErrorTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();
            // test that the api return 200 and there may or may not be synapse created by other tests which can have error.
            ResultActions resultGetSynapseErrors = mockMvc.perform(get("/api/v1/errors")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("errorType", "syncError")
                    .param("startTime", "2023-09-19T00:00:00")
                    .param("endTime", "2023-09-20T00:00:00"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result").exists())
                    .andExpect(status().isOk());

        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void listSynapseErrorNegativeTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultGetSynapseErrorsBadParam = mockMvc.perform(get("/api/v1/errors")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("operation", "Create")
                            .param("errorType", "invalidType"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Request parameter errorType value invalidType is not supported. Supported values are synapseError and syncError")))
                    .andExpect(status().isBadRequest());

        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void listSynapseErrorInvalidErrorType() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultGetSynapseErrorsBadParam = mockMvc.perform(get("/api/v1/errors")
                    .header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .param("operation", "Create")
                    .param("errorType", "synapseError"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Request parameter value synapseError for errorType has no other valid request parameters")))
                    .andExpect(status().isBadRequest());

        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void listSyncErrorNegativeTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultGetSyncErrorsMissingTime = mockMvc.perform(get("/api/v1/errors")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("errorType", "syncError"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Request parameters startTime and endTime are required for errorType of syncError")))
                    .andExpect(status().isBadRequest());

            ResultActions resultGetSyncErrorsBadTimes = mockMvc.perform(get("/api/v1/errors")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", "2024-07-01T17:00:00")
                            .param("endTime", "2020-07-01T17:00:00")
                            .param("operation", "BadOperation")
                            .param("errorType", "syncError"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Start time of 2024-07-01T17:00:00 is greater than equal to the end date of 2020-07-01T17:00:00")))
                    .andExpect(status().isBadRequest());

            ResultActions resultGetSyncErrorsBadOperation = mockMvc.perform(get("/api/v1/errors")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", "2020-07-01T17:00:00")
                            .param("endTime", "2024-07-01T17:00:00")
                            .param("operation", "BadOperation")
                            .param("errorType", "syncError"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Operation of BadOperation not in accepted operations [Create, Update, Delete, Disconnect, Merge]")))
                    .andExpect(status().isBadRequest());

            ResultActions resultGetSyncErrorsBadLimit = mockMvc.perform(get("/api/v1/errors")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", "2020-07-01T17:00:00")
                            .param("endTime", "2024-07-01T17:00:00")
                            .param("limit", "1000")
                            .param("errorType", "syncError"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Limit value of 1000 exceeds max value of 100")))
                    .andExpect(status().isBadRequest());

        }catch (Exception e) {
            assertTrue(false);
        }
    }





}
