package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.syncari.core.model.Role;
import com.syncari.core.repositories.SyncariRepo;

public interface RoleRepo extends SyncariRepo<Role> {

	Optional<Role> findById(String id);
	
	Set<Role> findByIdIn(Set<String> id);
	
	Optional<Role> findByName(String string);
	
    List<Role> findByNameIn(Set<String> roleNames);
    
}
