package com.syncari.core.repositories.customer;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Scope;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

public class MappingGraphRepoImpl implements MappingGraphCustom {

    @Autowired
    protected MongoTemplate customerMongoTemplate;

    public List<MappingGraph> retrieveEntityMappingGraphs(String draftStatus, String mappingGraphId, int limit) {
        Criteria criteria = where("scope").is(Scope.ENTITY).and("draftStatus").is(draftStatus).and("versionInfo").is(null);
        if(mappingGraphId!=null){
            criteria = criteria.and("_id").gt(new ObjectId(mappingGraphId));
        }
        Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("_id").ascending()).limit(limit);
        return customerMongoTemplate.find(query, MappingGraph.class);
    }

    public List<MappingGraph> retrieveFieldMappingGraphs(List<String> targetIds, Scope scope, DraftStatus draftStatus,
                                                         String mappingGraphId, int limit) {
        Criteria criteria = where("targetId").in(targetIds).and("scope").is(scope).and("draftStatus").is(draftStatus).and("versionInfo").is(null);
        if(mappingGraphId!=null){
            criteria = criteria.and("_id").gt(new ObjectId(mappingGraphId));
        }
        Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("_id").ascending()).limit(limit);
        return customerMongoTemplate.find(query, MappingGraph.class);
    }

	@Override
	public Optional<MappingGraph> findActiveDraftForMappingGraph(String parentId) {
		  Criteria criteria = where("parentId").is(parentId).and("draftStatus").is(DraftStatus.NEW).and("versionInfo").is(null);
	        
	        Query query = new Query().addCriteria(
	                criteria
	        );
	        return customerMongoTemplate.find(query, MappingGraph.class).stream().findFirst();
	}

	@Override
	public Optional<MappingGraph> findPreviousVersion(MappingGraph graph) {
		Criteria criteria = where("targetId").is(graph.getTargetId()).and("versionInfo.versionNumber").lt(graph.getVersionInfo().getVersionNumber());
		Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("versionInfo.versionNumber").descending()).limit(1);
		
		return customerMongoTemplate.find(query, MappingGraph.class).stream().findFirst();
	}

	@Override
	public Optional<MappingGraph> findLastModifiedDraftAttributeGraph(List<String> targetIds) {
		Criteria criteria = where("targetId").in(targetIds).and("scope").is(Scope.ATTRIBUTE).and("draftStatus")
				.is(DraftStatus.NEW).and("versionInfo").is(null);
		Query query = new Query().addCriteria(criteria).with(Sort.by("updatedAt").descending()).limit(1);
		return Optional.ofNullable(customerMongoTemplate.findOne(query, MappingGraph.class));
	}

}
