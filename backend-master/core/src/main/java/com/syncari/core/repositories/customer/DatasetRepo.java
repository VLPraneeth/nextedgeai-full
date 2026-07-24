package com.syncari.core.repositories.customer;

import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.SyncariRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

public interface DatasetRepo extends DraftableRepo<Dataset> {

    @Query("{ 'name' : ?0}")
    List<Dataset> findByName(String name);

    @Query("{ 'name' : ?0, 'draftStatus' : 'APPROVED'}")
    Optional<Dataset> findApprovedByName(String name);

    @Query("{ 'name' : ?0, 'draftStatus' :{$exists: false}}")
    Optional<Dataset> findByNameWithoutDraftStatus(String name);

    @Query("{'version':{$exists: false}}")
    List<Dataset> findAllWithoutVersion();

    @Query("{'draftStatus' : {$ne:'ARCHIVED'}}")
    List<Dataset> findAllActiveDatasets();

    @Query("{'draftStatus' : 'NEW'}")
    List<Dataset> findAllDraftDatasets();

    @Query("{'draftStatus' : {$ne:'ARCHIVED'}, 'version':{$exists: true}}")
    List<Dataset> findAllDatasetsWithVersion();

    @Query("{'draftStatus' : 'APPROVED', 'version':{$exists: true}}")
    List<Dataset> findAllApprovedDatasetsWithVersion();

    @Query("{'draftStatus' : {$ne:'ARCHIVED'}, 'seeded': false}")
    List<Dataset> findAllActiveNonSeededDatasets();

    @Query("{'draftStatus' : 'APPROVED', 'seeded': false, 'variablesMap' : { $exists: true}}")
    List<Dataset> findAllActiveDatasetsWithVariables();
}
