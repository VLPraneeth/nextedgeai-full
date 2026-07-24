package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Watermark;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EntityPageIteratorTest {

    @Test
    public void singleSourceStopsAtMaxPageSize(){
        EntityDataBatchIterator mock = mock(EntityDataBatchIterator.class);
        when(mock.hasNext()).thenReturn(true,true,true,true,true,false);
        when(mock.next()).thenReturn(createRecords(750),createRecords(750),createRecords(750),createRecords(750));
        when(mock.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(mock)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(2249, pages.get(0).size());
        assertFalse(entityPageIterator.hasNext());
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
        verify(mock,times(4)).hasNext();
        verify(mock,times(3)).next();
    }

    @Test
    public void singleSourceStopsAtMaxPageSizeWithResync(){
        EntityDataBatchIterator mock = mock(EntityDataBatchIterator.class);
        when(mock.hasNext()).thenReturn(true,true,true,true,true,false);
        when(mock.next()).thenReturn(createRecords(750),createRecords(750),createRecords(750),createRecords(750));
        when(mock.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(mock)), true);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(2249, pages.get(0).size());
        assertFalse(entityPageIterator.hasNext());
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
        verify(mock,times(4)).hasNext();
        verify(mock,times(3)).next();
    }

    @Test
    public void repeatedWatermarksWithOutliers() {
        final EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(), true);
        final List<EntityData> records = createRecords(10);
        final long outlier = Instant.now().plusSeconds(100000L).toEpochMilli();
        records.get(records.size() - 1).setLastModified(outlier);
        records.get(records.size() - 1).setOutlierTimestamp(true);
        records.get(records.size() - 2).setLastModified(outlier);
        records.get(records.size() - 2).setOutlierTimestamp(true);
        records.get(records.size() - 3).setLastModified(outlier);
        records.get(records.size() - 3).setOutlierTimestamp(true);
        assertEquals(records.get(records.size() - 4), entityPageIterator.getNonOutlierFromLast(records, 0));
        assertEquals(records.get(records.size() - 5), entityPageIterator.getNonOutlierFromLast(records, 1));
        assertEquals(records.get(records.size() - 4).getLastModified(), entityPageIterator.getLastWatermark(records));
        assertFalse(entityPageIterator.hasPotentialRepeatedWatermarks(records));
        records.get(records.size() - 5).setLastModified(records.get(records.size() - 4).getLastModified());
        assertTrue(entityPageIterator.hasPotentialRepeatedWatermarks(records));
    }

    @Test
    public void deletedRecordsNotConsideredForWM() {
        final EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(), true);
        final List<EntityData> records = createRecords(10);
        final long outlier = Instant.now().plusSeconds(100000L).toEpochMilli();
        records.get(records.size() - 1).setLastModified(outlier);
        records.get(records.size() - 1).setDeleted(true);
        records.get(records.size() - 2).setLastModified(outlier);
        records.get(records.size() - 2).setDeleted(true);
        records.get(records.size() - 3).setLastModified(outlier);
        records.get(records.size() - 3).setDeleted(true);

        assertEquals(records.get(records.size() - 4).getLastModified(), entityPageIterator.getLastWatermark(records));

        final List<EntityData> allRecords = createRecords(10);
        allRecords.forEach(r -> r.setDeleted(true));
        allRecords.get(records.size() - 1).setOutlierTimestamp(true);
        assertEquals(allRecords.get(allRecords.size() - 2).getLastModified(), entityPageIterator.getLastWatermark(allRecords));
    }

    @Test
    public void pruningIgnoresOutliers() {
        final List<EntityData> records = createRecords(100);
        final long outlier = Instant.now().plusSeconds(100000L).toEpochMilli();
        records.get(records.size() - 1).setLastModified(outlier);
        records.get(records.size() - 1).setOutlierTimestamp(true);
        records.get(records.size() - 2).setLastModified(outlier);
        records.get(records.size() - 2).setOutlierTimestamp(true);
        records.get(records.size() - 3).setLastModified(outlier);
        records.get(records.size() - 3).setOutlierTimestamp(true);
        //we expect the first 50 records and the three outliers after prune
        final long targetWM = records.get(49).getLastModified();
        final EntityDataBatchIterator mock = mock(EntityDataBatchIterator.class);
        when(mock.getLastOffset()).thenReturn((long) records.size());
        when(mock.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.RECORD_COUNT, 100));
        when(mock.applyPrune(50)).thenReturn(50L);
        final EntityFetchResult entityFetchResult = mock(EntityFetchResult.class);
        final Watermark wm = new Watermark();
        when(entityFetchResult.getWatermark()).thenReturn(wm);
        final EntityPage entityPage = new EntityPage(mock, entityFetchResult, records, targetWM, 0, null, 0L);
        final EntityPage pruned = entityPage.prune(targetWM);
        assertEquals(53, pruned.size());
        assertEquals(targetWM, pruned.getWatermark());
        //make sure outliers are still present,even if their WM > tagetWM
        assertTrue(pruned.getRecords().contains(records.get(97)));
        assertTrue(pruned.getRecords().contains(records.get(98)));
        assertTrue(pruned.getRecords().contains(records.get(99)));
        //make sure records are pruned
        for (int i = 50; i < 97; i++) {
            assertFalse(pruned.getRecords().contains(records.get(i)));
        }
        assertTrue(wm.getPruneState().isPruned());
        assertEquals(50L, wm.getPruneState().getOffset());

    }

    @Test
    public void outlierRecordTimestampNotUsedForWM() {
        EntityDataBatchIterator mock = mock(EntityDataBatchIterator.class);
        when(mock.hasNext()).thenReturn(true, true, false);
        final List<EntityData> records = createRecords(100);
        //make the last record an outlier
        final long outlier = Instant.now().plusSeconds(100000L).toEpochMilli();
        records.get(records.size() - 1).setLastModified(outlier);
        records.get(records.size() - 1).setOutlierTimestamp(true);
        when(mock.next()).thenReturn(records);
        when(mock.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 200));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(mock)), true);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(100, pages.get(0).size());
        assertFalse(entityPageIterator.hasNext());
        assertEquals(records.get(records.size() - 2).getLastModified(), pages.get(0).getWatermark());
        assertEquals(records.get(records.size() - 2).getLastModified(), pages.get(0).getLastModified());
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
        verify(mock, times(3)).hasNext();
        verify(mock, times(1)).next();
    }

    @Test
    public void multipleSourcesStopsAtMaxPageSize(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,true,true,false);
        when(source1.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000),createRecords(750,4500));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,4500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(1000, 4000));
        when(source2.getLastWatermark()).thenReturn(4000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(5000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1, currentWm),createResult(source2,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        System.out.println(pages.get(0).size());
        System.out.println(pages.get(1).size());
        assertEquals(2249, pages.get(0).size());
        assertEquals(4498, pages.get(0).getWatermark());
        assertEquals(1000, pages.get(1).size());
        //The results are exhausted, set WM to currentWm
        assertEquals(4498, pages.get(1).getWatermark());
        assertFalse(entityPageIterator.hasNext());
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }

    @Test
    public void singleSourceExhaustsRepeatedWatermarks(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        when(source1.hasNext()).thenReturn(true,true,true,true,false);
        when(source1.next()).thenReturn(createRecordsWithSameTs(750, 1000),
                createRecordsWithSameTs(750,2000),
                createRecordsWithSameTs(750,2000),
                createRecordsWithSameTs(750,3000));

        when(source1.getLastWatermark()).thenReturn(2000l,3000l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));
        WatermarkInfo currentWm = new WatermarkInfo().setEnd(5000);

        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1, currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(2250, pages.get(0).size());
        assertEquals(2000L, pages.get(0).getWatermark());
        assertFalse(entityPageIterator.hasNext());
    }

    @Test
    public void multipleSourcesExhaustsRepeatedWatermarks(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,true,true,true,true,true,false);
        //Repeated watermarks
        List<EntityData> recordsWithSameWMPage1 = createRecords(750);
        recordsWithSameWMPage1.forEach(r->r.setLastModified(2000));
        List<EntityData> recordsWithSameWMPage2 = createRecords(750);
        recordsWithSameWMPage2.forEach(r->r.setLastModified(2000));
        when(source1.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000), recordsWithSameWMPage1,recordsWithSameWMPage2,createRecords(750,4000));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,2000l,4500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(1000, 4000));
        when(source2.getLastWatermark()).thenReturn(4000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(5000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1, currentWm),createResult(source2,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        //all records with the same watermark are exhausted
        assertEquals(2250, pages.get(0).size());
        pages.get(0).getRecords().forEach(record->{
            assertTrue(record.has("IDField"));
        });
        assertEquals(2000l, pages.get(0).getWatermark());
        //no record from secound source qualifies. All records are pruned out
        assertEquals(0, pages.get(1).size());
        //The watermark for this pruned source is set to the smallest watermark of all- 2000 from above
        assertEquals(2000l, pages.get(1).getWatermark());
        assertTrue(entityPageIterator.hasNext());
        pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(750, pages.get(0).size());
        assertEquals(2000l, pages.get(0).getWatermark());
        assertFalse(entityPageIterator.hasNext());
    }

    @Test
    public void multipleSourcesWithRepeatedWatermarksAtBoundary(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);


        //Repeated watermarks
        List<EntityData> recordsWithSameWMPage1 = createRecords(750);
        recordsWithSameWMPage1.forEach(r->r.setLastModified(2000));
        List<EntityData> recordsWithSameWMPage2 = createRecords(2000);
        recordsWithSameWMPage2.forEach(r->r.setLastModified(2000));

        when(source1.hasNext()).thenReturn(true,true,true,true,true,true,true,true,true,true);
        when(source1.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000), recordsWithSameWMPage1,
                recordsWithSameWMPage2,createRecords(750, 2751),
                createRecords(750, 3551),createRecords(750, 4251));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,2000l,2751l,3551l,4251l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(1000, 4000));
        when(source2.getLastWatermark()).thenReturn(4000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(5000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1, currentWm),createResult(source2,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        //all records with the same watermark are exhausted
        assertEquals(2250, pages.get(0).size());
        assertEquals(2000l, pages.get(0).getWatermark());
        //no record from secound source qualifies. All records are pruned out
        assertEquals(0, pages.get(1).size());
        //The watermark for this pruned source is set to the smallest watermark of all- 2000 from above
        assertEquals(2000l, pages.get(1).getWatermark());
        assertTrue(entityPageIterator.hasNext());
        pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(2000, pages.get(0).size());
        assertEquals(2000l, pages.get(0).getWatermark());
        assertTrue(entityPageIterator.hasNext());
        //This will return one last page with zero records in it, because of the boundary
        pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(0, pages.get(0).size());
        assertEquals(2000l, pages.get(0).getWatermark());
    }

    @Test
    public void multipleSourcesWithRepeatedWatermarksInSmallPage(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);


        //Repeated watermarks

        List<EntityData> records = createRecords(1000, 2000);
        //Last 2 records have same watermarks
        records.subList(998,999).forEach(r->r.setLastModified(2000));
        List<EntityData> recordsWithSameWMPage1 = createRecords(1000);
        //next 10 have same watermark
        recordsWithSameWMPage1.subList(0,10).forEach(r->r.setLastModified(2000));

        when(source1.hasNext()).thenReturn(true);
        when(source1.next()).thenReturn(createRecords(1000, 1000), records, recordsWithSameWMPage1,
                createRecords(1000, 4000),createRecords(1000, 5000)
                );
        when(source1.getLastWatermark()).thenReturn(1000l,2000l, 3000l,4000l,5000l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(1000, 4000));
        when(source2.getLastWatermark()).thenReturn(4000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(8000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1, currentWm),createResult(source2,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        //all records with the same watermark are exhausted
        assertEquals(2000, pages.get(0).size());
        assertEquals(2000l, pages.get(0).getWatermark());
        //no record from secound source qualifies. All records are pruned out
        assertEquals(0, pages.get(1).size());
        //The watermark for this pruned source is set to the smallest watermark of all- 2000 from above
        assertEquals(2000l, pages.get(1).getWatermark());
        assertTrue(entityPageIterator.hasNext());
        pages = entityPageIterator.next();
        assertEquals(1, pages.size());
        assertEquals(10, pages.get(0).size());
        assertEquals(2000l, pages.get(0).getWatermark());
        assertFalse(entityPageIterator.hasNext());
    }

    @Test
    public void emptySourcesGeneratePages(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,true,true,false);
        when(source1.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000),createRecords(750,4500));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,4500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(false);
        when(source2.getLastWatermark()).thenReturn(0l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        EntityFetchResult result = createResult(source2);
        result.getRequest().setWatermark(new WatermarkInfo(10000,10000,false,0));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1), result), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(2249, pages.get(0).size());
        assertEquals(4498, pages.get(1).getWatermark());
        //empty pages follow smallest watermark
        assertEquals(0, pages.get(1).size());
        assertEquals(4498l, pages.get(1).getWatermark());
        assertFalse(entityPageIterator.hasNext());
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }

    @Test
    public void exhaustedPagesReturnCurrentWatermark(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,false);
        when(source1.next()).thenReturn(createRecords(750, 1000));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,4500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn( true, false);
        when(source2.next()).thenReturn(createRecords(300, 1000));
        when(source2.getLastWatermark()).thenReturn(1000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        EntityFetchResult result = createResult(source2);
        result.getRequest().setWatermark(new WatermarkInfo(10000,10000,false,0));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1), result), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(750, pages.get(0).size());
        assertEquals(10000, pages.get(1).getWatermark());
        //empty pages inherit the request watermark
        assertEquals(300, pages.get(1).size());
        assertEquals(10000, pages.get(1).getWatermark());
        assertFalse(entityPageIterator.hasNext());
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }
    @Test
    public void multipleSourcesPrunesRecordsOverTimestamp(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,true,true,false);
        when(source1.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000),createRecords(750,3000));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,3000l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,true, false);
        when(source2.next()).thenReturn(createRecords(1000, 1000), createRecords(1000, 2500));
        when(source2.getLastWatermark()).thenReturn(1000l,2500l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1),createResult(source2)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(2498, pages.get(0).getWatermark());
        assertEquals(1749, pages.get(0).size());
        assertEquals(1999, pages.get(1).size());
        assertEquals(2498, pages.get(1).getWatermark());

        assertFalse(entityPageIterator.hasNext());
        verify(source1,times(4)).hasNext();
        verify(source1,times(3)).next();

        verify(source2,times(2)).hasNext();
        verify(source2,times(2)).next();
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }

    @Test
    public void multipleSourcesPrunesRecordsOverTimestampForSecondSource(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source2.hasNext()).thenReturn(true,true,true,true,false);
        when(source2.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000),createRecords(750,3000));
        when(source2.getLastWatermark()).thenReturn(1000l,2000l,3000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source1.hasNext()).thenReturn(true,true,true, false);
        when(source1.next()).thenReturn(createRecords(1000, 1000), createRecords(1000, 2500));
        when(source1.getLastWatermark()).thenReturn(1000l,2500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1),createResult(source2)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(1999, pages.get(0).size());
        assertEquals(2498, pages.get(0).getWatermark());
        assertEquals(1749, pages.get(1).size());
        assertEquals(2498, pages.get(1).getWatermark());

        assertFalse(entityPageIterator.hasNext());
        verify(source2,times(3)).hasNext();
        verify(source2,times(3)).next();

        verify(source1,times(3)).hasNext();
        verify(source1,times(2)).next();
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }
    @Test
    public void multipleSourcesNoPruningInHistoricSyncMode(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source2.hasNext()).thenReturn(true,true,true,false);
        when(source2.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000),createRecords(750,3000));
        when(source2.getLastWatermark()).thenReturn(1000l,2000l,3000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source1.hasNext()).thenReturn(true,true,true, false);
        when(source1.next()).thenReturn(createRecords(1000, 1000), createRecords(1000, 2500));
        when(source1.getLastWatermark()).thenReturn(1000l,2500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));
        WatermarkInfo wm = new WatermarkInfo().setEnd(5000l);
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1,wm),createResult(source2,wm)), true);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(1999, pages.get(0).size());
        assertEquals(2498, pages.get(0).getWatermark());
        assertEquals(2249, pages.get(1).size());
        assertEquals(2998, pages.get(1).getWatermark());

        assertFalse(entityPageIterator.hasNext());
        verify(source2,times(3)).hasNext();
        verify(source2,times(3)).next();

        verify(source1,times(3)).hasNext();
        verify(source1,times(2)).next();
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }

    @Test
    public void multipleSourcesNoPruningForSourcesWithBatchJobs(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,true, false);
        when(source1.next()).thenReturn(createRecords(1000, 1000), createRecords(1000, 2500));
        when(source1.getLastWatermark()).thenReturn(1000l,2500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,true,true,true,true, false);
        when(source2.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000),createRecords(750,3000),createRecords(750,4000));
        when(source2.getLastWatermark()).thenReturn(1000l,2000l,3000l,4000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));


        EntityFetchResult result2 = createResult(source2);
        result2.getRequest().setBatchJobs(List.of(new BatchJob()));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1), result2), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(1999, pages.get(0).size());
        assertEquals(2498, pages.get(0).getWatermark());
        assertEquals(2250, pages.get(1).size());
        assertEquals(3000, pages.get(1).getWatermark());

        assertTrue(entityPageIterator.hasNext());
        pages = entityPageIterator.next();
        assertEquals(750, pages.get(0).size());
        assertEquals(4000, pages.get(0).getWatermark());
        assertEquals(1, pages.size());
        //3 times round 1 while fetching the first 3 pages, once while checking hasNext,
        // twice for fetching page 4 (once returning true to fetch the page, second one returning false)
        verify(source2,times(6)).hasNext();
        verify(source2,times(4)).next();
        //once in first entityPageIterator.hasNext, twice in next(), once more in second entityPageIterator.hasNext,
        verify(source1,times(3)).hasNext();
        verify(source1,times(2)).next();
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }

    @Test
    public void multipleSourcesNoPruningForSourcesWithBatchJobsAndSameWM(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,true, false);
        when(source1.next()).thenReturn(createRecords(1000, 1000), createRecords(1000, 2500));
        when(source1.getLastWatermark()).thenReturn(1000l,2500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,true,true,true,true, false);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));
        List<EntityData> page1BatchJob = createRecords(750, 1000);
        List<EntityData> page2BatchJob = createRecords(750, 2000);
        List<EntityData> page3BatchJob = createRecords(750, 3000);
        List<EntityData> page4BatchJob = createRecords(750, 4000);
        page1BatchJob.forEach(r->r.setLastModified(1000));
        page2BatchJob.forEach(r->r.setLastModified(1000));
        page3BatchJob.forEach(r->r.setLastModified(1000));
        page4BatchJob.forEach(r->r.setLastModified(1000));
        when(source2.next()).thenReturn(page1BatchJob,page2BatchJob,page3BatchJob,page4BatchJob);
        when(source2.getLastWatermark()).thenReturn(1000l,2000l,3000l,4000l);


        EntityFetchResult result2 = createResult(source2);
        result2.getRequest().setBatchJobs(List.of(new BatchJob()));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1), result2), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(1999, pages.get(0).size());
        assertEquals(2498, pages.get(0).getWatermark());
        assertEquals(2250, pages.get(1).size());
        assertEquals(1000, pages.get(1).getWatermark());

        assertTrue(entityPageIterator.hasNext());
        pages = entityPageIterator.next();
        assertEquals(750, pages.get(0).size());
        assertEquals(1000, pages.get(0).getWatermark());
        assertEquals(1, pages.size());
        //3 times round 1 while fetching the first 3 pages, once while checking hasNext,
        // twice for fetching page 4 (once returning true to fetch the page, second one returning false)
        verify(source2,times(6)).hasNext();
        verify(source2,times(4)).next();
        //once in first entityPageIterator.hasNext, twice in next(), once more in second entityPageIterator.hasNext,
        verify(source1,times(3)).hasNext();
        verify(source1,times(2)).next();
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }

    @Test
    public void multipleSourcesNoPruningForSourcesWithBatchJobsAndHistoricSync(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true,true, false);
        when(source1.next()).thenReturn(createRecords(1000, 1000), createRecords(1000, 2500));
        when(source1.getLastWatermark()).thenReturn(1000l,2500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,true,true,false);
        when(source2.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000),createRecords(750,3000));
        when(source2.getLastWatermark()).thenReturn(1000l,2000l,3000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));


        EntityFetchResult result2 = createResult(source2);
        result2.getRequest().setBatchJobs(List.of(new BatchJob()));
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1), result2), true);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(2, pages.size());
        assertEquals(1999, pages.get(0).size());
        assertEquals(2498, pages.get(0).getWatermark());
        assertEquals(2250, pages.get(1).size());
        assertEquals(3000, pages.get(1).getWatermark());

        assertFalse(entityPageIterator.hasNext());
        verify(source2,times(4)).hasNext();
        verify(source2,times(3)).next();

        verify(source1,times(3)).hasNext();
        verify(source1,times(2)).next();
        //once in the first entityPageIterator.hasNext(), and 3 more times to get > 2k records
    }


    @Test
    public void multipleSourcesPrunedResetOffset(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source3 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);
        System.out.println("testSource3"+source3);

        when(source1.hasNext()).thenReturn(true,true,true,false);
        // This will be called twice, so 2k limit will be hit in the framework.
        when(source1.next()).thenReturn(createRecords(1000, 1000));
        when(source1.getLastWatermark()).thenReturn(1000l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(1500, 2000));
        // page size is set to 100, hence 15*100 = 15 pages as offset.
        when(source2.getLastOffset()).thenReturn(15l);
        when(source2.getLastWatermark()).thenReturn(2000l);
        Offset offset2 = new Offset(Offset.OffsetType.PAGE_NUMBER, 100);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.PAGE_NUMBER, 100));
        when(source2.applyPrune(anyInt())).thenReturn(Offset.recomputeOffset(offset2, 15l, 1001));

        when(source3.hasNext()).thenReturn(true,false);
        when(source3.next()).thenReturn(createRecords(1500, 2000));
        // This is record based offset so record count is the offset.
        when(source3.getLastOffset()).thenReturn(1500l);
        when(source3.getLastWatermark()).thenReturn(2000l);
        Offset offset3 = new Offset(Offset.OffsetType.RECORD_COUNT, 100 /*Page size is irrelevant*/);
        when(source3.getOffsetInfo()).thenReturn(offset3);
        when(source3.applyPrune(anyInt())).thenReturn(Offset.recomputeOffset(offset3, 1500, 1001));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(3000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(
            List.of(createResult(source1, currentWm),createResult(source2,currentWm),createResult(source3,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(3, pages.size());

        // First page, all records with the same watermark are exhausted
        assertEquals(1999, pages.get(0).size());
        pages.get(0).getRecords().forEach(record->{
            assertTrue(record.has("IDField"));
        });
        assertEquals(998l, pages.get(0).getWatermark());

        // Second page 
        assertEquals(499, pages.get(1).size());
        // We pruned 11 pages worth of data (1001 in total), rewind by that much 15-11=4.
        assertEquals(4, pages.get(1).getOffset());
        assertEquals(998l, pages.get(1).getWatermark());

        // Third page 
        assertEquals(499, pages.get(2).size());
        // We pruned 1001 in total, rewind by that much 1500-1001-1=498.
        assertEquals(498, pages.get(2).getOffset());
        assertEquals(998l, pages.get(2).getWatermark());
        assertFalse(entityPageIterator.hasNext());
    }

    @Test
    public void multipleSourcesPrunedResetOffset_PageNumberOffsetHasLeastWatermark(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source3 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);
        System.out.println("testSource3"+source3);

        when(source1.hasNext()).thenReturn(true,true,false);
        // This will be called twice, so 2k limit will be hit in the framework.
        when(source1.next()).thenReturn(createRecords(2000, 3000));
        when(source1.getLastWatermark()).thenReturn(3000l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(2000, 2000));
        // page size is set to 100, hence 20*100 = 20 pages as offset.
        when(source2.getLastOffset()).thenReturn(20l);
        when(source2.getLastWatermark()).thenReturn(2000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.PAGE_NUMBER, 100));

        when(source3.hasNext()).thenReturn(true,false);
        when(source3.next()).thenReturn(createRecords(2000, 3000));
        // This is record based offset so record count is the offset.
        when(source3.getLastOffset()).thenReturn(2000l);
        when(source3.getLastWatermark()).thenReturn(3000l);
        Offset offset3 = new Offset(Offset.OffsetType.RECORD_COUNT, 100 /*Page size is irrelevant*/);
        when(source3.getOffsetInfo()).thenReturn(offset3);
        when(source3.applyPrune(anyInt())).thenReturn(Offset.recomputeOffset(offset3, 2000, 999));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(4000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(
            List.of(createResult(source1, currentWm),createResult(source2,currentWm),createResult(source3,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(3, pages.size());

        // First page
        assertEquals(1001, pages.get(0).size());
        pages.get(0).getRecords().forEach(record->{
            assertTrue(record.has("IDField"));
        });
        assertEquals(2000l, pages.get(0).getWatermark());

        // Second page 
        assertEquals(2000, pages.get(1).size());
        // Offset is not reset here.
        assertEquals(20, pages.get(1).getOffset());
        assertEquals(2000l, pages.get(1).getWatermark());

        // Third page 
        assertEquals(1001, pages.get(2).size());
        // We pruned 999 in total, rewind by that much 2000-1000-1=999.
        assertEquals(1000, pages.get(2).getOffset());
        assertEquals(2000l, pages.get(2).getWatermark());
        assertFalse(entityPageIterator.hasNext());
    }

    @Test
    public void multipleSourcesPrunedResetOffset_RecordCountOffsetHasLeastWatermark(){
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source3 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);
        System.out.println("testSource3"+source3);

        when(source1.hasNext()).thenReturn(true,true,false);
        // This will be called twice, so 2k limit will be hit in the framework.
        when(source1.next()).thenReturn(createRecords(2000, 3000));
        when(source1.getLastWatermark()).thenReturn(3000l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(2000, 3000));
        // page size is set to 100, hence 20*100 = 20 pages as offset.
        when(source2.getLastOffset()).thenReturn(20l);
        when(source2.getLastWatermark()).thenReturn(3000l);
        Offset offset2 = new Offset(Offset.OffsetType.PAGE_NUMBER, 100);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.PAGE_NUMBER, 100));
        when(source2.applyPrune(anyInt())).thenReturn(Offset.recomputeOffset(offset2, 20l, 999));

        when(source3.hasNext()).thenReturn(true,false);
        when(source3.next()).thenReturn(createRecords(2000, 2000));
        // This is record based offset so record count is the offset.
        when(source3.getLastOffset()).thenReturn(2000l);
        when(source3.getLastWatermark()).thenReturn(2000l);
        when(source3.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.RECORD_COUNT, 100 /*Page size is irrelevant*/));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(4000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(
            List.of(createResult(source1, currentWm),createResult(source2,currentWm),createResult(source3,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(3, pages.size());

        // First page
        assertEquals(1001, pages.get(0).size());
        pages.get(0).getRecords().forEach(record->{
            assertTrue(record.has("IDField"));
        });
        assertEquals(2000l, pages.get(0).getWatermark());

        // Second page, page size based records got pruned.
        assertEquals(1001, pages.get(1).size());
        // 1000 records are over the limit 2000-1000/10=10 is the new page size
        assertEquals(10, pages.get(1).getOffset());
        assertEquals(2000l, pages.get(1).getWatermark());

        // Third page, offset based no records pruned and is a complete batch
        assertEquals(2000, pages.get(2).size());
        // No pruning here, this batch offset is at the end.
        assertEquals(2000, pages.get(2).getOffset());
        assertEquals(2000l, pages.get(2).getWatermark());
        assertFalse(entityPageIterator.hasNext());
    }

    @Test
    public void multipleSourcesOneSourceHasLessMaxRecordsPerSyncCycle(){
        /**
         * This test asserts that when there is a getMaxRecordsPerEntitySyncCycle() override for an iterator, all the other iterators will
         * prune records to match the sync watermark of the least `records per synccycle` iterator.
         */

        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source3 = mock(EntityDataBatchIterator.class);

        when(source1.getMaxRecordsPerEntitySyncCycle()).thenReturn(2000);
        when(source1.hasNext()).thenReturn(true,true,false);
        // This will be called twice, so 2k limit will be hit in the framework.
        when(source1.next()).thenReturn(createRecords(2000, 2000));
        when(source1.getLastWatermark()).thenReturn(2000l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.getMaxRecordsPerEntitySyncCycle()).thenReturn(2000);
        when(source2.hasNext()).thenReturn(true,false);
        when(source2.next()).thenReturn(createRecords(2000, 2000));
        // page size is set to 100, hence 20*100 = 20 pages as offset.
        when(source2.getLastOffset()).thenReturn(20l);
        when(source2.getLastWatermark()).thenReturn(2000l);
        Offset offset2 = new Offset(Offset.OffsetType.PAGE_NUMBER, 100);
        when(source2.getOffsetInfo()).thenReturn(offset2);
        when(source2.applyPrune(anyInt())).thenReturn(Offset.recomputeOffset(offset2, 20, 1599 /** we expect 1599 recs to be pruned */));

        // Reduce max page for the 3rd source.
        when(source3.getMaxRecordsPerEntitySyncCycle()).thenReturn(400);
        when(source3.hasNext()).thenReturn(true,false);
        when(source3.next()).thenReturn(createRecords(400, 400));
        // This is record based offset so record count is the offset.
        when(source3.getLastOffset()).thenReturn(400l);
        when(source3.getLastWatermark()).thenReturn(400l);
        Offset offset3 = new Offset(Offset.OffsetType.RECORD_COUNT, 100 /*Page size is irrelevant*/);
        when(source3.getOffsetInfo()).thenReturn(offset3);

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(2000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(
            List.of(createResult(source1, currentWm),createResult(source2,currentWm),createResult(source3,currentWm)), false);
        assertTrue(entityPageIterator.hasNext());
        List<EntityPage> pages = entityPageIterator.next();
        assertEquals(3, pages.size());

        // First page
        assertEquals(401l, pages.get(0).size());
        assertEquals(400l, pages.get(0).getWatermark());

        // Second page 
        assertEquals(401l, pages.get(1).size());
        // We mock to make sure prune logic is applied
        assertEquals(4, pages.get(1).getOffset());
        assertEquals(400l, pages.get(1).getWatermark());

        // Third page 
        assertEquals(400, pages.get(2).size());
        assertEquals(400, pages.get(2).getOffset());
        assertEquals(400l, pages.get(2).getWatermark());
        assertFalse(entityPageIterator.hasNext());
    }

    @Test
    public void iteratorHasNextWithError() {
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true).thenThrow(new RuntimeException("Test Entity exception"));
        //when(source1.hasNext()).thenThrow(new RuntimeException("Test Entity exception"));

        //Repeated watermarks
        List<EntityData> recordsWithSameWMPage1 = createRecords(750);
        recordsWithSameWMPage1.forEach(r->r.setLastModified(2000));
        List<EntityData> recordsWithSameWMPage2 = createRecords(750);
        recordsWithSameWMPage2.forEach(r->r.setLastModified(2000));
        when(source1.next()).thenReturn(createRecords(750, 1000),createRecords(750,2000), recordsWithSameWMPage1,recordsWithSameWMPage2,createRecords(750,4000));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,2000l,4500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true, true, true, false);
        when(source2.next()).thenReturn(createRecords(1000, 4000));
        when(source2.getLastWatermark()).thenReturn(4000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(5000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1, currentWm),createResult(source2,currentWm)), false);

        Exception exception = null;
        try {
            List<EntityPage> pages = entityPageIterator.next();
        } catch (Exception e) {
            exception = e;
        }
        assertNotNull(exception);
        assertTrue(exception instanceof PipelineException);
        assertEquals("java.lang.RuntimeException: Test Entity exception", exception.getMessage());
        assertEquals("entityId", ((PipelineException) exception).getExternalEntityDefinitionId());

    }

    @Test
    public void iteratorNextWithError() {
        EntityDataBatchIterator source1 = mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator source2 = mock(EntityDataBatchIterator.class);
        //Investigate. Why does the tes fail without this sout? What's going on with mocks?
        System.out.println("testSource1"+source1);
        System.out.println("testSource2"+source2);

        when(source1.hasNext()).thenReturn(true,true, true, false);
        //when(source1.hasNext()).thenThrow(new RuntimeException("Test Entity exception"));

        //Repeated watermarks
        List<EntityData> recordsWithSameWMPage1 = createRecords(750);
        recordsWithSameWMPage1.forEach(r->r.setLastModified(2000));
        List<EntityData> recordsWithSameWMPage2 = createRecords(750);
        recordsWithSameWMPage2.forEach(r->r.setLastModified(2000));
        when(source1.next()).thenReturn(createRecords(750, 1000)).thenThrow(new RuntimeException("Test Entity exception"));
        when(source1.getLastWatermark()).thenReturn(1000l,2000l,2000l,4500l);
        when(source1.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        when(source2.hasNext()).thenReturn(true, true, true, false);
        when(source2.next()).thenReturn(createRecords(1000, 4000));
        when(source2.getLastWatermark()).thenReturn(4000l);
        when(source2.getOffsetInfo()).thenReturn(new Offset(Offset.OffsetType.NONE, 100));

        WatermarkInfo currentWm = new WatermarkInfo().setEnd(5000);
        EntityPageIterator entityPageIterator = new EntityPageIterator(List.of(createResult(source1, currentWm),createResult(source2,currentWm)), false);

        Exception exception = null;
        try {
            List<EntityPage> pages = entityPageIterator.next();
        } catch (Exception e) {
            exception = e;
        }
        assertNotNull(exception);
        assertTrue(exception instanceof PipelineException);
        assertEquals("java.lang.RuntimeException: Test Entity exception", exception.getMessage());
        assertEquals("entityId", ((PipelineException) exception).getExternalEntityDefinitionId());

    }


    /**
     * Creates an EntityData object with lastModified starting at the higher of 0, or maxTs-num and incrementing 1 at a time
     * The last record always has maxTs as the lastModified
     * @param num
     * @param maxTs
     * @return
     */
    private List<EntityData> createRecords(int num, long maxTs) {
        List<EntityData> records = new ArrayList<>();
        long ts = Math.max(0,maxTs - num);
        for(int i=0;i<num-1;i++){
            records.add(new EntityData().setLastModified(ts++));
        }
        records.add(new EntityData().setLastModified(Math.max(maxTs, ts-1)).setId(UUID.randomUUID().toString()));
        return records;
    }

    private List<EntityData> createRecords(int num) {
        long maxTs = Instant.now().toEpochMilli();
        return createRecords(num, maxTs);
    }

    private List<EntityData> createRecordsWithSameTs(int num, long ts) {
        List<EntityData> records = new ArrayList<>();
        for(int i=0;i<num;i++){
            records.add(new EntityData().setLastModified(ts));
        }
        return records;
    }

    public EntityFetchResult createResult(EntityDataBatchIterator mock) {
        return createResult(mock, new WatermarkInfo());
    }
    public EntityFetchResult createResult(EntityDataBatchIterator mock, WatermarkInfo computedWm) {
        EntityDefinition entityDefinition = new EntityDefinition();
        entityDefinition.setId("entityId");
        entityDefinition.addField(new AttributeDefinition().setIdField(true).setApiName("IDField").setDataType(StringType.VALUE));
        EntitySchema schema = new EntitySchema("schema");
        schema.addField(new AttributeSchema("IDField",StringType.VALUE.getName()).setIdField(true));
        return new EntityFetchResult(entityDefinition, new SyncRequest().setWatermark(computedWm),new FetchResponse(computedWm, mock),
                new Connector("c1"), schema, new Watermark(), false);
    }

}