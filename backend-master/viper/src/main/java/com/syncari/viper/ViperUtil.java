package com.syncari.viper;

import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
public class ViperUtil {

    public static <T> T withPipelineException(Supplier<T> func, MappingGraph graph, EntityDefinition entityDefinition, boolean isSource) {
        try {
            return func.get();
        } catch (Exception e) {
            PipelineException exception = new PipelineException(e).setGraphId(graph.getId()).setScope(graph.getScope());
            Optional<MappingNode> node = isSource ? graph.getSourceNode(entityDefinition.getId()) : graph.getSinkNode(entityDefinition.getId());
            node.ifPresent(n -> exception.setNodeId(n.getId()));
            throw exception;
        }
    }
}
