package com.syncari.core.repositories.customer;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.RuleAssignment;
import com.syncari.core.repositories.SyncariRepo;

public interface RuleAssignmentRepo extends SyncariRepo<RuleAssignment> {

	@Query("{ 'entityApiName' : ?0 } }")
	List<RuleAssignment> findByEntityApiName(String entityApiName);

}
