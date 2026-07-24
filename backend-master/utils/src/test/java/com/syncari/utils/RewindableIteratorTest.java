package com.syncari.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public class RewindableIteratorTest {
    @Test
    public void rewindBehavior() throws Exception {
        Iterator<Integer> iter = List.of(1, 2, 3, 4, 5, 6, 7).iterator();
        RewindableIterator<Integer> rewindableIterator = new RewindableIterator<>(iter);
        rewindableIterator.collect(true);
        assertEquals(1, rewindableIterator.next().intValue());
        assertEquals(2, rewindableIterator.next().intValue());
        rewindableIterator.rewind();
        assertEquals(1, rewindableIterator.next().intValue());
        assertEquals(2, rewindableIterator.next().intValue());
        assertEquals(3, rewindableIterator.next().intValue());
        rewindableIterator.collect(false);
        //this is not collected, so when we rewind next, we wont see this
        assertEquals(4, rewindableIterator.next().intValue());
        rewindableIterator.rewind();
        List<Integer> actual = new ArrayList<>();
        while (rewindableIterator.hasNext()) {
            actual.add(rewindableIterator.next());
        }
        assertEquals(List.of(1, 2, 3, 5, 6, 7), actual);
        rewindableIterator.rewind();
        assertTrue(rewindableIterator.hasNext());
        rewindableIterator.reset();
        assertFalse(rewindableIterator.hasNext());

    }
}