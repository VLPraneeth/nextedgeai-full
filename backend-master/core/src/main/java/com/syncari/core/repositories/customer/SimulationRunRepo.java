package com.syncari.core.repositories.customer;

import com.syncari.core.model.*;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.*;

import java.util.*;

public interface SimulationRunRepo extends SyncariRepo<SimulationRun>, CustomSimulationRunRepo {
    @Query(value = "{'targetId' : ?0}")
    List<SimulationRun> findByTargetId(String targetId, Pageable pageable);
}
