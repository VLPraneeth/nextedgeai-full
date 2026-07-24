package com.syncari.karibu.rest.controllers;

import com.syncari.core.model.User;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.UserService;
import com.syncari.karibu.rest.request.UserReqest;
import com.syncari.karibu.rest.response.UserResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.karibu.rest.util.UserUtils;
import com.syncari.restutils.validations.OrganisationValidations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import java.util.Optional;

import static com.syncari.core.security.Permissions.INVITE_USER;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/users")
public class UserController {

    @Autowired
    ProvisioningService provisioningService;

    @Autowired
    OrganisationValidations organisationValidations;

    @Autowired
    UserUtils userUtils;

    @Autowired
    ResponseUtils responseUtils;

    @Autowired
    UserService userService;

    UserResponse userResponse = new UserResponse();

    @Secured(INVITE_USER)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<?> createUser(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                            @Valid @RequestBody UserReqest userReqest) {

        try {
            organisationValidations.validateUserRoles(userReqest.getUserRoles());
            User user = userUtils.convertUserCreateRequest(userReqest);
            user = provisioningService.inviteUser(user, userReqest.getUserRoles(), false, Optional.empty());
            ValidResponse validResponse = responseUtils.convertDTOToResponse(userUtils.convertToUserResponseWithRoles(user));
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);
        } catch (Exception e) {
            e.printStackTrace();
            ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(e.getMessage(), "Org ", "Subscription "));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @Secured(INVITE_USER)
    @RequestMapping(method = RequestMethod.POST, value = "/reinvite")
    public ResponseEntity<?> resetToken(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                        @Valid @RequestBody UserReqest userReqest) {

        try {
            organisationValidations.validateUserRoles(userReqest.getUserRoles());
            User user = userService.resetToken(userUtils.convertUserCreateRequest(userReqest));
            ValidResponse validResponse = responseUtils.convertDTOToResponse(userUtils.convertToUserResponseWithRoles(user));
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);
        } catch (Exception e) {
            e.printStackTrace();
            ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(e.getMessage(), "Org ", "Subscription "));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
