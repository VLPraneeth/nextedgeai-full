package com.syncari.karibu.rest.controllers;

import com.syncari.karibu.rest.util.OauthUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Override
    public void setUp() {super.setUp();}

    @Test
    public void createUserTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String userRequestAPI = "{\"email\": \"newuser1@syncari.com\", \"firstName\": \"new\", \"lastName\": \"user\", \"isApiUser\" : true, \"userRoles\" : { \"syncari_admin\" : [\"Viewer\"] } }";
            String userRequestNotAPI = "{\"email\": \"newuser2@syncari.com\", \"firstName\": \"new\", \"lastName\": \"user2\", \"isApiUser\" : false, \"userRoles\" : { \"syncari_admin\" : [\"Viewer\"] } }";

            ResultActions resultCreateUserAPI = mockMvc.perform(post("/api/v1/users")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(userRequestAPI))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.email", is("newuser1@syncari.com")))
                    .andExpect(jsonPath("$.result.firstName", is("new")))
                    .andExpect(jsonPath("$.result.lastName", is("user")))
                    .andExpect(jsonPath("$.result.apiUser", is(true)))
                    .andExpect(jsonPath("$.result.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.result.clientId").isNotEmpty())
                    .andExpect(jsonPath("$.result.clientSecret").isNotEmpty())
                    .andExpect(status().isOk());

            ResultActions resultCreateUserNotAPI = mockMvc.perform(post("/api/v1/users")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(userRequestNotAPI))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.email", is("newuser2@syncari.com")))
                    .andExpect(jsonPath("$.result.firstName", is("new")))
                    .andExpect(jsonPath("$.result.lastName", is("user2")))
                    .andExpect(jsonPath("$.result.apiUser", is(false)))
                    .andExpect(jsonPath("$.result.status", is("PENDING")))
                    .andExpect(jsonPath("$.result.clientId").isEmpty())
                    .andExpect(jsonPath("$.result.clientSecret").isEmpty())
                    .andExpect(status().isOk());

        }catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createUserNegativeTest() {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String userRequestBadEmail = "{\"email\": \"newuser1\", \"firstName\": \"new\", \"lastName\": \"user\", \"isApiUser\" : true, \"userRoles\" : { \"syncari_admin\" : [\"Viewer\"] } }";
            String userRequestMissingFirstName = "{\"email\": \"newuser1@syncari.com\", \"lastName\": \"user\", \"isApiUser\" : true, \"userRoles\" : { \"syncari_admin\" : [\"Viewer\"] } }";
            String userRequestNoRoles = "{\"email\": \"newuser1@syncari.com\", \"firstName\": \"new\", \"lastName\": \"user\", \"isApiUser\" : true }";
            String userRequestUnkonwnInstance = "{\"email\": \"newuser1@syncari.com\", \"firstName\": \"new\", \"lastName\": \"user\", \"isApiUser\" : true, \"userRoles\" : { \"syncari_adminUnkonw\" : [\"Viewer\"] } }";

            ResultActions resultCreateUserBadEmail = mockMvc.perform(post("/api/v1/users")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(userRequestBadEmail))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("email: must be a well-formed email address")))
                    .andExpect(status().isBadRequest());

            ResultActions resultCreateUserMissingFirstName = mockMvc.perform(post("/api/v1/users")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(userRequestMissingFirstName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field firstName is empty. Please verify this request parameter")))
                    .andExpect(status().isBadRequest());

            ResultActions resultCreateUserMissingNoRoles = mockMvc.perform(post("/api/v1/users")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(userRequestNoRoles))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Field userRoles is empty. Please verify this request parameter")))
                    .andExpect(status().isBadRequest());

            ResultActions resultCreateUserMissingUnkownInstance = mockMvc.perform(post("/api/v1/users")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(userRequestUnkonwnInstance))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Subscription with syncari id syncari_adminUnkonw not found")))
                    .andExpect(status().isBadRequest());

        }catch (Exception e) {
            assertTrue(false);
        }
    }

}
