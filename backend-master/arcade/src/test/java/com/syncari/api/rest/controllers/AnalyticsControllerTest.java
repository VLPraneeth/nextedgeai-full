package com.syncari.api.rest.controllers;

import com.google.cloud.bigquery.BigQuery;
import com.syncari.core.event.store.BigQueryEventStore;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.service.ErrorNotificationService;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.syncari.core.security.Permissions.ANALYTICS;
import static org.junit.Assert.*;

@Slf4j
public class AnalyticsControllerTest extends AbstractSyncariTest {
    @Autowired
    private AnalyticsController controller;
    @Autowired
    BigQueryEventStore eventStore;
    @Autowired
    ErrorNotificationService errorNotificationService;
    @Autowired
    BigQuery bigQuery;
    @Autowired
    BigQueryHelper helper;
    @Autowired
    DateUtil util;
    static int rows = 0;

    @Override
    public void setUp() {
        super.setUp();
        eventStore = new BigQueryEventStore();
        eventStore.setDateUtil(util);
        eventStore.setBigQuery(bigQuery);
        eventStore.setHelper(helper);
        eventStore.setNotificationService(errorNotificationService);
        if(rows == 0) {
            createRecords();
        }
    }

    @Override
    public void tearDown() {
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {ANALYTICS})
    public void downloadNoFilter() throws Exception {
        try {
            ResponseEntity<Resource> download = controller.download("", "", "",
                    "", "", "", "");
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Start and End date are required", e.getMessage());
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {ANALYTICS})
    public void downloadWithDate() throws Exception {
        ResponseEntity<Resource> download = controller.download("2024-01-23T08:00:00", "2024-01-28T08:00:00", "",
                    "", "", "", "");
        InputStream stream = download.getBody().getInputStream();
        List<List<String>> rows = new CsvUtils().getRows(stream, 10, new CSVOptions());
        assertEquals(6, rows.size());
        assertEquals(10,rows.get(0).size());
        assertTrue(rows.get(0).contains("contact"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {ANALYTICS})
    public void downloadWithOperation() throws Exception {
        ResponseEntity<Resource> download = controller.download("2024-01-23T08:00:00", "2024-01-28T08:00:00", "",
                "update", "", "", "");
        InputStream stream = download.getBody().getInputStream();
        List<List<String>> rows = new CsvUtils().getRows(stream, 10, new CSVOptions());
        assertEquals(3, rows.size());
        assertEquals(10,rows.get(0).size());
        assertTrue(rows.get(0).contains("contact"));
    }

    private void createRecords() {
        List<SyncError> logs = new ArrayList<>();
        logs.add(new SyncError().setSyncariEntityName("account").setErrorCode("404").setSyncariRecordId("123").setExternalRecordId("234").
                setOccuredTime(Instant.parse("2024-01-26T08:00:00.00Z")).setConnectorName("hubspot").setOperation("update"));
        logs.add(new SyncError().setSyncariEntityName("account").setErrorCode("500").setSyncariRecordId("123").
                setOccuredTime(Instant.parse("2024-01-26T08:00:00.00Z")).setConnectorName("hubspot").setOperation("create"));
        logs.add(new SyncError().setSyncariEntityName("contact").setErrorCode("404").setSyncariRecordId("444").setExternalRecordId("234").
                setOccuredTime(Instant.parse("2024-01-26T08:00:00.00Z")).setConnectorName("hubspot").setOperation("update"));
        logs.add(new SyncError().setSyncariEntityName("contact").setErrorCode("500").setSyncariRecordId("555").
                setOccuredTime(Instant.parse("2024-01-26T08:00:00.00Z")).setConnectorName("hubspot").setOperation("create"));
        logs.add(new SyncError().setSyncariEntityName("contact").setErrorCode("404").setSyncariRecordId("567").setExternalRecordId("234").
                setOccuredTime(Instant.parse("2024-01-26T08:00:00.00Z")).setConnectorName("salesforce").setOperation("update"));
        logs.add(new SyncError().setSyncariEntityName("contact").setErrorCode("500").setSyncariRecordId("888").
                setOccuredTime(Instant.parse("2024-01-26T08:00:00.00Z")).setConnectorName("salesforce").setOperation("create"));
        eventStore.insertErrorLogs(logs);
        rows = 6;
    }

}
