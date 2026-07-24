package com.syncari.core.repositories.customer;

import com.syncari.core.model.SimulationRun;

public interface CustomSimulationRunRepo {

    SimulationRun findLatest(String targetId);
}
