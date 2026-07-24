package com.syncari.core.repositories.customer;

import com.syncari.core.model.ActionDefinition;

import java.util.Optional;

public interface CustomActionDefinitionRepo {
	Optional<ActionDefinition> findByName(String name);

	Optional<ActionDefinition> findByObjectId(String name);
}
