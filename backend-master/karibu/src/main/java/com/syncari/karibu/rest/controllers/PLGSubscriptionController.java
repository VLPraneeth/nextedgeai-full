package com.syncari.karibu.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Event;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.ProvisioningService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.response.OrgResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.SubscriptionUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.data.ProvisionRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/plg")
public class PLGSubscriptionController {

    @Autowired
    UserService userService;

    @Autowired
    ProvisioningService provisioningService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    SubscriptionUtils orgUtil;

    @Autowired
    ResponseUtils responseUtils;

    @Autowired
    Publisher publisher;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    AppConfig appConfig;

    private static final int USERNAME_PREFIX_LENGTH = 15;
    private static final int ORGNAME_LENGTH = 15;

    @Secured(PROVISION_TRIAL_ORG)
    @RequestMapping(method = RequestMethod.POST, value = "/organizations")
    public ResponseEntity<ValidResponse> addOrganization(@RequestBody ProvisionRequest request) {
        InstanceType type = InstanceType.trial;
        try {
            type = InstanceType.valueOf(request.getInstanceType());
        } catch (Exception e) {
        }
        try{
            validateRequest(request);
            validateUserName(request.getAdminUserName());
            log.info("Input organization name is {} and username is {}", request.getOrganizationName(), request.getAdminUserName());

            // Assume username is a valid email
            String prefix = request.getAdminUserName().substring(0,request.getAdminUserName().indexOf("@"));
            //trim username prefix
            prefix = StringUtils.substring(prefix, 0, Math.min(prefix.length(), USERNAME_PREFIX_LENGTH));
            //trim orgname
            String organizationName = StringUtils.substring(request.getOrganizationName(), 0, Math.min(request.getOrganizationName().length(), ORGNAME_LENGTH));
            organizationName = populateOrgName(organizationName,prefix);

            String instanceName = type == InstanceType.trial ? organizationName : request.getInstanceName();
            String instanceDisplayName = type == InstanceType.trial ? organizationName : request.getInstanceDisplayName();
            String planName = (StringUtils.isBlank(request.getPlanName())) ? ((type == InstanceType.trial ) ? "trial" : "default") : request.getPlanName();

            log.info(format("Pushing event to generic queue to process provisioning %s", instanceName, organizationName));
            try{
                Event event = new Event().setType(EventTypes.TRIAL_PROVISION).setDetails(Map.of("instanceName", instanceName, "instanceDisplayName", instanceDisplayName,
                        "planName", planName,"organizationName", organizationName, "adminUserName", request.getAdminUserName(), "adminFirstName", request.getAdminFirstName(),
                        "adminLastName",request.getAdminLastName(), "type",type));
                Message msg = new Message(SyncariContext.getSyncariId(), event);
                String eventString = mapper.writeValueAsString(msg);
                log.info(String.format("Sending Message: %s", eventString));
                publisher.publishToGenericQueue(eventString);
            }catch (com.syncari.core.exception.NotFoundException nfe) {
                throw new com.syncari.core.exception.NotFoundException(nfe.getMessage());
            } catch (Exception e) {
                log.error("Could not push event of provisiong trial subscription with instance display name {}", instanceDisplayName);
                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
                        format("Could not push event of of provisiong trial subscriptio with instance name %s for org %s", instanceName, organizationName),
                        ExceptionUtils.getStackTrace(e));
            }
            OrgResponse orgResponse = new OrgResponse();
            orgResponse.setName(organizationName);
            ValidResponse response = responseUtils.convertDTOToResponse(orgResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (BadRequestException bre) {
            log.error("BadRequestException occurred with message {}", bre.getMessage());
            throw new BadRequestException(bre.getMessage());
        } catch (NotFoundException nfe) {
            log.error("NotFoundException occurred with message {}", nfe.getMessage());
            throw new NotFoundException(nfe.getMessage());
        }catch (SyncariValidationException e) {
            log.error("Exception occurred with message {}", e.getMessage());
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (Exception e) {
            log.error("Exception occurred with message {}", e.getMessage());
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(DELETE_INSTANCE)
    @RequestMapping(method = RequestMethod.DELETE, value = "/instance/trial/{syncariId}")
    public ResponseEntity<ValidResponse> deleteTrialInstance(@PathVariable("syncariId") String syncariId) {
        if (StringUtils.isBlank(syncariId)){
            String message = i18n("invalid_syncariid");
            log.error(message);
            throw new BadRequestException(message);
        }
        try{
            Instance instance = subscriptionService.getInstance(syncariId);
            if (!instance.isTrial()){
                String message = i18n("invalid_instance_type", instance.getType());
                log.error(message);
                throw new BadRequestException(message);
            }
            log.info("Starting deprovision for instance {} ", syncariId);
            provisioningService.deprovisionInstance(syncariId, true);
            log.info("Deprovisioned instance {} ", syncariId);
        } catch (BadRequestException e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("Error deprovisioning instance {} ", syncariId);
            log.error(ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        ValidResponse response = responseUtils.convertDTOToResponse("Successfully deprovisioned instance "+syncariId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    private void validateRequest(ProvisionRequest request){
        String firstName = request.getAdminFirstName();
        String lastName = request.getAdminLastName();
        String userName = request.getAdminUserName();
        String organizationName = request.getOrganizationName();
        if (StringUtils.isBlank(firstName) || StringUtils.isBlank(lastName) || StringUtils.isBlank(userName) || StringUtils.isBlank(organizationName)){
            String message = i18n("blank_provision_request_attribute");
            log.error(message);
            throw new BadRequestException(message);
        }
        if ((request.getInstanceType() != null) && (!request.getInstanceType().equalsIgnoreCase(InstanceType.trial.toString()))){
            String message = i18n("invalid_instance_type", request.getInstanceType());
            log.error(message);
            throw new BadRequestException(message);
        }
    }

    private void validateUserName(String username) throws SyncariValidationException {
        Optional<User> existingUser = userService.getUserByEmail(username);
        if (existingUser.isPresent()){
            String message = String.format(i18n("already_exist_user"), existingUser.get().getEmail());
            log.error(message);
            throw new SyncariValidationException(message);
        }
    }

    private String populateOrgName(String organizationName, String prefix){
        String orgName = organizationName + "(" + prefix +")";
        String tempOrg = orgName;
        Optional<Organization> existingOrganization = subscriptionService.getOrgByName(organizationName);
        int counter = 0;
        while (existingOrganization.isPresent() && counter < 5) {
            orgName = tempOrg + counter;
            existingOrganization = subscriptionService.getOrgByName(orgName);
            counter++;
        }
        if (existingOrganization.isPresent() && (counter == 5)){
            String message = String.format(i18n("already_exist_organization"), organizationName);
            log.error(message);
            throw new SyncariValidationException(message);
        }
        return orgName;
    }



}
