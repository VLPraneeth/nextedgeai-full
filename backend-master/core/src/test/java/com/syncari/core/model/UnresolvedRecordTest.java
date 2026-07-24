package com.syncari.core.model;

import org.junit.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.Assert.*;

public class UnresolvedRecordTest {

    @Test
    public void errorState(){

        UnresolvedRecord unresolvedRecord = new UnresolvedRecord();
        unresolvedRecord.setCreatedAt(new Date(Instant.now().minusSeconds(UnresolvedRecord.MAX_UNRESOLVED_ERROR_TIME/1000 -1).toEpochMilli()));
        assertFalse(unresolvedRecord.exceedsErrorThreshold());
        unresolvedRecord.setCreatedAt(new Date(Instant.now().minusSeconds(UnresolvedRecord.MAX_UNRESOLVED_ERROR_TIME/1000 +1).toEpochMilli()));
        assertTrue(unresolvedRecord.exceedsErrorThreshold());
    }

}