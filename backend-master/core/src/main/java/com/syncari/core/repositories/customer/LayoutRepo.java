package com.syncari.core.repositories.customer;

import com.syncari.core.model.Layout;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LayoutRepo extends SyncariRepo<Layout> {

    Optional<Layout> findByTargetIdAndTargetType(String targetId, String targetType);

    List<Layout> findByTargetIdInAndTargetType(List<String> targetIds, String targetType);

    @Query(value="{'targetId' : {$in: ?0}, 'targetType':?1}", delete = true)
    void deleteAllByTargetId(List<String> ids,String targetType);

}
