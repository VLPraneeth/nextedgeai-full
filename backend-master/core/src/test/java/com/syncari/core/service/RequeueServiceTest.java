package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.RequeueRequest;
import com.syncari.core.repositories.customer.RequeueRequestRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RequeueServiceTest extends AbstractSyncariTest {
    @Autowired
    RequeueService requeueService;
    @Autowired
    RequeueRequestRepo repo;

    @Override
    public void tearDown() {
        resetRepos(repo);
        super.tearDown();
    }
    @Override
    public void setUp() {
        super.setUp();
        resetRepos(repo);
    }

    @Test
    public void requeueRecord(){
        RequeueRequest requeueRequest = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r1");
        requeueService.requeue(List.of(requeueRequest));
        Page<RequeueRequest> e1 = requeueService.findRequeueRequests("e1","g1", Pageable.unpaged());
        assertEquals(1,e1.getContent().size());
        //rqueue the same record again
        ZonedDateTime retryTimeLimit = ZonedDateTime.now().plusDays(2);
        RequeueRequest requeueRequest2 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRetryTimeLimit(retryTimeLimit).setRecordId("r1");
        requeueService.requeue(List.of(requeueRequest2));
        Page<RequeueRequest> e2 = requeueService.findRequeueRequests("e1","g1", Pageable.unpaged());
        assertEquals(1,e2.getContent().size());
    }

    @Test
    public void requeueSourceRecord(){
        RequeueRequest requeueRequest = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r1");
        requeueService.requeue(List.of(requeueRequest));
        Page<RequeueRequest> e1 = requeueService.findSourceRequeueRequests("e1","g1");
        assertEquals(1,e1.getContent().size());
        //rqueue the same record again
        ZonedDateTime retryTimeLimit = ZonedDateTime.now().plusDays(2);
        RequeueRequest requeueRequest2 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setRetryTimeLimit(retryTimeLimit).setRecordId("r1");
        requeueService.requeue(List.of(requeueRequest2));
        Page<RequeueRequest> e2 = requeueService.findSourceRequeueRequests("e1","g1");
        assertEquals(1,e2.getContent().size());

        //expired record
        RequeueRequest requeueRequest3 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r1");
        requeueService.requeue(List.of(requeueRequest3));
        Page<RequeueRequest> e3 = requeueService.findSourceRequeueRequests("e1", "g1");
        //not found
        assertEquals(0, e3.getContent().size());
    }

    @Test
    public void findExpiredRecordsToProcess() {
        RequeueRequest r1 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setProcessExpiredRecord(true)
                .setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r1");
        RequeueRequest r2 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setProcessExpiredRecord(true)
                .setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r2");
        RequeueRequest r3 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setProcessExpiredRecord(true)
                .setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r3");
        RequeueRequest r4 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setProcessExpiredRecord(true)
                .setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r4");
        RequeueRequest r5 = new RequeueRequest()
                .setEntityDefinitionId("e1")
                .setGraphId("g1")
                .setRecordType(RequeueRequest.RecordType.SOURCE)
                .setProcessExpiredRecord(true)
                .setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r5");

        requeueService.requeue(List.of(r1, r2, r3, r4, r5));
        Page<RequeueRequest> e1 = requeueService.findSourceRequeueRequests("e1", "g1");
        assertEquals(0, e1.getContent().size());
        Page<RequeueRequest> e2 = requeueService.findExpiredSourceRequeueRequestsToProcess("e1", "g1");
        assertEquals(5, e2.getContent().size());

        final Page<RequeueRequest> page1 = requeueService.findExpiredSourceRequeueRequestsToProcess("e1", "g1", PageRequest.of(0, 2));
        assertEquals(2, page1.getContent().size());
        assertTrue(page1.hasNext());

        final Page<RequeueRequest> page2 = requeueService.findExpiredSourceRequeueRequestsToProcess("e1", "g1", page1.nextPageable());
        assertEquals(2, page2.getContent().size());
        assertTrue(page2.hasNext());

        final Page<RequeueRequest> page3 = requeueService.findExpiredSourceRequeueRequestsToProcess("e1", "g1", page2.nextPageable());
        assertEquals(1, page3.getContent().size());
        assertFalse(page3.hasNext());
    }

    @Test
    public void cleanProcessedRecords() {
        requeuRecords(
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r11"),
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r12"),
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r13"),
                new RequeueRequest().setEntityDefinitionId("e2").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r21"),
                new RequeueRequest().setEntityDefinitionId("e2").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r22"),
                new RequeueRequest().setEntityDefinitionId("e3").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r31"),
                new RequeueRequest().setEntityDefinitionId("e3").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r32")
        );
        //partial cleanup
        requeueService.cleanupProcessedRecords(List.of(
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRecordId("r11"),
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRecordId("r12")
                )
        );
        Page<RequeueRequest> requeueRequests = requeueService.findRequeueRequests("e1", "g1", Pageable.unpaged());
        assertEquals(1, requeueRequests.getContent().size());
        assertEquals("r13", requeueRequests.getContent().get(0).getRecordId());

        //cleanup scoped to a graph, requests from another graph not deleted
        requeueService.cleanupProcessedRecords(List.of(
                new RequeueRequest().setEntityDefinitionId("e2").setGraphId("g2").setRecordId("r21"),
                new RequeueRequest().setEntityDefinitionId("e2").setGraphId("g2").setRecordId("r22")
                )
        );
        Page<RequeueRequest> requeueRequests2 = requeueService.findRequeueRequests("e2", "g1", Pageable.unpaged());
        assertEquals(2, requeueRequests2.getContent().size());
        assertEquals("r21", requeueRequests2.getContent().get(0).getRecordId());
        assertEquals("r22", requeueRequests2.getContent().get(1).getRecordId());

        //cleanup all records
        requeueService.cleanupProcessedRecords(List.of(
                new RequeueRequest().setEntityDefinitionId("e3").setGraphId("g1").setRecordId("r32"),
                new RequeueRequest().setEntityDefinitionId("e3").setGraphId("g1").setRecordId("r31")
                )
        );
        Page<RequeueRequest> requeueRequests3 = requeueService.findRequeueRequests("e3", "g1", Pageable.unpaged());
        assertEquals(0, requeueRequests3.getContent().size());
    }

    @Test
    public void cleanupExpired(){
        requeuRecords(
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r11"),
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().minusDays(2)).setRecordId("r12"),
                new RequeueRequest().setEntityDefinitionId("e1").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(3)).setRecordId("r13"),
                new RequeueRequest().setEntityDefinitionId("e2").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r21"),
                new RequeueRequest().setEntityDefinitionId("e2").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("r22"),
                new RequeueRequest().setEntityDefinitionId("e3").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r31"),
                new RequeueRequest().setEntityDefinitionId("e3").setGraphId("g1").setRetryTimeLimit(ZonedDateTime.now().minusMinutes(2)).setRecordId("r32")
        );
        //partial cleanup
        requeueService.cleanupAndNotifyExpiredRequests("e1","g1");

        Page<RequeueRequest> requeueRequests = requeueService.findRequeueRequests("e1", "g1", Pageable.unpaged());
        assertEquals(1, requeueRequests.getContent().size());
        assertEquals("r13", requeueRequests.getContent().get(0).getRecordId());

        //cleanup scoped to a graph, requests from another graph not deleted
        requeueService.cleanupAndNotifyExpiredRequests("e2","g2");
        Page<RequeueRequest> requeueRequests2 = requeueService.findRequeueRequests("e2", "g1", Pageable.unpaged());
        assertEquals(2, requeueRequests2.getContent().size());
        assertEquals("r21", requeueRequests2.getContent().get(0).getRecordId());
        assertEquals("r22", requeueRequests2.getContent().get(1).getRecordId());

        //cleanup all records
        requeueService.cleanupAndNotifyExpiredRequests("e3","g1");
        Page<RequeueRequest> requeueRequests3 = requeueService.findRequeueRequests("e3", "g1", Pageable.unpaged());
        assertEquals(0, requeueRequests3.getContent().size());
    }

    private void requeuRecords(RequeueRequest... requests) {
        if(requests!=null){
            requeueService.requeue(Arrays.asList(requests));
        }
    }
}