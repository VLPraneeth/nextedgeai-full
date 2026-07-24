package com.syncari.connector.data;

import com.google.common.math.Quantiles;
import com.syncari.utils.Pair;

import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ToString
public class Stats {
    private List<Pair<Long, Integer>> latencies = new ArrayList<>();

    public Stats addLatencyCount(Long latencyMs, Integer count) {
        latencies.add(Pair.of(latencyMs, count));
        return this;
    }

    public int totalCount() {
        return latencies.stream().reduce(Pair.of(0l, 0), (first, second) -> Pair.of(0l, first.y + second.y)).y;
    }

    public double perc90Latency() {
        return Quantiles.percentiles().index(90).compute(latencies.stream().map(l -> l.x).sorted().collect(Collectors.toList()));
    }
    
    public boolean hasData() {
        return latencies.size() > 0;
    }

    public int numLatencies(){
        return latencies.size();
    }

    public Stats merge(Stats other){
        if(other!=null) {
            latencies.addAll(other.latencies);
        }
        return this;
    }
}
