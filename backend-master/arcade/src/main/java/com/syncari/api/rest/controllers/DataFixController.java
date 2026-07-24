package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.*;
import com.syncari.core.model.DataFixAuditLog;
import com.syncari.core.model.DataFixQuery;
import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.service.DataFixAuditService;
import com.syncari.core.service.DataFixService;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;

/**
 * Controller for Data Fix Support Tool
 * Provides endpoints for executing read queries, managing update queries with approval workflow,
 * and viewing audit logs
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/data-fix")
public class DataFixController {

    @Autowired
    private DataFixService dataFixService;

    @Autowired
    private DataFixAuditService auditService;

    @Autowired
    private ObjectTransformer transformer;

    private final ModelMapper modelMapper = new ModelMapper();

    // ===== Read Query Endpoints =====

    /**
     * Execute a read-only query
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.POST, value = "/read-query")
    public Map<String, Object> executeReadQuery(@Valid @RequestBody DataFixReadQueryRequest request) {
        log.info("Executing read query");
        return dataFixService.executeReadQuery(
                request.getQueryText(),
                request.getTargetDatabase()
        );
    }

    // ===== Update Query Endpoints =====

    /**
     * Submit an update query for approval
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.POST, value = "/update-query")
    public DataFixQueryResponse submitUpdateQuery(@Valid @RequestBody DataFixQueryRequest request) {
        log.info("Submitting update query for approval. Type: {}", request.getQueryType());
        DataFixQuery query = dataFixService.submitForApproval(
                request.getQueryText(),
                request.getJustification(),
                request.getApproverId(),
                request.getQueryType()
        );
        return toResponse(query);
    }

    /**
     * Execute dry run for an update query (without submitting)
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.POST, value = "/dry-run")
    public Map<String, Object> executeDryRunDirect(@Valid @RequestBody DataFixDryRunRequest request) {
        log.info("Executing dry run directly. Type: {}", request.getQueryType());
        return dataFixService.executeDryRunDirect(
                request.getQueryText(),
                request.getQueryType()
        );
    }

    /**
     * Execute dry run for an update query (deprecated - use /dry-run instead)
     */
    @Deprecated
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.POST, value = "/update-query/{id}/dry-run")
    public Map<String, Object> executeDryRun(@PathVariable String id) {
        log.info("Executing dry run for query: {}", id);
        return dataFixService.executeDryRun(id);
    }

    /**
     * Approve an update query
     */
    @Secured(APPROVE_UPDATE_QUERY)
    @RequestMapping(method = RequestMethod.POST, value = "/update-query/{id}/approve")
    public DataFixQueryResponse approveQuery(@PathVariable String id,
                                            @Valid @RequestBody DataFixApprovalRequest request) {
        log.info("Approving query: {}", id);
        DataFixQuery query = dataFixService.approveQuery(id, request.getApprovalNote());
        return toResponse(query);
    }

    /**
     * Reject an update query
     */
    @Secured(APPROVE_UPDATE_QUERY)
    @RequestMapping(method = RequestMethod.POST, value = "/update-query/{id}/reject")
    public DataFixQueryResponse rejectQuery(@PathVariable String id,
                                           @Valid @RequestBody DataFixApprovalRequest request) {
        log.info("Rejecting query: {}", id);
        DataFixQuery query = dataFixService.rejectQuery(id, request.getRejectionReason());
        return toResponse(query);
    }

    /**
     * Execute an approved update query
     */
    @Secured(EXECUTE_UPDATE_QUERY)
    @RequestMapping(method = RequestMethod.POST, value = "/update-query/{id}/execute")
    public Map<String, Object> executeApprovedQuery(@PathVariable String id) {
        log.info("Executing approved query: {}", id);
        return dataFixService.executeApprovedQuery(id);
    }

    // ===== Query Management Endpoints =====

    /**
     * Get all queries created by the current user
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/queries/my-requests")
    public List<DataFixQueryResponse> getMyQueries() {
        String userId = SyncariContext.getUser().getId();
        log.info("Getting queries for user: {}", userId);
        List<DataFixQuery> queries = dataFixService.getQueriesByRequester(userId);
        return queries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Get all queries pending approval for the current user
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/queries/pending-approvals")
    public List<DataFixQueryResponse> getPendingApprovals() {
        String userId = SyncariContext.getUser().getId();
        log.info("Getting pending approvals for user: {}", userId);
        List<DataFixQuery> queries = dataFixService.getPendingApprovals(userId);
        return queries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Get a specific query by ID
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/queries/{id}")
    public DataFixQueryResponse getQuery(@PathVariable String id) {
        log.info("Getting query: {}", id);
        DataFixQuery query = dataFixService.getQueryById(id)
                .orElseThrow(() -> new RuntimeException("Query not found: " + id));
        return toResponse(query);
    }

    /**
     * Get all queries (filtered by status if provided)
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/queries")
    public List<DataFixQueryResponse> getAllQueries(@RequestParam(required = false) DataFixQueryStatus status) {
        log.info("Getting all queries. Status filter: {}", status);
        List<DataFixQuery> queries;
        if (status != null) {
            queries = dataFixService.getQueriesByStatus(status);
        } else {
            queries = dataFixService.getAllQueries();
        }
        return queries.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Get all collection names from database
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/collections")
    public List<String> getCollectionNames() {
        log.info("Getting collection names");
        return dataFixService.getCollectionNames();
    }

    // ===== Audit Log Endpoints =====

    /**
     * Get audit logs for a specific query
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/audit-logs/query/{queryId}")
    public List<DataFixAuditLog> getAuditLogsForQuery(@PathVariable String queryId) {
        log.info("Getting audit logs for query: {}", queryId);
        return auditService.getLogsForQuery(queryId);
    }

    /**
     * Get audit logs for current user
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/audit-logs/my-activity")
    public List<DataFixAuditLog> getMyAuditLogs() {
        String userId = SyncariContext.getUser().getId();
        log.info("Getting audit logs for user: {}", userId);
        return auditService.getLogsForUser(userId);
    }

    /**
     * Get all audit logs with pagination
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/audit-logs")
    public Page<DataFixAuditLog> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        log.info("Getting all audit logs. Page: {}, Size: {}", page, size);

        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return auditService.getLogsPaginated(pageable);
    }

    /**
     * Get audit logs by date range
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/audit-logs/date-range")
    public List<DataFixAuditLog> getAuditLogsByDateRange(
            @RequestParam Long startDate,
            @RequestParam Long endDate) {
        log.info("Getting audit logs for date range: {} to {}", startDate, endDate);
        return auditService.getLogsByDateRange(new Date(startDate), new Date(endDate));
    }

    /**
     * Get all failed audit logs
     */
    @Secured(READ_DATA_FIX)
    @RequestMapping(method = RequestMethod.GET, value = "/audit-logs/failed")
    public List<DataFixAuditLog> getFailedAuditLogs() {
        log.info("Getting all failed audit logs");
        return auditService.getAllFailedLogs();
    }

    // ===== Helper Methods =====

    private DataFixQueryResponse toResponse(DataFixQuery query) {
        return modelMapper.map(query, DataFixQueryResponse.class);
    }
}
