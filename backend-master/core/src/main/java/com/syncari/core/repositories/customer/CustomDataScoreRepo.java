package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.EntityScoreWrapper;

public interface CustomDataScoreRepo {
    EntityScoreWrapper getAvgScores(EntityDefinition entity, Optional<Integer> numberOfRecords, Optional<String> computedDayString);
}
