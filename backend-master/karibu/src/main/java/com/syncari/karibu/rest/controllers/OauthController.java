package com.syncari.karibu.rest.controllers;

import static com.syncari.utils.I18n.i18n;

import java.util.Arrays;
import java.util.List;

import com.syncari.karibu.rest.config.security.SecurityConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

import com.syncari.core.model.User;
import com.syncari.core.service.EncryptionService;
import com.syncari.core.service.UserService;
import com.syncari.karibu.Constants;
import com.syncari.karibu.rest.config.security.JwtUtil;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import com.syncari.karibu.rest.response.OauthTokenRequest;
import com.syncari.karibu.rest.response.OauthTokenResponse;
import com.syncari.karibu.rest.util.ResponseUtils;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/oauth")
public class OauthController {

	@Autowired
    UserService userService;

    @Autowired
    EncryptionService encryptionService;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    ResponseUtils responseUtils;

    List grantTypes = Arrays.asList(Constants.CLIENT_CREDENTIALS, Constants.REFRESH_TOKEN);

    @RequestMapping(method = RequestMethod.POST, value = "/token", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE}, produces = MediaType.APPLICATION_JSON_VALUE)
    public OauthTokenResponse getAccessToken(@ModelAttribute @RequestBody OauthTokenRequest request)
            throws UnauthorizedException, BadRequestException, MissingRequestHeaderException {

        try {
            // validate grantTypes
            if (!grantTypes.contains(request.getGrant_type()))
                throw new BadRequestException(i18n("invalid_grant_type"));

            // get user for passed in client id
            User apiUser = userService.getUserByClientId(request.getClient_id());

            // validate grantType
            if (request.getGrant_type().equals(Constants.REFRESH_TOKEN) && apiUser.getRefreshToken() == null)
                throw new BadRequestException(i18n("invalid_grant_type"));
            
            // validate client secret/id
            if (request.getClient_secret() == null || request.getClient_id() == null)
            	throw new BadRequestException(i18n("invalid_creds"));

            // validate refresh token
            if (request.getGrant_type().equals(Constants.REFRESH_TOKEN)  && !request.getRefresh_token().equals(encryptionService.decryptIfPossible(apiUser.getRefreshToken()).get()))
                throw new UnauthorizedException(i18n("invalid_refresh_token"));

            // validate user is an api user
            if (!apiUser.isApiUser())
                throw new UnauthorizedException(i18n("unauthorized_error"));
            
            // get the access token
            return jwtUtil.getAccessTokenResponse(request.getGrant_type(), request.getClient_id(), request.getClient_secret(), apiUser);

        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        } catch (UnauthorizedException ue) {
            throw new UnauthorizedException(ue.getMessage());
        } catch (Exception e) {
            throw new UnauthorizedException(i18n("unauthorized_error"));
        }

    }


    @RequestMapping(method = RequestMethod.GET, value = "/validate")
    public void validateToken(HttpServletResponse request) {
        return;
    }
}
