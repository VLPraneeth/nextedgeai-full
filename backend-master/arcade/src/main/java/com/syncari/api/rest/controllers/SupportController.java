package com.syncari.api.rest.controllers;

import com.syncari.core.SupportConfig;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/support")
@Setter
public class SupportController {

    private static final String DEFAULT_SUPPORT_URL = "https://support.syncari.com";

    @Autowired
    SupportConfig supportConfig;

    @RequestMapping(method = RequestMethod.GET, value = "/{placeholder}")
    public void redirectSupport(HttpServletResponse response, @PathVariable("placeholder") String placeholder) {
        String redirectUrl = supportConfig.getUrl(placeholder);
        if (StringUtils.isBlank(redirectUrl)) {
            redirectUrl = DEFAULT_SUPPORT_URL;
        }
        response.setHeader("Location", redirectUrl);
        response.setStatus(302);
    }

}