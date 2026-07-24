package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.SyncDetail;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

public interface SyncDetailRepo extends SyncariRepo<SyncDetail> {
	@Query("{externalEntityId:?0, entityName:?1, 'watermark.direction':'INBOUND'}")
	Optional<SyncDetail> findWatermark(String externalEntityId, String entityName);

	@Query("{externalEntityId:{$in:?0}, entityName:?1, 'watermark.direction':'INBOUND'}")
	List<SyncDetail> findWatermarks(List<String> externalEntityIds, String entityName);

	@Query("{externalEntityId:?0, entityName:?1, 'watermark.direction':?2}")
	Optional<SyncDetail> findWatermark(String externalEntityId, String entityName, SyncDirection direction);

	@Query("{entityName:?0, externalEntityId:{$in:?1}, 'watermark.direction':'INBOUND'}")
	List<SyncDetail> findUpstreamWatermarks(String syncariEntityName, List<String> externalEntityDefinitionIds);

	@Query(value = "{entityName:?0}", delete = true)
	List<SyncDetail> deleteWatermarksForSyncariEntity(String syncariEntityName);

	@Query("{ externalEntityId:{$in:?0} }")
	List<SyncDetail> findAllWatermarksFor(List<String> externalEntityIds);
	
	@Query("{externalEntityId:?0, entityName:?1, 'watermark.direction':'OUTBOUND'}")
	Optional<SyncDetail> findDownstreamWatermarks(String externalEntityId, String entityName);

}
