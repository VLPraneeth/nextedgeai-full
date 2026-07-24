package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_DATA_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_DATA_STUDIO;
import static com.syncari.utils.I18n.i18n;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncari.restutils.utils.DataQueryUtils;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import com.syncari.core.exception.NotFoundException;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.studio.DataFilterDTO;
import com.syncari.api.rest.controllers.data.studio.DataFilterResponse;
import com.syncari.api.rest.controllers.data.studio.DataMetaResponse;
import com.syncari.restutils.data.DataQueryMetadata;
import com.syncari.api.rest.controllers.data.studio.DataQueryResponse;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.connector.EntityData;
import com.syncari.connector.FileEntityData;
import com.syncari.connector.Operation;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Batch;
import com.syncari.core.model.Connector;
import com.syncari.core.model.DataFilter;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.BatchService;
import com.syncari.core.service.DataFilterService;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/studio/data")
public class DatastudioController {
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    DataFilterService filterService;
    @Autowired
    UserService userService;
    @Autowired
    BatchService batchService;
    @Autowired
    DataQueryUtils dataUtils;

    @ExceptionHandler(value = { SyncariValidationException.class })
    protected ResponseEntity<Object> handleValidationException(SyncariValidationException ex, WebRequest request) {
        log.error("Validation error: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @Secured(READ_DATA_STUDIO)
    @PostMapping("/entity/{entityId}/records")
    public DataQueryResponse query(
        @PathVariable String entityId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer page,
        @RequestParam String direction,
        @RequestParam int count,
        @RequestParam(required = false) String orderBy,
        @RequestParam(required = false) String sortDirection,
        @RequestBody(required = false) String predicate
    ) {
        // For cursor-based pagination (no orderBy/page), cursor is required for previous direction
        // For offset-based pagination (with orderBy), page number is used instead of cursor
        if (StringUtils.isBlank(orderBy) && StringUtils.isBlank(cursor) && PageDirection.valueOf(direction) == PageDirection.previous) {
            throw new SyncariValidationException(i18n("cursor_needed_for_prev"));
        }

        Optional<Expression> input = dataUtils.getExpression(predicate);
        EntityDefinition entity = schemaService.getEntity(entityId);
        Map<String, KeyValue> fields = dataUtils.getFilterFields(entity);
        List<Connector> sourceConnectors= new ArrayList<>();
        List<EntityDefinition> entities = new ArrayList<>();
        dataUtils.populateConnectorsAndEntities(entityId, sourceConnectors, entities);

        Set<String> selectedColumns = userService.getDataStudioColumns(SyncariContext.getUser().getId(), entityId);

        DataQueryMetadata metadata = new DataQueryMetadata(
            selectedColumns == null || selectedColumns.isEmpty() ? fields.keySet() : selectedColumns, fields
        );

        PageCursor pageCursor;
        if (StringUtils.isNotBlank(orderBy)) {
            boolean fieldExists = entity.getActiveAttributes().stream()
                .anyMatch(attr -> attr.getApiName().equals(orderBy));

            if (!fieldExists) {
                throw new SyncariValidationException(i18n("invalid_order_by_field", orderBy));
            }

            boolean ascending = "asc".equalsIgnoreCase(StringUtils.trimToEmpty(sortDirection));
            int pageNumber = (page != null && page > 0) ? page : 1;

            pageCursor = PageCursor.offsetBased(pageNumber, PageDirection.valueOf(direction), count, orderBy, ascending);
        } else {
            pageCursor = new PageCursor(cursor, PageDirection.valueOf(direction), count);
        }

        Page<EntityData> result = repoService.query(entityId, input, pageCursor, true);
        List<EntityRecord> entityRecords = transformer.toEntityRecords(result.getRecords());
        dataUtils.populateIdMappings(entity, entityRecords, sourceConnectors, entities);

        return new DataQueryResponse(entityRecords, result.getPageInfo(), metadata);
    }

    @Secured(WRITE_DATA_STUDIO)
    @PostMapping("/batch/entity/{entityId}/records/delete")
    public KeyValue batchDelete(
        @PathVariable String entityId,
        @RequestParam(required = false) boolean deleteInEndSystem,
        @RequestBody(required = false) Map<String, String> predicateMap
    ) {
        String predicate = predicateMap.getOrDefault("predicate", "");

        if (StringUtils.isBlank(predicate) && !deleteInEndSystem) {
            log.info("Purge request submitted for entire entity {}", entityId);
            Batch batch = repoService.submitBatchPurge(entityId);
            return toBatch(batch);
        }

        Optional<Expression> filter = dataUtils.getExpression(predicate);
        if (filter.isEmpty()) {
            throw new RuntimeException(i18n("filter_required"));
        }

        Batch batch = repoService.submitBatchDelete(entityId, predicate, deleteInEndSystem);
        return toBatch(batch);
    }

    @Secured(WRITE_DATA_STUDIO)
    @PatchMapping("/batch/entity/{entityId}/records")
    public KeyValue batchUpdate(@PathVariable String entityId, @RequestBody KeyValue data) {

        String predicate = data.get("predicate");
        Optional<Expression> filter = dataUtils.getExpression(predicate);
        if (filter.isEmpty()) {
            throw new RuntimeException(i18n("filter_required"));
        }
        Map<String, Object> updates = data.get("updates");
        if (updates == null) {
            throw new RuntimeException(i18n("missing_batch_update"));
        }
        Batch batch = repoService.submitBatchUpdate(entityId, predicate, updates);
        return toBatch(batch);
    }

    @Secured(WRITE_DATA_STUDIO)
    @PatchMapping("/batch/{batchId}/cancel")
    public KeyValue cancelBatch(@PathVariable String batchId) {
        Batch batch = batchService.cancel(batchId);
        return toBatch(batch);
    }

	@Secured(READ_DATA_STUDIO)
	@GetMapping("/batch/entity/{entityId}")
	public List<KeyValue> getBatches(@PathVariable String entityId, @RequestParam(required = false) String operation) {
		if (!StringUtils.isBlank(operation)) {
			return toBatchList(batchService.findBy(entityId, Operation.valueOf(operation)));
		}
		return toBatchList(batchService.findByEntityId(entityId));
	}

    @Secured(READ_DATA_STUDIO)
    @GetMapping("/batch/{batchId}")
    public KeyValue getBatch(@PathVariable String batchId) {
        Batch batch = batchService.findById(batchId).orElseThrow();
        return toBatchList(List.of(batch)).get(0);
    }

    @Deprecated
    @Secured(WRITE_DATA_STUDIO)
    @DeleteMapping("/entity/{entityId}")
    public void delete(@PathVariable String entityId) {
        repoService.deleteAllForEntity(entityId);
    }

    protected String idMappingLabel(Connector connector, EntityDefinition entityDefinition) {
        return (connector == null ? "Unknown Connector" : connector.getName()) + " / " + (entityDefinition == null ? "Unknown Entity" : entityDefinition.getDisplayName());

    }

    class QueryCSVInputStream extends InputStream {
        private final String predicate;
        private EntityDefinition entityDefinition;
        private  String cursor;
        private ByteArrayInputStream bin;
        private final Set<String> selectedColumns;
        private List<Connector> sourceConnectors;
        private List<EntityDefinition> connectedEntities;
        Map<String, Connector> connectorMap = new HashMap<>();
        private final CSVPrinter csvPrinter;
        private final StringWriter csvBuffer;
        private boolean completed;

        public QueryCSVInputStream(String predicate, EntityDefinition entityDefinition, Set<String> selectedColumns, List<Connector> sourceConnectors, List<EntityDefinition> connectedEntities){
            this.predicate = predicate;
            this.entityDefinition = entityDefinition;
            this.selectedColumns = selectedColumns;
            this.sourceConnectors = sourceConnectors;
            this.connectedEntities = connectedEntities;
            //add headers
            try {
                List<String> headers = new ArrayList<>();
                selectedColumns.forEach(c -> headers.add(c));
                sourceConnectors.forEach(connector -> connectorMap.put(connector.getId(),connector));
                connectedEntities.forEach(connectedEntity -> {
                    headers.add("Id Mapping: "+idMappingLabel(connectorMap.get(connectedEntity.getConnectorId()),connectedEntity) +" "+" Id");
                });
                csvBuffer = new StringWriter();
                csvPrinter = new CSVPrinter(csvBuffer, CSVFormat.DEFAULT
                        .withHeader(headers.toArray(new String[headers.size()])).withQuoteMode(QuoteMode.ALL));
            }catch(IOException ex){
                throw new RuntimeException(ex);
            }
            readPage();
        }

        private void readPage(){
            Optional<Expression> input = dataUtils.getExpression(predicate);
            // Get specific page
            Page<EntityData> page = repoService.query(entityDefinition.getId(), input, new PageCursor(cursor, PageDirection.next, 1000),true);
            Map<String, KeyValue> fields = dataUtils.getFilterFields(entityDefinition);
            DataQueryMetadata metadata = new DataQueryMetadata(selectedColumns, fields);

            // Convert that to entityRecords
            List<EntityRecord> entityRecords = transformer.toEntityRecords(page.getRecords());
            dataUtils.populateIdMappings(entityDefinition, entityRecords, sourceConnectors, connectedEntities);
            DataQueryResponse dataQueryResponse = new DataQueryResponse(entityRecords, page.getPageInfo(), metadata);
            writeToBuffer(dataQueryResponse);
            cursor = dataQueryResponse.getPageInfo().getEnd();
        }


        private String formatValueForCsv(Object value) {
            if (value == null) {
                return "";
            }

            // Format Date objects to ISO 8601 format (yyyy-MM-dd)
            if (value instanceof Date) {
                SimpleDateFormat isoFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                isoFormatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return isoFormatter.format((Date) value);
            }

            // Format LocalDate objects to ISO 8601 format (yyyy-MM-dd)
            if (value instanceof LocalDate) {
                return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }

            // Format Instant objects to ISO 8601 format
            if (value instanceof Instant) {
                return ((Instant) value).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
            }

            return value.toString();
        }

        private void writeToBuffer(DataQueryResponse dataQueryResponse) {
            dataQueryResponse.getRecords().forEach(entityRecord -> {
                List<String> record = new LinkedList<>();
                selectedColumns.forEach(column->{
                    record.add(formatValueForCsv(entityRecord.getValues().get(column)));
                });
                connectedEntities.forEach(connectedEntity -> {
                    Map<String, Object> idMapping = entityRecord.getIdMapping();
                    Connector connector = connectorMap.get(connectedEntity.getConnectorId());
                    String idMappingLabel = idMappingLabel(connector, connectedEntity);
                    if (MapUtils.isNotEmpty(idMapping) && StringUtils.isNotEmpty(idMappingLabel)) {
                        var id = idMapping.getOrDefault(idMappingLabel,"");
                        if (id == null) {
                            id = "";
                        }
                        record.add(id.toString());
                    } else {
                        // Adding empty idMapping while downloading if not present
                        record.add("");
                    }
                });
                try {
                    csvPrinter.printRecord(record);
                } catch (IOException e) {
                    log.warn("DS Export: cannot write row "+record,e);
                }
            });
            try {
                if (csvBuffer.getBuffer().length() > 0) {
                    bin = new ByteArrayInputStream(csvBuffer.getBuffer().toString().getBytes("utf-8"));
                    csvBuffer.getBuffer().setLength(0);
                } else {
                    log.info("CSV Buffer length is 0");
                }
            } catch (UnsupportedEncodingException e) {
                log.error(e.getMessage(),e);
                throw new SyncariValidationException("Invalid data found while exporting CSV");
            }
        }

        public int read() {
            if (bin == null && !completed) {
                readPage();
            }
            if (bin == null) {
                log.info("bin is null so not calling read page");
                completed = true;
                return -1;
            }
            int read = bin.read();
            if (read == -1) {
                bin = null;
                return read();
            }
            return read;
        }
    }

    @Secured(READ_DATA_STUDIO)
    @RequestMapping(value = "/entity/{entityId}/records/download", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<Resource> download(@PathVariable String entityId, @RequestParam(required = false) Map<String,String> predicateMap) {
        String predicate = predicateMap != null ? predicateMap.get("predicate") : "";
        EntityDefinition entity = schemaService.getEntity(entityId);
        Set<String> selectedColumns = userService.getDataStudioColumns(SyncariContext.getUser().getId(), entityId);
        Map<String, KeyValue> fields = dataUtils.getFilterFields(entity);

        List<Connector> sourceConnectors= new ArrayList<>();
        List<EntityDefinition> entities = new ArrayList<>();
        dataUtils.populateConnectorsAndEntities(entityId, sourceConnectors, entities);
        Set<String> columnsToExport = selectedColumns == null || selectedColumns.isEmpty() ? new LinkedHashSet<>(fields.keySet()) : selectedColumns;
        InputStreamResource resource = new InputStreamResource(new QueryCSVInputStream(predicate, entity, columnsToExport,sourceConnectors, entities));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getApiName() + ".csv\"")
                .body(resource);
    }


    @Secured(READ_DATA_STUDIO)
    @GetMapping("/entity/{entityId}/records/{recordId}")
    public KeyValue recordDetail(@PathVariable String entityId, @PathVariable String recordId) {
        EntityDefinition entity = schemaService.getEntity(entityId);
        Optional<EntityData> record = repoService.getRecordById(entity, recordId);
        Map<String, KeyValue> fields = dataUtils.getFilterFields(entity);

        if (record.isEmpty()) {
            throw new NotFoundException(i18n("generic_record_not_found"));
        }

        return new KeyValue(
          "metadata", fields,
          "record", record.get()
        );
    }

    @Secured(READ_DATA_STUDIO)
    @GetMapping("/entity/{entityId}/records/downloadFile/{recordId}")
    public ResponseEntity<Resource> downloadFileTypeRecord(@PathVariable String entityId, @PathVariable String recordId) {
        EntityDefinition entity = schemaService.getEntity(entityId);
        Optional<EntityData> record = repoService.getRecordById(entity, recordId);

        if (record.isEmpty()) {
            throw new NotFoundException(i18n("generic_record_not_found"));
        }

        InputStreamResource resource = new InputStreamResource(repoService.getDocumentContents(entity, record.get()));
        FileEntityData fileEntityData = new FileEntityData(record.get());
        return ResponseEntity.ok()
                .contentType(fileEntityData.getFileMediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileEntityData.getFullFileName() + "\"")
                .body(resource);
    }

    /**
     * Creates a new record in the specified entity with validation, DFI evaluation, and merge/dedupe support.
     *
     * @param entityId the ID of the entity to create the record in
     * @param record the record data to create
     * @return ResponseEntity containing the created record and any validation errors
     *         - HTTP 200 if creation successful
     *         - HTTP 400 if validation errors occurred
     * @throws SyncariValidationException if entity is not found, deleted, or inactive
     */
    @Secured(WRITE_DATA_STUDIO)
    @PostMapping("/entity/{entityId}/record")
    public ResponseEntity<EntityDataResponse> create(
        @PathVariable String entityId,
        @RequestBody EntityRecord record) {

        EntityDefinition entity = validateAndGetEntity(entityId, true);

        boolean runDFI = entity.isRunDFI();
        boolean runMerge = entity.isRunMerge();

        EntityData entityData = transformer.toEntityData(record, entity.getApiName());
        com.syncari.core.model.misc.EntityDataResponse created = repoService.create(entityData, entity, runDFI, runMerge);

        return buildResponse(created);
    }

    /**
     * Updates an existing record in the specified entity with validation, DFI evaluation, and merge/dedupe support.
     *
     * @param entityId the ID of the entity containing the record
     * @param recordId the ID of the record to update
     * @param record the updated record data
     * @return ResponseEntity containing the updated record and any validation errors
     *         - HTTP 200 if update successful
     *         - HTTP 400 if validation errors occurred
     * @throws SyncariValidationException if entity is not found or deleted
     */
    @Secured(WRITE_DATA_STUDIO)
    @PutMapping("/entity/{entityId}/records/{recordId}")
    public ResponseEntity<EntityDataResponse> update(
        @PathVariable String entityId,
        @PathVariable String recordId,
        @RequestBody EntityRecord record) {

        EntityDefinition entity = validateAndGetEntity(entityId, false);

        boolean runDFI = entity.isRunDFI();
        boolean runMerge = entity.isRunMerge();

        EntityData entityData = transformer.toEntityData(record, entity.getApiName());
        entityData.setId(recordId);
        com.syncari.core.model.misc.EntityDataResponse updated = repoService.update(entityData, entity, runDFI, runMerge);

        return buildResponse(updated);
    }

    @Secured(WRITE_DATA_STUDIO)
    @DeleteMapping("/entity/{entityId}/records/{recordId}")
    public void delete(@PathVariable String entityId, @PathVariable String recordId, @RequestParam(required = false) boolean deleteInEndSystems) {
        EntityDefinition entity = schemaService.getEntity(entityId);
        repoService.deleteGivenRecord(entity.getApiName(), recordId, deleteInEndSystems);
    }

    @Secured(READ_DATA_STUDIO)
    @GetMapping("/filters")
    public DataFilterResponse getFilters(@RequestParam String direction, @RequestParam int count,
            @RequestParam(required = false) String entityId, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) boolean bookmarked) {
        DataFilterResponse response = new DataFilterResponse();
        List<DataFilterDTO> dataFilterDTO = new ArrayList<>();
        PageCursor pageInfo = new PageCursor(cursor, PageDirection.valueOf(direction), count);
        Set<String> bookMarkedFilters = userService.getBookMarkedFilters(SyncariContext.getUser().getId());
        Page<DataFilter> page;
        if (StringUtils.isBlank(entityId)) {
            if (bookmarked) {
                page = filterService.getByIds(bookMarkedFilters, pageInfo);
            } else {
                page = filterService.getAll(pageInfo);
            }
        } else {
            if (bookmarked) {
                page = filterService.getByEntityByIds(entityId, bookMarkedFilters, pageInfo);
            } else {
                page = filterService.getByEntity(entityId, pageInfo);
            }
        }
        dataFilterDTO = transformer.toDataFilterDTO(page);
        dataFilterDTO.forEach(d ->  {
            if (bookMarkedFilters.contains(d.getId())) d.setBookmarked(true);
        });
        response.setPageInfo(page.getPageInfo());
        response.setFilters(dataFilterDTO);
        return response;
    }

    @Secured(WRITE_DATA_STUDIO)
    @PatchMapping("/filters/{filterId}")
    public void bookmark(@PathVariable String filterId, @RequestBody boolean bookmark) {
        userService.updateFilterBookmark(SyncariContext.getUser().getId(), filterId, bookmark);
    }

    @Secured(WRITE_DATA_STUDIO)
    @PostMapping("/filters")
    public DataFilterDTO createFilter(@RequestBody DataFilterDTO filter) {
        DataFilter f = filterService.create(transformer.toDataFilter(List.of(filter)).get(0));
        filter.setId(f.getId());
        if (filter.isBookmarked()) {
            userService.updateFilterBookmark(SyncariContext.getUser().getId(), filter.getId(), true);
        }
        return filter;
    }

    @Secured(WRITE_DATA_STUDIO)
    @DeleteMapping("/filters/{filterId}")
    public void deleteFilter(@PathVariable String filterId) {
        filterService.delete(filterId);
    }

    @Secured(WRITE_DATA_STUDIO)
    @PutMapping("/filters/{filterId}")
    public DataFilterDTO updateFilter(@RequestBody DataFilterDTO filter) {
        DataFilter f = filterService.update(transformer.toDataFilter(List.of(filter)).get(0));
        userService.updateFilterBookmark(SyncariContext.getUser().getId(), filter.getId(), filter.isBookmarked());
        return transformer.toDataFilterDTO(f);
    }

    @Secured(READ_DATA_STUDIO)
    @GetMapping("/meta")
    public DataMetaResponse getEntityMeta(@RequestParam List<String> entityApiNames) {
        DataMetaResponse resp = new DataMetaResponse();
        entityApiNames.forEach(e -> {
            resp.getCountMap().put(e, repoService.getCount(e));
            resp.getDeletedMap().put(e, repoService.getDeletedCount(e));
        });
        resp.getTotals().putIfAbsent("active", resp.getCountMap().values().stream().reduce(0L, Long::sum));
        resp.getTotals().putIfAbsent("deleted", resp.getDeletedMap().values().stream().reduce(0L, Long::sum));

        return resp;
    }

    private void validateExpression(Map<String, Object> map) {
        List<String> externalIds = new ArrayList<>();
        map.forEach((k, v) -> {
            try {
                Map values = (Map)v;
                if ("left".equalsIgnoreCase(k) && values.containsKey("value") && values.get("value").toString().startsWith("datastudio")) {
                    externalIds.add(values.get("value").toString());
                }
            } catch (Exception e) {
            }
        });
        if (externalIds.size() > 1) {
            throw new SyncariValidationException(i18n("only_one_external_id_allowed"));
        }
    }

    private KeyValue toBatch(Batch batch) {
        KeyValue b = new KeyValue(
            "id", batch.getId(),
            "status", batch.getStatus().name(),
            "operation", batch.getOperation().name(),
            "initiatedAt", batch.getCreatedAt(),
            "initiatedByUser", userService.findUserById(batch.getCreatedBy()).map(u -> u.getName())
        );
        b.put("lastUpdatedAt", batch.getUpdatedAt());
        b.put("filter", batch.getConfig().get("filter"));
        b.put("recordsProcessed", batch.getRowsAffected());
        b.put("recordsTotal", batch.getRowsTotal() == 0L ? batch.getRowsAffected() : batch.getRowsTotal());

        return b;
    }

    private List<KeyValue> toBatchList(List<Batch> batches) {
        List<KeyValue> results = new ArrayList<>();
        for (Batch batch : batches) {
            results.add(toBatch(batch));
        }
        return results;
    }

    /**
     * Validates and retrieves an entity by ID with comprehensive checks.
     *
     * @param entityId the ID of the entity to retrieve
     * @param checkActive whether to validate that the entity is active
     * @return the validated EntityDefinition
     * @throws SyncariValidationException if entity is not found, deleted, or inactive (when checkActive=true)
     */
    private EntityDefinition validateAndGetEntity(String entityId, boolean checkActive) {
        EntityDefinition entity;
        try {
            entity = schemaService.getEntity(entityId);
        } catch (RuntimeException e) {
            throw new SyncariValidationException(e.getMessage());
        }

        if (entity == null) {
            throw new SyncariValidationException(i18n("entity_not_found", entityId));
        }

        if (entity.isDeleted()) {
            throw new SyncariValidationException(i18n("entity_deleted", entity.getDisplayName()));
        }

        if (checkActive && !entity.isActive()) {
            throw new SyncariValidationException(i18n("entity_not_active", entity.getDisplayName()));
        }

        return entity;
    }

    /**
     * Builds a ResponseEntity from a core EntityDataResponse by mapping errors and determining HTTP status.
     *
     * @param coreResponse the core service response containing record data and errors
     * @return ResponseEntity with 400 status if errors exist, 200 otherwise
     */
    private ResponseEntity<EntityDataResponse> buildResponse(
            com.syncari.core.model.misc.EntityDataResponse coreResponse) {

        EntityRecord entityRecord = transformer.toEntityRecord(coreResponse.getRecord());
        EntityDataResponse response = new EntityDataResponse().setRecord(entityRecord);

        coreResponse.getErrors().getFields().forEach((fieldName, errorDetails) -> {
            errorDetails.forEach(error -> {
                response.getErrors().addFieldError(fieldName, error.getCode(), error.getMessage());
            });
        });

        coreResponse.getErrors().getRecord().forEach(error -> {
            response.getErrors().addRecordError(error.getCode(), error.getMessage());
        });

        return response.getErrors().isEmpty()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }
}

@Data
@Accessors(chain = true)
class EntityDataResponse {
    EntityRecord record;
    ValidationErrors errors = new ValidationErrors();

    @Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class ErrorDetail {
        String code;
        String message;
    }

    @Data
    @Accessors(chain = true)
    static class ValidationErrors {
        Map<String, List<ErrorDetail>> fields = new HashMap<>();
        List<ErrorDetail> record = new ArrayList<>();

        @JsonIgnore
        public boolean isEmpty() {
            return fields.isEmpty() && record.isEmpty();
        }

        public void addFieldError(String fieldName, String code, String message) {
            fields.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(new ErrorDetail(code, message));
        }

        public void addRecordError(String code, String message) {
            record.add(new ErrorDetail(code, message));
        }
    }
}
