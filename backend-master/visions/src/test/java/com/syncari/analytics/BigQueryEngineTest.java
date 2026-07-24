package com.syncari.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.syncari.analytics.repositories.QueryCacheRepo;
import com.syncari.analytics.service.data.Direction;
import com.syncari.analytics.service.data.MetricOverTime;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.misc.PageRequest;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.misc.SyncLog;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;

@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@DirtiesContext
public class BigQueryEngineTest extends AbstractSyncariTest {
    @Autowired
    BigQueryEngine engine;
    @Autowired
    QueryCacheRepo queryCacheRepo;
    @Autowired
    EventStore eventStore;

    @Before
    public void setUp() {
        super.setUp();
        queryCacheRepo.deleteAll();
    }

    @Ignore
    @Test
    public void getSyncThroughputWithinRangeByDay() throws ParseException {
        try {
            eventStore.provision(SyncariContext.getSyncariId());
            throughputDayInsert();
            List<MetricOverTime> result = engine.getSyncThroughput(new PageRequest(0, 10),
                    Instant.now().minus(6, ChronoUnit.DAYS), Instant.now(), null, null);
            assertEquals(12, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS), null, null);
            assertEquals(0, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS), Direction.inbound, null);
            assertEquals(0, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS), Direction.outbound, null);

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS), Direction.inbound, "Zendesk1");
            assertEquals(0, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS), Direction.outbound, "Zendesk1");
            assertEquals(0, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(6, ChronoUnit.DAYS),
                    Instant.now(), Direction.inbound, null);
            assertEquals(4, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(6, ChronoUnit.DAYS),
                    Instant.now(), Direction.outbound, null);
            assertEquals(4, result.size());
        } finally {
            eventStore.deprovision(SyncariContext.getSyncariId());
        }
    }

    @Ignore
    @Test
    public void getSyncThroughputWithinRangeByHour() throws ParseException {
        try {
            eventStore.provision(SyncariContext.getSyncariId());
            Instant baseInsertInstant = throughputHourInsert();
            List<MetricOverTime> result = engine.getSyncThroughput(new PageRequest(0, 10), baseInsertInstant, baseInsertInstant,
                    null, null);
            assertEquals(9, result.size());
            List<String> connectors = result.stream().map(r -> r.getConnectorName()).collect(Collectors.toList());
            assertTrue(connectors.contains("Syncari"));
            assertTrue(connectors.contains("Zendesk1"));
            assertTrue(connectors.contains("Mysfdc"));
            List<Long> counts = result.stream().map(r -> r.getCount()).collect(Collectors.toList());
            assertTrue(counts.contains(150L));
            assertTrue(counts.contains(105L));
            assertTrue(counts.contains(185L));

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS), null, null);
            assertEquals(0, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now(), Instant.now(), Direction.outbound,
                    null);
            assertEquals(2, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now(), Instant.now(), Direction.inbound,
                    null);
            assertEquals(4, result.size());

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now(), Instant.now(), null, "Zendesk1");
            assertEquals(6, result.size());
            connectors = result.stream().map(r -> r.getConnectorName()).collect(Collectors.toList());
            assertTrue(connectors.contains("Syncari"));
            assertTrue(connectors.contains("Zendesk1"));
            counts = result.stream().map(r -> r.getCount()).collect(Collectors.toList());
            assertTrue(counts.contains(105L));
            assertTrue(counts.contains(150L));

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now(), Instant.now(), null, "Mysfdc");
            assertEquals(6, result.size());
            connectors = result.stream().map(r -> r.getConnectorName()).collect(Collectors.toList());
            assertTrue(connectors.contains("Syncari"));
            assertTrue(connectors.contains("Mysfdc"));
            counts = result.stream().map(r -> r.getCount()).collect(Collectors.toList());
            assertTrue(counts.contains(105L));
            assertTrue(counts.contains(185L));

            result = engine.getSyncThroughput(new PageRequest(0, 10), Instant.now(), Instant.now(), null, "Syncari");
            assertEquals(3, result.size());
        } finally {
            eventStore.deprovision(SyncariContext.getSyncariId());
        }
    }

    @Ignore
    @Test
    public void getSyncLatencyWithinRangeByDay() throws ParseException {
        try {
            eventStore.provision(SyncariContext.getSyncariId());
            throughputDayInsert();
            List<MetricOverTime> result = engine.getSyncLatency(new PageRequest(0, 10),
                    Instant.now().minus(6, ChronoUnit.DAYS), Instant.now());
            assertEquals(12, result.size());

            result = engine.getSyncLatency(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS));
            assertEquals(0, result.size());

        } finally {
            eventStore.deprovision(SyncariContext.getSyncariId());
        }
    }

    @Test
    public void getSyncErrors() {
        try {
            eventStore.provision(SyncariContext.getSyncariId());
            syncErrorsInsertFilterableData();

            Instant fromDate = Instant.now().minus(10, ChronoUnit.DAYS);
            Instant toDate = Instant.now().plus(2, ChronoUnit.DAYS);

            PageCursor pageCursor = new PageCursor(0, 40);

            Page<SyncError> result = engine.getSyncErrors(pageCursor, fromDate, toDate, null,
                    "merge", null, null);
            assertEquals(3L, result.getRecords().size());
            long count = engine.syncErrorCountByRange(fromDate, toDate, null,
            		"merge", null, null);
            assertEquals(3L, count);

            result = engine.getSyncErrors(pageCursor, fromDate, toDate, null,
                    null, null, "S1234");
            // total 15 records, 1 per page, we should have 2 left
            assertEquals(1L, result.getRecords().size());
            count = engine.syncErrorCountByRange(fromDate, toDate, null,
                    null, null, "S1234");
            assertEquals(1L, count);

            result = engine.getSyncErrors(pageCursor, fromDate, toDate, null,
                    null, null, null);
            assertEquals(11L, result.getRecords().size());
            count = engine.syncErrorCountByRange(fromDate, toDate, null,
                    null, null, null);
            assertEquals(11L, count);

            /********************************************************************************
             *
             * Cannot properly reset the "GCP Streaming table" so we'll merge these tests.
             * Just means more paging data for the paging test below
             *
             ********************************************************************************/

            syncErrorsInsert();

            int pageSize = 10;

            result = engine.getSyncErrors(new PageCursor(0, pageSize),
                    Instant.now().minus(10, ChronoUnit.DAYS), Instant.now(), null,
                    null, null, null);
            assertEquals(10L, result.getRecords().size());

            result = engine.getSyncErrors(new PageCursor(1, pageSize),
                    Instant.now().minus(10, ChronoUnit.DAYS), Instant.now(), null,
                    null, null, null);
            assertEquals(10L, result.getRecords().size());

            result = engine.getSyncErrors(new PageCursor(2, pageSize),
                    Instant.now().minus(10, ChronoUnit.DAYS), Instant.now(), null,
                    null, null, null);
            assertEquals(6L, result.getRecords().size());

        } finally {
            eventStore.deprovision(SyncariContext.getSyncariId());
        }
    }

    @Ignore
    @Test
    public void getSyncLatencyWithinRangeByHour() throws ParseException {
        try {
            eventStore.provision(SyncariContext.getSyncariId());
            Instant baseInsertInstant = syncLatencyHourInsert();
            // Query by same day return 2 connectors
            List<MetricOverTime> result = engine.getSyncLatency(new PageRequest(0, 10), baseInsertInstant, baseInsertInstant);
            assertEquals(2, result.size());
            List<String> connectors = result.stream().map(r -> r.getConnectorName()).collect(Collectors.toList());
            assertTrue(connectors.contains("Source"));
            assertTrue(connectors.contains("Sink"));
            List<Long> counts = result.stream().map(r -> r.getCount()).collect(Collectors.toList());
            assertTrue(counts.contains(6L));
            assertTrue(counts.contains(11L));

            // Query by range which has no data returns empty
            result = engine.getSyncLatency(new PageRequest(0, 10), Instant.now().minus(16, ChronoUnit.DAYS),
                    Instant.now().minus(10, ChronoUnit.DAYS));
            assertEquals(0, result.size());
        } finally {
            eventStore.deprovision(SyncariContext.getSyncariId());
        }
    }
    
    @Test
    public void getMaxPageNumber(){
        int maxPage = engine.getMaxPageNumber(100L, 5);
        assertEquals(20, maxPage);
        maxPage = engine.getMaxPageNumber(0L, 5);
        assertEquals(0, maxPage);
    }

    private void throughputDayInsert() {
        List<SyncLog> logs = new ArrayList<>();
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(20).latency(5)
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(30).latency(2)
                .occuredTime(Instant.now().minus(4, ChronoUnit.DAYS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(20).latency(3)
                .occuredTime(Instant.now().minus(3, ChronoUnit.DAYS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(25).latency(5)
                .occuredTime(Instant.now().minus(2, ChronoUnit.DAYS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(45).latency(3)
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(40).latency(4)
                .occuredTime(Instant.now().minus(4, ChronoUnit.DAYS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(15).latency(5)
                .occuredTime(Instant.now().minus(3, ChronoUnit.DAYS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(20).latency(2)
                .occuredTime(Instant.now().minus(2, ChronoUnit.DAYS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(55).latency(1)
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(50).latency(2)
                .occuredTime(Instant.now().minus(4, ChronoUnit.DAYS)).build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(35).latency(1)
                .occuredTime(Instant.now().minus(3, ChronoUnit.DAYS)).build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(40).latency(2)
                .occuredTime(Instant.now().minus(2, ChronoUnit.DAYS)).build());
        eventStore.insertSyncLogs(logs);
    }

    private Instant throughputHourInsert() {
        List<SyncLog> logs = new ArrayList<>();
        Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(120).latency(5)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(30).latency(2)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(20).latency(3)
                .occuredTime(now.plus(3, ChronoUnit.HOURS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(25).latency(5)
                .occuredTime(now.plus(2, ChronoUnit.HOURS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(145).latency(3)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(40).latency(4)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(15).latency(5)
                .occuredTime(now.plus(3, ChronoUnit.HOURS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Mysfdc").recordCount(20).latency(2)
                .occuredTime(now.plus(2, ChronoUnit.HOURS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(55).latency(1)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(50).latency(2)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(35).latency(1)
                .occuredTime(now.plus(3, ChronoUnit.HOURS)).build());
        logs.add(SyncLog.builder().connectorName("Syncari").recordCount(40).latency(2)
                .occuredTime(now.plus(2, ChronoUnit.HOURS)).build());
        eventStore.insertSyncLogs(logs);
        return now;

    }

    private Instant syncLatencyHourInsert() {
        List<SyncLog> logs = new ArrayList<>();
        Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(20).latency(5)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(20).latency(6)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Zendesk1").recordCount(20).latency(7)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("outbound").build());
        logs.add(SyncLog.builder().connectorName("Sfdc").recordCount(20).latency(12)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Sfdc").recordCount(20).latency(11)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("inbound").build());
        logs.add(SyncLog.builder().connectorName("Sfdc").recordCount(20).latency(10)
                .occuredTime(now.plus(5, ChronoUnit.HOURS)).direction("inbound").build());
        eventStore.insertSyncLogs(logs);
        return now;
    }

    private void syncErrorsInsert() {
        List<SyncError> logs = new ArrayList<>();
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #2")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #3")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #4")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #5")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #6")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #7")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #8")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #9")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #10")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #11")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #12")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #13")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #14")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());
        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #15")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS)).build());

        eventStore.insertErrorLogs(logs);
    }

    private void syncErrorsInsertFilterableData() {
        List<SyncError> logs = new ArrayList<>();

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS))
                .operation("create")
                .externalEntityName("Entity1")
                .syncariRecordId("S1234")
                .connectorName("Impact")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(4, ChronoUnit.DAYS))
                .operation("create")
                .externalEntityName("Entity1")
                .syncariRecordId("S1235")
                .connectorName("Impact1")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(3, ChronoUnit.DAYS))
                .operation("update")
                .externalEntityName("Entity1")
                .syncariRecordId("S1236")
                .connectorName("Impact2")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(2, ChronoUnit.DAYS))
                .operation("update")
                .externalEntityName("Entity1")
                .syncariRecordId("S1237")
                .connectorName("Zendesk1")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .operation("delete")
                .externalEntityName("Entity1")
                .syncariRecordId("S1238")
                .connectorName("Zendesk2")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS))
                .operation("delete")
                .externalEntityName("Entity1")
                .syncariRecordId("S1239")
                .connectorName("Salesforce1")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(4, ChronoUnit.DAYS))
                .operation("merge")
                .externalEntityName("Entity1")
                .syncariRecordId("S1240")
                .connectorName("Salesforce2")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(3, ChronoUnit.DAYS))
                .operation("merge")
                .externalEntityName("Entity1")
                .syncariRecordId("S1241")
                .connectorName("Impact1")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(2, ChronoUnit.DAYS))
                .operation("convert")
                .externalEntityName("Entity1")
                .syncariRecordId("S12342")
                .connectorName("Impact2")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .operation("merge")
                .externalEntityName("Entity2")
                .syncariRecordId("S12343")
                .connectorName("Zendesk1")
                .build());

        logs.add(SyncError.builder().connectorName("Zendesk1").errorDetails("Invalid data #1")
                .occuredTime(Instant.now().minus(5, ChronoUnit.DAYS))
                .operation("create")
                .externalEntityName("Entity2")
                .syncariRecordId("S1255")
                .connectorName("Zendesk2")
                .build());

        eventStore.insertErrorLogs(logs);
    }

}
