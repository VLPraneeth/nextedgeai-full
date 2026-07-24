package com.syncari.core.repositories.customer;

import java.util.List;

import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.SyncariRepo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

public interface PipelineTestRepo extends SyncariRepo<PipelineTest>, MonitorableRepo<PipelineTest> {
    List<PipelineTest> findByGraphIdAndStatusIn(String graphId, List<Status> status);

    List<PipelineTest> findByGraphIdInAndStatusIn(List<String> graphIds, List<Status> status);

    List<PipelineTest> findByGraphId(String graphId, Pageable pageable);

    List<PipelineTest> findByTargetIdAndScope(String targetId, Scope scope);

    @Query(value="{'id' : ?0}", delete = true)
    void deleteByTestId(String testId);

}
