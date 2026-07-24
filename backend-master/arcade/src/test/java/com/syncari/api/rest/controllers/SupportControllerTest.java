package com.syncari.api.rest.controllers;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;

public class SupportControllerTest extends AbstractSyncariTest{

    @Autowired
    SupportController supportController;

    @Test
    public void testSupportController() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String validSlug = "functions.advancedAttachRecord";
        String invalidSlug = "functions.invalidslug";

        supportController.redirectSupport(response, validSlug);

        assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, response.getStatus());
        assertEquals("https://support.syncari.com/hc/en-us/articles/360052207232-Entity-Functions",
                response.getHeader("Location"));

        response = new MockHttpServletResponse();
        supportController.redirectSupport(response, invalidSlug);

        assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, response.getStatus());
        assertEquals("https://support.syncari.com",
                response.getHeader("Location"));
    }
}
