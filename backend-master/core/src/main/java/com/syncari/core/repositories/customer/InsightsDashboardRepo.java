package com.syncari.core.repositories.customer;

import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.repositories.DraftableRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InsightsDashboardRepo extends DraftableRepo<InsightsDashboard> {

    @Query("{ 'name' : ?0}")
    Optional<InsightsDashboard> findByName(String name);

    @Query("{'draftStatus' : 'APPROVED'}")
    List<InsightsDashboard> findAllActiveDashboards();

    @Query("{'draftStatus':{$ne:'ARCHIVED'}}")
    List<InsightsDashboard> findAllDashboards();

    @Query("{'draftStatus':{$ne:'ARCHIVED'}, 'dataCardSettings': {'$elemMatch':{ 'datacardId' : ?0 }} }")
    List<InsightsDashboard> findAllDashboardByDataCardIn(String datacardId);

}
