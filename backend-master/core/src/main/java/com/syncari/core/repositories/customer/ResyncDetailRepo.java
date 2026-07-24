package com.syncari.core.repositories.customer;

import com.syncari.core.model.ResyncDetail;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.repositories.SyncariRepo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ResyncDetailRepo extends SyncariRepo<ResyncDetail> {

    Optional<ResyncDetail> findByIdAndStatus(String id, ResyncStatus status);

    Optional<ResyncDetail> findBySyncariEntityIdAndStatus(String syncariEntityId, ResyncStatus status);

    Optional<ResyncDetail> findBySyncariEntityIdAndStatusIn(String syncariEntityId, List<ResyncStatus> statuses);

    List<ResyncDetail> findBySyncariEntityId(String syncariEntityId);

    List<ResyncDetail> findBySyncariEntityId(String syncariEntityId, Pageable pageable);

    List<ResyncDetail> findByStatus(ResyncStatus status);
}
