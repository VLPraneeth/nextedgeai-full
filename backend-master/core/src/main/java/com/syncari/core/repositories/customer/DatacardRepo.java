package com.syncari.core.repositories.customer;

import com.syncari.core.model.insights.Datacard;
import com.syncari.core.repositories.DraftableRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DatacardRepo extends DraftableRepo<Datacard> {

    @Query("{ 'name' : ?0}")
    Optional<Datacard> findByName(String name);

    @Query("{'draftStatus':{$ne:'ARCHIVED'}}")
    List<Datacard> findAllDatacards();

    @Query("{'contents.config.datasetId': ?0}")
    List<Datacard> findAllByDatasetId(String datasetId);

    @Query("{'draftStatus' : 'APPROVED', 'seeded': false, 'contents': {$elemMatch: {'config.variablesMap' : { $exists: true}}}}")
    List<Datacard> findAllActiveDatacardsWithVariables();

}
