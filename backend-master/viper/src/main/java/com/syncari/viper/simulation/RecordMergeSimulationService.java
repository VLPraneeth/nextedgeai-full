package com.syncari.viper.simulation;

import com.syncari.connector.EntityData;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.RecordMergeService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RecordMergeSimulationService extends RecordMergeService {

    @Override
    public MergeOperation createMergeOperation(EntityDefinition entityDefinition, DedupeConfig dedupeConfig, EntityData incomingRecord) {
        return null;
    }

    @Override
    public void apply(MergeOperation operation, GraphContext context) {
        // Do nothing
    }

    @Override
    public List<EntityData> findDuplicates(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, EntityDefinition entityDefinition, MergeInfo  mergeInfo) {
        return Collections.emptyList();
    }

    @Override
    public Optional<EntityData> selectWinner(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, List<EntityData> candidates, EntityDefinition entityDefinition, MergeInfo mergeInfo) {
        return Optional.empty();
    }

    @Override
    public EntityData applyMergePolicies(AdvancedDedupeConfig advancedDedupeConfig, EntityData winner, List<EntityData> losers, EntityDefinition entityDefinition) {
        // Do nothing and return winner as is
        return winner;
    }

    @Override
    public EntityData merge(AdvancedDedupeConfig advancedDedupeConfig, EntityData mergedWinner, EntityData incomingRecord, List<EntityData> candidates, EntityDefinition entityDefinition) {
        // Do nothing and return mergeWinner as is
        return mergedWinner;
    }

    @Override
    public Optional<MergeOperation> advancedDedupeMerge(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, EntityDefinition entityDefinition, GraphContext graphContext,
                                                        TransactionLog log, Optional<EntityData> existingRecord) {
      return Optional.empty();
    }

    @Override
    public Optional<MergeOperation> advancedDedupeMerge(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, EntityDefinition entityDefinition, GraphContext graphContext,
                                                        TransactionLog txnLog, Optional<EntityData> existingRecord, List<EntityData> entitiesBatch) {
      return Optional.empty();
    }
}
