package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.service.MappingGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.stream.Collectors;

@Slf4j
public class SYN_17372_IdentifyPipelines {
    @ChangeSet(order = "001", id = "identifyPipelines", author = "venkat", runAlways = true)
    public void findInstances(MongoTemplate template) {
        MappingGraphService graphService = MigrationContext.getMappingGraphService();
        graphService.retrieveActiveEntityGraphs().stream().forEach(g  -> {
            checkNodes(graphService, g);
            for (MappingGraph fieldGraph : graphService.retrieveAttributeGraphsForEntityGraph(g.getId())) {
                checkNodes(graphService, fieldGraph);
            }
        });
    }

    private void checkNodes(MappingGraphService graphService, MappingGraph mappingGraph) {

        // checkup look up entity
        var lookupsByEntityDef = graphService.findNodesByGraphId(mappingGraph.getId()).stream().filter( node -> {
            return node.isFunctionNode() && (((SimpleFunctionNodeConfig)node.getTypedConfiguration()).getApiName().equals("advancedLookUpSyncariRecordOnField")
                    || ((SimpleFunctionNodeConfig)node.getTypedConfiguration()).getApiName().equals("advancedLookUpSyncariRecord"));
        }).collect(Collectors.groupingBy(node -> (String)(((SimpleFunctionNodeConfig)node.getTypedConfiguration()).getConfigMap().get("syncariEntityDefId"))));

        var insertsByEntityDef = graphService.findNodesByGraphId(mappingGraph.getId()).stream().filter( node -> {
            return node.isActionNode() && (((GenericActionConfig)node.getTypedConfiguration()).getApiName().equals("insertSyncariRecord"));
        }).collect(Collectors.groupingBy(node -> (String)(((GenericActionConfig)node.getTypedConfiguration()).getConfigMap().get("syncariEntityDefId"))));

        for (String syncariEntity : lookupsByEntityDef.keySet()) {

            if (insertsByEntityDef.containsKey(syncariEntity)) {
                log.info("Found Graph {},{},{} Entity ID {} Lookup Node {}/{} Insert Node {}/{}",
                        mappingGraph.getName(), mappingGraph.getTargetId(), mappingGraph.getScope(), syncariEntity,
                        lookupsByEntityDef.get(syncariEntity).stream().map(n -> n.getId()).collect(Collectors.joining(",")),
                        lookupsByEntityDef.get(syncariEntity).stream().map(n -> n.getName()).collect(Collectors.joining(",")),
                        insertsByEntityDef.get(syncariEntity).stream().map(n -> n.getId()).collect(Collectors.joining(",")),
                        insertsByEntityDef.get(syncariEntity).stream().map(n -> n.getName()).collect(Collectors.joining(",")));
            }
        }


    }
}
