package com.syncari.core.repositories.syncari;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.syncari.core.model.Cluster;

@Repository
public interface ClusterRepo extends MongoRepository<Cluster, String> {

	@Query("{ 'hasSyncariDb' : true }")
	Cluster findPrimary();
	
	@Query("{ 'isProvisionActive' : true, 'hasSyncariDb' : false }")
	List<Cluster> findActive();
	
}
