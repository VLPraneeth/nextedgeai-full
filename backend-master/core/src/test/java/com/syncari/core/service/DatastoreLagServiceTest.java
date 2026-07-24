package com.syncari.core.service;

import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.model.DatastoreLag;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.AssertTrue;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DatastoreLagServiceTest  extends AbstractSyncariTest {

    @Autowired
    DatastoreLagService datastoreLagService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    FeatureService featureService;

    @Autowired
    EntityRepoService entityRepoService;

    @Override
    public void setUp() {
        super.setUp();
        featureService.enableFeature(Features.Datastore);
    }

    @Override
    public void tearDown() {
        super.tearDown();
        featureService.disableFeature(Features.Datastore);
    }

    @Test
    public void testLag() {
        datastoreLagService.datastoreService = mock(DatastoreService.class);
        PostgresqlDatastoreService postgresqlDatastoreService = mock(PostgresqlDatastoreService.class);
        when(datastoreLagService.datastoreService.getService(any())).thenReturn(postgresqlDatastoreService);
        when(postgresqlDatastoreService.count(any(), any())).thenReturn(10l);
        try{
            List<DatastoreLag> datastoreLagList = datastoreLagService.lagForAllEntities();
            assertTrue(CollectionUtils.isNotEmpty(datastoreLagList));
            datastoreLagList.forEach(f -> {
                assertTrue(f != null);
                assertTrue(f.getPendingRecords() >=0);
            });
        }catch (Exception e){
            assertEquals("Datastore is not enabled to check for the lag",e.getMessage());
        }
    }
}
