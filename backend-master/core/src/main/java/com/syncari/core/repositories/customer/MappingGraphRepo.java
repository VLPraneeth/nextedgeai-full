package com.syncari.core.repositories.customer;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.DraftableRepo;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MappingGraphRepo extends DraftableRepo<MappingGraph>, MappingGraphCustom {

    @Query("{ 'targetId' : ?0, 'scope':'ENTITY','draftStatus':?1, 'versionInfo':null}")
    Optional<MappingGraph> findEntityGraph(String entityId, DraftStatus draftStatus);

    @Query("{ 'targetId' : ?0, 'scope':'ENTITY','draftStatus':{$ne:'ARCHIVED'}, 'versionInfo':null}")
    List<MappingGraph> findEntityGraphs(String entityId);

    @Query("{ 'targetId' : ?0, 'scope':'ATTRIBUTE','draftStatus':?1, 'versionInfo':null}")
    Optional<MappingGraph> findAttributeGraph(String attributeId, DraftStatus draftStatus);

    @Query("{ 'targetId' : ?0, 'scope':'ATTRIBUTE','draftStatus':{$ne:'ARCHIVED'}, 'versionInfo':null}")
    List<MappingGraph> findAttributeGraphs(String attributeId);
    
    @Query("{ 'scope':'ATTRIBUTE','draftStatus':'APPROVED'}")
    List<MappingGraph> findActiveAttributeGraphs();

    @Query("{ 'scope':'ENTITY','draftStatus':'APPROVED'}")
    List<MappingGraph> findPublishedEntityGraphs();

    @Query("{ 'targetId' : ?0, 'scope':'?1','draftStatus' : ?2, 'versionInfo':null}")
    Optional<MappingGraph> findGraph(String attributeId, Scope scope, DraftStatus draftStatus);

    @Query("{ 'targetId' :{'$in': ?0}, 'scope' : ?1, 'draftStatus' : ?2, 'versionInfo':null}")
    List<MappingGraph> findGraphs(List<String> targetIds,  Scope scope, DraftStatus draftStatus);

    @Query("{ 'targetId' :{'$in': ?0}, 'scope' : ?1 , 'draftStatus':{$ne:'ARCHIVED'}, 'versionInfo':null}")
    List<MappingGraph> findDraftAndPublishedGraphs(List<String> targetIds, Scope scope);

    @Query("{ _id :{'$in': ?0}, 'scope' : ?1 , 'draftStatus':{$ne:'ARCHIVED'}, 'versionInfo':null}")
    List<MappingGraph> findGraphsById(List<ObjectId> ids, Scope scope);

    @Query("{'scope':'ENTITY', 'versionInfo':null}")
    List<MappingGraph> findAllEntityGraphs();

    @Query("{ 'targetId' : ?0, 'draftStatus' : 'APPROVED'}")
    Optional<MappingGraph> findActiveGraph(String targetId);

    @Query("{ 'scope': 'ENTITY','settings.realtimeEndpointSuffix' : ?0, 'draftStatus' : 'APPROVED'}")
    Optional<MappingGraph> findActiveGraphByRealTimeEndPoint(String endpoint);

    @Query("{_id: {'$in': ?0}, 'scope': 'ENTITY','settings.realtimePipeline' : true}")
    List<MappingGraph> findRealtimeByIds(Set<ObjectId> ids);

    @Query("{ 'targetId' : ?0, 'draftStatus' : 'NEW', 'versionInfo':null}")
    Optional<MappingGraph> findDraftGraph(String targetId);
    
    @Query(value="{ 'targetId' : ?0 , 'versionInfo':{$ne:null}}", sort = "{ createdAt : -1 }")
    List<MappingGraph> findAllVersionByTargetId(String targetId);
    
    @Query("{ 'targetId' :{'$in': ?0}, 'scope' : ?1, 'draftStatus' : ?2, 'versionInfo':{$ne:null}}")
    List<MappingGraph> findGraphVersions(List<String> targetIds,  Scope scope, DraftStatus draftStatus);
    
    @Query(value="{ 'targetId' :{'$in': ?0}, 'versionInfo.id':?1}")
    List<MappingGraph> findAllVersionByTargetIdAndVersionId(List<String> targetId, String versionId);
    
    @Query(value="{ 'targetId' :?0, 'versionInfo.id':?1}")
    Optional<MappingGraph> findVersionByTargetIdAndVersionId(String targetId, String versionId);
    
    @Query(value="{'draftStatus': 'NEW', 'versionInfo.id':?0}")
    List<MappingGraph> findVersionsByVersionId(String versionId);
    
    @Query("{ 'scope':'ENTITY', 'draftStatus':'NEW', 'versionInfo':{$ne:null}}")
    List<MappingGraph> findAllGraphVersions();
    
    @Query(value = "{ 'targetId' :?0, 'scope' : 'ENTITY', 'draftStatus' : 'NEW', 'versionInfo':{$ne:null}}", count = true)
    Long countGraphVersions(String targetId);
    
    @Query(value = "{ 'targetId' :{'$in': ?0}, 'scope' : 'ATTRIBUTE', 'draftStatus' : ?1, 'versionInfo':null}", count = true)
    Long countAttributeGraphs(List<String> targetIds, DraftStatus draftStatus);
}
