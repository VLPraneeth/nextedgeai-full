package com.syncari.karibu.rest.controllers;

import com.syncari.connector.EntityData;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.AbacException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TagService;
import com.syncari.karibu.rest.request.CreateSyncariEntityRequest;
import com.syncari.karibu.rest.request.FieldRequest;
import com.syncari.karibu.rest.response.*;
import com.syncari.karibu.rest.util.DataUtils;
import com.syncari.karibu.rest.util.SchemaUtils;
import com.syncari.restutils.data.DataQueryMetadata;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.utils.KeyValue;
import com.syncari.utils.Pair;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.*;

import java.util.*;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/entities")
public class EntityController {

    @Autowired
    SchemaService schemaService;

    @Autowired
    TagService tagService;

    @Autowired
    SchemaUtils schemaUtils;

    @Autowired
    ApiUtils apiUtils;

    @Autowired
    TextUtil textUtil;

    @Autowired
    DataUtils dataUtils;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DataTransformer dataTransformer;

    @Autowired
    ResponseUtils<EntityResponse, EntityDefinition> responseEntityUtils;

    @Autowired
    ResponseUtils<FieldResponse, AttributeDefinition> responseFieldUtils;

    @Autowired
    ResponseUtils responseUtils;

    EntityResponse entityResponse = new EntityResponse();
    FieldResponse fieldResponse = new FieldResponse();

    List statuses = Arrays.asList("approved", "draft");

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<?> getEntities(@NotBlank @RequestParam(value = "synapseId") String synapseId,
                                         @RequestParam(value = "status", defaultValue = "approved") String requestStatus,
                                         @RequestParam(value = "includeFields", defaultValue = "false") boolean includeFields,
                                         @RequestParam(value = "cursorToken", required = false) String cursorToken,
                                         @RequestParam(value = "limit", required = false, defaultValue=KaribuConstants.MAX_LIMIT_STRING) Integer limit) {
        try {
            // validate limit mx value
            if (limit > KaribuConstants.MAX_LIMIT)
                throw new BadRequestException(i18n("limit_max_value_error", limit, KaribuConstants.MAX_LIMIT));

            // validate status
            if (!statuses.contains(requestStatus))
                throw new BadRequestException(i18n("invalid_entity_status", statuses.toString()));

            // convert draft status to new
            String status = requestStatus;
            if (requestStatus.equalsIgnoreCase("draft"))
                status = "NEW";

            // get entity id
            String entityId = null;
            if (cursorToken != null)
                entityId = apiUtils.decodeCursor(cursorToken);

            // get entities
            List<EntityDefinition> entities = schemaService.getEntitiesByDraftStatus(synapseId,
                    DraftStatus.valueOf(status.toUpperCase()), includeFields, entityId, limit);

            // check if empty
            if (entities.isEmpty())
                throw new NotFoundException(i18n("synapse_entities_not_found", requestStatus, synapseId));

            // prepare and return response
            ValidListResponse validListResponse = responseEntityUtils.convertDTOToResponse(entityResponse, entities, true);
            return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
        } catch (IllegalArgumentException iae){
            throw new BadRequestException(i18n("invalid_cursor_token", cursorToken));
        }
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{entityId}")
    public ResponseEntity<ValidResponse> getEntity(@PathVariable String entityId,
                                                   @RequestParam(value = "includeFields", defaultValue = "false") boolean includeFields) {

        try {
            // get entity
            EntityDefinition entity = schemaService.getEntity(entityId, includeFields);

            // get tags
            entity.setTags(tagService.findTagsFor(Taggable.entity, entity.getId()));

            // prepare and return response
            ValidResponse validResponse = responseEntityUtils.convertDTOToResponse(entityResponse, entity);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);

        } catch (AbacException e) {
          throw e;
        } catch (Exception e) {
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(e.getMessage());
            }

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{entityId}/fields")
    public ResponseEntity<?> getFields(@PathVariable String entityId) {
      
      try {
          // get entity
          schemaService.getEntity(entityId, false); //To trigger abac check
  
          // get fields
          List<AttributeDefinition> attributes = schemaService.getAttributesByEntityId(entityId);
  
          // check if empty
          if (attributes.isEmpty())
              throw new NotFoundException(i18n("entity_fields_not_found", entityId));
  
          // prepare and return response
          ValidListResponse validListResponse = responseFieldUtils.convertDTOToResponse(fieldResponse, attributes, false);
          return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
      } catch (AbacException e) {
        throw e;
      } catch (Exception e) {
        if (StringUtils.contains(e.getMessage(), "not found")) {
            throw new NotFoundException(e.getMessage());
        }

        ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
      }   
    }

    @Secured(READ_CONNECTOR)
    @RequestMapping(method = RequestMethod.GET, value = "/{entityId}/fields/{fieldId}")
    public ResponseEntity<ValidResponse> getField(@PathVariable String entityId, @PathVariable String fieldId) {

        try {
            // get entity
            schemaService.getEntity(entityId, false); //To trigger abac check
            
            // get entity
            AttributeDefinition attribute = schemaService.getAttribute(fieldId);

            // verify entity ids match
            if(!entityId.equals(attribute.getEntityId()))
                throw new BadRequestException(i18n("entity_field_mismatch", fieldId, entityId));

            // get tags
            attribute.setTags(tagService.findTagsFor(Taggable.attribute, attribute.getId()));

            // prepare and return response
            ValidResponse validResponse = responseFieldUtils.convertDTOToResponse(fieldResponse, attribute);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);
        } catch (BadRequestException bre) {
            throw new BadRequestException(bre.getMessage());
        } catch (AbacException e) {
          throw e;
        } catch (Exception e) {
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(StringUtils.replace(e.getMessage(), "Attribute", "Field"));
            }

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/createDraft")
    public ResponseEntity<ValidResponse> createSyncariEntityDraft(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                           @RequestBody CreateSyncariEntityRequest createSyncariEntityRequest) {
        try{
            Connector syncariConnector = connectorService.getSyncariConnector();
            EntityDefinition entity = schemaUtils.convertSyncariCreateEntityRequest(createSyncariEntityRequest);
            entity.setConnectorId(syncariConnector.getId());
            entity.setConnectorTypeId(syncariConnector.getMetadataId());
            // create syncari entity draft
            List<AttributeDefinition> attributeDefinitionList = new ArrayList<>();
            createSyncariEntityRequest.getFields().forEach(fieldRequest -> {
                // passing null entity id as entity id does not exists yet
                attributeDefinitionList.add(schemaUtils.convertFieldCreateRequest(null,fieldRequest));
            });
            entity.setAttributes(attributeDefinitionList);
            EntityDefinition draft = schemaService.createDraftEntity(entity, false);

            // prepare and return response
            ValidResponse validResponse = responseEntityUtils.convertDTOToResponse(entityResponse, draft);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);
        }catch (Exception e) {
            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }


    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{entityId}/createDraft")
    public ResponseEntity<ValidResponse> createEntityDraft(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                           @PathVariable String entityId) {

        try {
            // get entity
            EntityDefinition entity = schemaService.getEntity(entityId);

            // check if entity is already a draft
            if(entity.isDraft())
                throw new RuntimeException(i18n("entity_not_approved", entityId));

            // validate synapase type
            if(!schemaUtils.validateSynapseType(entity.getConnectorTypeId()))
                throw new RuntimeException("unsupported_synapse");

            // check if entity already has a draft
            Optional<EntityDefinition> draftEntity = schemaService.getDraft(entity.getConnectorId(), entity.getApiName());
            if(draftEntity.isPresent())
                throw new RuntimeException(i18n("entity_has_draft", entityId, draftEntity.get().getId()));

            // create draft
            EntityDefinition draft = schemaService.createEntityDraftFor(entityId);

            // prepare and return response
            ValidResponse validResponse = responseEntityUtils.convertDTOToResponse(entityResponse, draft);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);

        } catch (AbacException e) {
          throw e;
        } catch (Exception e) {
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(i18n("entity_not_found", entityId));
            }

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{entityId}/publish")
    public ResponseEntity<ValidResponse> publishEntityDraft(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                            @PathVariable String entityId) {

        try {
            // get entity
            EntityDefinition entity = schemaService.getEntity(entityId);

            // check if entity is already published
            if(entity.isApproved())
                throw new RuntimeException(i18n("entity_not_draft", entityId));

            // validate synapase type
            if(!schemaUtils.validateSynapseType(entity.getConnectorTypeId()))
                throw new RuntimeException("unsupported_synapse");

            // publish entity
            schemaService.approveDraftEntity(entity);

            // get published entity
            EntityDefinition publishedEntity = schemaService.getEntity(entity.getConnectorId(), entity.getApiName());

            // prepare and return response
            ValidResponse validResponse = responseEntityUtils.convertDTOToResponse(entityResponse, publishedEntity);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);

        } catch (AbacException e) {
          throw e;
        } catch (Exception e) {
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(i18n("entity_not_found", entityId));
            }

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{entityId}")
    public ResponseEntity<ValidResponse> discardEntityDraft(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                            @PathVariable String entityId) {

        try {
            // get entity
            EntityDefinition entity = schemaService.getEntity(entityId, false);

            // check if entity is already published
            if(entity.isApproved())
                throw new RuntimeException(i18n("cannot_delete_approved_entity", entityId));

            // validate synapase type
            if(!schemaUtils.validateSynapseType(entity.getConnectorTypeId()))
                throw new RuntimeException("unsupported_synapse");

            // discard entity draft
            schemaService.discardDraftEntity(entity);
            entity.setStatus(Status.DELETED);

            // prepare and return response
            ValidResponse validResponse = responseEntityUtils.convertDTOToResponse(entityResponse, entity);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);

        } catch (AbacException e) {
          throw e;
        } catch (Exception e) {
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(i18n("entity_not_found", entityId));
            }

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{entityId}/fields/{fieldId}")
    public ResponseEntity<ValidResponse> deleteField(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                     @PathVariable String entityId, @PathVariable String fieldId) {

        try {
            // get attribute/entity
            AttributeDefinition attribute = schemaService.getAttribute(fieldId);
            EntityDefinition entity = schemaService.getEntity(attribute.getEntityId());

            // verify entity ids match
            if(!entityId.equals(attribute.getEntityId()))
                throw new BadRequestException(i18n("entity_field_mismatch", fieldId, entityId));

            // validate synapase type
            if(!schemaUtils.validateSynapseType(entity.getConnectorTypeId()))
                throw new RuntimeException("unsupported_synapse");

            // Delete field
            schemaService.deleteField(entityId, fieldId);
            attribute.setStatus(Status.DELETED);

            // prepare and return response
            ValidResponse validResponse = responseFieldUtils.convertDTOToResponse(fieldResponse, attribute);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);

        } catch (AbacException e) {
          throw e;
        } catch (Exception e) {
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(i18n("field_not_found", fieldId));
            }

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{entityId}/fields")
    public ResponseEntity<ValidResponse> createField(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                     @PathVariable String entityId, @Valid @RequestBody FieldRequest request) {

        try {
            // get entity
            EntityDefinition entity = schemaService.getEntity(entityId);

            // check if entity is already published
            if(entity.isApproved())
                throw new RuntimeException(i18n("entity_not_draft", entityId));

            // validate request
            // TODO move all validations to schemaUtils and return and list of errors.
            if(null == request.getApiName())
                throw new BadRequestException(i18n("field_create_missing_request_field", "apiName"));

            if(null == request.getDatastoreName())
                throw new BadRequestException(i18n("field_create_missing_request_field", "datastoreName"));

            if(null == request.getDisplayName())
                throw new BadRequestException(i18n("field_create_missing_request_field", "displayName"));

            if(null == request.getDataType())
                throw new BadRequestException(i18n("field_create_missing_request_field", "dataType"));

            if (!textUtil.isValidApiName(request.getApiName()))
                    throw new BadRequestException(i18n("invalid_field_api_name", request.getApiName()));

            // validate synapase type
            if(!schemaUtils.validateSynapseType(entity.getConnectorTypeId()))
                throw new RuntimeException("unsupported_synapse");

            // create field
            AttributeDefinition attribute = schemaUtils.convertFieldCreateRequest(entityId, request);
            attribute = schemaService.createDraftAttribute(entityId, attribute);

            // prepare and return response
            ValidResponse validResponse = responseFieldUtils.convertDTOToResponse(fieldResponse, attribute);
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);

        } catch (BadRequestException e) {
            throw new BadRequestException(e.getMessage());
        } catch (AbacException e) {
          throw e;
        } catch (Exception e) {
            log.error("Exception occurred in createField with trace {}", ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(i18n("entity_not_found", entityId));
            }

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PATCH, value = "/{entityId}/fields/batch")
    public ResponseEntity<ValidResponse> updateField(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
                                                     @PathVariable String entityId, @Valid @RequestBody List<FieldRequest> requests) {

        log.info(String.format("updateField request body: %s", requests));

        try {
            // validate request
            List<String> errors = schemaUtils.validateFieldUpdateRequest(entityId, requests);

            // return list of errors if there are any
            if(!errors.isEmpty()){
                ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("field_batch_update_errors"), errors));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // update fields
            List<String> successFields = new ArrayList<>();
            List<Map<String, String>> failedFields = new ArrayList<>();
            for (FieldRequest request : requests) {
                try {
                    AttributeDefinition attribute = schemaService.getAttribute(request.getFieldId());
                    attribute = schemaUtils.getUpdateAttribute(attribute, request);
                    schemaService.updateDraftAttribute(entityId, attribute);
                    successFields.add(attribute.getId());
                } catch (Exception e) {
                    failedFields.add(Map.of(request.getFieldId(), e.getMessage()));
                }
            }

            // prepare and return response
            FieldUpdateResponse fieldUpdateResponse = new FieldUpdateResponse();
            ValidResponse validResponse = responseUtils.convertDTOToResponse(fieldUpdateResponse.populateFieldUpdateResponse(successFields, failedFields));
            return ResponseEntity.status(HttpStatus.OK).body(validResponse);
        } catch (BadRequestException e) {
            throw new BadRequestException(e.getMessage());
        } catch (Exception e) {
            log.error("Exception occurred in updateField with trace {}", ExceptionUtils.getStackTrace(e));
            e.printStackTrace();
            if (StringUtils.contains(e.getMessage(), "not found"))
                throw new NotFoundException(i18n("entity_not_found", entityId));

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @Secured(READ_DATA_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{entityId}/data")
    public ResponseEntity<?> listEntityData(@PathVariable String entityId,
                                            @RequestParam(required = false) String cursorToken,
                                            @RequestParam(required = false) Integer limit,
                                            @RequestBody(required = false) String predicates) {

        try {

            if (limit == null)
                limit = KaribuConstants.MAX_LIMIT;

            // get transaction id from cursor
            String cursorId = null;
            if (cursorToken != null)
                cursorId = apiUtils.decodeCursor(cursorToken);

            if (predicates != null)
                predicates = dataUtils.convertDataRequestPredicates(entityId, predicates);

            Pair<List<EntityRecord>,Boolean> entityData = dataUtils.getEntityData(entityId, predicates, cursorId, limit);
            List<EntityDataResponse> entityDataResponses = dataUtils.getEntityDataResponse(entityData.x, entityId);

            ValidListResponse response = responseUtils.convertDTOToResponse(entityDataResponses, entityData.y);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (Exception e) {
            log.error("Exception occurred in listEntityData with trace {}", ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found"))
                throw new NotFoundException(e.getMessage());

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
}

    @Secured(READ_DATA_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{entityId}/count")
    public ResponseEntity<?> listEntityDataCount(@PathVariable String entityId,
                                            @RequestBody(required = false) String predicates) {

        try {
            if (predicates != null)
                predicates = dataUtils.convertDataRequestPredicates(entityId, predicates);

            Long recordCount = dataUtils.getCount(entityId, predicates);

            ValidResponse response = responseUtils.convertDTOToResponse(recordCount);
            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (Exception e) {
            log.error("Exception occurred in listEntityDataCount with trace {}", ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found"))
                throw new NotFoundException(e.getMessage());

            ValidResponse response = responseEntityUtils.populateErrorResponse(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

}
