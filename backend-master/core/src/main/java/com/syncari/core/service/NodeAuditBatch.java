package com.syncari.core.service;

import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class NodeAuditBatch {
    @Getter
    private
    ConcurrentLinkedQueue<NodeAudit> auditLogs = new ConcurrentLinkedQueue<>();
    @Getter
    private ReentrantLock lock = new ReentrantLock();
    @Getter
    @Setter
    private Instance instance;
    @Getter
    @Setter
    private Organization organization;
    @Getter
    @Setter
    private User user;
    @Getter
    private String batchId;

    public NodeAuditBatch(String batchId) {
        this.batchId = batchId;
    }

    public boolean queue(NodeAudit auditLog) {
        if (auditLog == null) {
            log.warn("AUDIT_ENQUEUE_NULL: batchId={}", batchId);
            return false;
        }
        
        boolean result = auditLogs.offer(auditLog);
        // Only log enqueue operations if queue is getting large or there are failures
        if (!result || auditLogs.size() > 20) {
            log.debug("AUDIT_ENQUEUE: batchId={}, nodeId={}, queueSize={}, success={}",
                batchId, auditLog.getNodeId() != null ? auditLog.getNodeId() : "null", 
                auditLogs.size(), result);
        }
        
        // Debug log to track individual record syncariIds through queue
        if (log.isDebugEnabled() && auditLog.getSyncariRecordId() != null) {
            log.debug("AUDIT_RECORD_QUEUED: batchId={}, syncariRecordId={}, nodeId={}, nodeName={}, entityId={}", 
                batchId, auditLog.getSyncariRecordId(), auditLog.getNodeId(), 
                auditLog.getNodeName(), auditLog.getEntityId());
        }
        
        return result;
    }

    public int size() {
        return auditLogs.size();
    }

    public boolean isEmpty() {
        return auditLogs.isEmpty();
    }

    public void withContext(Runnable runnable) {
        SyncariContext.runWithContext(organization, instance, user, runnable);
    }

    public void storeCurrentContext() {
        setInstance(SyncariContext.getInstance());
        setOrganization(SyncariContext.getOrganziation());
        setUser(SyncariContext.getUser());
    }
    

    public List<NodeAudit> dequeueAll() {
        int initialSize = auditLogs.size();
        List<NodeAudit> auditLogBuffer = new ArrayList<>();
        
        while (!auditLogs.isEmpty()) {
            NodeAudit nodeAudit = auditLogs.poll();
            if (nodeAudit == null) {
                log.warn("AUDIT_DEQUEUE_NULL: batchId={}, remainingQueueSize={}", 
                    batchId, auditLogs.size());
            }
            if (nodeAudit != null) {
                auditLogBuffer.add(nodeAudit);
            }
        }
        
        // Only log if we actually dequeued records
        if (auditLogBuffer.size() > 0) {
            log.debug("AUDIT_DEQUEUE_ALL: batchId={}, recordsDequeued={}",
                batchId, auditLogBuffer.size());
        }
        return auditLogBuffer;
    }

    public List<NodeAudit> dequeue(int numRecords) {
        List<NodeAudit> auditLogBuffer = new ArrayList<>();
        while (!auditLogs.isEmpty() && auditLogBuffer.size() < numRecords) {
            NodeAudit nodeAudit = auditLogs.poll();
            if (nodeAudit == null) {
                log.warn("AUDIT_DEQUEUE_NULL: batchId={}, remainingQueueSize={}", 
                    batchId, auditLogs.size());
            }
            if (nodeAudit != null) {
                auditLogBuffer.add(nodeAudit);
            }
        }
        
        // Only log if we actually dequeued records
        if (auditLogBuffer.size() > 0) {
            log.debug("AUDIT_DEQUEUE: batchId={}, recordsDequeued={}, hasRemaining={}",
                batchId, auditLogBuffer.size(), !auditLogs.isEmpty());
        }
        return auditLogBuffer;
    }
}
