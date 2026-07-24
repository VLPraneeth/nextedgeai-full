package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;

import com.syncari.api.rest.controllers.data.insights.*;
import com.syncari.core.model.insights.NoQueryFunction;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.QField;
import com.syncari.core.model.insights.QueryFunction;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.DatacardService;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.repositories.customer.DatacardRepo;

import java.util.List;

@Ignore
public class DatacardControllerTest extends AbstractSyncariTest{

	@Autowired
    DatacardController controller;

    @Autowired
    DatacardRepo datacardRepo;

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    InsightsTransformer transformer;

    @Autowired
    DatasetTransformer datasetTransformer;

    @Autowired
    DatacardService datacardService;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD})
    public void testCreateDatacard(){
        var datacardMaybe = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardMaybe.isPresent());
        var datacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO datacardDTO = transformer.toDatacardDTO(null, datacard);
        datacardDTO.setName("allOpenPipelineCountEdited").setDisplayName("allOpenPipelineCountDisplayNameEdited").setDescription("New description");
        datacardDTO.setDraftStatus(DraftStatus.NEW);
        datacardDTO.setId(null);
        DatacardDTO datacardDTOCreated = controller.createDatacard(datacardDTO);
        assertNotNull(datacardDTOCreated);
        assertTrue(datacardDTOCreated.getName().contains(datacardDTO.getName()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD})
    public void testCreateDatacardWithoutDatasetConfig(){
        var datacardMaybe = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardMaybe.isPresent());
        var datacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO datacardDTO = transformer.toDatacardDTO(null, datacard);
        datacardDTO.setName("allOpenPipelineCountWithoutConfig").setDisplayName("allOpenPipelineCountDisplayNameEdited").setDescription("New description");
        datacardDTO.setId(null);
        datacardDTO.setDraftStatus(DraftStatus.NEW);
        DatacardDTO datacardDTOCreated = controller.createDatacard(datacardDTO);
        assertNotNull(datacardDTOCreated);
        assertTrue(datacardDTOCreated.getName().contains(datacardDTO.getName()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD, DELETE_DATACARD})
    public void testCreateDeleteDatacard(){

        var datacardMaybe = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardMaybe.isPresent());
        var datacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO datacardDTO = transformer.toDatacardDTO(null, datacard);
        datacardDTO.setName("allOpenPipelineCountEdited1").setDisplayName("allOpenPipelineCountDisplayNameEdited1").setDescription("New description");
        datacardDTO.setDraftStatus(DraftStatus.NEW);
        datacardDTO.setId(null);
        DatacardDTO datacardDTOCreated = controller.createDatacard(datacardDTO);
        assertNotNull(datacardDTOCreated);
        assertTrue(datacardDTOCreated.getName().contains(datacardDTO.getName()));
        assertNotNull(datacardDTOCreated.getId());
        assertTrue(datacardRepo.findByName("allOpenPipelineCountEdited1").isPresent());
        controller.deleteDatacard(datacardDTOCreated.getId());
        assertTrue(datacardRepo.findByName("allOpenPipelineCountEdited1").isEmpty());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD, UPDATE_DATACARD})
    public void testCreateUpdateDatacard(){

    	var datacardMaybe = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardMaybe.isPresent());
    	var datacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO datacardDTO = transformer.toDatacardDTO(null, datacard);
        datacardDTO.setName("allOpenPipelineCountEdited2").setDisplayName("allOpenPipelineCountDisplayNameEdited2").setDescription("New description");
        datacardDTO.setDraftStatus(DraftStatus.NEW);
        datacardDTO.setId(null);
        DatacardDTO datacardDTOCreated = controller.createDatacard(datacardDTO);
        assertNotNull(datacardDTOCreated);
        assertTrue(datacardDTOCreated.getName().contains(datacardDTO.getName()));
        assertNotNull(datacardDTOCreated.getId());
        datacardDTOCreated.setDisplayName("allOpenPipelineCountEdited3");
        var returned = controller.updateDatacard(datacardDTOCreated.getId(), datacardDTOCreated);
        assertNotNull(returned);
        var found = datacardRepo.findByName("allOpenPipelineCountEdited2");
        assertTrue(found.isPresent());
        assertTrue(returned.getId().equals(found.get().getId()));
        assertTrue(found.get().getDisplayName().contains(datacardDTOCreated.getDisplayName()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD, DELETE_DATACARD})
    public void testDeleteDatacard(){
        var datacardMaybe = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardMaybe.isPresent());
        var datacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO datacardDTO = transformer.toDatacardDTO(null, datacard);
        datacardDTO.setName("allOpenPipelineCountEdited2").setDisplayName("allOpenPipelineCountDisplayNameEdited2").setDescription("New description");
        datacardDTO.setDraftStatus(DraftStatus.NEW);
        datacardDTO.setId(null);
        DatacardDTO datacardDTOCreated = controller.createDatacard(datacardDTO);
        assertNotNull(datacardDTOCreated);
        assertTrue(datacardDTOCreated.getName().contains(datacardDTO.getName()));
        assertNotNull(datacardDTOCreated.getId());
        assertFalse(datacardDTOCreated.getId() == datacard.getId());
        controller.deleteDatacard(datacardDTOCreated.getId());
        assertFalse(datacardRepo.findById(datacardDTOCreated.getId()).isPresent());
    }

    /*@Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD, PUBLISH_DATACARD})
    public void testCreateDraftDatacard(){

        var datacardMaybe = datacardRepo.findByName("allOpenPipelineCount");
        assertTrue(datacardMaybe.isPresent());
        var datacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO datacardDTO = transformer.toDatacardDTO(null, datacard);
        datacardDTO.setName("allOpenPipelineCountDraft").setDisplayName("allOpenPipelineCountDisplayNameDraft").setDescription("New description");
        datacardDTO.setDraftStatus(DraftStatus.NEW);
        datacardDTO.setId(null);
        DatacardDTO datacardDTOCreated = controller.createDatacard(datacardDTO);
        controller.approveDatacard(datacardDTOCreated.getId());
        var draftDTO = controller.createDraftDatacardFor(datacardDTOCreated.getId());
        assertNotNull(draftDTO);
        
    }*/

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD, CREATE_DATASET})
    public void testCreateDatacardWithDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSWithSelectedFieldsForDatacard").setDisplayName("allOpenNewPipelineCountDSWithSelectedFieldsForDatacard").setDescription("New description");
        datasetCopy.setSeeded(false);
        DatasetDTO datasetDTO = datasetTransformer.transformToDTO(datasetCopy);


        var datacardMaybe = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardMaybe.isPresent());
        var datacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO datacardDTO = transformer.toDatacardDTO(null, datacard);
        datacardDTO.setName("allOpenPipelineCountEdited_1_withdataset").setDisplayName("allOpenPipelineCountDisplayNameEdited_withdataset").setDescription("New description");
        datacardDTO.setDraftStatus(DraftStatus.NEW);
        datacardDTO.setId(null);
        DatacardWithDatasetDTO datacardWithDatasetDTO = new DatacardWithDatasetDTO().setDatacard(datacardDTO).setDataset(datasetDTO);
        DatacardWithDatasetDTO datacardWithDatasetDTOCreated = controller.createDatacardWithDatset(datacardWithDatasetDTO);
        assertNotNull(datacardWithDatasetDTOCreated);
        assertTrue(datacardWithDatasetDTOCreated.getDatacard().getContents().getConfiguration().getDatasetId().equals(datacardWithDatasetDTOCreated.getDataset().getId()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS, CREATE_DATACARD, CREATE_DATASET})
    public void testUpdateDatacardWithDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("newDSToTestUpdateDatcard").setDisplayName("allOpenNewPipelineCountDSWithSelectedFieldsForDatacard").setDescription("New description");
        datasetCopy.setSeeded(false);
        DatasetDTO datasetDTO = datasetTransformer.transformToDTO(datasetCopy);


        var datacardMaybe = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardMaybe.isPresent());
        var seededDatacard = datacardService.getSeededOrFromDataset(datacardMaybe.get().getId());
        DatacardDTO seededDatacardDTO = transformer.toDatacardDTO(null, seededDatacard);
        seededDatacardDTO.setName("newDCToTestUpdateDatcard").setDisplayName("allOpenPipelineCountDisplayNameEdited_withdataset").setDescription("New description");
        seededDatacardDTO.setSeeded(false);
        seededDatacardDTO.setId(null);
        // create a copy datacard as seeded cannot be updated
        var datacardDTO = controller.createDatacard(seededDatacardDTO);
        var datacard = transformer.toDatacard(datacardDTO);
        // update the datacard name and add new dataset
        DatacardWithDatasetDTO datacardWithDatasetDTO = new DatacardWithDatasetDTO().setDatacard(datacardDTO).setDataset(datasetDTO);
        DatacardWithDatasetDTO datacardWithDatasetDTOCreated = controller.updateDatacardWithDatset(datacard.getId(), datacardWithDatasetDTO);
        assertNotNull(datacardWithDatasetDTOCreated);
        assertTrue(datacardWithDatasetDTOCreated.getDatacard().getContents().getConfiguration().getDatasetId().equals(datacardWithDatasetDTOCreated.getDataset().getId()));

        datacard = datacardService.getSeededOrFromDataset(datacard.getId());
        assertEquals("newDCToTestUpdateDatcard", datacard.getName());
        assertEquals("allOpenPipelineCountDisplayNameEdited_withdataset", datacard.getDisplayName());

        var ds = datasetRepo.findApprovedByName("newDSToTestUpdateDatcard");
        assertTrue(ds.isPresent());
        assertEquals("allOpenNewPipelineCountDSWithSelectedFieldsForDatacard", ds.get().getDisplayName());
        assertEquals(ds.get().getId(), datacard.getContents().get(0).getConfig().getDatasetId());
    }

}
