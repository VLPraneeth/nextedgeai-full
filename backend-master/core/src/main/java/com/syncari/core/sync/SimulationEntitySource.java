package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.core.DataSourceRequest;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.test.SimulationNodeInput;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.SimulationService;
import com.syncari.core.simulation.SimulationCurrentBatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.syncari.utils.CollectionUtils.map;

@Slf4j
@Component
public class SimulationEntitySource implements DataSource {

    @Autowired
    SimulationService simulationService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    EntitySourceHelper helper;

    @Autowired
    StagedBatchRepo stagingRepo;
    @Autowired
    StagedBatchRecordRepo recordRepo;

    @Override
    public CurrentBatch fetchSourceFromTestInput(EntityDefinition syncariEntity, PipelineTest test){
        // Entity test
        SimulationCurrentBatch currentBatch = new SimulationCurrentBatch();
        String syncCycleId = UUID.randomUUID().toString();
        currentBatch.setCurrentBatchId(syncCycleId);
        List<SimulationNodeInput> inputs = test.getTestConfig().getInputs();

        MappingGraph graph = getGraph(test.getTargetId(), test.getScope());
        inputs.forEach(input -> {
            Optional<MappingNode> nodeMaybe = graph.getNode(input.getNodeId());
            nodeMaybe.ifPresent(node -> {

                EntityDefinition srcEntity = node.getScope().equals(Scope.ENTITY)
                        ? mappingGraphService.extractEntityFromNode(node)
                        : schemaService.getEntity(mappingGraphService.extractAttributeFromNode(node).getEntityId());

                StagedBatch staged = new StagedBatch(syncariEntity.getApiName()).setConnectorId(srcEntity.getConnectorId())
                        .setCurrentBatchId(syncCycleId).setSourceEntityName(srcEntity.getApiName())
                        .setSourceEntityDefinitionId(srcEntity.getId());
                staged.setId(UUID.randomUUID().toString());

                List<EntityData> entityData = extractEntityDataFromInput(srcEntity, input, test);
                saveBatchRecords(srcEntity, currentBatch, staged, entityData);

                currentBatch.setEntityBatch(srcEntity, staged);
            });
        });

        return currentBatch;
    }

    private MappingGraph getGraph(String targetId, Scope scope){
        if(Scope.ENTITY.equals(scope)){
            return mappingGraphService.retrieveDraftEntityGraph(targetId)
                    .orElseThrow(() -> new RuntimeException(String.format("No Draft Entity graph is found for syncari entity id %s", targetId)));
        } else {
            return mappingGraphService.retrieveDraftAttributeGraph(targetId)
                    .orElseThrow(() -> new RuntimeException(String.format("No Draft Attribute graph is found for syncari attribute id %s", targetId)));
        }
    }

    private void saveBatchRecords(EntityDefinition entity, SimulationCurrentBatch currentBatch, StagedBatch staged, List<EntityData> batchData) {
        Map<String, AttributeDefinition> apiNameToAttrMap = entity.getApiNameLowerCasedToAttributes();
        List<StagedBatchRecord> stagedBatchRecords = map(batchData, d -> {
            EntityData entityData = helper.fixDatatypes(apiNameToAttrMap, d);
            entityData.setConnectorId(entity.getConnectorId());
            StagedBatchRecord record = new StagedBatchRecord()
                    .setStagedBatchId(staged.getId())
                    .setEntityData(entityData)
                    .setExternalRecordId(entityData.getId())
                    .setExternalEntityDefinitionId(entity.getId());
            record.setId(UUID.randomUUID().toString());

            return record;
        });

        currentBatch.setBatchRecords(stagedBatchRecords);
    }

    private List<EntityData> extractEntityDataFromInput(EntityDefinition entity, SimulationNodeInput input, PipelineTest test){
        EntityData entityData = new EntityData();
        entityData.setId(test.getId());
        entityData.setName(entity.getApiName());
        entityData.setConnectorId(entity.getConnectorId());
        entityData.setNew(true);
        entityData.setValues(new HashMap<>(input.getFieldValues()));

        return List.of(entityData);
    }

    @Override
    public CurrentBatch fetch(DataSourceRequest req) {
        return null;
    }

    @Override
    public CurrentBatch fetchSource(DataSourceRequest req) {
        return null;
    }

    @Override
    public CurrentBatch fetchSourceById(DataSourceRequest req) {
        return null;
    }

    @Override
    public void closeSource(GraphContext graphContext) {

    }
}
