package com.syncari.core.repositories.customer;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Scope;

import java.util.List;
import java.util.Optional;

public interface MappingGraphCustom {

    public List<MappingGraph> retrieveEntityMappingGraphs(String draftStatus, String mappingGraphId, int limit);

    public List<MappingGraph> retrieveFieldMappingGraphs(List<String> targetIds, Scope scope, DraftStatus draftStatus,
                                                         String mappingGraphId, int limit);
    Optional<MappingGraph> findActiveDraftForMappingGraph(String parentId);
    Optional<MappingGraph> findPreviousVersion(MappingGraph graph);
    Optional<MappingGraph> findLastModifiedDraftAttributeGraph(List<String> targetIds);
}
