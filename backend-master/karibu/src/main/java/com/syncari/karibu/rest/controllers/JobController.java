package com.syncari.karibu.rest.controllers;

import com.syncari.core.SyncariContext;
import com.syncari.core.model.JobQueue;
import com.syncari.core.service.JobQueueService;
import com.syncari.core.service.SubscriptionService;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.JobQueueUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static com.syncari.core.security.Permissions.LIST_INSTANCE;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/jobs")
public class JobController {

    @Autowired
    JobQueueService jobQueueService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    JobQueueUtils jobQueueUtils;

    @Autowired
    ResponseUtils responseUtils;

    // create instance is the only api using the job queue. When we more apis use the job queue we will need a new permission
    @Secured(LIST_INSTANCE)
    @RequestMapping(method = RequestMethod.GET, value = "/{jobId}")
    public ResponseEntity<ValidResponse> getJobQueueById(@PathVariable String jobId) {
        try {
            JobQueue jobQueue = new JobQueue();
            try {
                // get the job queue by id
                jobQueue = jobQueueService.getJobQueue(jobId);
            } catch (Exception e) {
                // default to syncari_admin instance to store all job queue related documents
                SyncariContext.setInstance(subscriptionService.getInstance("syncari_admin"));
                jobQueue = jobQueueService.getJobQueue(jobId);
            }
            // transform and return the job queue
            ValidResponse response = responseUtils.convertDTOToResponse(jobQueueUtils.getJobQueueResponse(jobQueue));
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

    }

}