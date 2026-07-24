package com.syncari.karibu.rest.controllers;

import com.syncari.core.service.DatasetService;
import com.syncari.karibu.rest.response.DatasetResponse;
import com.syncari.karibu.rest.response.ValidListResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import static com.syncari.core.security.Permissions.VIEW_DATASET;
import static org.junit.Assert.*;

public class DatasetControllerTest extends AbstractSyncariTest{

    @Autowired
    DatasetController datasetController;

    @Autowired
    DatasetService datasetService;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasetsWithoutCursor(){
        ResponseEntity responseEntity = datasetController.listDatasets(null, 6);
        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCodeValue());
        assertTrue(responseEntity.getBody() instanceof ValidListResponse);
        assertNotNull(((ValidListResponse)responseEntity.getBody()).getCursorToken());
        assertTrue(CollectionUtils.isNotEmpty (((ValidListResponse)responseEntity.getBody()).getResult()));
        assertEquals(6, (((ValidListResponse)responseEntity.getBody()).getResult().size()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasetsWithCursor(){
        ResponseEntity responseEntity = datasetController.listDatasets(null, 6);
        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCodeValue());
        assertTrue(responseEntity.getBody() instanceof ValidListResponse);
        assertNotNull(((ValidListResponse)responseEntity.getBody()).getCursorToken());
        assertTrue(CollectionUtils.isNotEmpty (((ValidListResponse)responseEntity.getBody()).getResult()));
        assertEquals(6, (((ValidListResponse)responseEntity.getBody()).getResult().size()));

        String cursorToken = ((ValidListResponse)responseEntity.getBody()).getCursorToken();
        ResponseEntity responseEntity1 = datasetController.listDatasets(cursorToken, 6);
        assertNotNull(responseEntity1);
        assertEquals(200, responseEntity1.getStatusCodeValue());
        assertTrue(responseEntity1.getBody() instanceof ValidListResponse);
        assertNotNull(((ValidListResponse)responseEntity1.getBody()).getCursorToken());
        assertTrue(CollectionUtils.isNotEmpty (((ValidListResponse)responseEntity1.getBody()).getResult()));
        assertEquals(6, (((ValidListResponse)responseEntity1.getBody()).getResult().size()));
        assertNotEquals(((DatasetResponse)(((ValidListResponse)responseEntity1.getBody()).getResult().get(5))).getId(),
                ((DatasetResponse)(((ValidListResponse)responseEntity.getBody()).getResult().get(5))).getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasetsWithCursorPagination(){
        String cursorToken = null;
        do{
            ResponseEntity responseEntity = datasetController.listDatasets(cursorToken, 6);
            assertNotNull(responseEntity);
            assertEquals(200, responseEntity.getStatusCodeValue());
            assertTrue(responseEntity.getBody() instanceof ValidListResponse);
            assertTrue(CollectionUtils.isNotEmpty (((ValidListResponse)responseEntity.getBody()).getResult()));
            assertEquals(6, (((ValidListResponse)responseEntity.getBody()).getResult().size()));
            cursorToken = ((ValidListResponse)responseEntity.getBody()).getCursorToken();
        }while (cursorToken != null);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasetsWithEmptyCursorForLastPage(){
        String cursorToken = null;
        int count = datasetService.getAllApprovedDatasetsWithVersion().size();
        ResponseEntity responseEntity = datasetController.listDatasets(cursorToken, count);
        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCodeValue());
        assertTrue(responseEntity.getBody() instanceof ValidListResponse);
        assertNull(((ValidListResponse)responseEntity.getBody()).getCursorToken());
        assertTrue(CollectionUtils.isNotEmpty (((ValidListResponse)responseEntity.getBody()).getResult()));
        assertEquals(count, (((ValidListResponse)responseEntity.getBody()).getResult().size()));
    }
}
