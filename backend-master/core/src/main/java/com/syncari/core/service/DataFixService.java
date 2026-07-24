package com.syncari.core.service;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.DataFixQuery;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.model.misc.DataFixQueryType;
import com.syncari.core.repositories.syncari.DataFixQueryRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.utils.MongoUtils;
import com.syncari.utils.I18n;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

/**
 * Service for managing data fix queries
 * Handles read queries, update queries with approval workflow, and execution
 */
@Slf4j
@Component
public class DataFixService {

    private static final int MAX_EXECUTION_TIME_SECONDS = 60;
    private static final int MAX_ROW_LIMIT = 10000;

    // Blacklisted collections that cannot be accessed via Data Fix tool
    private static final Set<String> BLACKLISTED_COLLECTIONS = Set.of(
        "auditLog",
        "apiErrorLog",
        "dbchangelog",
        "notification",
        "privilege",
        "transactionLog",
        "dataFixAuditLog",
        "dataFixQuery",
        "userRole"
    );

    @Autowired
    private DataFixQueryRepo dataFixQueryRepo;

    @Autowired
    private DataFixAuditService auditService;

    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Autowired
    @Qualifier("defaultEmailService")
    private EmailService emailService;

    @Autowired
    private UserRepo userRepo;

    /**
     * Execute a read-only query
     */
    public Map<String, Object> executeReadQuery(String queryText, String database) {
        // Get current user from context
        String userId = SyncariContext.getUser().getId();
        String userEmail = SyncariContext.getUser().getEmail();
        String instanceId = SyncariContext.getSyncariId();

        // Parse MongoDB query
        MongoQueryParts queryParts = parseMongoQuery(queryText);
        String collection = queryParts.collection;

        // Validate collection is not blacklisted
        validateCollectionNotBlacklisted(collection);

        // Validate query is read-only (find operations only)
        validateReadQuery(queryParts.operation);

        // Create query record
        DataFixQuery query = new DataFixQuery(queryText, DataFixQueryType.READ, "Read query execution");
        query.setRequesterId(userId);
        query.setRequesterEmail(userEmail);
        query.setTargetDatabase(database);
        query.setTargetCollection(collection);
        query.setInstanceId(instanceId);
        query.setStatus(DataFixQueryStatus.EXECUTED);

        try {
            // Execute query with timeout and row limit
            List<Document> results = MongoUtils.executeMongoQuery(customerMongoTemplate, queryText, collection, MAX_ROW_LIMIT);

            // Prepare result
            Map<String, Object> result = new HashMap<>();
            result.put("results", results);
            result.put("rowCount", results.size());
            result.put("limited", results.size() >= MAX_ROW_LIMIT);

            query.setExecutionResult(result);
            query.setActualAffectedRowCount(results.size());
            query.setExecutedAt(new Date());

            // Save query (no audit log for read queries)
            dataFixQueryRepo.save(query);

            log.info("Read query executed successfully. Rows returned: {}", results.size());
            return result;

        } catch (Exception e) {
            log.error("Error executing read query: {}", e.getMessage(), e);
            query.setStatus(DataFixQueryStatus.FAILED);
            query.setErrorMessage(e.getMessage());
            dataFixQueryRepo.save(query);
            throw new RuntimeException(I18n.i18n("datafix.query.execution.failed", e.getMessage()));
        }
    }

    /**
     * Execute a dry run directly without submitting query for approval
     */
    public Map<String, Object> executeDryRunDirect(String queryText, DataFixQueryType queryType) {
        log.info("Executing direct dry run. Type: {}", queryType);

        String userId = SyncariContext.getUser().getId();
        String userEmail = SyncariContext.getUser().getEmail();

        try {
            // Parse MongoDB query
            MongoQueryParts queryParts = parseMongoQuery(queryText);
            String collection = queryParts.collection;
            String operation = queryParts.operation;
            String argsString = queryParts.argsString;

            // Validate collection is not blacklisted
            validateCollectionNotBlacklisted(collection);

            log.info("Direct dry run - collection: {}, operation: {}", collection, operation);

            // Extract filter from MongoDB query arguments
            // For updateMany/updateOne/deleteMany/deleteOne, the first argument is the filter
            Document filterDoc = new Document();
            if (!argsString.isEmpty()) {
                // Find the first JSON object (filter document)
                int firstBraceIndex = argsString.indexOf('{');
                if (firstBraceIndex != -1) {
                    int braceCount = 0;
                    int endIndex = firstBraceIndex;
                    for (int i = firstBraceIndex; i < argsString.length(); i++) {
                        char c = argsString.charAt(i);
                        if (c == '{') braceCount++;
                        else if (c == '}') {
                            braceCount--;
                            if (braceCount == 0) {
                                endIndex = i + 1;
                                break;
                            }
                        }
                    }
                    String filterString = argsString.substring(firstBraceIndex, endIndex);
                    filterDoc = Document.parse(filterString);
                }
            }

            log.info("Extracted filter: {}", filterDoc);

            // Execute query with filter to show affected documents
            org.springframework.data.mongodb.core.query.BasicQuery mongoQuery =
                    new org.springframework.data.mongodb.core.query.BasicQuery(filterDoc);
            mongoQuery.limit(MAX_ROW_LIMIT);

            List<Document> rawResults = customerMongoTemplate.find(mongoQuery, Document.class, collection);

            // Convert ObjectId to String for _id field
            List<Document> affectedRows = new ArrayList<>();
            for (Document doc : rawResults) {
                Document processedDoc = new Document();
                for (Map.Entry<String, Object> entry : doc.entrySet()) {
                    if ("_id".equals(entry.getKey()) && entry.getValue() instanceof ObjectId) {
                        processedDoc.put("_id", entry.getValue().toString());
                    } else {
                        processedDoc.put(entry.getKey(), entry.getValue());
                    }
                }
                affectedRows.add(processedDoc);
            }

            // Prepare dry run result
            Map<String, Object> dryRunResult = new HashMap<>();
            dryRunResult.put("data", affectedRows);
            dryRunResult.put("rowCount", affectedRows.size());
            dryRunResult.put("limited", affectedRows.size() >= MAX_ROW_LIMIT);

            log.info("Direct dry run completed. Affected rows: {}", affectedRows.size());
            return dryRunResult;

        } catch (Exception e) {
            log.error("Error executing direct dry run: {}", e.getMessage(), e);
            throw new RuntimeException(I18n.i18n("datafix.dryrun.failed", e.getMessage()));
        }
    }

    /**
     * Execute a dry run for an update query
     */
    public Map<String, Object> executeDryRun(String queryId) {
        log.info("Executing dry run for query: {}", queryId);

        DataFixQuery query = dataFixQueryRepo.findById(queryId)
                .orElseThrow(() -> new RuntimeException(I18n.i18n("datafix.query.notfound")));

        String userId = SyncariContext.getUser().getId();
        String userEmail = SyncariContext.getUser().getEmail();

        validateCondition(query.getApproverId().equals(userId),
                I18n.i18n("datafix.query.unauthorized"));

        try {
            // Parse MongoDB query
            MongoQueryParts queryParts = parseMongoQuery(query.getQueryText());
            String collection = queryParts.collection;
            String argsString = queryParts.argsString;

            log.info("Dry run - collection: {}, operation: {}", collection, queryParts.operation);

            // Extract filter from MongoDB query arguments
            Document filterDoc = new Document();
            if (!argsString.isEmpty()) {
                int firstBraceIndex = argsString.indexOf('{');
                if (firstBraceIndex != -1) {
                    int braceCount = 0;
                    int endIndex = firstBraceIndex;
                    for (int i = firstBraceIndex; i < argsString.length(); i++) {
                        char c = argsString.charAt(i);
                        if (c == '{') braceCount++;
                        else if (c == '}') {
                            braceCount--;
                            if (braceCount == 0) {
                                endIndex = i + 1;
                                break;
                            }
                        }
                    }
                    String filterString = argsString.substring(firstBraceIndex, endIndex);
                    filterDoc = Document.parse(filterString);
                }
            }

            // Execute query with filter to show affected documents
            org.springframework.data.mongodb.core.query.BasicQuery mongoQuery =
                    new org.springframework.data.mongodb.core.query.BasicQuery(filterDoc);
            mongoQuery.limit(MAX_ROW_LIMIT);

            List<Document> rawResults = customerMongoTemplate.find(mongoQuery, Document.class, collection);

            // Convert ObjectId to String for _id field
            List<Document> affectedRows = new ArrayList<>();
            for (Document doc : rawResults) {
                Document processedDoc = new Document();
                for (Map.Entry<String, Object> entry : doc.entrySet()) {
                    if ("_id".equals(entry.getKey()) && entry.getValue() instanceof ObjectId) {
                        processedDoc.put("_id", entry.getValue().toString());
                    } else {
                        processedDoc.put(entry.getKey(), entry.getValue());
                    }
                }
                affectedRows.add(processedDoc);
            }

            // Prepare dry run result
            Map<String, Object> dryRunResult = new HashMap<>();
            dryRunResult.put("affectedRows", affectedRows);
            dryRunResult.put("rowCount", affectedRows.size());
            dryRunResult.put("limited", affectedRows.size() >= MAX_ROW_LIMIT);

            query.setDryRunResult(dryRunResult);
            query.setAffectedRowCount(affectedRows.size());
            dataFixQueryRepo.save(query);

            log.info("Dry run completed. Affected rows: {}", affectedRows.size());
            return dryRunResult;

        } catch (Exception e) {
            log.error("Error executing dry run: {}", e.getMessage(), e);
            throw new RuntimeException(I18n.i18n("datafix.dryrun.failed", e.getMessage()));
        }
    }

    /**
     * Submit an update query for approval
     */
    public DataFixQuery submitForApproval(String queryText, String justification,
                                         String approverId, DataFixQueryType queryType) {
        log.info("Submitting query for approval. Type: {}", queryType);

        String userId = SyncariContext.getUser().getId();
        String userEmail = SyncariContext.getUser().getEmail();
        String instanceId = SyncariContext.getSyncariId();

        // Parse MongoDB query
        MongoQueryParts queryParts = parseMongoQuery(queryText);
        String targetCollection = queryParts.collection;
        log.info("Extracted collection from query: {}", targetCollection);

        // Validate collection is not blacklisted
        validateCollectionNotBlacklisted(targetCollection);

        // Validate justification
        validateCondition(StringUtils.isBlank(justification),
                I18n.i18n("datafix.justification.required"));

        // Validate approver is provided
        validateCondition(StringUtils.isBlank(approverId),
                I18n.i18n("datafix.approver.required"));

        // Validate approver is different from requester
        validateCondition(approverId.equals(userId),
                I18n.i18n("datafix.approver.self.notallowed"));

        // Get approver details
        User approver = userRepo.findById(approverId)
                .orElseThrow(() -> new RuntimeException(I18n.i18n("user.notfound")));

        // Create query record
        DataFixQuery query = new DataFixQuery(queryText, queryType, justification);
        query.setRequesterId(userId);
        query.setRequesterEmail(userEmail);
        query.setApproverId(approverId);
        query.setApproverEmail(approver.getEmail());
        query.setTargetCollection(targetCollection);
        query.setTargetDatabase(instanceId); // Using instanceId as database identifier
        query.setInstanceId(instanceId);
        query.setStatus(DataFixQueryStatus.PENDING_APPROVAL);
        query.setSubmittedAt(new Date());

        // Save query
        DataFixQuery saved = dataFixQueryRepo.save(query);

        // Send email notification (if enabled)
        try {
            sendApprovalRequestEmail(saved, approver);
        } catch (Exception e) {
            log.warn("Failed to send approval request email: {}", e.getMessage());
        }

        log.info("Query submitted for approval. Query ID: {}", saved.getId());
        return saved;
    }

    /**
     * Approve a query
     */
    public DataFixQuery approveQuery(String queryId, String approvalNote) {
        log.info("Approving query: {}", queryId);

        DataFixQuery query = dataFixQueryRepo.findById(queryId)
                .orElseThrow(() -> new RuntimeException(I18n.i18n("datafix.query.notfound")));

        String userId = SyncariContext.getUser().getId();
        String userEmail = SyncariContext.getUser().getEmail();

        // Validate approver
        validateCondition(!query.getApproverId().equals(userId),
                I18n.i18n("datafix.query.unauthorized"));

        validateCondition(query.getStatus() != DataFixQueryStatus.PENDING_APPROVAL,
                I18n.i18n("datafix.query.invalid.status"));

        // Validate approval note is provided
        validateCondition(StringUtils.isBlank(approvalNote),
                I18n.i18n("datafix.approval.note.required"));

        // Update status
        query.setStatus(DataFixQueryStatus.APPROVED);
        query.setApprovedAt(new Date());
        query.setApprovalNote(approvalNote);

        DataFixQuery saved = dataFixQueryRepo.save(query);

        // Send email notification
        try {
            sendApprovalNotificationEmail(saved, true);
        } catch (Exception e) {
            log.warn("Failed to send approval notification email: {}", e.getMessage());
        }

        log.info("Query approved: {}", queryId);
        return saved;
    }

    /**
     * Reject a query
     */
    public DataFixQuery rejectQuery(String queryId, String rejectionReason) {
        log.info("Rejecting query: {}", queryId);

        DataFixQuery query = dataFixQueryRepo.findById(queryId)
                .orElseThrow(() -> new RuntimeException(I18n.i18n("datafix.query.notfound")));

        String userId = SyncariContext.getUser().getId();
        String userEmail = SyncariContext.getUser().getEmail();

        // Validate approver
        validateCondition(!query.getApproverId().equals(userId),
                I18n.i18n("datafix.query.unauthorized"));

        validateCondition(query.getStatus() != DataFixQueryStatus.PENDING_APPROVAL,
                I18n.i18n("datafix.query.invalid.status"));

        validateCondition(StringUtils.isBlank(rejectionReason),  I18n.i18n("datafix.rejection.reason.required"));

        // Update status
        query.setStatus(DataFixQueryStatus.REJECTED);
        query.setRejectedAt(new Date());
        query.setRejectionReason(rejectionReason);

        DataFixQuery saved = dataFixQueryRepo.save(query);

        // Send email notification
        try {
            sendApprovalNotificationEmail(saved, false);
        } catch (Exception e) {
            log.warn("Failed to send rejection notification email: {}", e.getMessage());
        }

        log.info("Query rejected: {}", queryId);
        return saved;
    }

    /**
     * Execute an approved query
     */
    public Map<String, Object> executeApprovedQuery(String queryId) {
        log.info("Executing approved query: {}", queryId);

        DataFixQuery query = dataFixQueryRepo.findById(queryId)
                .orElseThrow(() -> new RuntimeException(I18n.i18n("datafix.query.notfound")));

        String userId = SyncariContext.getUser().getId();
        String userEmail = SyncariContext.getUser().getEmail();

        // Validate query is approved
        validateCondition(query.getStatus() != DataFixQueryStatus.APPROVED,
                I18n.i18n("datafix.query.not.approved"));

        // Validate executor has permission (approver or requester)
        validateCondition(!query.getApproverId().equals(userId) && !query.getRequesterId().equals(userId),
                I18n.i18n("datafix.query.unauthorized"));

        try {
            // Execute the query based on type
            Map<String, Object> result = executeUpdateQuery(query);

            // Update query status
            query.setStatus(DataFixQueryStatus.EXECUTED);
            query.setExecutedAt(new Date());
            query.setExecutorId(userId);
            query.setExecutorEmail(userEmail);
            query.setExecutionResult(result);
            query.setActualAffectedRowCount((Integer) result.get("modifiedCount"));

            dataFixQueryRepo.save(query);

            // Audit log
            auditService.logQueryExecution(query, userId, userEmail, true, null);

            // Send email notification
            try {
                sendExecutionNotificationEmail(query, true, null);
            } catch (Exception e) {
                log.warn("Failed to send execution notification email: {}", e.getMessage());
            }

            log.info("Query executed successfully. Modified rows: {}", result.get("modifiedCount"));
            return result;

        } catch (Exception e) {
            log.error("Error executing approved query: {}", e.getMessage(), e);

            query.setStatus(DataFixQueryStatus.FAILED);
            query.setErrorMessage(e.getMessage());
            query.setExecutorId(userId);
            query.setExecutorEmail(userEmail);
            dataFixQueryRepo.save(query);

            auditService.logQueryExecution(query, userId, userEmail, false, e.getMessage());

            // Send email notification
            try {
                sendExecutionNotificationEmail(query, false, e.getMessage());
            } catch (Exception emailEx) {
                log.warn("Failed to send execution failure notification email: {}", emailEx.getMessage());
            }

            throw new RuntimeException(I18n.i18n("datafix.query.execution.failed", e.getMessage()));
        }
    }

    // ===== Private Helper Methods =====

    private void validateReadQuery(String operation) {
        // Valid read-only MongoDB operations
        List<String> readOnlyOperations = Arrays.asList("find", "findOne", "aggregate", "count", "distinct", "countDocuments");

        validateCondition(!readOnlyOperations.contains(operation),
                I18n.i18n("datafix.query.readonly.required"));
    }

    /**
     * Validate that the collection is not blacklisted
     */
    private void validateCollectionNotBlacklisted(String collection) {
        validateCondition(BLACKLISTED_COLLECTIONS.contains(collection),
                I18n.i18n("datafix.collection.blacklisted", collection));
    }

    private Map<String, Object> executeUpdateQuery(DataFixQuery query) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Execute based on query type
            switch (query.getQueryType()) {
                case UPDATE:
                    UpdateResult updateResult = executeMongoUpdate(query.getQueryText(), query.getTargetCollection());
                    result.put("matchedCount", updateResult.getMatchedCount());
                    result.put("modifiedCount", (int) updateResult.getModifiedCount());
                    break;

                case DELETE:
                    DeleteResult deleteResult = executeMongoDelete(query.getQueryText(), query.getTargetCollection());
                    result.put("deletedCount", (int) deleteResult.getDeletedCount());
                    result.put("modifiedCount", (int) deleteResult.getDeletedCount());
                    break;

                case INSERT:
                    Document insertResult = executeMongoInsert(query.getQueryText(), query.getTargetCollection());
                    result.put("insertedId", insertResult.get("_id"));
                    result.put("modifiedCount", 1);
                    break;

                default:
                    throw new RuntimeException("Unsupported query type: " + query.getQueryType());
            }
        } catch (Exception e) {
            log.error("Error executing update query: {}", e.getMessage(), e);
            throw e;
        }

        return result;
    }

    private UpdateResult executeMongoUpdate(String queryText, String collection) {
        // Parse MongoDB update query: db.collection.updateMany(filter, update)
        MongoQueryParts queryParts = parseMongoQuery(queryText);
        String argsString = queryParts.argsString;

        // Extract filter and update documents
        // Format: updateMany({filter}, {$set: {field: value}})
        // First JSON object is filter, second is update
        List<Document> documents = new ArrayList<>();
        int braceCount = 0;
        int startIndex = -1;

        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);
            if (c == '{') {
                if (braceCount == 0) startIndex = i;
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && startIndex != -1) {
                    String docString = argsString.substring(startIndex, i + 1);
                    documents.add(Document.parse(docString));
                    startIndex = -1;
                }
            }
        }

        if (documents.size() < 2) {
            throw new IllegalArgumentException("Invalid update query format. Expected: db.collection.updateMany(filter, update)");
        }

        Document filter = documents.get(0);
        Document update = documents.get(1);

        log.info("Executing update on collection '{}' with filter: {} and update: {}", collection, filter, update);

        // Execute the update
        return customerMongoTemplate.getCollection(collection).updateMany(filter, update);
    }

    private DeleteResult executeMongoDelete(String queryText, String collection) {
        // Parse MongoDB delete query: db.collection.deleteMany(filter)
        MongoQueryParts queryParts = parseMongoQuery(queryText);
        String argsString = queryParts.argsString;

        // Extract filter document
        Document filter = new Document();
        if (!argsString.isEmpty()) {
            int firstBraceIndex = argsString.indexOf('{');
            if (firstBraceIndex != -1) {
                int braceCount = 0;
                int endIndex = firstBraceIndex;
                for (int i = firstBraceIndex; i < argsString.length(); i++) {
                    char c = argsString.charAt(i);
                    if (c == '{') braceCount++;
                    else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            endIndex = i + 1;
                            break;
                        }
                    }
                }
                String filterString = argsString.substring(firstBraceIndex, endIndex);
                filter = Document.parse(filterString);
            }
        }

        log.info("Executing delete on collection '{}' with filter: {}", collection, filter);

        // Execute the delete
        return customerMongoTemplate.getCollection(collection).deleteMany(filter);
    }

    private Document executeMongoInsert(String queryText, String collection) {
        // Parse MongoDB insert query: db.collection.insertOne(document) or insertMany([documents])
        MongoQueryParts queryParts = parseMongoQuery(queryText);
        String argsString = queryParts.argsString;
        String operation = queryParts.operation;

        log.info("Executing insert on collection '{}' with operation: {}", collection, operation);

        if (operation.equals("insertOne")) {
            // Extract single document
            Document doc = Document.parse(argsString);
            customerMongoTemplate.getCollection(collection).insertOne(doc);
            return doc;
        } else if (operation.equals("insertMany")) {
            // Extract array of documents
            // Parse the array format: [{doc1}, {doc2}, ...]
            List<Document> documents = new ArrayList<>();
            int braceCount = 0;
            int startIndex = -1;

            for (int i = 0; i < argsString.length(); i++) {
                char c = argsString.charAt(i);
                if (c == '{') {
                    if (braceCount == 0) startIndex = i;
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0 && startIndex != -1) {
                        String docString = argsString.substring(startIndex, i + 1);
                        documents.add(Document.parse(docString));
                        startIndex = -1;
                    }
                }
            }

            if (documents.isEmpty()) {
                throw new IllegalArgumentException("No documents found in insertMany query");
            }

            customerMongoTemplate.getCollection(collection).insertMany(documents);
            return documents.get(0); // Return first inserted document
        } else {
            throw new IllegalArgumentException("Unsupported insert operation: " + operation);
        }
    }

    private void sendApprovalRequestEmail(DataFixQuery query, User approver) {
        String subject = "Data Fix Query Approval Request - " + query.getId();
        String body = String.format(
                "A data fix query requires your approval.\n\n" +
                "Query ID: %s\n" +
                "Type: %s\n" +
                "Requester: %s\n" +
                "Justification: %s\n" +
                "Collection: %s\n" +
                "Affected Rows: %s\n\n" +
                "Please review and approve/reject this request in the Data Fix tool.",
                query.getId(), query.getQueryType(), query.getRequesterEmail(),
                query.getJustification(), query.getTargetCollection(),
                query.getAffectedRowCount() != null ? query.getAffectedRowCount() : "Unknown"
        );

        emailService.sendText(Arrays.asList(approver.getEmail()), subject, body);
    }

    private void sendApprovalNotificationEmail(DataFixQuery query, boolean approved) {
        String subject = String.format("Data Fix Query %s - %s",
                approved ? "Approved" : "Rejected", query.getId());
        String body = String.format(
                "Your data fix query has been %s.\n\n" +
                "Query ID: %s\n" +
                "Type: %s\n" +
                "Approver: %s\n" +
                "%s\n\n" +
                "%s",
                approved ? "approved" : "rejected",
                query.getId(), query.getQueryType(), query.getApproverEmail(),
                approved ? "Approval Note: " + query.getApprovalNote() : "Rejection Reason: " + query.getRejectionReason(),
                approved ? "You can now execute this query in the Data Fix tool." : ""
        );

        emailService.sendText(Arrays.asList(query.getRequesterEmail()), subject, body);
    }

    private void sendExecutionNotificationEmail(DataFixQuery query, boolean success, String errorMessage) {
        String subject = String.format("Data Fix Query %s - %s",
                success ? "Executed Successfully" : "Execution Failed", query.getId());
        String body = String.format(
                "Your data fix query execution has %s.\n\n" +
                "Query ID: %s\n" +
                "Type: %s\n" +
                "Executor: %s\n" +
                "Modified Rows: %s\n" +
                "%s",
                success ? "completed successfully" : "failed",
                query.getId(), query.getQueryType(), query.getExecutorEmail(),
                query.getActualAffectedRowCount() != null ? query.getActualAffectedRowCount() : "Unknown",
                success ? "" : "Error: " + errorMessage
        );

        List<String> recipients = new ArrayList<>();
        recipients.add(query.getRequesterEmail());
        if (query.getApproverEmail() != null && !query.getApproverEmail().equals(query.getRequesterEmail())) {
            recipients.add(query.getApproverEmail());
        }

        emailService.sendText(recipients, subject, body);
    }

    // ===== Query Management Methods =====

    public List<DataFixQuery> getQueriesByRequester(String requesterId) {
        return dataFixQueryRepo.findByRequesterId(requesterId);
    }

    public List<DataFixQuery> getPendingApprovals(String approverId) {
        return dataFixQueryRepo.findPendingApprovalsByApproverId(approverId);
    }

    public List<DataFixQuery> getQueriesByStatus(DataFixQueryStatus status) {
        return dataFixQueryRepo.findByStatus(status);
    }

    public Optional<DataFixQuery> getQueryById(String queryId) {
        return dataFixQueryRepo.findById(queryId);
    }

    public List<DataFixQuery> getAllQueries() {
        return dataFixQueryRepo.findAll();
    }

    /**
     * Get all collection names from customer database
     */
    public List<String> getCollectionNames() {
        try {
            MongoDatabase db = customerMongoTemplate.getMongoDbFactory().getDb();
            List<String> collections = new ArrayList<>();
            db.listCollectionNames().into(collections);

            // Filter out system collections and blacklisted collections
            return collections.stream()
                    .filter(name -> !name.startsWith("system."))
                    .filter(name -> !BLACKLISTED_COLLECTIONS.contains(name))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching collection names: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch collection names: " + e.getMessage());
        }
    }

    /**
     * Parse MongoDB shell command and extract components
     * Example: "db.accounts.find({status: 'active'})"
     * Returns: {collection: "accounts", operation: "find", args: "{status: 'active'}"}
     */
    private MongoQueryParts parseMongoQuery(String queryText) {
        try {
            String trimmed = queryText.trim();

            // Remove trailing semicolon if present
            if (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }

            // Pattern to match: db.collection.operation(args)
            Pattern pattern = Pattern.compile("^db\\.([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\((.*)\\)$", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(trimmed);

            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid MongoDB query format. Expected: db.collection.operation(...)");
            }

            String collection = matcher.group(1);
            String operation = matcher.group(2);
            String argsString = matcher.group(3).trim();

            log.info("Parsed MongoDB query - collection: {}, operation: {}", collection, operation);

            return new MongoQueryParts(collection, operation, argsString);

        } catch (Exception e) {
            log.error("Error parsing MongoDB query: {}", queryText, e);
            throw new IllegalArgumentException("Invalid MongoDB query format: " + e.getMessage());
        }
    }

    /**
     * Helper class to hold parsed MongoDB query components
     */
    private static class MongoQueryParts {
        String collection;
        String operation;
        String argsString;

        MongoQueryParts(String collection, String operation, String argsString) {
            this.collection = collection;
            this.operation = operation;
            this.argsString = argsString;
        }
    }
}
