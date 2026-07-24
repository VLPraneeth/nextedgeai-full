package com.syncari.connector.data.iterator;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.utils.Storage;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CSVStorageIteratorTest {

    @Test
    public void emptyBatch() {
        Storage mockStorage = mock(Storage.class);
        BatchJob job = new BatchJob();
        job.setStatus(BatchJobStatus.COMPLETED);
        job.setJobId("jobId1");
        when(mockStorage.read("job1")).thenReturn(null);
        SyncRequest request = new SyncRequest();
        request.setWatermark(new WatermarkInfo(System.currentTimeMillis(), System.currentTimeMillis(), false, 0));
        CSVStorageIterator csvStorageIterator = new CSVStorageIterator(mockStorage, job,  10, request, true);

        assertFalse(csvStorageIterator.hasNext());
    }

    @Test
    public void csvWithHeaderAndOneRecord() {
        Storage mockStorage = mock(Storage.class);
        BatchJob job = new BatchJob();
        job.setStatus(BatchJobStatus.COMPLETED);
        job.setJobId("jobId1");
        job.setDownloadedFielURLs(List.of("jobId1"));
        Instant instant = Instant.now().minusSeconds(5000);
        String oneRow = "name,id,lastMod\n" +
                "TestAccount, 123," + instant.toEpochMilli();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(oneRow.getBytes());
        when(mockStorage.read("jobId1")).thenReturn(byteArrayInputStream);
        SyncRequest request = new SyncRequest();
        request.setWatermark(new WatermarkInfo(instant.minusSeconds(1000).toEpochMilli(), System.currentTimeMillis(), false, 0));
        EntitySchema account = new EntitySchema("account");
        account.addField(new AttributeSchema("name", "string"));
        account.addField(new AttributeSchema("id", "string").setIdField(true));
        account.addField(new AttributeSchema("lastmod", "timestamp").setWatermarkField(true).setDisplayName("lastMod"));
        request.setEntitySchema(account);

        request.setConnector(new ConnectorInfo("connId", "TestcOnnector", "","instance1"));
        CSVStorageIterator csvStorageIterator = new CSVStorageIterator(mockStorage, job, 10, request, true);

        assertTrue(csvStorageIterator.hasNext());
        List<EntityData> page = csvStorageIterator.next();
        assertEquals(1, page.size());
        assertFalse(csvStorageIterator.hasNext());
        assertRecord(instant, csvStorageIterator, page);
    }

    @Test
    public void csvWithHeaderMultiplePages() {
        Storage mockStorage = mock(Storage.class);
        BatchJob job = new BatchJob();
        job.setStatus(BatchJobStatus.COMPLETED);
        job.setJobId("jobId1");
        job.setDownloadedFielURLs(List.of("jobId1"));
        Instant instant = Instant.now().minusSeconds(5000);
        String oneRow = "name,id,lastMod\n" +
                "TestAccount1, 1," + instant.plusSeconds(2).toEpochMilli() + "\n" +
                "TestAccount2, 2," + instant.plusSeconds(1).toEpochMilli() + "\n" +
                "TestAccount3, 5," + instant.plusSeconds(-2).toEpochMilli() + "\n" +
                "TestAccount4, 8," + instant.plusSeconds(3).toEpochMilli() + "\n" +
                "TestAccount5, 3," + instant.plusSeconds(-5).toEpochMilli() + "\n" +
                "TestAccount6, 115," + instant.toEpochMilli() + "\n" +
                ", 123," + instant.toEpochMilli() + "\n";
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(oneRow.getBytes());
        when(mockStorage.read("jobId1")).thenReturn(byteArrayInputStream);
        SyncRequest request = new SyncRequest();
        request.setWatermark(new WatermarkInfo(instant.minusSeconds(1000).toEpochMilli(), System.currentTimeMillis(), false, 0));
        EntitySchema account = new EntitySchema("account");
        account.addField(new AttributeSchema("name", "string"));
        account.addField(new AttributeSchema("id", "string").setIdField(true));
        account.addField(new AttributeSchema("lastmod", "timestamp").setWatermarkField(true).setDisplayName("lastMod"));
        request.setEntitySchema(account);

        request.setConnector(new ConnectorInfo("connId", "TestcOnnector", "","instance1"));
        CSVStorageIterator csvStorageIterator = new CSVStorageIterator(mockStorage, job, 2, request, true);

        assertTrue(csvStorageIterator.hasNext());
        List<EntityData> page = csvStorageIterator.next();
        assertEquals(2, page.size());
        assertRecord(csvStorageIterator, page.get(0), "connId", "account", "TestAccount1", "1", instant.plusSeconds(2).toEpochMilli(), instant.plusSeconds(2).toEpochMilli());
        assertRecord(csvStorageIterator, page.get(1), "connId", "account", "TestAccount2", "2", instant.plusSeconds(1).toEpochMilli(), instant.plusSeconds(2).toEpochMilli());

        assertTrue(csvStorageIterator.hasNext());
        page = csvStorageIterator.next();
        assertEquals(2, page.size());
        assertRecord(csvStorageIterator, page.get(0), "connId", "account", "TestAccount3", "5", instant.plusSeconds(-2).toEpochMilli(), instant.plusSeconds(3).toEpochMilli());
        assertRecord(csvStorageIterator, page.get(1), "connId", "account", "TestAccount4", "8", instant.plusSeconds(3).toEpochMilli(), instant.plusSeconds(3).toEpochMilli());

        assertTrue(csvStorageIterator.hasNext());
        page = csvStorageIterator.next();
        assertEquals(2, page.size());
        assertRecord(csvStorageIterator, page.get(0), "connId", "account", "TestAccount5", "3", instant.plusSeconds(-5).toEpochMilli(), instant.plusSeconds(3).toEpochMilli());
        assertRecord(csvStorageIterator, page.get(1), "connId", "account", "TestAccount6", "115", instant.toEpochMilli(), instant.plusSeconds(3).toEpochMilli());

        assertTrue(csvStorageIterator.hasNext());
        page = csvStorageIterator.next();
        assertEquals(1, page.size());
        assertRecord(csvStorageIterator, page.get(0), "connId", "account", "", "123", instant.toEpochMilli(), instant.plusSeconds(3).toEpochMilli());

        assertFalse(csvStorageIterator.hasNext());
    }

    private void assertRecord(Instant instant, CSVStorageIterator csvStorageIterator, List<EntityData> page) {
        assertEquals("connId", page.get(0).getConnectorId());
        assertEquals("account", page.get(0).getName());
        assertEquals("TestAccount", page.get(0).getValueAsString("name"));
        assertEquals("123", page.get(0).getId());
        assertEquals(instant.toEpochMilli(), page.get(0).getLastModified());
        assertEquals(instant.toEpochMilli(), csvStorageIterator.getLastWatermark());
    }

    private void assertRecord(CSVStorageIterator csvStorageIterator, EntityData record, String connId, String entityName, String name, String id, long lastModified, long latestWM) {
        assertEquals(connId, record.getConnectorId());
        assertEquals(entityName, record.getName());
        assertEquals(name, record.getValueAsString("name"));
        assertEquals(id, record.getId());
        assertEquals(lastModified, record.getLastModified());
        assertEquals(latestWM, csvStorageIterator.getLastWatermark());
    }

}