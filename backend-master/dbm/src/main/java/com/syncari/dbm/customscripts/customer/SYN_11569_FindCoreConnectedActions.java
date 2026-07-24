package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SYN_11569_FindCoreConnectedActions {

    @ChangeSet(order = "001", id = "findCoreConnectedActions", author = "blesson", runAlways = true)
    public void findCoreConnectedActions(MongoTemplate template) {
        MappingGraphRepo mappingGraphRepo = MigrationContext.getMappingGraphRepo();
        MappingNodeRepo mappingNodeRepo = MigrationContext.getMappingNodeRepo();
        EdgeRepo edgeRepo = MigrationContext.getEdgeRepo();
        EntityDefinitionRepo entityDefinitionRepo = MigrationContext.getEntityDefinitionRepo();
        AttributeRepo attributeRepo = MigrationContext.getAttributeRepo();
        List<MappingGraph> allGraphs = mappingGraphRepo.findAllEntityGraphs();
        allGraphs.addAll(mappingGraphRepo.findActiveAttributeGraphs());
        List<MappingGraph> approvedGraphs = allGraphs.stream().filter(graph -> graph.isApproved()).collect(Collectors.toList());
        approvedGraphs.forEach(graph -> {
            var nodes = mappingNodeRepo.findByGraphId(graph.getId());
            var edges = edgeRepo.findByGraphId(graph.getId());
            graph.setNodes(nodes);
            graph.setEdges(edges);
            var actions = getCoreConnectedActions(graph);
            if(!actions.collect(Collectors.toList()).isEmpty()) {
                log.info("Graph {} has core connected actions. Target id - {}", graph.getId(), graph.getTargetId());
                String targetId = graph.getTargetId();
                if(graph.getScope() == Scope.ENTITY) {
                    Optional<EntityDefinition> entityDefinition = entityDefinitionRepo.findById(targetId);
                    if(entityDefinition.isPresent()) {
                        log.info("Entity definition - {}", entityDefinition.get());
                    }
                }
                if(graph.getScope() == Scope.ATTRIBUTE) {
                    Optional<AttributeDefinition> attributeDefinition = attributeRepo.findById(targetId);
                    if(attributeDefinition.isPresent()) {
                        log.info("Attribute id - {}", attributeDefinition.get().getId());
                        log.info("Attribute definition - {}", attributeDefinition.get());
                    }
                }
            }
        });
    }

    private Stream<MappingNode> getCoreConnectedActions(MappingGraph graph) {
        Stream<MappingNode> actions = graph.getActions();
        return actions.filter(a -> graph.getOutboundEdges(a).isEmpty() &&
                graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.CORE_ATTRIBUTE || node.getType()== MappingNodeType.CORE_ENTITY));
    }
}
