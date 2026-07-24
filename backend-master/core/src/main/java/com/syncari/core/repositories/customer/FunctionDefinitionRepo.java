package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.SyncariRepo;

public interface FunctionDefinitionRepo extends SyncariRepo<FunctionDefinition> {
	Optional<FunctionDefinition> findByName(String name);

	Optional<FunctionDefinition> findByNameAndScope(String name, Scope scope);

	@Query(value = "{ 'scope' :?0}", sort = "{ displayName : 1 }")
	List<FunctionDefinition> findByScope(Scope name);
}
