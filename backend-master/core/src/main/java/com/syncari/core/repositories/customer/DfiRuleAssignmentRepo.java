package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.repositories.DraftableRepo;

public interface DfiRuleAssignmentRepo extends DraftableRepo<DfiRuleAssignment>, MonitorableRepo<DfiRuleAssignment> {

    @Query("{ 'entityId' : ?0 } }")
    List<DfiRuleAssignment> findByEntityId(String entityId);

    @Query("{ 'entityId' : ?0, 'draftStatus':{$eq:'APPROVED'} }")
    Optional<DfiRuleAssignment> findPublishedByEntityId(String entityId);

    @Query("{ 'draftStatus':{$eq:'APPROVED'} }")
    List<DfiRuleAssignment> findAllPublished();

    @Query("{ 'entityId' : ?0, 'draftStatus':{$eq:'NEW'} }")
    Optional<DfiRuleAssignment> findDraftByEntityId(String entityId);

    @Query(value="{ 'entityId' : ?0, 'draftStatus':{$eq:'NEW'} }", delete = true)
    void deleteDraftByEntityId(String entityId);

    // Used for Test purpose ONLY, to cleanup records for testing
    @Query(value="{ 'entityId' : ?0 }", delete = true)
    void deleteByEntityId(String entityId);
}
