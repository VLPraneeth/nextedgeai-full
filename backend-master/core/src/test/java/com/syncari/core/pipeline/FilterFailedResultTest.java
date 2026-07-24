package com.syncari.core.pipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.syncari.core.AbstractSyncariTest;

public class FilterFailedResultTest extends AbstractSyncariTest {

    @Test
    public void filterFailedList() {
    	List failedList = new ArrayList<>();
    	failedList.add(new FilterFailedResult("a"));
    	failedList.add(new FilterFailedResult("b"));
    	assertTrue(FilterFailedResult.isFailedFilter(failedList));
    }
    
    @Test
    public void filterFailed() {
    	assertTrue(FilterFailedResult.isFailedFilter(new FilterFailedResult("a")));
    }
    
    @Test
    public void filterFailedNull() {
    	assertFalse(FilterFailedResult.isFailedFilter(null));
    	assertFalse(FilterFailedResult.isFailedFilter(List.of()));
    	assertFalse(FilterFailedResult.isFailedFilter(List.of(List.of())));
    }

}


