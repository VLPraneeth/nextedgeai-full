package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.event.EventTypes;
import com.syncari.core.model.JobQueue;
import com.syncari.core.model.util.JobQueueStatus;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.Assert.*;

import java.util.Map;

@Slf4j
public class JobQueueServiceTest extends AbstractSyncariTest {

    @Autowired
    JobQueueService jobQueueService;

    @Test
    public void testJobQueue(){
        String jobQueueId = ObjectId.get().toString();
        JobQueue jobQueue = jobQueueService.createJobQueue(jobQueueId, EventTypes.INSTALL_QUICK_START,
                JobQueueStatus.queued, Map.of());
        assertNotNull(jobQueue);
        JobQueue jobQueueFetch = jobQueueService.getJobQueue(jobQueueId);
        assertNotNull(jobQueueFetch);
    }


}
