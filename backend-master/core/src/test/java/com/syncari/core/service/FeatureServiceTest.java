package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.model.Feature;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class FeatureServiceTest extends AbstractSyncariTest {

    @Autowired
    FeatureService service;


    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void getAllFeatures() {
        List<Feature> features = service.getAllFeatures();
        assertEquals(6, features.size());
        assertEquals(Features.InsightsProvider.name(), features.get(0).name);
    }

}
