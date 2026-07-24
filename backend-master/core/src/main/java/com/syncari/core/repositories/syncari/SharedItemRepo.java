package com.syncari.core.repositories.syncari;

import com.syncari.core.model.SharedItem;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedItemRepo extends SyncariRepo<SharedItem>, SharedItemCustom {

    Optional<SharedItem> findSharedItemBySourceIdAndItemType(String sourceId, Sharable itemType);
    
    Optional<SharedItem> findBySourceIdAndItemTypeIn(String sourceId, List<Sharable> itemTypes);

    @Query("{ 'itemType' : ?0, 'publishedToMarketplace' : ?1 }")
    List<SharedItem> findAllMarketplaceSharedItemsByItemType(Sharable itemType, boolean publishedToMarketplace);

    @Query("{ 'itemType' : ?0 }")
    List<SharedItem> findAllSharedItemsByItemType(Sharable itemType);

    @Query("{ 'itemType' : ?0 , 'sourceInstance' : ?1}")
    List<SharedItem> findAllSharedItemsByItemTypeAAndSourceInstance(Sharable itemType, String sourceInstance);

    @Query("{ 'itemType' : ?0, 'recipientsUserId' : ?1 }")
    List<SharedItem> findAllSharedItemsByItemTypeAAndRecipientsUserId(Sharable itemType, String recipientsUserId);

    @Query("{ 'itemType' : ?0, 'sourceId' : ?1 }")
    List<SharedItem> findAllSharedItemsByItemTypeAAndSourceId(Sharable itemType, String sourceId);

    @Query("{ 'itemType' : ?0, 'createdBy' : ?1 , 'sourceId' : ?2}")
    List<SharedItem> findAllSharedItemsByItemTypeAndCreatedByAndSourceId(Sharable itemType, String createdById, String sourceId);

    @Query("{ 'itemType' : ?0, 'recipientsUserId' : ?1 , 'sourceId' : ?2}")
    Optional<SharedItem> findSharedItemByItemTypeAndRecipientsUserIdAndSourceId(Sharable itemType, String recipientsUserId, String dashboardId);

    @Query("{ 'itemType' : ?0, 'sourceInstance' : ?1 , 'recipientsEmailId' : {'$regex':?2}}")
    List<SharedItem> findSharedItemByItemTypeAndSourceInstance(Sharable itemType, String sourceInstance, String domain);
}
