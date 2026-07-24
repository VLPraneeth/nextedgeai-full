package com.syncari.core.utils;

import com.syncari.core.Features;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.token.TokenHelper;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.EnvironmentFactory;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class FeatureSampleVerifierTest {


    TokenEnvironment environment =new TokenEnvironment(new EnvironmentFactory().create(new DefaultEnvironmentConfiguration()), Map.of());
    TokenHelper tokenHelper = new TokenHelper(environment);


    @Test
    public void testCompareAndLog() {
        FeatureSampleVerifier<String> sampler = new FeatureSampleVerifier<>("", (a, b) -> a.equals(b));
        sampler.factor = 1d;

        Supplier<String> baseLineFunc = Mockito.mock(Supplier.class);
        Mockito.when(baseLineFunc.get()).thenReturn("a");
        String result = sampler.compareAndLog(() -> "a", baseLineFunc, () -> "");
        assertEquals("a", result);
        verify(baseLineFunc, times(1)).get();

        sampler.factor = 0d;
        baseLineFunc = Mockito.mock(Supplier.class);
        Mockito.when(baseLineFunc.get()).thenReturn("a");
        result = sampler.compareAndLog(() -> "a", baseLineFunc, () -> "");
        assertEquals("a", result);
        verify(baseLineFunc, times(0)).get();

    }

}
