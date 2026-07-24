package com.syncari.core.pipeline;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.syncari.core.model.PipelineStats;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class Stat {
    static final MetricRegistry metrics = new MetricRegistry();
    private String pipelineId;
    private String batchId;
    private String stageName; //name of function/action/entity as source,sink,core
    private String stageId;
    private String stageType; //function, core,source, sink or action
    private String targetId;
    private String targetType; //ENTITY or attribute
    private Instant occurredAt;

    private Counter recordsProcessed;
    private Counter emptyInputCount;
    private Counter emptyOutputCount;
    private Counter changeCount;
    private Optional<Counter> duplicateCount = Optional.empty();
    private Optional<Counter> dedupeCount = Optional.empty();
    private Timer latency;
    private PipelineStats stats;

    public Stat(PipelineStats stats) {
        this.stats = stats;

        recordsProcessed = metrics.counter(metricName("recordsProcessed"));
        emptyInputCount = metrics.counter(metricName("emptyInputCount"));
        emptyOutputCount = metrics.counter(metricName("emptyOutputCount"));
        changeCount = metrics.counter(metricName("changeCount"));
        latency = metrics.timer(metricName("latency"));
        if ("dedupe".equals(stats.getStageName())) {
            duplicateCount = Optional.of(metrics.counter(metricName("duplicateCount")));
            dedupeCount = Optional.of(metrics.counter(metricName("dedupeCount")));
        }
    }

    private String metricName(String name) {
        return String.format("%s_%s_%s_%s", name, stats.getStageName(), stats.getStageId(), stats.getBatchId());
    }

    public Map<String, ? extends Number> metrics() {
        var metrics = new HashMap<String, Number>();
        metrics.put("recordsProcessed", recordsProcessed.getCount());
        metrics.put("emptyInputCount", emptyOutputCount.getCount());
        metrics.put("emptyOutputCount", emptyOutputCount.getCount());
        metrics.put("changeCount", changeCount.getCount());
        duplicateCount.ifPresent(c -> metrics.put("duplicateCount", c.getCount()));
        dedupeCount.ifPresent(c -> metrics.put("dedupeCount", c.getCount()));
        metrics.put("latency", latency.getSnapshot().get999thPercentile());
        return metrics;
    }

    public void clear() {
        metrics.remove(metricName("recordsProcessed"));
        metrics.remove(metricName("emptyInputCount"));
        metrics.remove(metricName("emptyOutputCount"));
        metrics.remove(metricName("changeCount"));
        metrics.remove(metricName("latency"));
        metrics.remove(metricName("duplicateCount"));
        metrics.remove(metricName("dedupeCount"));
    }
}
