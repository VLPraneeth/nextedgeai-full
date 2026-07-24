package com.syncari.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class StatisticsUtil {
    public static final double DEFAULT_ALPHA = 0.05;

    public static <T> List<T> getOutliers(List<T> values, Function<T, Double> valueExtractor) {
        return getOutliers(values, valueExtractor, DEFAULT_ALPHA);
    }

    public static <T> List<T> getOutliers(List<T> values, Function<T, Double> valueExtractor, double alpha) {
        if (values.size() < 3) {
            return List.of();
        }
        final double grubbsCompareValue = getGrubbsCompareValue(values, alpha);
        List<T> outliers = getGrubbsOutliers(values, valueExtractor, grubbsCompareValue);
        return outliers;

    }

    private static <T> double getGrubbsCompareValue(List<T> values, double alpha) {
        double size = values.size();
        TDistribution t = new TDistribution(size - 2.0);

        double criticalValue = t.inverseCumulativeProbability(alpha / (2.0 * size));
        double criticalValueSquare = criticalValue * criticalValue;
        return ((size - 1) / Math.sqrt(size)) *
                Math.sqrt((criticalValueSquare) / (size - 2.0 + criticalValueSquare));
    }

    private static <T> List<T> getGrubbsOutliers(List<T> values, Function<T, Double> valueExtractor, double criticalValue) {
        List<Pair<T, Double>> recordAndValues = values.stream()
                .map(v -> Pair.of(v, valueExtractor.apply(v)))
                .collect(Collectors.toList());

        final double[] doubleValues = recordAndValues.stream().mapToDouble(r -> r.y).toArray();
        double mean = StatUtils.mean(doubleValues);
        double stddev = stdDev(doubleValues);
        return recordAndValues.stream()
                .filter(p -> Math.abs(mean - p.y) / stddev > criticalValue)
                .map(p -> p.x).collect(Collectors.toList());
    }


    private static Double stdDev(double[] values) {
        return new StandardDeviation().evaluate(values);
    }
}
