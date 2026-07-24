package com.syncari.connector.data.iterator;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.Stats;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class CompositeIteratorTest {

    @Test
    public void emptyStorages() {
        CompositeEntityDataIterator compositeEntityDataIterator = new CompositeEntityDataIterator(List.of(),1000);
        assertFalse(compositeEntityDataIterator.hasNext());
    }

    @Test
    public void multipleIteratorsExhausted() {
        EntityDataBatchIterator iterator1 = Mockito.mock(EntityDataBatchIterator.class);
        EntityDataBatchIterator iterator2 = Mockito.mock(EntityDataBatchIterator.class);
        when(iterator1.getLastWatermark()).thenReturn(2l);
        when(iterator1.getStats()).thenReturn(new Stats().addLatencyCount(1000l,2).addLatencyCount(500l,2).addLatencyCount(10l,2));
        when(iterator2.getStats()).thenReturn(new Stats().addLatencyCount(20l,3));
        when(iterator2.getLastWatermark()).thenReturn(1l);
        AtomicInteger iterator1Counter = new AtomicInteger(0);
        AtomicInteger iterator2Counter = new AtomicInteger(0);
        when(iterator1.hasNext()).thenAnswer((invocation) -> {
            if (iterator1Counter.get() < 3) {
                return true;
            }
            return false;
        });
        when(iterator1.next()).thenAnswer((invocation) -> {
            if (iterator1Counter.getAndIncrement() < 3) {
                return List.of(new EntityData().setLastModified(1l), new EntityData().setLastModified(100));
            }
            return List.of();
        });

        when(iterator2.hasNext()).thenAnswer((invocation) -> {
            if (iterator2Counter.get() < 1) {
                return true;
            }
            return false;
        });
        when(iterator2.next()).thenAnswer((invocation) -> {
            if (iterator2Counter.getAndIncrement() < 1) {
                return List.of(new EntityData().setLastModified(0), new EntityData().setLastModified(20), new EntityData().setLastModified(50));
            }
            return List.of();
        });

        CompositeEntityDataIterator compositeEntityDataIterator = new CompositeEntityDataIterator(List.of(iterator1,iterator2),1000);

        assertTrue(compositeEntityDataIterator.hasNext());
        List<EntityData> page = compositeEntityDataIterator.next();
        assertEquals(2,page.size());

        assertTrue(compositeEntityDataIterator.hasNext());
        page = compositeEntityDataIterator.next();
        assertEquals(2,page.size());

        assertTrue(compositeEntityDataIterator.hasNext());
        page = compositeEntityDataIterator.next();
        assertEquals(2,page.size());

        assertTrue(compositeEntityDataIterator.hasNext());
        page = compositeEntityDataIterator.next();
        assertEquals(3,page.size());

        assertFalse(compositeEntityDataIterator.hasNext());
        assertEquals(100,compositeEntityDataIterator.getLastWatermark());
        assertEquals(4,compositeEntityDataIterator.getStats().numLatencies());
    }


}