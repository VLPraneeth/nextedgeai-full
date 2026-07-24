package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.Dashboard;
import com.syncari.core.repositories.SyncariRepo;

public interface DashboardRepo extends SyncariRepo<Dashboard> {

	@Query("{ 'name' : ?0}")
	Optional<Dashboard> findByName(String name);
	
	@Query("{ 'category' : ?0}")
	List<Dashboard> findByCategory(String category);

}
