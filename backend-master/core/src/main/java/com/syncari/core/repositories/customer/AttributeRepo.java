package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import com.syncari.core.repositories.DraftableRepo;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.AttributeDefinition;

public interface AttributeRepo extends DraftableRepo<AttributeDefinition> {
	@Query("{ 'entityId' :?0, 'apiName':{$regex: '^?1$',$options: 'i'}, 'draftStatus':{$ne:'ARCHIVED'} }")
    Optional<AttributeDefinition> findByEntityIdAndApiName(String entityId, String apiName);

	@Query(value = "{ 'entityId' :?0, 'draftStatus':{$ne:'ARCHIVED'}}", sort = "{ displayName : 1 }")
	List<AttributeDefinition> findByEntityId(String entityId);

	@Query("{ 'entityId' :?0, 'status':'ACTIVE', 'draftStatus':{$ne:'ARCHIVED'}}")
	List<AttributeDefinition> findActiveByEntityId(String entityId);

	@Query("{ 'dataType' :?0}")
	List<AttributeDefinition> findAllByDataType(String datatype);

	@Query("{ 'entityId' :{$in:?0}, 'status':'ACTIVE', 'draftStatus':{$ne:'ARCHIVED'}}")
	List<AttributeDefinition> findActiveByEntityIds(Iterable<String> entityIds);
	
	@Query("{ 'entityId' :{$in:?0}, 'status': { $in: ['ACTIVE', 'INACTIVE'] }, 'draftStatus':{$ne:'ARCHIVED'}}")
	List<AttributeDefinition> findActiveAndInactiveByEntityIds(Iterable<String> entityIds);
	
	@Query("{ 'dataType' :'externalId', 'referenceTo' :{'$in': ?0}}")
	List<AttributeDefinition> findExternalId(List<String> entityIds);
}
