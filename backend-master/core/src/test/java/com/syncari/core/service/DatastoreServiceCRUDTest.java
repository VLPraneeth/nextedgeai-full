package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.database.DatastoreFactory;
import com.syncari.connector.database.PostgresService;
import com.syncari.connector.datastore.Datastore;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.Schema;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@Slf4j
public class DatastoreServiceCRUDTest extends AbstractSyncariTest {

    @Autowired
    FeatureService featureService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ConnectorMetadataService connMetaService;

    @MockBean
    DatastoreFactory datastoreFactory;

    Datastore mockDatastore;

    @Autowired
    SchemaService schemaService;


    private static final String pwd = "!SyncariDemo12#";
    private static final String user = "demo";
    private static final String cluster = "35.230.89.186:5432";

    @Override
    public void setUp() {
        super.setUp();
        featureService.enableFeature(Features.Datastore);

        mockDatastore = mock(Datastore.class);
        doReturn(mockDatastore).when(datastoreFactory).getService(any());
        doNothing().when(mockDatastore).provision(any(), anyString(), anyString(), anyBoolean());
        doNothing().when(mockDatastore).deprovision(any(), anyString());
        doReturn(new TestConnectionResponse()).when(mockDatastore).testConnection(any(), anyList());

        var syncariDS = connectorService.getSyncariDatastore();
        if(syncariDS.isEmpty()){
            datastoreService.provision(SyncariContext.getSyncariId());
        } else {
            // make sure syncari datastore is active
            datastoreService.activate(syncariDS.get().getId());
        }
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void provisionSyncariDatastore(){

        datastoreService.provision(SyncariContext.getSyncariId());
        assertTrue(connectorService.getSyncariDatastore().isPresent());
        assertEquals("Syncari Datastore", connectorService.getSyncariDatastore().get().getName());
    }

    @Test
    public void createExternalPostgresDatastore(){

        Connector postgresDS = getPostgresConnector();
        Connector saved = datastoreService.createExternalDatastoreConnection(postgresDS);
        assertEquals(ConnectorStatus.INACTIVE, saved.getStatus());

        // validate list returns the same saved connection
        var list = datastoreService.getAllDatastores();
        assertTrue(list.size() > 1);
        assertTrue(list.stream().anyMatch(d -> saved.equals(d)));

        // delete the connection
        datastoreService.deleteDatastore(saved.getId());
    }

    @Test
    public void updateInactiveExternalPostgresDatastore(){

        Connector postgresDS = getPostgresConnector();
        Connector saved = datastoreService.createExternalDatastoreConnection(postgresDS);
        assertEquals(ConnectorStatus.INACTIVE, saved.getStatus());

        // validate list returns the same saved connection
        var list = datastoreService.getAllDatastores();
        assertTrue(list.size() > 1);
        assertTrue(list.stream().anyMatch(d -> saved.equals(d)));

        // update the datastore connection
        saved.setName("external_postgres_datastore_updated");
        saved.getAuthConfig().setPassword(pwd);
        var updated = datastoreService.updateExternalDatastoreConnection(saved.getId(), saved);
        var retrieved = datastoreService.get(updated.getId());
        assertEquals(updated, retrieved);
        assertEquals(saved.getId(), retrieved.getId());

        // delete the connection
        datastoreService.deleteDatastore(updated.getId());
    }

    @Test
    public void updateActiveExternalPostgresDatastore(){

        Connector postgresDS = getPostgresConnector();
        Connector saved = datastoreService.createExternalDatastoreConnection(postgresDS);
        assertEquals(ConnectorStatus.INACTIVE, saved.getStatus());

        //activate connection
        datastoreService.activate(saved.getId());
        assertTrue(datastoreService.get(saved.getId()).isActive());

        // update the datastore connection
        saved.setName("external_postgres_datastore_updated");
        saved.getAuthConfig().setPassword(pwd);
        var updated = datastoreService.updateExternalDatastoreConnection(saved.getId(), saved);
        var retrieved = datastoreService.get(updated.getId());
        assertTrue(retrieved.isActive());
        assertEquals(updated, retrieved);
        assertEquals(saved.getId(), retrieved.getId());

        // deactivate the connection and delete
        datastoreService.deactivate(updated.getId());
        datastoreService.deleteDatastore(updated.getId());
    }

    @Test
    public void activatePostgresDatastore(){

        Connector postgresDS = getPostgresConnector();
        Connector saved = datastoreService.createExternalDatastoreConnection(postgresDS);
        assertEquals(ConnectorStatus.INACTIVE, saved.getStatus());

        // current activate datasatore should be syncari datastore
        var active = datastoreService.findActiveDatastore();
        assertTrue(active.isPresent());
        assertEquals("Syncari Datastore", active.get().getName());

        // activate external ds
        datastoreService.activate(saved.getId());
        active = datastoreService.findActiveDatastore();
        assertTrue(active.isPresent());
        assertEquals("external_postgres_datastore", active.get().getName());

        var syncariDS = connectorService.getSyncariDatastore();
        assertEquals(ConnectorStatus.INACTIVE, syncariDS.get().getStatus());

        // deactivate external postgres connection
        datastoreService.deactivate(saved.getId());
        assertEquals(ConnectorStatus.INACTIVE, datastoreService.get(saved.getId()).getStatus());

        // deactivate the connection and delete
        datastoreService.deleteDatastore(saved.getId());
    }

    @Test
    public void deletePostgresDatastore(){

        Connector postgresDS = getPostgresConnector();
        Connector saved = datastoreService.createExternalDatastoreConnection(postgresDS);
        assertEquals(ConnectorStatus.INACTIVE, saved.getStatus());

        // current activate datasatore should be syncari datastore
        var active = datastoreService.findActiveDatastore();
        assertTrue(active.isPresent());
        assertEquals("Syncari Datastore", active.get().getName());

        // activate external ds
        datastoreService.activate(saved.getId());
        active = datastoreService.findActiveDatastore();
        assertTrue(active.isPresent());
        assertEquals("external_postgres_datastore", active.get().getName());

        //try and delete active connection
        try{
            datastoreService.deleteDatastore(saved.getId());
            fail("Active datastore deletion should fail");
        } catch (Exception e){
            assertEquals("Cannot delete an active datastore connection", e.getMessage());
        }

        // deactivate external postgres connection
        datastoreService.deactivate(saved.getId());
        assertEquals(ConnectorStatus.INACTIVE, datastoreService.get(saved.getId()).getStatus());

        // deactivate the connection and delete
        datastoreService.deleteDatastore(saved.getId());

        // deletion successful
        try {
            datastoreService.get(saved.getId());
        } catch (Exception e) {
            assertEquals(String.format("Datastore with id %s not found", saved.getId()), e.getMessage());
        }

    }

    @Test
    public void createUpdateEntity() {
        Schema syncariSchema = schemaService.getSyncariSchema();
        EntityDef account = syncariSchema.findEntityByName("account").get();
        EntityDefinition entityDefinition = schemaService.getEntity(account.getId());
        EntitySchema entitySchema = datastoreService.toEntitySchema(entityDefinition, schemaService.connectorService.getSyncariConnector());
        when(datastoreFactory.getService(any())).thenReturn(mockDatastore);
        when(mockDatastore.describe(any())).thenReturn(Optional.of(entitySchema));

        datastoreService.createEntity(entityDefinition);
        verify(mockDatastore, times(0)).updateField(any());

        AttributeDefinition revenueAttr = entityDefinition.getAttributes().stream().filter(a -> a.getApiName().equals("AnnualRevenue")).findFirst().get();
        revenueAttr.setDataType(StringType.VALUE);
        datastoreService.createEntity(entityDefinition);
        verify(mockDatastore, times(1)).updateField(any());
    }

    private Connector getPostgresConnector() {
        var meta = connMetaService.findByName("postgresql_datastore").get();
        Connector connector = new Connector("external_postgres_datastore", meta, null, user, pwd);
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, cluster);
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "jenkins");
        connector.getMetaConfig().put(PostgresService.SCHEMA_NAME, "public");
        return connector;
    }

}
