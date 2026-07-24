package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.model.AsyncJob;
import com.syncari.core.model.Event;
import com.syncari.core.model.util.Status;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Map;

@Slf4j
public class CreateAsyncJob {
    @ChangeSet(order = "001", id = "createAsyncJob", author = "venkat", runAlways = true)
    public void createAsyncJob(MongoTemplate db) {

        var asyncJobService = MigrationContext.getAsyncJobService();
        String jobType = System.getProperty("jobType");

        Event event = new Event().setType(jobType)
                .setDetails(Map.of("syncariId", SyncariContext.getSyncariId()));

        asyncJobService.save(new AsyncJob().setEvent(event).setStartTime(Instant.now()).setStatus(Status.NEW).setType(jobType));
    }
}
