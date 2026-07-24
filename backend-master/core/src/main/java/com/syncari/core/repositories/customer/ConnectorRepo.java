package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.mongodb.repository.CountQuery;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.repositories.SyncariRepo;

public interface ConnectorRepo extends SyncariRepo<Connector> {
	Optional<Connector> findByName(String connectorName);

	@Query("{ 'name' : {$regex: '^?0$',$options: 'i'} }")
	Optional<Connector> findByNameIgnoreCase(String connectorName);

	@Query("{ 'name' : 'syncari'}")
	Connector findSyncariConnector();
	
	List<Connector> findByStatusIn(Set<ConnectorStatus> statuses);

	@Query("{ 'status' : {$ne : 'DELETED'}}")
	List<Connector> excludeDeleted();
	
	@Query("{ 'status' : {$ne : 'DELETED'}, 'metadataId' : ?0}")
	List<Connector> findByMetadataId(String metadataId);

	@Query("{ 'status' : 'ACTIVE', 'metadataId' : ?0}")
	List<Connector> findActiveSynpaseByMetadataId(String metadataId);


	@CountQuery("{ 'name' : {$ne : 'syncari'}, 'status' : 'ACTIVE'}")
	long countAllActiveNonSyncari();
}
