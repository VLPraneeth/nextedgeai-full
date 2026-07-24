package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.ErrorDTO;
import com.syncari.api.rest.controllers.data.insights.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.Visualization;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.InsightsDashboardService;
import com.syncari.core.service.InsightsService;
import com.syncari.core.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.core.model.insights.Datacard;
import com.syncari.core.service.DatacardService;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Slf4j
@RestController
@RequestMapping("/api/v1/insights/datacard")
public class DatacardController {

    @Autowired
    InsightsTransformer transformer;

    @Autowired
    DatacardService datacardService;

    @Autowired
    InsightsService insightsService;

    @Autowired
    DatasetService datasetService;

    @Autowired
    DatasetTransformer datasetTransformer;

    @Autowired
    InsightsDashboardService dashboardService;

    @Autowired
    UserService userService;

    @Secured(CREATE_DATACARD)
    @RequestMapping(method = RequestMethod.POST)
    public DatacardDTO createDatacard(@RequestBody DatacardDTO draft) {
        Datacard datacard = transformer.toDatacard(draft);
        datacard.setDraftStatus(DraftStatus.APPROVED);
        datacard = datacardService.createDatacard(datacard);
        return transformer.toDatacardDTO(null, datacard);
    }

    @Secured({CREATE_DATACARD, CREATE_DATASET})
    @RequestMapping(method = RequestMethod.POST, value = "/withDataset")
    public DatacardWithDatasetDTO createDatacardWithDatset(@RequestBody DatacardWithDatasetDTO datacardWithDatset) {
        Dataset dataset = datasetTransformer.transformToDataset(datacardWithDatset.getDataset());
        Dataset savedDataset = datasetService.createDataset(dataset);

        Datacard datacard = transformer.toDatacard(datacardWithDatset.getDatacard());
        datacard.getContents().forEach(viz -> {
            viz.getConfig().setDatasetId(savedDataset.getId());
        });
        datacard = datacardService.createDatacard(datacard);

        return new DatacardWithDatasetDTO()
                .setDatacard(transformer.toDatacardDTO(null, datacard))
                .setDataset(datasetTransformer.transformToDTO(dataset));
    }

    @Secured({UPDATE_DATACARD, CREATE_DATASET})
    @RequestMapping(method = RequestMethod.PUT, value = "/{datacardId}/withDataset")
    public DatacardWithDatasetDTO updateDatacardWithDatset(@PathVariable String datacardId, @RequestBody DatacardWithDatasetDTO datacardWithDatset) {
        Dataset dataset = datasetTransformer.transformToDataset(datacardWithDatset.getDataset());
        Dataset savedDataset = datasetService.createDataset(dataset);

        Datacard datacard = transformer.toDatacard(datacardWithDatset.getDatacard());
        datacard.getContents().forEach(viz -> {
            viz.getConfig().setDatasetId(savedDataset.getId());
        });
        Datacard existing = datacardService.findDatacard(datacardId)
                .orElseThrow(() -> new NotFoundException(Datacard.class, "id", datacardId));
        validateCondition(existing.isSeeded(), "Cannot update seeded datacards");
        datacard = datacardService.updateDatacard(datacardId, datacard);

        return new DatacardWithDatasetDTO()
                .setDatacard(transformer.toDatacardDTO(null, datacard))
                .setDataset(datasetTransformer.transformToDTO(dataset));
    }

    @Secured(VIEW_DATACARD)
    @RequestMapping(method = RequestMethod.GET)
    public List<DatacardDTO> listDatacards() {
        List<Datacard> datacards = datacardService.getAllDatacards();
        // return only published datacards until we cleanup all drafts
        return datacards.stream().filter(d -> d.isApproved())
                .map(d -> {
                    try {
                        return transformer.toDatacardDTO(null, d);
                    } catch (Exception ex) {
                        String message = null != d.getName() ?
                            new StringBuilder()
                                    .append("Error transforming datacard: ")
                                    .append(d.getName())
                                    .append(" ").toString() : "";
                        log.error(message + ex.getMessage(), ex);
                    }
                    return null;
                })
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }

    @Secured(VIEW_DATACARD)
    @RequestMapping(method = RequestMethod.GET, value = "/{datacardId}")
    public DatacardDTO getDatacard(@PathVariable String datacardId) {
        Datacard datacard = datacardService.getDatacard(datacardId);
        return transformer.toDatacardDTO(null, datacard);
    }

    @Secured(DELETE_DATACARD)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{datacardId}/delete")
    public void deleteDatacard(@PathVariable String datacardId) {
        var datacard = datacardService.findDatacard(datacardId);
        datacard.ifPresent(d -> datacardService.deleteDatacard(d));
    }

    @Secured(UPDATE_DATACARD)
    @RequestMapping(method = RequestMethod.PUT, value = "/{datacardId}")
    public DatacardDTO updateDatacard(@PathVariable String datacardId, @RequestBody DatacardDTO datacardDTO) {
        Datacard datacard = transformer.toDatacard(datacardDTO);
        Datacard existing = datacardService.findDatacard(datacardId)
                .orElseThrow(() -> new NotFoundException(Datacard.class, "id", datacardId));
        validateCondition(existing.isSeeded(), "Cannot update seeded datacards");
        datacard = datacardService.updateDatacard(datacardId, datacard);
        return transformer.toDatacardDTO(null, datacard);
    }

    @Secured({CREATE_DATACARD, UPDATE_DATACARD})
    @RequestMapping(method = RequestMethod.POST, value = "/{datacardId}/preview")
    public DatacardDTO previewDatacard(@PathVariable String datacardId, @RequestBody DatacardDTO datacardDTO) {
        // TODO: Refactor this
        Datacard datacard = transformer.toDatacard(datacardDTO);

        // retrieve and add data to datacard
        Visualization viz = datacard.getContents().get(0);
        ErrorDTO error = null;
        try {
            insightsService.validatePreRequisites(viz.getConfig());
        } catch (SyncariValidationException ex){
            error = new ErrorDTO();
            error.setTitle("Missing Requirements");
            error.setBody(ex.getMessage());
            log.error(ex.getMessage(), ex);
        }

        List<Map<String, Object>> data = Collections.emptyList();
        try {
            if(datacard.isSeeded()) {
                data = insightsService.retrieveDataForSeededVisualization(viz);
            } else {
                // run the dataset query -> get data -> extract relevant columns
                data = insightsService.retrieveDataForVisualization(viz, Optional.empty());
            }
        } catch (Exception ex){
            error = new ErrorDTO();
            error.setTitle("Error retrieving data");
            error.setBody(ex.getMessage());
            log.error(ex.getMessage(), ex);
        }

        VizData vizData = transformer.toVizData(viz, data);
        vizData.setError(error);
        DatacardDTO dto = transformer.toDatacardDTO(null, datacard);
        dto.getContents().setData(vizData);

        return dto;
    }

    @Secured({CREATE_DATACARD, UPDATE_DATACARD})
    @RequestMapping(method = RequestMethod.POST, value = "/preview")
    public DatacardDTO previewDatacard(@RequestBody DatacardWithDatasetDTO datacardWithDatasetDTO) {
        DatasetDTO datasetDTO = datacardWithDatasetDTO.getDataset();
        Dataset dataset  = datasetTransformer.transformToDataset(datasetDTO);
        DatacardDTO datacardDTO = datacardWithDatasetDTO.getDatacard();
        Datacard datacard = transformer.toDatacard(datacardDTO);

        // retrieve and add data to datacard
        Visualization viz = datacard.getContents().get(0);
        ErrorDTO error = null;
        try {
            insightsService.validatePreRequisites(viz.getConfig());
        } catch (SyncariValidationException ex){
            error = new ErrorDTO();
            error.setTitle("Missing Requirements");
            error.setBody(ex.getMessage());
            log.error(ex.getMessage(), ex);
        }

        List<Map<String, Object>> data = Collections.emptyList();
        try {
            // run the dataset query -> get data -> extract relevant columns
            data = insightsService.retrieveDataForVisualization(viz, Optional.of(dataset));
        } catch (Exception ex){
            error = new ErrorDTO();
            error.setTitle("Error retrieving data");
            error.setBody(ex.getMessage());
            log.error(ex.getMessage(), ex);
        }

        VizData vizData = transformer.toVizData(viz, data);
        vizData.setError(error);
        DatacardDTO dto = transformer.toDatacardDTO(null, datacard);
        dto.getContents().setData(vizData);

        return dto;

    }

    @Secured(VIEW_DATACARD)
    @RequestMapping(method = RequestMethod.GET, value = "/{datacardId}/dependencies")
    public List<InsightsDependencyDTO> getDatacardDependencies(@PathVariable String datacardId){
        List<InsightsDependencyDTO> dependencies = new ArrayList<>();
        datacardService.getDatacardDependencies(datacardId).forEach(d -> {
            // retrieve dashboard if exists
            insightsService.findDashboard(d.getFromId()).ifPresent(dashboard -> {
                InsightsDependencyDTO dep = new InsightsDependencyDTO();
                dep.setId(d.getFromId());
                dep.setType(d.getFromComponent());
                dep.setDraftStatus(dashboard.getDraftStatus());
                if(dashboard.hasPublishedParent()){
                    // for deep linking draft still uses the published id
                    dep.setId(dashboard.getParentId());
                    dep.setNestedDraft(true);
                }
                dep.setName(dashboard.getDisplayName());
                if(!StringUtils.isBlank(dashboard.getCreatedBy())){
                    userService.findUserById(dashboard.getCreatedBy()).ifPresent(u -> dep.setAuthor(u.getName()));
                }
                dependencies.add(dep);
            });

        });
        return dependencies;
    }
}
