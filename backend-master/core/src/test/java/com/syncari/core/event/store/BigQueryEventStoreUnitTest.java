package com.syncari.core.event.store;

import com.google.cloud.bigquery.*;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.Instance;
import com.syncari.core.service.EmailService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BigQueryEventStoreUnitTest extends AbstractSyncariTest {
    @Autowired
    BigQueryHelper helper;

    @Test
    public void testInsertFailures(){
        BigQueryEventStore bigQueryEventStore = new BigQueryEventStore();
        BigQuery mockBQ = mock(BigQuery.class);
        bigQueryEventStore.bigQuery = mockBQ;
        BigQueryHelper bigQueryHelper = new BigQueryHelper();
        bigQueryHelper.bigQuery = mockBQ;
        List<InsertAllRequest.RowToInsert> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(InsertAllRequest.RowToInsert.of(Map.of("key", "v" + i)));
        }
        AtomicInteger counter = new AtomicInteger();
        when(mockBQ.insertAll(any())).thenAnswer(args -> {
            InsertAllRequest request = args.getArgument(0);
            if (request.getRows().size() > 75) {
                throw new BigQueryException(400, "Request payload size exceeds the limit: 10243040 bytes");
            } else {
                counter.addAndGet(request.getRows().size());
                return mock(InsertAllResponse.class);
            }
        });
        bigQueryHelper.insertRows(TableId.of("dummy", "dataset"), rows);
        assertEquals(1000, counter.get());
    }

    @Test
    public void testInsertFailuresDuetoNewColumn(){

        boolean syncariInstanceIsNull = false;
        try {
            // Some dummy
            if (SyncariContext.getInstance() == null) {
                syncariInstanceIsNull = true;
                Instance instance = new Instance("test_org_instance", "test_org_instance");
                SyncariContext.setInstance(instance);
            }
            String syncariId = SyncariContext.getSyncariId();


            BigQueryEventStore bigQueryEventStore = new BigQueryEventStore();
            BigQueryHelper bigQueryHelper = new BigQueryHelper();
            BigQuery mockBQ = mock(BigQuery.class);
            bigQueryEventStore.bigQuery = mockBQ;

            bigQueryHelper.bigQuery = mockBQ;

            AppConfig mckAppConfig = mock(AppConfig.class);
            bigQueryEventStore.appConfig = mckAppConfig;
            bigQueryEventStore.appConfig.setErrorEmail(List.of("dev@syncari.com"));

            EmailService mckEmailService = mock(EmailService.class);
            bigQueryEventStore.emailService = mckEmailService;
            doNothing().when(mckEmailService).sendErrorEmail(any(), any(), any(), any());

            List<InsertAllRequest.RowToInsert> rows = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rows.add(InsertAllRequest.RowToInsert.of(Map.of("key", "v" + i)));
            }

            Map<Long, List<BigQueryError>> errors = new HashMap<>();
            errors.put(123L, List.of(new BigQueryError("invalid", "entityId", "no such field: entityId")));

            InsertAllResponse mckResponse = mock(InsertAllResponse.class);
            when(mckResponse.hasErrors()).thenReturn(true);
            when(mckResponse.getInsertErrors()).thenReturn(errors);

            when(mockBQ.insertAll(any())).thenReturn(mckResponse);

            bigQueryHelper.insertRows(TableId.of(syncariId, "dataset"), rows);
            // This basically just ensure it does not go on a infinite loop. Will retry once and skip.
            verify(mockBQ, times(1)).getTable(TableId.of(syncariId, "dataset"));
        } finally {
            if (syncariInstanceIsNull) {
                SyncariContext.setInstance(null);
            }
        }
    }

    @Test
    public void testErrorBackoff() {
        BigQueryEventStore bigQueryEventStore = new BigQueryEventStore();
        BigQueryHelper bigQueryHelper = new BigQueryHelper();
        BigQuery mockBQ = mock(BigQuery.class);
        bigQueryEventStore.bigQuery = mockBQ;
        bigQueryHelper.bigQuery = mockBQ;
        bigQueryEventStore.helper = bigQueryHelper;
        List<InsertAllRequest.RowToInsert> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(InsertAllRequest.RowToInsert.of(Map.of("key", "v" + i)));
        }
        AtomicInteger counter = new AtomicInteger();
        when(mockBQ.insertAll(any())).thenAnswer(args -> {
            throw new BigQueryException(500, "An internal error occurred and the request could not be completed. This is usually caused by a transient issue. Retrying the job with back-off as " +
                    "described in the BigQuery SLA should solve the problem");
        });

        try {
            bigQueryHelper.insertRows(TableId.of("dummy", "dataset"), rows);
            fail();
        } catch (Exception e) {
        }
        verify(mockBQ, times(5)).insertAll(any());
    }

}