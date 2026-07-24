package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.RequeueRequest;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SYN_12165_RemoveFromWait {
    @ChangeSet(order = "001", id = "removeFromWait", author = "venkat", runAlways = true)
    public void removeFromWait(MongoTemplate db) {
        var requeuService = MigrationContext.getRequeueService();
        var graphId = System.getProperty("graphId");
        var entityDefinitionId = System.getProperty("entityDefinitionId");

        Page<RequeueRequest> reqeueRequest = requeuService.findSourceRequeueRequests(entityDefinitionId, graphId);
        var currentTime = ZonedDateTime.now();
        while (!reqeueRequest.isEmpty()) {
            var updatedRequeRequests = reqeueRequest.stream().map(r -> r.setRetryTimeLimit(currentTime)).collect(Collectors.toList());
            requeuService.requeue(updatedRequeRequests);
            reqeueRequest = requeuService.findRequeueRequests(entityDefinitionId, graphId, reqeueRequest.nextPageable());
        }
    }


}
