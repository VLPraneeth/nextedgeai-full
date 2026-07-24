package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.RuleDefinition;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.SyncariRepo;

public interface RuleDefinitionRepo extends SyncariRepo<RuleDefinition> {
	Optional<RuleDefinition> findByName(String name);

	Optional<RuleDefinition> findByNameAndScope(String name, Scope scope);

	@Query(value = "{ 'scope' :?0}", sort = "{ displayName : 1 }")
	List<RuleDefinition> findByScope(Scope name);
}
