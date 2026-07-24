package com.syncari.karibu.rest.util;

import com.jayway.jsonpath.JsonPath;
import com.syncari.core.service.UserService;
import com.syncari.karibu.Constants;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Component
public class OauthUtil {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    PasswordEncoder passwordEncoder;

    String clientId = "Fu3dgclRwNBKgod_KhnS_xYrWiY";
    String clientSecret = System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME");

    public String getTestAccessToken () throws UnauthorizedException {
        try {
            ResultActions resultActions = mockMvc.perform(post("/api/v1/oauth/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(EntityUtils.toString(new UrlEncodedFormEntity(Arrays.asList(
                                    new BasicNameValuePair(Constants.CLIENT_ID, clientId),
                                    new BasicNameValuePair(Constants.CLIENT_SECRET, clientSecret),
                                    new BasicNameValuePair(Constants.GRANT_TYPE, Constants.CLIENT_CREDENTIALS)
                            ))))
                    )
                    .andDo(print())
                    .andExpect(status().isOk());

            MvcResult result = resultActions.andReturn();
            String contentAsString = result.getResponse().getContentAsString();

            return StringUtils.join("Bearer ",
                    JsonPath.read(result.getResponse().getContentAsString(), "$.access_token"));

            //return JsonPath.read(result.getResponse().getContentAsString(), "$.result.access_token");
        } catch (Exception e) {
            throw new UnauthorizedException(e);

        }

    }

}

