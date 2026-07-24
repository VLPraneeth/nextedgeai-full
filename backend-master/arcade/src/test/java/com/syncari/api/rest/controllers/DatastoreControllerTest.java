package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.api.rest.controllers.data.ConnectorResponse;
import com.syncari.api.rest.controllers.data.NodeDef;
import com.syncari.core.SyncariContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatastoreService;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static com.syncari.core.security.Permissions.READ_DATASTORE;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class DatastoreControllerTest extends AbstractSyncariTest {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Override
    public void setUp() {
        super.setUp();
        if(connectorService.getSyncariDatastore().isEmpty()){
            datastoreService.createOrGetSyncariDSConnector(SyncariContext.getSyncariId());
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATASTORE})
    public void testGetAllDatastoreConnection() throws Exception {

        var result = mvc.perform(
                get("/api/v1/datastore")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
        var datastoreList = mapper.readValue(result.getResponse().getContentAsString(), ConnectorResponse[].class);
        assertFalse(datastoreList.length == 0);
        var syncariDatastore = datastoreList[0];
        assertEquals("Syncari Datastore", syncariDatastore.getName());

    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATASTORE})
    public void testDescribeDatastoreConnections() throws Exception {

        var syncariDatastore = connectorService.getSyncariDatastore().get();
        var result = mvc.perform(
                get("/api/v1/datastore/metadata/describe", syncariDatastore.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
        var datastoreMetaList = mapper.readValue(result.getResponse().getContentAsString(), ConnectorMetadataDTO[].class);
        assertTrue(datastoreMetaList.length > 1);
        assertTrue(Arrays.asList(datastoreMetaList).stream().anyMatch(d -> d.getDisplayName().equals("Syncari Datastore")));
        assertTrue(Arrays.asList(datastoreMetaList).stream().anyMatch(d -> d.getDisplayName().equals("PostgreSQL")));

    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATASTORE})
    public void testGetDatastoreConnection() throws Exception {

        var syncariDatastore = connectorService.getSyncariDatastore().get();
        var result = mvc.perform(
                get("/api/v1/datastore/{datastoreId}", syncariDatastore.getId())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
        var datastore = mapper.readValue(result.getResponse().getContentAsString(), ConnectorResponse.class);
        assertEquals("Syncari Datastore", datastore.getName());
        assertEquals(syncariDatastore.getId(), datastore.getId());

    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATASTORE})
    public void testGetSyncariDatastoreConnection() throws Exception {

        var syncariDatastore = connectorService.getSyncariDatastore().get();
        var result = mvc.perform(
                get("/api/v1/datastore/{datastoreId}", "syncari")
                        .contentType(MediaType.APPLICATION_JSON)
        ).andReturn();

        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
        var datastore = mapper.readValue(result.getResponse().getContentAsString(), ConnectorResponse.class);
        assertEquals("Syncari Datastore", datastore.getName());
        assertEquals(syncariDatastore.getId(), datastore.getId());

    }
}
