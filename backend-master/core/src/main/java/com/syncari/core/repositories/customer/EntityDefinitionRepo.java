package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.DraftableRepo;

public interface EntityDefinitionRepo extends DraftableRepo<EntityDefinition>, EntityDefinitionCustom {

	@Query("{'connectorId' : ?0, 'apiName' : {$regex : ?1, $options: 'i'}, 'status':'ACTIVE', 'draftStatus':{$in:['APPROVED','NEW']}}")
	List<EntityDefinition> findEntities(String connectorId, String apiName);

	/**
	 * @deprecated Used only in tests, and doesnt handle case insensitive apiName
 	 * @param connectorId
	 * @param apiName
	 * @return
	 */
	@Query("{'connectorId' : ?0, 'apiName' : ?1}")
	Optional<EntityDefinition> findByConnectorIdAndApiName(String connectorId, String apiName);

	@Query("{'connectorId' : ?0, 'apiName' : ?1, 'status':'ACTIVE', 'draftStatus':{$eq:'APPROVED'}  }")
	Optional<EntityDefinition> findActiveEntityByConnectorIdAndApiName(String connectorId, String apiName);

	@Query("{'connectorId' : ?0,  'status':'ACTIVE', 'draftStatus':{$eq:'APPROVED'} }")
	List<EntityDefinition> findActiveEntities(String connectorId);

	@Query("{'connectorId' : ?0, 'apiName' : ?1, 'draftStatus':{$eq:'APPROVED'} }")
	Optional<EntityDefinition> findEntityByConnectorIdAndApiName(String connectorId, String apiName);

	@Query("{'connectorId' : ?0, 'apiName' : ?1, 'draftStatus':{$eq:'APPROVED'} }")
	Optional<EntityDefinition> findChildEntityByConnectorIdAndApiName(String connectorId, String apiName);

	@Query("{'connectorId' : ?0, 'apiName' : ?1, 'draftStatus':{$eq:'NEW'} }")
	Optional<EntityDefinition> findDraftEntityByConnectorIdAndApiName(String connectorId, String apiName);

	@Query("{'connectorId' : ?0, 'apiName' : ?1, 'draftStatus':{$ne:'ARCHIVED'} }")
	List<EntityDefinition> findEntityVersions(String connectorId, String apiName);

	@Query("{'connectorId' : ?0, 'draftStatus':{$eq:'APPROVED'} }")
	List<EntityDefinition> findByConnectorId(String connectorId);

	@Query("{'connectorId' : {$in:?0},  'status':'ACTIVE', 'draftStatus':{$eq:'APPROVED'} }")
	List<EntityDefinition> findActiveEntitiesByConnectorIds(Set<String> connectorIds);

	@Query("{'connectorId' : ?0, 'draftStatus':{$ne:'ARCHIVED'} }")
	List<EntityDefinition> findAllByConnectorId(String connectorId);

	@Query("{'connectorTypeId' : ?0, 'draftStatus':{$eq:'APPROVED'} }")
	List<EntityDefinition> findByConnectorTypeId(String connectorTypeId);

}
