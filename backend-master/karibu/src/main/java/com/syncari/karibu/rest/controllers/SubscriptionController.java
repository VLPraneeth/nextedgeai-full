package com.syncari.karibu.rest.controllers;

import com.syncari.core.model.Organization;
import com.syncari.core.service.SubscriptionService;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.karibu.rest.util.SubscriptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import java.util.Optional;

import static com.syncari.core.security.Permissions.PROVISION_ORG;
import static com.syncari.core.security.Permissions.PROVISION_TRIAL_ORG;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/subscriptions")
public class SubscriptionController {

    @Autowired
    SubscriptionUtils subscriptionUtils;

    @Autowired
    ResponseUtils responseUtils;

    @Autowired
    SubscriptionService subscriptionService;

    @Secured({PROVISION_TRIAL_ORG, PROVISION_ORG})
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getOrgByName(@NotBlank @RequestParam(value = "subName") String subName){
        try{
            if (null == subName)
                throw new BadRequestException(i18n("subscription_name_notfound", subName));

            Optional<Organization> organization = subscriptionService.getOrgByName(subName);
            if(organization.isPresent()){
                ValidResponse response = responseUtils.convertDTOToResponse(subscriptionUtils.toOrgResponse(organization.get()));
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else{
                ValidResponse response = responseUtils.populateErrorResponse("Organization is not present");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        }catch (BadRequestException bre){
            ValidResponse response = responseUtils.populateErrorResponse(bre.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

    }
}
