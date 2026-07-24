package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.ActionDefinition;
import com.syncari.core.repositories.DraftableRepo;

public interface ActionDefinitionRepo extends DraftableRepo<ActionDefinition>,CustomActionDefinitionRepo {

    @Query("{ 'type' : {$eq : 'CUSTOM'}, 'draftStatus' : {$in : ['APPROVED', 'NEW']}, 'globalSharedItemId':null}")
    List<ActionDefinition> findEditableActions();

    @Query(value = "{ 'type' : 'CUSTOM', 'draftStatus' : 'APPROVED'}", sort = "{ displayName : 1 }")
    List<ActionDefinition> findPublishedActions();

    @Query("{'type' : {$eq : 'STANDARD'}}")
    List<ActionDefinition> findStandardActions();

    @Query("{apiName : ?0, 'type' : 'CUSTOM', 'draftStatus' : 'APPROVED'}")
    Optional<ActionDefinition> findPublishedActionByName(String apiName);

    @Query("{'apiName' : ?0, 'type' : 'CUSTOM'}")
    List<ActionDefinition> getInstalledAction(String apiName);
    
    @Query("{apiName : ?0, 'draftStatus' : 'NEW'}")
    List<ActionDefinition> findExistingActions(String apiName);

}
