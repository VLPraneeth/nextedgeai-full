package com.syncari.viper.streams.stages;

import com.syncari.core.model.misc.EntitySyncErrorMetric;
import com.syncari.core.model.misc.ErrorType;
import com.syncari.core.model.misc.NodeStatusMetric;
import com.syncari.core.pipeline.NodeError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class PipelineUtil {

    public Stream<EntitySyncErrorMetric> getEntitySyncErrorMetrics(Map<String, List<NodeError>> errors, Map<String, NodeStatusMetric> nodeStatusMetric) {
        return errors.values().stream().flatMap(error -> error.stream()).collect(Collectors.toMap(error -> error.getNodeId() + "_" + error.getError(),
                error -> new EntitySyncErrorMetric(error.getError(), error.getError(), error.getNodeId(), error.getTargetId(), error.getScope(),
                        1, Optional.of(nodeStatusMetric.getOrDefault(error.getNodeId(), new NodeStatusMetric(0))).map(NodeStatusMetric::getRecordCount).orElse(0), ErrorType.ACTION),
                (error1, error2) -> error1.setErrorCount(error1.getErrorCount() +  error2.getErrorCount()))).values().stream();
    }
}
