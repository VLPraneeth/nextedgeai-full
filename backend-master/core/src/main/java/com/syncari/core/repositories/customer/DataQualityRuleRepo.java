package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.DataQualityRule;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

public interface DataQualityRuleRepo extends SyncariRepo<DataQualityRule> {

  @Query("{ 'mappingGraphId' : ?0, 'isDeleted': false}")
  List<DataQualityRule> findByGraphId(String graphId);

  @Query("{ 'mappingGraphId' : ?0, 'scopeType' : 'attribute'}")
  List<DataQualityRule> findFieldRulesByGraphId(String graphId);

  @Query("{'mappingGraphId' : ?0, 'isDeleted': false, $or: [ {$and: [{'scope': 'all_fields'}, {'scopeType': 'system'}]}, {$and: [{'scope': ?1}, {'scopeType': 'attribute'}]}]}")
  List<DataQualityRule> findByGraphAttrId(String graphId, String attrId);

  @Query("{'mappingGraphId' : ?0, 'scopeType' : 'system', 'scope' : 'record', 'isDeleted': false}")
  List<DataQualityRule> findRecordRulesByGraphId(String graphId);

  @Query("{'category' : ?0}")
  List<DataQualityRule> findByCategoryId(String categoryId);

  @Query("{'entityId' : ?0, 'name' : ?1, 'isDeleted': false, 'mappingGraphId' : ?2}")
  Optional<DataQualityRule> findByName(String entityId, String name, String graphId);

  @Query("{'_id' : ?0, 'isDeleted': false }")
  Optional<DataQualityRule> findByRuleId(String Id);

}
