package com.syncari.karibu.rest.controllers;

import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.PipelineService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.request.ExtractLoadRequest;
import com.syncari.karibu.rest.response.ExtractLoadResponse;
import com.syncari.karibu.rest.response.MappedField;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.WRITE_STUDIO;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1")
public class ExtractLoadController {

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private SchemaService schemaService;

    /**
     * Creates a simple extract-load pipeline: Source Entity → Syncari Entity → Destination Entity
     *
     * @param request ExtractLoadRequest containing pipeline configuration
     * @return ExtractLoadResponse with pipeline details and field mappings
     */
    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/extract-load")
    public ResponseEntity<ExtractLoadResponse> extractLoad(@Valid @RequestBody ExtractLoadRequest request) {
        try {
            log.info("Extract-Load request received: {}", request);

            // Fetch source entity to use its name as default for syncariEntityName and destinationEntityName
            EntityDefinition sourceEntity = schemaService.getEntity(request.getSourceEntityId());
            if (sourceEntity == null) {
                throw new NotFoundException("Source entity not found: " + request.getSourceEntityId());
            }
            String sourceEntityName = sourceEntity.getApiName();

            // Derive createDestinationEntity: true if destinationEntityId is absent
            boolean createDestinationEntity = StringUtils.isBlank(request.getDestinationEntityId());

            // Default syncariEntityName to source entity name if not provided
            String syncariEntityName = StringUtils.isNotBlank(request.getSyncariEntityName())
                    ? request.getSyncariEntityName()
                    : sourceEntityName;

            // Default destinationEntityName to source entity name if not provided
            String destinationEntityName = StringUtils.isNotBlank(request.getDestinationEntityName())
                    ? request.getDestinationEntityName()
                    : sourceEntityName;

            // Default pipelineName to source entity name if not provided
            String pipelineName = StringUtils.isNotBlank(request.getPipelineName())
                    ? request.getPipelineName()
                    : sourceEntityName;

            PipelineService.ExtractLoadResult result = pipelineService.createExtractLoadPipeline(
                    request.getSourceEntityId(),
                    request.getDestinationEntityId(),
                    request.getDestinationConnectorId(),
                    destinationEntityName,
                    createDestinationEntity,
                    syncariEntityName,
                    pipelineName,
                    request.getPublish(),
                    request.getStartResync(),
                    request.getAutoSchemaSync()
            );

            // Convert result to response
            ExtractLoadResponse response = buildResponse(result);
            return ResponseEntity.ok(response);

        } catch (NotFoundException | SyncariValidationException e) {
            log.error("Request validation failed", e);
            return ResponseEntity.badRequest().body(
                    ExtractLoadResponse.builder()
                            .status("ERROR")
                            .message(e.getMessage())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to process extract-load request", e);
            String errorMessage = e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ExtractLoadResponse.builder()
                            .status("ERROR")
                            .message("Internal error: " + errorMessage)
                            .build()
            );
        }
    }

    private ExtractLoadResponse buildResponse(PipelineService.ExtractLoadResult result) {
        List<MappedField> mappedFields = result.getMappedFields().stream()
                .map(this::toMappedField)
                .collect(Collectors.toList());

        return ExtractLoadResponse.builder()
                .status("SUCCESS")
                .message(result.isDestEntityCreated() ? "Pipeline created with new destination entity" : "Pipeline created/updated")
                .pipelineId(result.getPipelineId())
                .pipelineName(result.getPipelineName())
                .sourceEntityId(result.getSourceEntityId())
                .sourceEntityName(result.getSourceEntityName())
                .syncariEntityId(result.getSyncariEntityId())
                .syncariEntityName(result.getSyncariEntityName())
                .destinationEntityId(result.getDestinationEntityId())
                .destinationEntityName(result.getDestinationEntityName())
                .fieldsMapped(result.getFieldsMapped())
                .fieldsCreated(result.getFieldsCreated())
                .mappedFields(mappedFields)
                .published(result.isPublished())
                .resyncStarted(result.isResyncStarted())
                .resyncId(result.getResyncId())
                .autoSchemaSyncEnabled(result.isAutoSchemaSyncEnabled())
                .build();
    }

    private MappedField toMappedField(PipelineService.MappedFieldInfo info) {
        return MappedField.builder()
                .sourceFieldId(info.getSourceFieldId())
                .sourceFieldName(info.getSourceFieldName())
                .sourceFieldApiName(info.getSourceFieldApiName())
                .syncariFieldId(info.getSyncariFieldId())
                .syncariFieldName(info.getSyncariFieldName())
                .syncariFieldApiName(info.getSyncariFieldApiName())
                .destinationFieldId(info.getDestinationFieldId())
                .destinationFieldName(info.getDestinationFieldName())
                .destinationFieldApiName(info.getDestinationFieldApiName())
                .newlyCreated(info.isNewlyCreated())
                .build();
    }
}
