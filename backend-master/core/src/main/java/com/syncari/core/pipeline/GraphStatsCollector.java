package com.syncari.core.pipeline;

import com.codahale.metrics.Counter;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.PipelineStats;
import com.syncari.core.model.util.MappingNodeType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GraphStatsCollector {
    private Map<String, Stat> stats = new ConcurrentHashMap<>();

    public Stat getStat(MappingGraph graph, MappingNode node, String batchId) {
        String key = node.getId() + "_" + batchId;
        var stat = stats.getOrDefault(key, new Stat(new PipelineStats()
                .setPipelineId(node.getMappingGraphId())
                .setBatchId(batchId)
                .setStageId(node.getId())
                .setStageName(node.getApiName())
                .setStageType(node.getType().name())
                .setTargetId(graph.getTargetId())
                .setTargetType(graph.getScope().name()))
        );
        stats.put(key, stat);
        return stat;
    }

    public void remove(Stat stat) {
        var current = stats.remove(stat.getStageId() + "_" + stat.getBatchId());
        if (current != null) {
            current.clear();
        }
    }

    public List<PipelineStats> getCurrentStats() {
        return stats.values().stream().map(stat -> stat.getStats()
                .setRecordsProcessed(stat.getRecordsProcessed().getCount())
                .setChangeCount(stat.getChangeCount().getCount())
                .setDedupeCount(stat.getDedupeCount().map(Counter::getCount).orElse(0l))
                .setDuplicateCount(stat.getDuplicateCount().map(Counter::getCount).orElse(0l))
                .setEmptyInputCount(stat.getEmptyInputCount().getCount())
                .setEmptyOutputCount(stat.getEmptyOutputCount().getCount())
                .setLatency(Math.round(stat.getLatency().getSnapshot().get99thPercentile()/Math.pow(10,6)))
                .setOccurredAt(Instant.now())
        ).collect(Collectors.toList());
    }

    public void clear() {
        stats.forEach((key, stat) -> remove(stat));
        stats.clear();
    }
}


