package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.syncari.core.model.UserRole;
import com.syncari.core.repositories.SyncariRepo;

public interface UserRoleRepo extends SyncariRepo<UserRole> {

	Optional<UserRole> findById(String id);

	Optional<UserRole> findByUserId(String userId);
	
	List<UserRole> findByRoleIdsIn(Set<String> roleIds);
}
