package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_DATA_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_DATA_STUDIO;

import com.syncari.api.rest.controllers.data.DfiRuleAssignmentDTO;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.dashboard.DashboardSeed;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.model.Event;
import com.syncari.core.service.DfiRuleAssignmentService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/dfi/rules")
public class DfiRulesController {
    @Autowired
    UserService userService;
    @Autowired
    DfiRuleAssignmentService service;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    Publisher publisher;


    @Secured(READ_DATA_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entity/{entityId}")
    public DfiRuleAssignmentDTO getEntityRules(@PathVariable String entityId) {
        log.info("Fetching DFI rules assignment for entity with id: " + entityId);
        if (DashboardSeed.DQS_OVERVIEW.equalsIgnoreCase(entityId)) {
            // TODO: This should really be an empty response. Currently frontend shows a blank page of empty response is sent.
            throw new SyncariValidationException("DQS Dashboard does not have DFI Rule Assignment");
            //return new DfiRuleAssignmentDTO(service, schemaService);
        }
        DfiRuleAssignment dra = service.findOrCreateDraft(entityId);
        return new DfiRuleAssignmentDTO(service, schemaService).toDto(dra, DfiRuleAssignmentDTO.class);
    }

    @Secured(READ_DATA_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/isCustomRuleAssignmentExists")
    public boolean isCustomRuleAssignmentExists() {
        return service.isCustomRulesExists();
    }

    @Secured(WRITE_DATA_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entity/{entityId}")
    public DfiRuleAssignmentDTO saveDraft(@PathVariable String entityId, @RequestBody DfiRuleAssignmentDTO draft) {
        log.info("Saving DFI rules assignment for entity: " + draft.getEntityApiName());
        DfiRuleAssignment dra = service.saveDraft(draft.toDfiRuleAssignment());
        return new DfiRuleAssignmentDTO(service, schemaService).toDto(dra, DfiRuleAssignmentDTO.class);
    }

    @Secured(WRITE_DATA_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entity/{entityId}/publish")
    public DfiRuleAssignmentDTO publish(@PathVariable String entityId, @RequestBody DfiRuleAssignmentDTO draft) {
        log.info("Publishing DFI rules assignment for entity: " + draft.getEntityApiName());
        DfiRuleAssignment dra = service.publish(draft.toDfiRuleAssignment());
        Event event = new Event().setType(EventTypes.RECALCULATE_DFI_SCORES).setDetails(Map.of("entityId", entityId));
        Message msg = new Message(SyncariContext.getSyncariId(), event);
        try {
            String eventString = mapper.writeValueAsString(msg);
            log.info(String.format("Sending Message: %s", eventString));
            publisher.publishToViperQueue(eventString);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new SyncariValidationException("Error while recomputing data fitness index scores. Please contact NextEdge AI support.", e);
        }
        return new DfiRuleAssignmentDTO(service, schemaService).toDto(dra, DfiRuleAssignmentDTO.class);
    }

    @Secured(WRITE_DATA_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/entity/{entityId}")
    public DfiRuleAssignmentDTO deleteDraft(@PathVariable String entityId) {
        var draft = service.findDraft(entityId).orElseThrow(() -> new SyncariValidationException("No draft was found for entity."));
        log.info("Deleting draft DFI rules assignment for entity: " + draft.getEntityApiName());
        DfiRuleAssignment dra = service.deleteDraft(draft);
        return new DfiRuleAssignmentDTO(service, schemaService).toDto(dra, DfiRuleAssignmentDTO.class);
    }
}
