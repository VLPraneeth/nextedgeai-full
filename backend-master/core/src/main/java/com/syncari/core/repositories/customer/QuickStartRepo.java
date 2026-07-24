package com.syncari.core.repositories.customer;

import com.syncari.core.quickstart.v2.QuickStart;
import com.syncari.core.repositories.DraftableRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuickStartRepo extends DraftableRepo<QuickStart> {

    @Query("{ '_id' : ?0, 'draftStatus':{$eq:'NEW'} }")
    Optional<QuickStart> findDraftByQuickStartId(String quickStartId);

    @Query("{ '_id' : ?0, 'draftStatus':{$eq:'APPROVED'} }")
    Optional<QuickStart> findApprovedByQuickStartId(String quickStartId);

    @Query("{ 'draftStatus':{$ne:'ARCHIVED'} }")
    List<QuickStart> findAllQuickStart();

    @Query("{ 'draftStatus':{$in: ?0} }")
    List<QuickStart> findByDraftStatuses(List<String> statuses);
}
