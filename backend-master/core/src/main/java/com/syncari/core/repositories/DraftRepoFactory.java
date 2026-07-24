package com.syncari.core.repositories;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;

@Component
public class DraftRepoFactory {
	@Autowired
	MappingGraphRepo mappingGraphRepo;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;
	@Autowired
	AttributeRepo attributeProxyRepo;
	public DraftableRepo<? extends DraftableModel> getRepo(Class clazz) {
		if(clazz.isAssignableFrom(MappingGraph.class)) return mappingGraphRepo;
		if(clazz.isAssignableFrom(EntityDefinition.class)) return entityProxyRepo;
		if(clazz.isAssignableFrom(AttributeDefinition.class)) return attributeProxyRepo;
		throw new RuntimeException(String.format("Type %s is not implemented as draftable", clazz));
	}
}
