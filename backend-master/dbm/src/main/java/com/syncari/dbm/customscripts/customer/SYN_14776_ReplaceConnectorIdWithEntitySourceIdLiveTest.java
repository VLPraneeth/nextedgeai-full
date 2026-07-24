package com.syncari.dbm.customscripts.customer;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_14776_ReplaceConnectorIdWithEntitySourceIdLiveTest {
    
    @ChangeSet(order = "001", id = "replaceConnectorIdWithEntitySourceIdLiveTest", author = "sibin", runAlways = true)
    public void replaceConnectorIdWithEntitySourceIdLiveTest(MongoTemplate template) {
    	boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		log.info("Running in dry run mode - {}", dryRunMode);
			var pipelineTestRepo = MigrationContext.getPipelineTestRepo();
			var mappingGraphService = MigrationContext.getMappingGraphService();
			pipelineTestRepo.findAll().forEach(pipelineTest -> {
				try{
					Map<String, MappingGraph> cache = new HashMap<>();
					if (pipelineTest.getGraphId() != null && pipelineTest.getRecordIds() != null && !pipelineTest.getRecordIds().isEmpty()) {
						var mappingGraph = cache.get(pipelineTest.getGraphId());
						if(mappingGraph == null) {
							mappingGraph = mappingGraphService.retrieve(pipelineTest.getGraphId()).orElse(null);
						}
						if(mappingGraph != null) {
							cache.put(pipelineTest.getGraphId(), mappingGraph);
							var sources = mappingGraphService.getConnectedSourceEntityMap(mappingGraph).values();
							Map<String, List<String>> recordIds = new LinkedHashMap<>();
							pipelineTest.getRecordIds().keySet().forEach(connectorId -> {
								String entityId = findEntityIdByConnectorId(sources, connectorId).orElse(connectorId);
								if(connectorId.equals(entityId)) {
									log.info("Cannot find entity for connector {}, graph {}, pipeline test {}. Keeping the connector id as it is",connectorId, pipelineTest.getGraphId(), pipelineTest.getId());
								}
								recordIds.put(entityId, pipelineTest.getRecordIds().get(connectorId));
							});
							log.info("Replacing {} with {}", pipelineTest.getRecordIds(), recordIds);
							if(!dryRunMode) {
								pipelineTest.setRecordIds(recordIds);
								pipelineTestRepo.save(pipelineTest);
							}
						} else {
							log.info("Cannot find graph {} for pipeline test {} ", pipelineTest.getGraphId(), pipelineTest.getId());
						}
					}
				}catch (Exception e){
					log.error("Exception occurred while running the script {}", ExceptionUtils.getStackTrace(e));
				}
			});
    }
    
    private Optional<String> findEntityIdByConnectorId(Collection<EntityDefinition> entities, String connectorId) {
    	return entities.stream().filter(e -> connectorId.equals(e.getConnectorId())).findFirst().map(e -> e.getId());
    }

}
