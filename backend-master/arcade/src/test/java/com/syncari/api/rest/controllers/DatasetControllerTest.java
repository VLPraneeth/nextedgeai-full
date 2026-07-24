package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.insights.*;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.dataset.DatasetExport;
import com.syncari.core.repositories.customer.DatasetExportRepo;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatasetExportService;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.SchemaService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;

@Ignore
public class DatasetControllerTest extends AbstractSyncariTest{

    @Autowired
    DatasetController datasetController;

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    DatasetTransformer transformer;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DatasetService datasetService;

    @Autowired
    DatasetExportService datasetExportService;

    @Autowired
    DatasetExportRepo datasetExportRepo;

    @Autowired
    SchemaService schemaService;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasets(){
        assertNotNull(datasetController.getDatasets());
        assertTrue(CollectionUtils.isNotEmpty(datasetController.getDatasets()));
        assertTrue(datasetController.getDatasets().size() > 0);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        DatasetDTO dataset1 = datasetController.getDataset(dataset.stream().findFirst().get().getId());
        assertNotNull(dataset1);
        assertTrue(dataset1.getName().contains(dataset.stream().findFirst().get().getName()));
        List<String> apiNames = dataset1.getDatasetConfig().getCalculatedFields().stream().map(x -> x.getApiName()).collect(Collectors.toList());
        assertTrue(CollectionUtils.isNotEmpty(apiNames));
        assertTrue(apiNames.size() > 0);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasourceDetails(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        DatasourceDTO dto = datasetController.getDataSourceDetails(dataset.stream().findFirst().get().getId(), QField.Type.DATASET.name(),"allOpenNewPipelineCountDS");
        assertNotNull(dto);
        assertTrue(CollectionUtils.isNotEmpty(dto.getDataSourceFields()));
        assertTrue(dto.getDataSourceFields().size() > 0);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasourceDetailsForEntity(){
        EntityDefinition edef = schemaService.getEntity(connectorService.getSyncariConnector().getId(), "opportunity");
        DatasourceDTO dto = datasetController.getDataSourceDetails(edef.getId(), QField.Type.ENTITY.name(),"oppty");
        assertNotNull(dto);
        assertTrue(CollectionUtils.isNotEmpty(dto.getDataSourceFields()));
        assertTrue(CollectionUtils.isNotEmpty(dto.getDataSourceFields().stream().filter(d -> d.getApiName().equals("syncariid")).collect(Collectors.toList())));
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET})
    public void testCreateDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEdited").setDisplayName("allOpenNewPipelineCountDisplayNameEdited").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        List<DatasetDTO> datasetsGets = datasetController.getDatasets();
        assertNotNull(datasetsGets);
        assertTrue(datasetsGets.size() > 1);
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET})
    public void testCreateDatasetForOpenTicketsAccountforOpenPipelineDS(){
        List<Dataset> dataset = datasetRepo.findByName("openTicketsAccountforOpenPipelineDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("openTicketsAccountforOpenPipelineDSEdited").setDisplayName("openTicketsAccountforOpenPipelineDSEdited").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        List<DatasetDTO> datasetsGets = datasetController.getDatasets();
        assertNotNull(datasetsGets);
        assertTrue(datasetsGets.size() > 1);
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET})
    public void testCreateAndGetVariable(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar").setDisplayName("allOpenNewPipelineCountDSEditedForVar").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        VariableDTO variableDTO = new VariableDTO().setDisplayName("testvaar")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDatasetId(datasetDTOCreated.getId()).setDefaultValue("defaultValue").setDatatype("string"));
        VariableDTO dto = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO);
        assertNotNull(dto);
        assertNotNull(dto.getApiName());
        assertNotNull(datasetController.getVariable(datasetDTOCreated.getId(), dto.getApiName()));
        datasetController.deleteDatatset(datasetDTOCreated.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET})
    public void testCreateWithAdditionalParamAndGetVariable(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar").setDisplayName("allOpenNewPipelineCountDSEditedForVar").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        Map<String, Object> additionalParam = Map.of("param", 7);
        VariableDTO variableDTO = new VariableDTO().setDisplayName("testvaar")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDatasetId(datasetDTOCreated.getId()).setDefaultValue("defaultValue").setDatatype("string").setAdditionalParamForDefaultVal(additionalParam));
        VariableDTO dto = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO);
        assertNotNull(dto);
        assertNotNull(dto.getApiName());
        assertNotNull(dto.getVariableDefaultValue());
        assertTrue(MapUtils.isNotEmpty(dto.getVariableDefaultValue().getAdditionalParamForDefaultVal()));
        assertEquals(Integer.valueOf(7), ((Integer)dto.getVariableDefaultValue().getAdditionalParamForDefaultVal().get("param")));
        assertNotNull(datasetController.getVariable(datasetDTOCreated.getId(), dto.getApiName()));
        datasetController.deleteDatatset(datasetDTOCreated.getId());
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET})
    public void testGetVariables(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar1").setDisplayName("allOpenNewPipelineCountDSEditedForVar1").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        VariableDTO variableDTO = new VariableDTO().setDatasetId(datasetDTOCreated.getId()).setDisplayName("testvaar")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDefaultValue("defaultValue").setDatatype("string"));

        VariableDTO variableDTO1 = new VariableDTO().setDatasetId(datasetDTOCreated.getId()).setDisplayName("testvaar1")
                .setDatatype("string").setHelpText("help for variable1").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDefaultValue("defaultValue1").setDatatype("string"));

        VariableDTO dto = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO);
        VariableDTO dto1 = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO1);
        assertNotNull(dto);
        assertNotNull(dto.getApiName());
        assertNotNull(dto1);
        assertNotNull(dto1.getApiName());
        List<VariableDTO> variableDTOS = datasetController.getVariables(datasetDTOCreated.getId());
        assertNotNull(variableDTOS);
        assertEquals(2,variableDTOS.size());
        datasetController.deleteDatatset(datasetDTOCreated.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET})
    public void testDeleteVariable(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar2").setDisplayName("allOpenNewPipelineCountDSEditedForVar2").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        VariableDTO variableDTO = new VariableDTO().setDatasetId(datasetDTOCreated.getId()).setDisplayName("testvaar")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDefaultValue("defaultValue").setDatatype("string"));
        VariableDTO dto = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO);
        assertNotNull(dto);
        assertNotNull(dto.getApiName());
        datasetController.deleteVariable(datasetDTOCreated.getId(), dto.getApiName());
        assertNull(datasetController.getVariable(datasetDTOCreated.getId(), dto.getApiName()));
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET, UPDATE_DATASET})
    public void testCreateAndUpdateVariable(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar3").setDisplayName("allOpenNewPipelineCountDSEditedForVar3").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        VariableDTO variableDTO = new VariableDTO().setDatasetId(datasetDTOCreated.getId()).setDisplayName("testvar1")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDefaultValue("defaultValue").setDatatype("string"));
        VariableDTO dto = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO);
        assertNotNull(dto);
        assertNotNull(dto.getApiName());
        assertNotNull(datasetController.getVariable(datasetDTOCreated.getId(), dto.getApiName()));
        VariableDTO variableDTO1 = new VariableDTO().setApiName(dto.getApiName()).setDatasetId(datasetDTOCreated.getId()).setDisplayName("testvar1updated")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDefaultValue("defaultValue").setDatatype("string"));
        VariableDTO newDto = datasetController.updateVariable(datasetDTOCreated.getId(), variableDTO1);
        assertTrue(newDto.getApiName().equals(dto.getApiName()));
        assertTrue(newDto.getDisplayName().equals("testvar1updated"));
        datasetController.deleteDatatset(datasetDTOCreated.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET})
    public void testGetVariablesForSameDisplayName(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar4").setDisplayName("allOpenNewPipelineCountDSEditedForVar4").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        VariableDTO variableDTO = new VariableDTO().setDatasetId(datasetDTOCreated.getId()).setDisplayName("testvar")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDefaultValue("defaultValue").setDatatype("string"));

        VariableDTO variableDTO1 = new VariableDTO().setDatasetId(datasetDTOCreated.getId()).setDisplayName("testvar")
                .setDatatype("string").setHelpText("help for variable").setRequired(false)
                .setVariableDefaultValue(new VariableValueDTO().setDefaultValue("defaultValue1").setDatatype("string"));

        VariableDTO dto = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO);
        VariableDTO dto1 = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO1);
        VariableDTO dto2 = datasetController.createVariable(datasetDTOCreated.getId(), variableDTO1);
        assertNotNull(dto);
        assertNotNull(dto.getApiName());
        assertNotNull(dto1);
        assertNotNull(dto1.getApiName());
        List<VariableDTO> variableDTOS = datasetController.getVariables(datasetDTOCreated.getId());
        assertNotNull(variableDTOS);
        assertEquals(3,variableDTOS.size());
        assertTrue(dto.getApiName().equals("testvar"));
        assertTrue(dto1.getApiName().equals("testvar__c"));
        assertTrue(dto2.getApiName().equals("testvar__c1"));
        datasetController.deleteDatatset(datasetDTOCreated.getId());
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET})
    public void testCreateDatasetWithoutDatasetConfig(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSWithoutConfig").setDisplayName("allOpenNewPipelineCountDisplayNameEdited").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDatasetConfig(null);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        try{
            datasetController.createDataset(transformer.transformToDTO(datasetCopy));
            fail();
        }catch (SyncariValidationException e){
            assertEquals(e.getMessage(), "Please provide valid dataset config.");
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET})
    public void testCreateDatasetWithSelectedFields(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSWithSelectedFields").setDisplayName("allOpenNewPipelineCountDSWithSelectedFields").setDescription("New description");
        datasetCopy.setSeeded(false);
        DatasetConfig confg = new DatasetConfig();
        QueryFunction func = new NoQueryFunction();
        func.setColumns(List.of(new QField().setName("closedate").setType(QField.Type.ENTITY).setDataType("date").setDatasetId("datasetId")));
        func.setAlias("closedate");
        func.setDataType("date");
        Projection proj = new Projection();
        proj.setFunction(func);
        proj.setAliasName("closedate");
        confg.setProjectionsList(List.of(proj));
        confg.setFromDatasets(datasetCopy.getDatasetConfig().getFromDatasets());
        datasetCopy.setDatasetConfig(confg);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertNotNull(datasetDTOCreated.getDatasetConfig());
        assertFalse(datasetDTOCreated.getDatasetConfig().getSelectedFields().isEmpty());
        assertTrue(datasetDTOCreated.getDatasetConfig().getSelectedFields().stream().findFirst().get().getApiName().equals("closedate"));
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET})
    public void testCreateDeleteDataset(){

        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setVersion(null);
        datasetCopy.setName("allOpenNewPipelineCountDSEdited1").setDisplayName("allOpenNewPipelineCountDisplayNameEdited1").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        assertNotNull(datasetDTOCreated.getId());
        assertTrue(CollectionUtils.isNotEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSEdited1")));
        datasetController.deleteDatatset(datasetDTOCreated.getId());
        assertTrue(CollectionUtils.isEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSEdited1")));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET, UPDATE_DATASET})
    public void testCreateUpdateDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSE").setDisplayName("allOpenNewPipelineCountDisplayNameEdited").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        assertNotNull(datasetDTOCreated.getId());
        datasetDTOCreated.setDisplayName("allOpenNewPipelineCountDisplayNameEdited2");
        DatasetDTO returned = datasetController.updateDataset(datasetDTOCreated.getId(), datasetDTOCreated);
        assertNotNull(returned);
        assertTrue(CollectionUtils.isNotEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSE")));
        datasetController.deleteDatatset(datasetDTOCreated.getId());
        assertTrue(CollectionUtils.isEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSE")));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET, UPDATE_DATASET})
    public void testCreateUpdateDatasetWithTimeGrain(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEWithTimeGrain").setDisplayName("allOpenNewPipelineCountDisplayNameEditeddatasetDTOCreated").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));;
        try{
            assertNotNull(datasetDTOCreated);
            assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
            assertNotNull(datasetDTOCreated.getId());
            datasetDTOCreated.setDisplayName("allOpenNewPipelineCountDisplayNameEdited2WithTimeGrain");
            DatasetConfigDTO config = datasetDTOCreated.getDatasetConfig();
            ProjectionDTO calculatedField = config.getCalculatedFields().get(0);
            DatasetFieldDTO field = calculatedField.getDatasetFields().stream().findFirst().get();
            DatasetFieldDTO fieldDTO = new DatasetFieldDTO();
            fieldDTO.setDatasetType(field.getDatasetType()).setDataType(field.getDataType()).setDatasetId(field.getDatasetId()).setDisplayName("Open pipeline count").setApiName("Open pipeline count");
            config.setGroupBy(List.of(new GroupByDTO().setDatasetField(fieldDTO).setDateGroupByOption("year")));
            config.setGroup(true);
            /*calculatedField.setDatasetFields(List.of(fieldDTO));
            config.setCalculatedFields(List.of(calculatedField));
            datasetDTOCreated.setDatasetConfig(config);*/
            DatasetDTO returned = datasetController.updateDataset(datasetDTOCreated.getId(), datasetDTOCreated);
            assertNotNull(returned);
            assertTrue(CollectionUtils.isNotEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSEWithTimeGrain")));
            assertNotNull(returned.getDatasetConfig());
            assertNotNull(returned.getDatasetConfig().getGroupBy());
            assertTrue(returned.getDatasetConfig().isGroup());
            assertTrue(CollectionUtils.isNotEmpty(returned.getDatasetConfig().getGroupBy()));
            assertTrue(returned.getDatasetConfig().getGroupBy().get(0).getDateGroupByOption().equals("year"));
            assertTrue(returned.getDatasetConfig().getGroupBy().get(0).getDatasetField().getDatasetId().equals(field.getDatasetId()));
        }finally {
            datasetController.deleteDatatset(datasetDTOCreated.getId());
            assertTrue(CollectionUtils.isEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSEWithTimeGrain")));
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET, UPDATE_DATASET})
    public void testCreateUpdateDatasetinDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get();
        Dataset newDataset = new Dataset();
        newDataset.setName("allOpenNewPipelineCountDSinDataset").setDisplayName("allOpenNewPipelineCountDSinDataset").setDescription("New allOpenNewPipelineCountDSinDataset description");
        newDataset.setSeeded(false);
        newDataset.setDraftStatus(DraftStatus.NEW);
        DatasetConfigDTO config = new DatasetConfigDTO();
        SelectedFieldDTO projectionDTO = new SelectedFieldDTO();
        String aliasName = datasetCopy.getDatasetConfig().getProjectionsList().stream().findFirst().get().getAliasName();
        projectionDTO.setApiName(aliasName);
        projectionDTO.setDatasetType(QField.Type.DATASET).setDataType("integer").setDatasetId(datasetCopy.getId()).setApiName(aliasName).setDisplayName("test");
        projectionDTO.setAlias("test");
        config.setSelectedFields(List.of(projectionDTO));
        config.setFromDataset(List.of(new DatasetFromDTO().setDatasetId(datasetCopy.getId()).setDatasetType(DatasourceType.DATASET).setApiName(datasetCopy.getName()).setDisplayName(datasetCopy.getDisplayName())));
        DatasetDTO datasetDTOCreated = transformer.transformToDTO(newDataset);
        datasetDTOCreated.setDatasetConfig(config);
        DatasetDTO returned = datasetController.createDataset(datasetDTOCreated);
        assertNotNull(returned);
        assertNotNull(returned.getDatasetConfig());
        assertFalse(returned.getDatasetConfig().isGroup());
        assertTrue(CollectionUtils.isNotEmpty(returned.getDatasetConfig().getFromDataset()));
        assertTrue(CollectionUtils.isNotEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSinDataset")));
        datasetController.deleteDatatset(returned.getId());
        assertTrue(CollectionUtils.isEmpty(datasetRepo.findByName("allOpenNewPipelineCountDSE")));
    }

    @Test
    @Ignore
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET, UPDATE_DATASET})
    public void testCreateDraftDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSForDraft").setDisplayName("allOpenNewPipelineCountDSForDraft").setDescription("New description");
        datasetCopy.setSeeded(false);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        DatasetFromDTO fromDTO = new DatasetFromDTO().setApiName(datasetCopy.getDatasetConfig().getFromDatasets().get(0).getApiName()).setDatasetType(DatasourceType.ENTITY).setDatasetId(datasetCopy.getDatasetConfig().getFromDatasets().get(0).getDatasetId());
        datasetDTOCreated.getDatasetConfig().setFromDataset(List.of(fromDTO));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        assertNotNull(datasetDTOCreated.getId());
        datasetService.approveDraftDataset(datasetService.getDataset(datasetDTOCreated.getId()), false);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {CREATE_DATASET, UPDATE_DATASET})
    public void getFunctions(){
        List<DatasetFunctionDTO> functions = datasetController.getFunctions();
        assertFalse(functions.isEmpty());
        assertFalse(functions.stream().anyMatch(f -> AggFunctions.NONE.name().equals(f.getName())));
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testgetTimegrainOptions(){
        List<DatasetGroupByTimeGrainOptionsDTO> options = datasetController.getTimeGrainOptions(Optional.empty());
        assertFalse(options.isEmpty());
        assertEquals(8, options.size());
        assertTrue(options.stream().anyMatch(f -> DateGroupByOption.HOURLY.name().equals(f.getDisplayName())));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testgetTimegrainOptionsWithDateType(){
        List<DatasetGroupByTimeGrainOptionsDTO> options = datasetController.getTimeGrainOptions(Optional.of("date"));
        assertFalse(options.isEmpty());
        assertEquals(5, options.size());
        assertFalse(options.stream().anyMatch(f -> DateGroupByOption.HOURLY.name().equals(f.getDisplayName())));
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasetsAndEntitiesDef(){
        List<DatasetFromDTO> datasetInfo = datasetController.getDatasources(false);
        assertTrue(CollectionUtils.isNotEmpty(datasetInfo));
        assertTrue(datasetInfo.size() > 0);
        assertTrue(datasetInfo.stream().filter(di -> di.getDatasetType().equals(DatasourceType.DATASET)).collect(Collectors.toList()).size() > 0);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testGetDatasetsAndEntitiesDefWithEntityInfo(){
        List<DatasetFromDTO> datasetInfo = datasetController.getDatasources(true);
        assertTrue(CollectionUtils.isNotEmpty(datasetInfo));
        assertTrue(datasetInfo.size() > 0);
        assertTrue(datasetInfo.stream().filter(di -> di.getDatasetType().equals(DatasourceType.DATASET)).collect(Collectors.toList()).size() > 0);
        assertTrue(datasetInfo.stream().filter(di -> di.getDatasetType().equals(DatasourceType.ENTITY)).collect(Collectors.toList()).size() > 0);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET, UPDATE_DATASET})
    public void testUpdateDatasetValidation(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("testvalidation").setDisplayName("allOpenNewPipelineCountDSForDraft").setDescription("New description");
        datasetCopy.setSeeded(false);

        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        try{
            datasetController.updateDataset(null, datasetDTOCreated);
            fail();
        }catch (SyncariValidationException e){
            assertTrue(e.getMessage().contains("Dataset Id cannot be empty"));
        }

        datasetCopy.setName("test").setDisplayName("newtest").setDescription("New description");
        try{
            datasetController.updateDataset(datasetDTOCreated.getId(), null);
            fail();
        }catch (SyncariValidationException e){
            assertTrue(e.getMessage().contains("Dataset information cannot be empty to create dataset"));
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET})
    public void testSchemaName(){
        assertNotNull(datasetController.getSchema());
        assertEquals("syncari_"+ SyncariContext.getSyncariId().toLowerCase(),datasetController.getSchema());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET, DELETE_DATASET, UPDATE_DATASET})
    public void testGroupTimeGrainProjection(){
        Optional<EntityDefinition> edef = entityProxyRepo.findEntityByConnectorIdAndApiName(connectorService.getSyncariConnector().getId(), "opportunity");
        DatasetFieldDTO fieldDTO = new DatasetFieldDTO().setApiName("Closed date")
                .setDatasetType(QField.Type.ENTITY).setDisplayName("Closed date").setDatasetId(edef.get().getId()).setDataType("date");
        ProjectionDTO projectionDTO = datasetController.getTimeGrainProjectionForGroup(new GroupByDTO().setDateGroupByOption("month").setDatasetField(fieldDTO));
        assertNotNull(projectionDTO);
        assertNotNull(projectionDTO.getDatasetFields());
        assertEquals(2, projectionDTO.getDatasetFields().size());
        assertEquals("month", projectionDTO.getDatasetFields().stream().filter(x -> x.getDatasetType().equals(QField.Type.LITERAL)).collect(Collectors.toList()).stream().findFirst().get().getApiName());
        assertEquals("Closed date", projectionDTO.getDatasetFields().stream().filter(x -> x.getDatasetType().equals(QField.Type.ENTITY)).collect(Collectors.toList()).stream().findFirst().get().getApiName());
        assertEquals(AggFunctions.DATE_PART, projectionDTO.getAggFunctions());
        assertEquals("Opportunity:Closed date(month)", projectionDTO.getAliasName());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {VIEW_DATASET, CREATE_DATASET,EXPORT_DATASET,VIEW_EXPORT_JOBS, DELETE_EXPORT})
    public void testCreateGetAndDeleteDatasetExportJob(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForExport").setDisplayName("allOpenNewPipelineCountDisplayNameEdited").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetDTO datasetDTOCreated = datasetController.createDataset(transformer.transformToDTO(datasetCopy));
        assertNotNull(datasetDTOCreated);
        assertTrue(datasetDTOCreated.getName().contains(datasetCopy.getName()));
        List<DatasetDTO> datasetsGets = datasetController.getDatasets();
        assertNotNull(datasetsGets);
        assertTrue(datasetsGets.size() > 1);
        List<DatasetDTO> createdDSDTO = datasetsGets.stream().filter(d -> d.getName().equalsIgnoreCase("allOpenNewPipelineCountDSEditedForExport")).collect(Collectors.toList());
        createdDSDTO.stream().findFirst().ifPresent(dto -> {
            DatasetExport export = new DatasetExport().setDatasetToBeExported(transformer.transformToDataset(dto))
                    .setDatasetId(dto.getId()).setRequestedTime(Instant.now()).setExpiredTime(Instant.now().plusSeconds(10))
                    .setUserName("system");
            datasetExportService.saveDatasetExport(export);
            List<DatasetExportJobDTO> exportJobDTOS = datasetController.getExportJobs(dto.getId());
            assertTrue(CollectionUtils.isNotEmpty(exportJobDTOS));
            assertEquals(1, exportJobDTOS.size());
            datasetExportRepo.deleteById(exportJobDTOS.stream().findFirst().get().getExportJobId());
        });
    }
}
