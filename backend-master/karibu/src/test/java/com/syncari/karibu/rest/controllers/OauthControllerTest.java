package com.syncari.karibu.rest.controllers;

import com.syncari.karibu.Constants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

public class OauthControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    String clientId = "Fu3dgclRwNBKgod_KhnS_xYrWiY";
    String clientSecret = System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME");


    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void getAccessToken () {
        try {
            mockMvc.perform(post("/api/v1/oauth/token")
            		.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            		.content(EntityUtils.toString(new UrlEncodedFormEntity(Arrays.asList(
                            new BasicNameValuePair(Constants.CLIENT_ID, clientId),
                            new BasicNameValuePair(Constants.CLIENT_SECRET, clientSecret),
                            new BasicNameValuePair(Constants.GRANT_TYPE, Constants.CLIENT_CREDENTIALS)
                    ))))
            		)
                    .andDo(print())
                    .andExpect(status().isOk());
        } catch (Exception e) {
            assertTrue(false);
        }

    }

    @Test
    public void badGrantType() throws Exception {
        try {
            ResultActions resultActions = mockMvc.perform(post("/api/v1/oauth/token")
            		.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            		.content(EntityUtils.toString(new UrlEncodedFormEntity(Arrays.asList(
                            new BasicNameValuePair(Constants.CLIENT_ID, clientId),
                            new BasicNameValuePair(Constants.CLIENT_SECRET, clientSecret),
                            new BasicNameValuePair(Constants.GRANT_TYPE, "bad_authorization_code")
                    )))))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        } catch (Exception e){
            assertTrue(false);
        }
    }

    @Test
    public void missingHeader() throws Exception {
        try {
            ResultActions resultActions = mockMvc.perform(post("/api/v1/oauth/token")
            		.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            		.content(EntityUtils.toString(new UrlEncodedFormEntity(Arrays.asList(
                            new BasicNameValuePair(Constants.CLIENT_ID, clientId),
                            new BasicNameValuePair(Constants.GRANT_TYPE, Constants.CLIENT_CREDENTIALS)
                    )))))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        } catch (Exception e){
            assertTrue(false);
        }
    }

    @Test
    public void badClientId() throws Exception {
        try {
            ResultActions resultActions = mockMvc.perform(post("/api/v1/oauth/token")
            		.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            		.content(EntityUtils.toString(new UrlEncodedFormEntity(Arrays.asList(
                            new BasicNameValuePair(Constants.CLIENT_ID, "invalid"),
                            new BasicNameValuePair(Constants.CLIENT_SECRET, clientSecret),
                            new BasicNameValuePair(Constants.GRANT_TYPE, Constants.CLIENT_CREDENTIALS)
                    )))))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        } catch (Exception e){
            assertTrue(false);
        }
    }

    @Test
    public void badClientSecret() throws Exception {
        try {
            ResultActions resultActions = mockMvc.perform(post("/api/v1/oauth/token")
            		.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            		.content(EntityUtils.toString(new UrlEncodedFormEntity(Arrays.asList(
                            new BasicNameValuePair(Constants.CLIENT_ID, clientId),
                            new BasicNameValuePair(Constants.CLIENT_SECRET, "invalid"),
                            new BasicNameValuePair(Constants.GRANT_TYPE, Constants.CLIENT_CREDENTIALS)
                    )))))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        } catch (Exception e){
            assertTrue(false);
        }
    }

    @Test
    public void getAccessTokenBadRefreshToken () {
        try {
            ResultActions resultActions = mockMvc.perform(post("/api/v1/oauth/token")
            		.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            		.content(EntityUtils.toString(new UrlEncodedFormEntity(Arrays.asList(
                            new BasicNameValuePair(Constants.CLIENT_ID, clientId),
                            new BasicNameValuePair(Constants.CLIENT_SECRET, clientSecret),
                            new BasicNameValuePair(Constants.GRANT_TYPE, Constants.REFRESH_TOKEN),
                            new BasicNameValuePair(Constants.REFRESH_TOKEN, "badRefreshToken")
                    )))))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        } catch (Exception e) {
            assertTrue(false);
        }

    }


}
