package com.syncari.core.repositories.customer;

import com.syncari.core.model.SyncStream;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface StreamRepo extends SyncariRepo<SyncStream>, CustomStreamRepo {

    Page<SyncStream> findByStatus(SyncStream.Status status, Pageable pageable);

    Optional<SyncStream> findByGraphId(String graphId);

    List<SyncStream> findByGraphIdIn(List<String> graphIds);

    List<SyncStream> findByProcessorId(String processorId);

    Page<SyncStream> findByProcessorId(String processorId, Pageable pageable);

    List<SyncStream> findByProcessorIdAndStatus(String processorId, SyncStream.Status status);

    List<SyncStream> findByProcessorIdAndStatusIn(String processorId, List<SyncStream.Status> statuses);
    
    List<SyncStream> findByStatusIn(List<SyncStream.Status> statuses);

    int countByStatusIn(List<SyncStream.Status> statuses);
}
