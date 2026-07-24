package com.syncari.core.repositories.syncari;

import java.util.List;
import java.util.Set;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.GhostAccessAudit;
import com.syncari.core.repositories.SyncariRepo;

public interface GhostAccessAuditRepo extends SyncariRepo<GhostAccessAudit> {
	@Query("{'status' : {$eq : '?0'}}")
    List<GhostAccessAudit> findByStatus(String status);
    @Query("{ 'requesterId' : ?0 , 'syncariId' : ?1 , 'status' : ?2 }")
    List<GhostAccessAudit> findByRequesterIdAndSyncariIdAndStatus(String requesterId, String syncariId, String status);
    @Query("{ 'requesterId' : ?0 , 'status' : ?1 }")
    List<GhostAccessAudit> findByRequesterIdAndStatus(String requesterId, String status);

    @Query("{ 'requesterId' : ?0 }")
    List<GhostAccessAudit> findByRequesterId(String requesterId);

    @Query("{'syncariId':{$in:?0}, 'status':'ACTIVE'}")
    List<GhostAccessAudit> findActiveBySyncariIds(Set<String> syncariIds);

}
