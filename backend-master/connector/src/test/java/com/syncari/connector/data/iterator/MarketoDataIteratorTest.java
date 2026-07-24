package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.MarketoEntityPage;
import com.syncari.connector.data.WatermarkInfo;
import org.jooq.lambda.function.Function2;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class MarketoDataIteratorTest {

    @Test
    public void getNext() {

        long startTime = Instant.now().toEpochMilli();
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorWithNext();

        WatermarkInfo watermark = new WatermarkInfo(startTime, Instant.now().toEpochMilli(), false, 0);
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        assertTrue(iterator.hasNext());
        var entities = iterator.next();
        assertEquals(2, entities.size());
        assertTrue(iterator.hasNext());
    }

    @Test
    public void getNext_SingleIteration() {

        long startTime = Instant.now().toEpochMilli();
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorWithoutNext();

        WatermarkInfo watermark = new WatermarkInfo(startTime, Instant.now().toEpochMilli(), false, 0);
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        assertTrue(iterator.hasNext());
        //hasNext should return true until next() is called
        assertTrue(iterator.hasNext());
        var entities = iterator.next();
        assertEquals(2, entities.size());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void getNext_NoData() {

        long startTime = Instant.now().toEpochMilli();
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorNoData();

        WatermarkInfo watermark = new WatermarkInfo(startTime, Instant.now().toEpochMilli(), false, 0);
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        assertFalse(iterator.hasNext());
        assertTrue(iterator.data.isEmpty());
    }

    @Test
    public void getNext_FilterRecordsOutsideWMEndDate() throws InterruptedException {

        long startTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        long endTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        WatermarkInfo watermark = new WatermarkInfo(startTime, endTime, false, 0);

        //generate data after watermark end time
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorWithNext();
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        assertFalse(iterator.hasNext());
        // data is filtered after endDate in watermark
        assertTrue(iterator.data.isEmpty());
    }

    @Test
    public void getNext_FilterRecordsCreatedAtWithinWM() throws InterruptedException {

        long startTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        long endTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        WatermarkInfo watermark = new WatermarkInfo(startTime, endTime, false, 0);

        //generate data with createdAt within WM but lastModified outside wm
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorWithCreatedAtAndWithoutNext(startTime + 100l, endTime + 100);
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        // data is not filtered out as createdAT is within watermark
        assertTrue(iterator.hasNext());
        var data = iterator.next();
        assertFalse(data.isEmpty());
        assertEquals(1, data.size());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void getNext_FilterRecordsCreatedAtOnWMBoundaries() throws InterruptedException {

        long startTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        long endTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        WatermarkInfo watermark = new WatermarkInfo(startTime, endTime, false, 0);

        //generate data with createdAt is wm start date but lastModified outside wm
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorWithCreatedAtAndWithoutNext(startTime, endTime + 100);
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        // data is filtered out as createdAt is equal to wm start
        assertFalse(iterator.hasNext());
        assertTrue(iterator.data.isEmpty());

        //generate data with createdAt within WM but lastModified outside wm
        generator = getGeneratorWithCreatedAtAndWithoutNext(endTime, endTime + 100);
        iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        // data is not filtered out as createdAT is at wm endTime
        assertTrue(iterator.hasNext());
        var data = iterator.next();
        assertFalse(data.isEmpty());
        assertEquals(1, data.size());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void getNext_NoFilteringWithoutEndDate() throws InterruptedException {

        long startTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        long endTime = -1l;
        WatermarkInfo watermark = new WatermarkInfo(startTime, endTime, true, 0);

        //generate data after watermark end time
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorWithNext();
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        // data is not filtered even after end date as its initial sync cycle
        assertTrue(iterator.hasNext());
        var entities = iterator.next();
        assertEquals(2, entities.size());
    }

    @Test
    public void getNext_NoDataWithNextPage() throws InterruptedException {

        long startTime = Instant.now().toEpochMilli();
        Thread.sleep(1000);
        long endTime = -1l;
        WatermarkInfo watermark = new WatermarkInfo(startTime, endTime, true, 0);

        //generate data after watermark end time
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = getGeneratorNoDataWithNext();
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken("page_token");

        // data is not filtered even after end date as its initial sync cycle
        assertTrue(iterator.hasNext());
        var entities = iterator.next();
        assertTrue(entities.isEmpty());
        assertEquals("next_page", iterator.pageToken);
    }

    private Function2<WatermarkInfo, String, MarketoEntityPage> getGeneratorWithNext(){
        MarketoEntityPage entityPage = new MarketoEntityPage();
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData("test_data1").setLastModified(Instant.now().toEpochMilli()));
        entities.add(new EntityData("test_data2").setLastModified(Instant.now().toEpochMilli()));
        entityPage.setData(entities);
        entityPage.setHasMore(true);
        entityPage.setNextPage("next_page");

        return (wm, pageSize) -> entityPage;
    }

    private Function2<WatermarkInfo, String, MarketoEntityPage> getGeneratorWithoutNext(){
        MarketoEntityPage entityPage = new MarketoEntityPage();
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData("test_data1").setLastModified(Instant.now().toEpochMilli()));
        entities.add(new EntityData("test_data2").setLastModified(Instant.now().toEpochMilli()));
        entityPage.setData(entities);
        entityPage.setHasMore(false);
        entityPage.setNextPage(null);

        return (wm, pageSize) -> entityPage;
    }

    private Function2<WatermarkInfo, String, MarketoEntityPage> getGeneratorWithCreatedAtAndWithoutNext(long createdAt, long updatedAt){
        MarketoEntityPage entityPage = new MarketoEntityPage();
        List<EntityData> entities = new ArrayList<>();
        entities.add(new EntityData("test_data1").setCreatedAt(createdAt).setLastModified(updatedAt));
        entityPage.setData(entities);
        entityPage.setHasMore(false);
        entityPage.setNextPage(null);

        return (wm, pageSize) -> entityPage;
    }

    private Function2<WatermarkInfo, String, MarketoEntityPage> getGeneratorNoData(){
        MarketoEntityPage entityPage = new MarketoEntityPage();
        entityPage.setData(List.of());
        entityPage.setHasMore(false);
        entityPage.setNextPage(null);

        return (wm, pageSize) -> entityPage;
    }

    private Function2<WatermarkInfo, String, MarketoEntityPage> getGeneratorNoDataWithNext(){
        MarketoEntityPage entityPage = new MarketoEntityPage();
        entityPage.setData(List.of());
        entityPage.setHasMore(true);
        entityPage.setNextPage("next_page");

        return (wm, pageSize) -> entityPage;
    }
}
