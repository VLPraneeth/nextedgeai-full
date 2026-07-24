package com.syncari.core.repositories.syncari;

import com.syncari.core.model.BrandDetail;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandDetailRepo extends SyncariRepo<BrandDetail> {

    Optional<BrandDetail> findByOrgId(String orgId);

    void deleteByOrgId(String orgId);
}
