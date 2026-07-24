package com.syncari.core.repositories.customer;

import com.syncari.core.model.Fragment;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.SyncariRepo;

import java.util.List;
import java.util.Optional;

public interface FragmentRepo extends SyncariRepo<Fragment> {

    List<Fragment> findAllByScope(Scope scope);

    Optional<Fragment> findByName(String name);
}
