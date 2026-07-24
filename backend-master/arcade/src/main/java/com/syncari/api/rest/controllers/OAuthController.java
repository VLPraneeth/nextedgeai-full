package com.syncari.api.rest.controllers;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.controllers.data.RedirectResponse;
import com.syncari.api.rest.controllers.exceptions.UnauthorizedException;
import com.syncari.core.service.EventService;
import com.syncari.core.service.OAuthService;
import com.syncari.utils.I18n;

@RestController
@RequestMapping("/api/v1/oauth")
public class OAuthController {
	@Autowired
	OAuthService oAuthService;
	@Autowired
	EventService eventService;

	@RequestMapping(method = RequestMethod.GET, value = "/initiate/{connectorId}")
	public RedirectResponse initiate(@PathVariable String connectorId) {
		return new RedirectResponse(oAuthService.initiate(connectorId));
	}

    @RequestMapping(method = RequestMethod.GET, value = "/authorize")
	public String authorize(@RequestParam String code, @RequestParam(required = false) String guid, @RequestParam(required = false) String state) {
        if (StringUtils.isNotEmpty(guid)) {
            oAuthService.authorize(guid, code);
        } else if (StringUtils.isNotEmpty(state)) {
            oAuthService.authorizeWithConnectorId(state, code);
        } else {
            throw new UnauthorizedException(I18n.i18n("connector_authentication_failed"));
        }
		return "OAuth process successfully completed!";
	}
	
}
