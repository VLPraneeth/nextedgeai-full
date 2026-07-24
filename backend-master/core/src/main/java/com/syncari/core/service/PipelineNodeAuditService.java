package com.syncari.core.service;

import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.event.store.repo.BigQueryNodeAuditRepo;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PipelineNodeAuditService {
    public static final int MAX_RECORDS_TO_FLUSH_PER_BATCH_ID = 100;
    @Autowired
    BigQueryNodeAuditRepo pipelineNodeAuditRepo;
    private Map<String, NodeAuditBatch> nodeAuditBatches = new ConcurrentHashMap<>();
    private static final NodeAuditBatch EMPTY_BATCH = new NodeAuditBatch("");


    public void insertNodeAudit(NodeAudit nodeAudit) {
        pipelineNodeAuditRepo.insertNodeAudit(List.of(nodeAudit));
    }

    public boolean queue(NodeAudit nodeAudit) {
        if (nodeAudit == null) {
            log.warn("AUDIT_QUEUE_NULL: nodeAudit is null");
            return false;
        }
        
        final String batchId = nodeAudit.getBatchId();
        if (batchId == null) {
            log.warn("AUDIT_QUEUE_NULL_BATCH_ID: nodeId={}", nodeAudit.getNodeId());
            return false;
        }
        
        boolean isNewBatch = false;
        if (!nodeAuditBatches.containsKey(batchId)) {
            nodeAuditBatches.put(batchId, new NodeAuditBatch(batchId));
            isNewBatch = true;
            log.debug("AUDIT_BATCH_CREATED: batchId={}, totalBatches={}",
                batchId, nodeAuditBatches.size());
        }
        final NodeAuditBatch currentNodeAuditBatch = nodeAuditBatches.get(batchId);
        if (currentNodeAuditBatch != null) {
            currentNodeAuditBatch.storeCurrentContext();
            boolean result = currentNodeAuditBatch.queue(nodeAudit);
            
            // Only log queue status if queue is getting large
            if (!isNewBatch && currentNodeAuditBatch.size() > 50) {
                log.debug("AUDIT_QUEUE_LARGE: batchId={}, queueSize={}",
                    batchId, currentNodeAuditBatch.size());
            }
            return result;
        }
        return false;
    }

    private int queueSize(String batchId) {
        return nodeAuditBatches.getOrDefault(batchId, EMPTY_BATCH).size();
    }

    private boolean isQueueEmpty(String batchId) {
        return nodeAuditBatches.getOrDefault(batchId, EMPTY_BATCH).isEmpty();
    }

    public void forceFlush(String batchId) {
        final NodeAuditBatch nodeAuditBatch = nodeAuditBatches.get(batchId);
        if (nodeAuditBatch == null) {
            log.warn("AUDIT_FORCE_FLUSH_MISSING: batchId={}", batchId);
            return;
        }
        
        nodeAuditBatch.withContext(() -> {
            List<NodeAudit> auditLogBuffer = nodeAuditBatch.dequeueAll();
            if (!auditLogBuffer.isEmpty()) {
                logRecordsBeforeWrite(auditLogBuffer);
                pipelineNodeAuditRepo.insertNodeAudit(auditLogBuffer);
                log.debug("AUDIT_FORCE_WRITE: batchId={}, recordsWritten={}",
                        nodeAuditBatch.getBatchId(), auditLogBuffer.size());
            }
        });
    }

    public void flush() {
        // Only log if there are batches with data to flush
        long nonEmptyBatches = nodeAuditBatches.values().stream().filter(batch -> !batch.isEmpty()).count();
        if (nonEmptyBatches > 0) {
            log.debug("AUDIT_FLUSH_ALL_START: nonEmptyBatches={}, totalBatches={}",
                nonEmptyBatches, nodeAuditBatches.size());
        }
            
        nodeAuditBatches.forEach((batchId, nodeAuditBatch) -> {
            nodeAuditBatch.withContext(() -> {
                if (!nodeAuditBatch.isEmpty()) {
                    List<NodeAudit> auditLogBuffer = nodeAuditBatch.dequeue(MAX_RECORDS_TO_FLUSH_PER_BATCH_ID);
                    logRecordsBeforeWrite(auditLogBuffer);
                    pipelineNodeAuditRepo.insertNodeAudit(auditLogBuffer);
                    log.debug("AUDIT_WRITE: batchId={}, recordsWritten={}, hasRemaining={}",
                            nodeAuditBatch.getBatchId(), auditLogBuffer.size(), !nodeAuditBatch.isEmpty());
                }
            });
        });
        
        // Only log completion if we actually flushed something
        if (nonEmptyBatches > 0) {
            log.debug("AUDIT_FLUSH_ALL_COMPLETE: recordsFlushed=true, batches={}", nonEmptyBatches);
        }
    }

    public Page<NodeAudit> query(String entityId, String syncariRecordId, Instant startDate, Instant endDate, PageCursor cursor) {
        return pipelineNodeAuditRepo.query(entityId, syncariRecordId, startDate, endDate, cursor);
    }

    public void remove(String batchId) {
        NodeAuditBatch removedBatch = nodeAuditBatches.remove(batchId);
        if (removedBatch != null) {
            // Only log batch removal if it had pending records
            if (removedBatch.size() > 0) {
                log.debug("AUDIT_BATCH_REMOVED: batchId={}, pendingRecords={}, remainingBatches={}",
                    batchId, removedBatch.size(), nodeAuditBatches.size());
            }
        }
    }

    public Optional<NodeAuditBatch> getNodeAuditsInBuffer(String batchId) {
        return Optional.ofNullable(nodeAuditBatches.get(batchId));
    }

    private void logRecordsBeforeWrite(List<NodeAudit> auditLogBuffer) {
        if (log.isDebugEnabled()) {
            auditLogBuffer.forEach(audit -> {
                if (audit.getSyncariRecordId() != null) {
                    log.debug("AUDIT_RECORD_WRITING: batchId={}, syncariRecordId={}, nodeId={}, nodeName={}, entityId={}, pipelineName={}", 
                        audit.getBatchId(), audit.getSyncariRecordId(), audit.getNodeId(), 
                        audit.getNodeName(), audit.getEntityId(), audit.getPipelineName());
                }
            });
        }
    }

    public void flushAndRemove(String batchId) {
        if (!isQueueEmpty(batchId)) {
            log.warn("AUDIT_FLUSH: Force flushing remaining audit logs for batch={}, queueSize={}", 
                batchId, queueSize(batchId));
            forceFlush(batchId);
        }
        remove(batchId);
    }
}