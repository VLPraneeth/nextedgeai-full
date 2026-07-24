package com.syncari.api.rest.controllers;

import java.util.List;

import com.syncari.core.model.misc.PhoneHome;
import com.syncari.core.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.extern.slf4j.Slf4j;

import static com.syncari.core.security.Permissions.WRITE_PROFILE;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/application")
public class ApplicationController {

    @Autowired
    ApplicationService applicationService;

    @Secured(WRITE_PROFILE)
    @RequestMapping(method = RequestMethod.POST, value = "/phoneHome")
    public PhoneHome sendPhoneHome(@RequestBody PhoneHome phoneHome) {
        return applicationService.sendPhoneHome(phoneHome);
    }
}
