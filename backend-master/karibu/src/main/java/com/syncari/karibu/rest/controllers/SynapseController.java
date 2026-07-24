package com.syncari.karibu.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.Event;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.service.ConnectorService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.exceptions.TestConnectionError;
import com.syncari.karibu.rest.request.SynapseRequest;
import com.syncari.karibu.rest.response.*;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.karibu.rest.util.SynapseUtils;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.*;

import static com.syncari.core.security.Permissions.READ_CONNECTOR;
import static com.syncari.core.security.Permissions.WRITE_CONNECTOR;
import static com.syncari.utils.I18n.i18n;


@Slf4j
@RestController
@RequestMapping(value = "/api/v1/synapses")

public class SynapseController {
    @Autowired
    SynapseUtils synapseUtil;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    Publisher publisher;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ResponseUtils<SynapseResponse, Connector> responseUtils;

    @Autowired
    ResponseUtils<ConnectorMetadataResponse, ConnectorMetadata> connectorResponseUtils;

    SynapseResponse synapseResponse = new SynapseResponse();
    SynapseTestConnectionResponse synapseTestConnectionResponse = new SynapseTestConnectionResponse();
    ConnectorMetadataResponse connectorMetadataResponse = new ConnectorMetadataResponse();

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getSynapses() {
        try {
            // get list of connectors
            List<Connector> response = new ArrayList<>();

            List<Connector> persisted = connectorService.listPublished();
            List<Connector> connectors = new ArrayList<>();
            for (Connector connector : persisted) {
                apiUtils.maskSensitiveData(connector);
                connectors.add(connector);
            }
            // add syncari connector to list
            connectors.add(connectorService.getSyncariConnector());
            for (Connector connector : connectors) {
                //connector.setMetaConfig(synapseUtil.getConfiguration(connector));
                response.add(connector);
            }

            List<SynapseResponse> responseList = synapseUtil.getSynapseListResponse(connectors);

            // prepare and return response
            ValidListResponse validListResponse = responseUtils.convertDTOToResponse(responseList);
            return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/describe")
    public ResponseEntity<?> describe(@RequestParam(required = false) String cursorToken,
                                      @RequestParam(required = false, defaultValue = KaribuConstants.MAX_LIMIT_STRING) Integer limit,
                                      @RequestParam(required = false) String synapseTypeId) {
        try {
            String connectorId = null;
            if (cursorToken != null)
                connectorId = apiUtils.decodeCursor(cursorToken);

            ValidListResponse validListResponse = null;
            if (StringUtils.isEmpty(synapseTypeId)){
                Pair<List<ConnectorMetadata>, Boolean> connectorMetadataWithHasMore = connectorService.retrieveConnectorsPaginated(connectorId, limit);
                validListResponse = connectorResponseUtils.convertDTOToResponse(connectorMetadataResponse, connectorMetadataWithHasMore.x, connectorMetadataWithHasMore.y);
            }else{
                 validListResponse = connectorResponseUtils.convertDTOToResponse(connectorMetadataResponse,List.of(connectorService.describeById(synapseTypeId)),false);
            }
            return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<ValidResponse> createSynapses(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                        @RequestBody SynapseRequest synapseRequest) {

        try {
            // validate inputs and prepare connector to create
            Connector newConnector = synapseUtil.validateCreateSynapseRequest(synapseRequest);
            // create synapse
            Connector savedConnector = connectorService.save(newConnector, false);
            connectorService.createWebhookConfig(savedConnector);

            // prepare and return response
            apiUtils.maskSensitiveData(savedConnector);
            SynapseResponse synapseResponse = synapseUtil.getSynapseResponse(savedConnector);
            ValidResponse response = responseUtils.convertDTOToResponse(synapseResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

    }


    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{synapseId}")
    public ResponseEntity<ValidResponse> getSynapseById(@PathVariable String synapseId) {

        try {
            // get synpase by passed in id
            Connector connector = synapseUtil.validateSynapseNotDeleted(synapseId, true);

            // prepare and return response
            apiUtils.maskSensitiveData(connector);
            SynapseResponse synapseResponse = synapseUtil.getSynapseResponse(connector);
            ValidResponse response = responseUtils.convertDTOToResponse(synapseResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }


    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.PATCH, value = "/{synapseId}")
    public ResponseEntity<ValidResponse> updateSynapses(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                        @RequestBody SynapseRequest synapseRequest,
                                                        @PathVariable String synapseId) {
        try {
            // get connector
            Connector connector = synapseUtil.validateSynapseNotDeleted(synapseId, false);

            // prepare connector for updating
            Connector updateConnector = synapseUtil.validateUpdateSynapseRequest(synapseRequest, connector);
            updateConnector.setId(synapseId);

            // update synapse
            Connector savedConnector = connectorService.save(updateConnector);
            connectorService.createWebhookConfig(savedConnector);

            // prepare and return response
            apiUtils.maskSensitiveData(savedConnector);
            SynapseResponse synapseResponse = synapseUtil.getSynapseResponse(savedConnector);
            ValidResponse response = responseUtils.convertDTOToResponse(synapseResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }


    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{synapseId}")
    public ResponseEntity<ValidResponse> deleteSynapse(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                       @PathVariable String synapseId) {

        try {
            // validate connector status
            synapseUtil.validateSynapseNotDeleted(synapseId, false);

            // delete the synapse for the given synapse id
            connectorService.deleteWebhookConfig(synapseId);
            connectorService.delete(synapseId, false);

            ValidResponse response = responseUtils.convertDTOToResponse(synapseId);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{synapseId}/activate")
    public ResponseEntity<ValidResponse> activateSynapse(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                         @PathVariable String synapseId) {

        try {
            // validate synapse is not deleted
            synapseUtil.validateSynapseNotDeleted(synapseId, true);
            // test and parse connection
            TestConnectionResponse testConnectionResponse = connectorService.testConnection(synapseId);
            if (testConnectionResponse.getCode() != null)
                throw new TestConnectionError(Connector.class, "synapseId", synapseId);

            // activate synapse
            Event event = new Event().setType(EventTypes.ACTIVATE_CONNECTOR)
                    .setLoggedTime(new Date())
                    .setDetails(Map.of("connectorId", synapseId, "createMappings", String.valueOf(false)));
            Message message = new Message(SyncariContext.getInstance().getSyncariId(), event);
            String eventString = mapper.writeValueAsString(message);
            log.info(String.format("Sending Activate Message: %s", eventString));
            publisher.publishToGenericQueue(eventString);
            Connector connector = connectorService.setStatus(synapseId, ConnectorStatus.ACTIVATING, null, null);
            apiUtils.maskSensitiveData(connector);
            SynapseResponse synapseResponse = synapseUtil.getSynapseResponse(connector);
            ValidResponse response = responseUtils.convertDTOToResponse(synapseResponse);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{synapseId}/deactivate")
    public ResponseEntity<ValidResponse> deactivateSynapse(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                           @PathVariable String synapseId) {

        try {
            // validate synapse is not deleted
            synapseUtil.validateSynapseNotDeleted(synapseId, false);
            // deactivate the synapse for the given synapse id
            connectorService.deactivate(synapseId);
            Optional<Connector> connector = connectorService.find(synapseId);
            // prepare and return response
            apiUtils.maskSensitiveData(connector.get());
            SynapseResponse synapseResponse = synapseUtil.getSynapseResponse(connector.get());
            ValidResponse response = responseUtils.convertDTOToResponse(synapseResponse);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{synapseId}/refreshSchema")
    public ResponseEntity<ValidResponse> refreshSchema(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                       @PathVariable String synapseId) {
        try {
            // validate synapse is not deleted
            Connector connector = synapseUtil.validateSynapseNotDeleted(synapseId, true);

            if(connector.getStatus().equals(ConnectorStatus.NEW) || connector.getStatus().equals(ConnectorStatus.AUTHENTICATED))
                throw new RuntimeException(i18n("synapse_not_active", synapseId));

            if(connector.getStatus().equals(ConnectorStatus.ACTIVATING))
                throw new RuntimeException(i18n("synapse_activating", synapseId));

            Event event = new Event().setType(EventTypes.REFRESH_SCHEMA).setDetails(Map.of("connectorId", synapseId));
            Message msg = new Message(SyncariContext.getSyncariId(), event);

            String eventString = mapper.writeValueAsString(msg);
            log.info(String.format("Sending Message: %s", eventString));
            publisher.publishToGenericQueue(eventString);

            // prepare and return response
            Optional<Connector> queuedConnector = connectorService.find(synapseId);
            apiUtils.maskSensitiveData(queuedConnector.get());

            SynapseResponse synapseResponse = synapseUtil.getSynapseResponse(queuedConnector.get());
            synapseResponse.setRefreshSchemaStatus("QUEUED");
            ValidResponse response = responseUtils.convertDTOToResponse(synapseResponse);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_CONNECTOR)
    @RequestMapping(method = RequestMethod.POST, value = "/{synapseId}/connection")
    public ResponseEntity<?> testSynapseConnection(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                   @PathVariable String synapseId) {

        try {
            // validate synapse is not deleted
            synapseUtil.validateSynapseNotDeleted(synapseId, true);
            // test and parse connection
            TestConnectionResponse resp = connectorService.testConnection(synapseId);

            // if it comes back as unsuccessful get the synapse and return the error
            if (!resp.isSuccess()) {
                Connector connector = synapseUtil.validateSynapseNotDeleted(synapseId, true);
                String errorMessage = (connector.getErrorMessage() != null) ?connector.getErrorMessage() : resp.getMessage();
                ValidResponse response = responseUtils.populateErrorDetailResponse(
                        StringUtils.replace(errorMessage, "Connector", "Synapse"),
                        StringUtils.replace(connector.getErrorDetail(), "Connector", "Synapse"));
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            // prepare and return response
            ValidResponse response = responseUtils.convertDTOToResponse(synapseId);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (NotFoundException nfe) {
            throw new NotFoundException(nfe.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ValidResponse response = responseUtils.populateErrorResponse(
                    StringUtils.replace(e.getMessage(), "Connector", "Synapse"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }
}
