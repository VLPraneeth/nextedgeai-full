package com.syncari.core.repositories.customer.cache;

import com.syncari.core.model.cache.CacheLoadJob;
import com.syncari.core.model.cache.CacheLoadStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
@Slf4j
public class CustomCacheLoadRepoImpl implements CustomCacheLoadRepo {
    public static final int ABORTED_JOB_WAIT_TIME_MS = 30 * 60 * 1000;
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public CacheLoadJob findAndReserveJob() {
        final Date maxAbortedJobLastModifiedDate = new Date(new Date().getTime() - ABORTED_JOB_WAIT_TIME_MS);
        final Criteria abortedJobCriteria = Criteria.where("status").is(CacheLoadStatus.IN_PROGRESS.name())
                .and("updatedAt").lt(maxAbortedJobLastModifiedDate);
        final Criteria pendingJobCritieria = Criteria
                .where("status").is(CacheLoadStatus.PENDING);

        Update update = new Update().set("status", CacheLoadStatus.IN_PROGRESS.name()).currentDate("updatedAt");
        CacheLoadJob pending = customerMongoTemplate.findAndModify(new Query(pendingJobCritieria), update,
                FindAndModifyOptions.options().upsert(false).returnNew(true), CacheLoadJob.class);
        if (pending != null) {
            return pending;
        } else {
            log.debug("No Pending CacheLoad Jobs found. Trying to find aborted jobs now");
            return customerMongoTemplate.findAndModify(new Query(abortedJobCriteria), update,
                    FindAndModifyOptions.options().upsert(false).returnNew(true), CacheLoadJob.class);
        }

    }

}
