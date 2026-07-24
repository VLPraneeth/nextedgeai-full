package com.syncari.core.utils;

import com.syncari.core.Features;
import com.syncari.core.model.Feature;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

@Slf4j
public class FeatureSampleVerifier<T> {

    protected double factor = 0.01d;
    private String feature;
    private BiPredicate<T,T> comparator;

    public FeatureSampleVerifier(String feature, BiPredicate<T,T> comparator) {
        this.feature = feature;
        this.comparator = comparator;
    }

    protected boolean sample() {
        return Math.random() < factor;
    }

    public T compareAndLog( Supplier<T> featureFunc, Supplier<T> baselineFunc, Supplier<String> context) {

        T returnValue = featureFunc.get();
        if (sample()) {
            var baselineValue = baselineFunc.get();
            if (!comparator.test(returnValue, baselineValue)) {
                log.error("Difference found for feature {}, New Value {}, Baseline value {} Context :{}", feature, returnValue, baselineValue, context.get());
            }
        }
        return returnValue;
    }
}
