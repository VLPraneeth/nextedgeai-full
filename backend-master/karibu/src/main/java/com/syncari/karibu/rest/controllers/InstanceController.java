package com.syncari.karibu.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.Event;
import com.syncari.core.model.JobQueue;
import com.syncari.core.model.misc.InstanceProfileResponse;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.util.JobQueueStatus;
import com.syncari.core.service.*;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.request.InstanceRequest;
import com.syncari.karibu.rest.response.InstanceResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.JobQueueUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.karibu.rest.util.SubscriptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/instances")
public class InstanceController {

    @Autowired
    JobQueueService jobQueueService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    JobQueueUtils jobQueueUtils;

    @Autowired
    SubscriptionUtils subscriptionUtils;

    @Autowired
    ResponseUtils responseUtils;

    @Autowired
    Publisher publisher;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ProvisioningService provisioningService;
    @Autowired
    InstanceService instanceService;

    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;

    @Autowired
    AppConfig appConfig;

    @Secured(ADD_INSTANCE)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<?> createInstance(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                            @Valid @RequestBody InstanceRequest request) {
        try {
            if(!SyncariContext.getOrganziation().getName().equals(request.getSubscriptionName())){
                ValidResponse response = responseUtils.populateErrorResponse(String.format("This user cannot create instances in org '%s'",request.getSubscriptionName()));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            // default to syncari_admin instance to store all job queue related documents
            SyncariContext.setInstance(subscriptionService.getInstance("syncari_admin"));
            subscriptionUtils.validateCreateInstanceRequest(request);
            String jobQueueId = ObjectId.get().toString();
            Map<String, Object> jobDetails = new HashMap<>();
            jobDetails.put("name", request.getName());
            jobDetails.put("displayName", request.getDisplayName());
            jobDetails.put("type", InstanceType.valueOf(request.getType()));
            // overriding planName to be trial if instance type is trial
            String planName = request.getPlanName();
            if (InstanceType.valueOf(request.getType()) == InstanceType.trial){
                planName = "trial";
            }
            jobDetails.put("planName", planName);
            jobDetails.put("subscriptionName", request.getSubscriptionName());
            JobQueue jobQueue = jobQueueService.createJobQueue(jobQueueId, EventTypes.CREATE_INSTANCE, JobQueueStatus.queued, jobDetails);
            try {
                //TODO: Send verifyable credentials in Message object to make sure the message sender has
                //appropriate permissions for the call.
                Event event = new Event().setType(EventTypes.CREATE_INSTANCE).setDetails(Map.of("name", request.getName(),
                        "displayName", request.getDisplayName(), "type", InstanceType.valueOf(request.getType()),
                        "planName", planName, "jobId", jobQueueId,
                        "subscriptionName",request.getSubscriptionName()));
                Message msg = new Message(SyncariContext.getSyncariId(), event);
                String eventString = mapper.writeValueAsString(msg);
                log.info(String.format("Sending Message: %s", eventString));
                publisher.publishToGenericQueue(eventString);
            }catch (com.syncari.core.exception.NotFoundException nfe) {
                jobQueueService.deleteJobQueue(jobQueueId);
                throw new com.syncari.core.exception.NotFoundException(nfe.getMessage());
            } catch (Exception e) {
                log.error("Could not push event of provisiong instance with instance display name {}", request.getDisplayName());
                emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(),
                        format("Could not push event of of provisiong instance with instance name %s for org %s",  request.getName(),
                                SyncariContext.getOrganziation().getName()),
                        ExceptionUtils.getStackTrace(e));
                jobQueueService.deleteJobQueue(jobQueueId);
            }

            ValidResponse response = responseUtils.convertDTOToResponse(jobQueueUtils.getJobQueueResponse(jobQueue));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @Secured(LIST_INSTANCE)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?>  listInstances() {

        try {
            //List<InstanceResponse> instanceResponseList =  transformer
            // prepare and return response
            List<InstanceResponse> instanceResponseList = provisioningService.listInstances(SyncariContext.getOrganziation().getId()).stream().map(i -> subscriptionUtils.getInstance(i,SyncariContext.getOrganziation().getName())).collect(Collectors.toList());

            ValidListResponse validListResponse = responseUtils.convertDTOToResponse(instanceResponseList, false);
            return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
        } catch(Exception e) {
            log.error("{}", ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(LIST_INSTANCE)
    @RequestMapping(method = RequestMethod.GET, value = "/profile/{instanceId}")
    public ResponseEntity<?>  getInstanceProfile(@PathVariable String instanceId) {

        try {
            InstanceProfileResponse instanceProfile = instanceService.getInstanceProfile(instanceId);
            return ResponseEntity.status(HttpStatus.OK).body(instanceProfile);
        } catch (Exception e) {
            log.error("{}", ExceptionUtils.getStackTrace(e));
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(DELETE_INSTANCE)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{syncariId}")
    public ResponseEntity<ValidResponse> deleteInstance(@PathVariable("syncariId") String syncariId) {
        if (StringUtils.isBlank(syncariId)) {
            String message = i18n("invalid_syncariid");
            log.error(message);
            throw new BadRequestException(message);
        }
        try {
            log.info("Starting deprovisioning for instance {} ", syncariId);
            provisioningService.deprovInstance(syncariId, true);
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
        ValidResponse response = responseUtils.convertDTOToResponse("Successfully deprovisioned instance " + syncariId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}