package com.syncari.core.repositories.customer;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.MappingNode;
import com.syncari.core.repositories.SyncariRepo;

public interface MappingNodeRepo extends SyncariRepo<MappingNode> {

    @Query("{ 'mappingGraphId' : ?0}")
    List<MappingNode> findByGraphId(String graphId);

    @Query("{ 'mappingGraphId' : {$in:?0}}")
    List<MappingNode> findByGraphIds(List<String> graphIds);

    @Query("{ 'configuration.entityDefinition.$id':{$in:?0}}")
    List<MappingNode> findByEntityIds(List<ObjectId> entityIds);

    @Query("{ 'configuration.entityDefinition.$id': ?0 }")
    List<MappingNode> findByEntityId(ObjectId entityId);

    @Query("{ 'configuration.attributeDefinition.$id':{$in:?0}}")
    List<MappingNode> findByAttributeIds(List<ObjectId> attributeIds);

    @Query("{ 'configuration.attributeDefinition.$id':{$in:?0},'configuration._class':'com.syncari.core.model.AttributeSinkNodeConfig'}")
    List<MappingNode> findSinkByAttributeIds(List<ObjectId> attributeIds);

    @Query("{ 'configuration.attributeDefinition.$id': ?0 }")
    List<MappingNode> findByAttributeId(ObjectId attributeId);

    @Query("{ 'configuration.attributeDefinition._id': ?0 ,'configuration._class':'com.syncari.core.model.CoreAttributeNodeConfig'}")
    List<MappingNode> findCoreNodesByAttributeId(ObjectId attributeId);

    @Query("{ 'configuration.attributeDefinition.$id': ?0 ,'configuration._class':'com.syncari.core.model.AttributeSinkNodeConfig'}")
    List<MappingNode> findSinkByAttributeId(ObjectId attributeId);

    @Query(value="{'mappingGraphId' : ?0}", delete = true)
    void deleteByGraphId(String graphId);

    @Query(value="{'mappingGraphId' : {'$in':?0}}", delete = true)
    void deleteByGraphIdIn(List<String> graphIds);

    @Query("{'configuration._class':'com.syncari.core.model.CoreEntityNodeConfig'}")
    List<MappingNode> findCoreNodes();

    @Query("{ 'apiName':{$regex: ?0, $options: 'i'} }")
    List<MappingNode> findByApiName(String text);

    @Query("{ 'name':{$regex: ?0, $options: 'i'} }")
    List<MappingNode> findByName(String text);

    @Query("{ 'configuration.advancedDedupeConfig.fieldMergePolicies.compositeValues':{$elemMatch: {'fieldMergePredicate' : {$exists:true, $ne: [] }}}}")
    List<MappingNode> findAllContainsDedupeAndFieldMergePolicies();

    @Query("{'scope':'ENTITY', 'configuration.advancedDedupeConfig':{$ne:null}}")
    List<MappingNode> findNodesWithAdvancedDedupeMerge();

}
