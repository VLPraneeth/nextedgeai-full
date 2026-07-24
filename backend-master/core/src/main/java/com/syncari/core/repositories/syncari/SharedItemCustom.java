package com.syncari.core.repositories.syncari;

import com.syncari.core.model.SharedItem;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.pipeline.expression.Expression;

import java.util.List;
import java.util.Optional;

public interface SharedItemCustom {
    public List<SharedItem> getSharedItems(Sharable itemType, boolean publishedToMarketplace,
                                           String displayName, String sharedItemId, int limit);

    public Page<SharedItem> getSharedItems(String sourceId,PageCursor pageInfo,Optional<Expression> filter);

    public List<SharedItem> findAllSharedItemsByItemTypeAAndSharingInstance(Sharable itemType, String sharingInstance,List<String> currentOrgInstances);

    }
