package com.syncari.core.repositories.syncari;

import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.repositories.DraftableRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConnectorMetadataRepo extends DraftableRepo<ConnectorMetadata>, CustomConnectorMetadataRepo {
    List<ConnectorMetadata> findByName(String systemName);

    @Query("{'$or':[{'draftStatus':{$in : ['APPROVED', 'APPROVAL_IN_PROGRESS', 'NEW', 'SUBMIT_FOR_APPROVAL']}},{'draftStatus':null}]}")
    List<ConnectorMetadata> findAllActive();

    Optional<ConnectorMetadata> findByCustomSynapseIdentifier(String synapseIdentifier);

    List<ConnectorMetadata> findByType(String type);

    @Query("{ 'isCustom' : {$eq:true}, 'draftStatus' : {$in : ['APPROVED', 'NEW', 'SUBMIT_FOR_APPROVAL', 'APPROVAL_IN_PROGRESS']}} }")
    List<ConnectorMetadata> findIsCustom();

    @Query("{ 'draftStatus' : {$in : ['APPROVED', 'NEW']}}")
    List<ConnectorMetadata> findEditableConnectorMDs();
    
    @Query("{ 'isHttpSource' : {$eq:true}, 'draftStatus' : {$in : ['APPROVED', 'NEW']}} }")
    List<ConnectorMetadata> findHttpSources();
    
    @Query("{ 'isWebhook' : {$eq:true}, 'draftStatus' : {$in : ['APPROVED', 'NEW']}} }")
    List<ConnectorMetadata> findWebhookReceivers();
}
