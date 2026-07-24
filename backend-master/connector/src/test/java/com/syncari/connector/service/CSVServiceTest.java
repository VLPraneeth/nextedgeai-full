package com.syncari.connector.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.utils.Storage;
import com.syncari.utils.file.File;
import com.syncari.utils.file.FileManager;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class CSVServiceTest {
    private static final String BASE_RESOURCE_PATH = "src/test/resources/csv/";

    @Test
    public void getCSVRecordsByWatermarkWithSkipLinesAndHeader() throws IOException {
        FileManager fm = mock(FileManager.class);
        Storage storage = mock(Storage.class);
        when(storage.read("movies.csv")).thenReturn(new FileInputStream(BASE_RESOURCE_PATH + "movies_extra_header_footer.csv"));
        when(storage.lastModified("movies.csv")).thenReturn(Instant.now().toEpochMilli());
        when(fm.list("", "")).thenReturn(List.of(new File().setName("movies").setDirectory(true)));
        when(fm.list("/movies", CSVService.DEFAULT_FILE_MATCH_PATTERN)).thenReturn(List.of(new File().setName("movies.csv").setDirectory(false)));
        when(fm.readFile("/movies/movies.csv")).thenReturn(new FileInputStream(BASE_RESOURCE_PATH + "movies_extra_header_footer.csv"));
        final CSVService csvService = new CSVService(fm, storage);
        final BatchJob job = new BatchJob();
        job.setDownloadedFielURLs(List.of("movies.csv"));
        final ConnectorInfo connector = new ConnectorInfo();
        connector.setMetaConfig(Map.of(CSVService.SKIP_LINES_PATTERN, "\\-\\-custom.*"));
        final EntitySchema movies = csvService.describe(new DescribeRequest(connector, "movies")).get();
        movies.getField("id").get().setIdField(true);
        final SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(movies)
                .setWatermark(new WatermarkInfo()
                        .setStart(Instant.now().minusSeconds(200000).toEpochMilli())
                        .setEnd(Instant.now().minusSeconds(100000).toEpochMilli())
                );
        final FetchResponse csvRecordsByWatermark = csvService.getCSVRecordsByWatermark(request, storage, job);
        final List<EntityData> records = csvRecordsByWatermark.getIterator().next();
        //11 lines, 2 skipped and one header
        assertEquals(8, records.size());

    }

    @Test
    public void getCSVRecordsByWatermarkWithHeaderAndSkipPatternButNoSkippedLines() throws IOException {
        FileManager fm = mock(FileManager.class);
        Storage storage = mock(Storage.class);
        final String filePath = BASE_RESOURCE_PATH + "movies.csv";
        when(storage.read("movies.csv")).thenReturn(new FileInputStream(filePath));
        when(storage.lastModified("movies.csv")).thenReturn(Instant.now().toEpochMilli());
        when(fm.list("", "")).thenReturn(List.of(new File().setName("movies").setDirectory(true)));
        when(fm.list("/movies", CSVService.DEFAULT_FILE_MATCH_PATTERN)).thenReturn(List.of(new File().setName("movies.csv").setDirectory(false)));
        when(fm.readFile("/movies/movies.csv")).thenReturn(new FileInputStream(filePath));
        final CSVService csvService = new CSVService(fm, storage);
        final BatchJob job = new BatchJob();
        job.setDownloadedFielURLs(List.of("movies.csv"));
        final ConnectorInfo connector = new ConnectorInfo();
        connector.setMetaConfig(Map.of(
                CSVService.SKIP_LINES_PATTERN, "\\-\\-custom.*"));
        final EntitySchema movies = csvService.describe(new DescribeRequest(connector, "movies")).get();
        movies.getField("id").get().setIdField(true);
        final SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(movies)
                .setWatermark(new WatermarkInfo()
                        .setStart(Instant.now().minusSeconds(200000).toEpochMilli())
                        .setEnd(Instant.now().minusSeconds(100000).toEpochMilli())
                );
        final FetchResponse csvRecordsByWatermark = csvService.getCSVRecordsByWatermark(request, storage, job);
        final List<EntityData> records = csvRecordsByWatermark.getIterator().next();
        //11 lines, 2 skipped and one header
        assertEquals(8, records.size());

    }

    @Test
    public void getCSVRecordsByWatermarkWithHeader() throws IOException {
        FileManager fm = mock(FileManager.class);
        Storage storage = mock(Storage.class);
        final String filePath = BASE_RESOURCE_PATH + "movies.csv";
        when(storage.read("movies.csv")).thenReturn(new FileInputStream(filePath));
        when(storage.lastModified("movies.csv")).thenReturn(Instant.now().toEpochMilli());
        when(fm.list("", "")).thenReturn(List.of(new File().setName("movies").setDirectory(true)));
        when(fm.list("/movies", CSVService.DEFAULT_FILE_MATCH_PATTERN)).thenReturn(List.of(new File().setName("movies.csv").setDirectory(false)));
        when(fm.readFile("/movies/movies.csv")).thenReturn(new FileInputStream(filePath));
        final CSVService csvService = new CSVService(fm, storage);
        final BatchJob job = new BatchJob();
        job.setDownloadedFielURLs(List.of("movies.csv"));
        final ConnectorInfo connector = new ConnectorInfo();

        final EntitySchema movies = csvService.describe(new DescribeRequest(connector, "movies")).get();
        movies.getField("id").get().setIdField(true);
        final SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(movies)
                .setWatermark(new WatermarkInfo()
                        .setStart(Instant.now().minusSeconds(200000).toEpochMilli())
                        .setEnd(Instant.now().minusSeconds(100000).toEpochMilli())
                );
        final FetchResponse csvRecordsByWatermark = csvService.getCSVRecordsByWatermark(request, storage, job);
        final List<EntityData> records = csvRecordsByWatermark.getIterator().next();
        //11 lines, 2 skipped and one header
        assertEquals(8, records.size());

    }

    @Test
    public void getCSVRecordsByWatermarkWithNoHeader() throws IOException {
        FileManager fm = mock(FileManager.class);
        Storage storage = mock(Storage.class);
        final String filePath = BASE_RESOURCE_PATH + "movies_no_header.csv";
        when(storage.read("movies.csv")).thenReturn(new FileInputStream(filePath));
        when(storage.lastModified("movies.csv")).thenReturn(Instant.now().toEpochMilli());
        when(fm.list("", "")).thenReturn(List.of(new File().setName("movies").setDirectory(true)));
        when(fm.list("/movies", CSVService.DEFAULT_FILE_MATCH_PATTERN)).thenReturn(List.of(new File().setName("movies.csv").setDirectory(false)));
        when(fm.readFile("/movies/movies.csv")).thenReturn(new FileInputStream(filePath));
        final CSVService csvService = new CSVService(fm, storage);
        final BatchJob job = new BatchJob();
        job.setDownloadedFielURLs(List.of("movies.csv"));
        final ConnectorInfo connector = new ConnectorInfo();
        connector.setMetaConfig(Map.of("hasHeader", "false"));
        final EntitySchema movies = csvService.describe(new DescribeRequest(connector, "movies")).get();
        movies.getField("field1").get().setIdField(true);
        final SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(movies)
                .setWatermark(new WatermarkInfo()
                        .setStart(Instant.now().minusSeconds(200000).toEpochMilli())
                        .setEnd(Instant.now().minusSeconds(100000).toEpochMilli())
                );
        final FetchResponse csvRecordsByWatermark = csvService.getCSVRecordsByWatermark(request, storage, job);
        final List<EntityData> records = csvRecordsByWatermark.getIterator().next();
        assertEquals(8, records.size());
    }

    @Test
    public void getCSVRecordsByWatermarkWithNoHeaderExtraLines() throws IOException {
        FileManager fm = mock(FileManager.class);
        Storage storage = mock(Storage.class);
        final String filePath = BASE_RESOURCE_PATH + "movies_no_header_extra_lines.csv";
        when(storage.read("movies.csv")).thenReturn(new FileInputStream(filePath));
        when(storage.lastModified("movies.csv")).thenReturn(Instant.now().toEpochMilli());
        when(fm.list("", "")).thenReturn(List.of(new File().setName("movies").setDirectory(true)));
        when(fm.list("/movies", CSVService.DEFAULT_FILE_MATCH_PATTERN)).thenReturn(List.of(new File().setName("movies.csv").setDirectory(false)));
        when(fm.readFile("/movies/movies.csv")).thenReturn(new FileInputStream(filePath));
        final CSVService csvService = new CSVService(fm, storage);
        final BatchJob job = new BatchJob();
        job.setDownloadedFielURLs(List.of("movies.csv"));
        final ConnectorInfo connector = new ConnectorInfo();
        connector.setMetaConfig(Map.of("hasHeader", "false",
                CSVService.SKIP_LINES_PATTERN, "\\-\\-custom.*"));

        final EntitySchema movies = csvService.describe(new DescribeRequest(connector, "movies")).get();
        movies.getField("field1").get().setIdField(true);
        final SyncRequest request = new SyncRequest().setConnector(connector)
                .setEntitySchema(movies)
                .setWatermark(new WatermarkInfo()
                        .setStart(Instant.now().minusSeconds(200000).toEpochMilli())
                        .setEnd(Instant.now().minusSeconds(100000).toEpochMilli())
                );
        final FetchResponse csvRecordsByWatermark = csvService.getCSVRecordsByWatermark(request, storage, job);
        final List<EntityData> records = csvRecordsByWatermark.getIterator().next();
        assertEquals(8, records.size());
    }

    @Test
    public void testFilesSortedByLastModifiedDescending() {
        // Create File objects with different lastModified dates
        File oldFile = new File()
                .setName("old_file.csv")
                .setDirectory(false)
                .setLastModified(Instant.now().minusSeconds(3600).toEpochMilli()); // 1 hour ago

        File newestFile = new File()
                .setName("newest_file.csv")
                .setDirectory(false)
                .setLastModified(Instant.now().toEpochMilli()); // now

        File middleFile = new File()
                .setName("middle_file.csv")
                .setDirectory(false)
                .setLastModified(Instant.now().minusSeconds(1800).toEpochMilli()); // 30 min ago

        // Create list in wrong order (oldest first, lexically "middle" would be between)
        List<File> files = new ArrayList<>();
        files.add(oldFile);
        files.add(middleFile);
        files.add(newestFile);

        // Sort by lastModified descending (same logic as in getAttributes)
        files.sort((a, b) -> {
            boolean aValid = a.getLastModified() > 0;
            boolean bValid = b.getLastModified() > 0;
            if (!aValid && !bValid) return 0;
            if (!aValid) return 1;
            if (!bValid) return -1;
            return Long.compare(b.getLastModified(), a.getLastModified());
        });

        // Verify newest file is first
        assertEquals("newest_file.csv", files.get(0).getName());
        assertEquals("middle_file.csv", files.get(1).getName());
        assertEquals("old_file.csv", files.get(2).getName());
    }

    @Test
    public void testFilesSortedWithZeroLastModified() {
        // Create File objects - some with zero/invalid lastModified
        File validFile = new File()
                .setName("valid_file.csv")
                .setDirectory(false)
                .setLastModified(Instant.now().toEpochMilli());

        File zeroFile = new File()
                .setName("zero_file.csv")
                .setDirectory(false)
                .setLastModified(0); // not set

        File olderFile = new File()
                .setName("older_file.csv")
                .setDirectory(false)
                .setLastModified(Instant.now().minusSeconds(3600).toEpochMilli());

        List<File> files = new ArrayList<>();
        files.add(zeroFile);
        files.add(olderFile);
        files.add(validFile);

        // Sort with invalid-value-safe comparator
        files.sort((a, b) -> {
            boolean aValid = a.getLastModified() > 0;
            boolean bValid = b.getLastModified() > 0;
            if (!aValid && !bValid) return 0;
            if (!aValid) return 1;
            if (!bValid) return -1;
            return Long.compare(b.getLastModified(), a.getLastModified());
        });

        // Valid files sorted by date descending, invalid files at the end
        assertEquals("valid_file.csv", files.get(0).getName());
        assertEquals("older_file.csv", files.get(1).getName());
        assertEquals("zero_file.csv", files.get(2).getName());
    }

    @Test
    public void testDescribeUsesNewestFile() throws IOException {
        // This test verifies that describe() uses the NEWEST file (by lastModified)
        // to determine schema columns. If sorting is removed, this test will fail.

        FileManager fm = mock(FileManager.class);
        Storage storage = mock(Storage.class);

        // Create files with different lastModified times
        // old_schema.csv has 3 columns: id, name, email
        // new_schema.csv has 5 columns: id, name, email, phone, address
        File oldFile = new File()
                .setName("old_schema.csv")
                .setDirectory(false)
                .setLastModified(Instant.now().minusSeconds(3600).toEpochMilli()); // 1 hour ago

        File newFile = new File()
                .setName("new_schema.csv")
                .setDirectory(false)
                .setLastModified(Instant.now().toEpochMilli()); // now (newest)

        // Mock list to return files in WRONG order (oldest first)
        // If sorting works correctly, new_schema.csv should be used
        // If sorting is removed, old_schema.csv would be used (WRONG)
        when(fm.list("", "")).thenReturn(List.of(new File().setName("testentity").setDirectory(true)));
        when(fm.list("/testentity", CSVService.DEFAULT_FILE_MATCH_PATTERN))
                .thenReturn(List.of(oldFile, newFile)); // oldest first - wrong order

        // Mock readFile to return different content based on file
        // Both files are mocked so we can verify which one was actually used
        when(fm.readFile("/testentity/new_schema.csv"))
                .thenReturn(new FileInputStream(BASE_RESOURCE_PATH + "new_schema.csv"));
        when(fm.readFile("/testentity/old_schema.csv"))
                .thenReturn(new FileInputStream(BASE_RESOURCE_PATH + "old_schema.csv"));

        final CSVService csvService = new CSVService(fm, storage);
        final ConnectorInfo connector = new ConnectorInfo();

        // Call describe - it should use the NEWEST file (new_schema.csv)
        final EntitySchema schema = csvService.describe(new DescribeRequest(connector, "testentity")).get();

        // Verify readFile was called with the NEWEST file (new_schema.csv)
        // If sorting is removed, it would be called with old_schema.csv instead
        verify(fm).readFile("/testentity/new_schema.csv");
        verify(fm, never()).readFile("/testentity/old_schema.csv");

        // new_schema.csv has 5 columns + lastModifiedTime + 4 meta fields = 10 total
        // old_schema.csv has 3 columns + lastModifiedTime + 4 meta fields = 8 total
        // If sorting is removed, we'd get 8 columns instead of 10
        assertEquals("Schema should have columns from newest file (new_schema.csv)",
                10, schema.getAttributes().size());

        // Verify the extra columns from new_schema.csv are present
        assertTrue("Should have 'phone' column from newest file",
                schema.hasField("phone"));
        assertTrue("Should have 'address' column from newest file",
                schema.hasField("address"));
    }
}