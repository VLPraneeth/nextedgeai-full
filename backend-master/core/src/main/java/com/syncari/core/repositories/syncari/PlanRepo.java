package com.syncari.core.repositories.syncari;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.syncari.core.model.Plan;

@Repository
public interface PlanRepo extends MongoRepository<Plan, String> {
	Optional<Plan> findByName(String name);
}
