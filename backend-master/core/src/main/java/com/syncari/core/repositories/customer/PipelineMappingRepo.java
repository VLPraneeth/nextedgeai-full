package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.PipelineMapping;
import com.syncari.core.repositories.SyncariRepo;

public interface PipelineMappingRepo extends SyncariRepo<PipelineMapping>{

	@Query("{ 'scope' : 'ATTRIBUTE', 'targetId':  { '$in' : ?0 }}")
	List<PipelineMapping> findByAttributeIdIn(List<String> attributeIds);

	@Query("{ 'scope' : 'ATTRIBUTE', 'targetId': ?0, 'pipelineId' :?1}")
	Optional<PipelineMapping> findByAttributeIdAndPipelineId(String attributeDefinitionId, String pipelineId);

	@Query("{ 'scope' : 'ENTITY', 'targetId':  { '$in' : ?0 }}")
	List<PipelineMapping> findByEntityIdIn(List<String> entityIds);

	@Query("{ 'scope' : 'ENTITY', 'targetId': ?0, 'pipelineId' :?1}")
	Optional<PipelineMapping> findByEntityIdAndPipelineId(String entityDefinitionId, String pipelineId);

}
