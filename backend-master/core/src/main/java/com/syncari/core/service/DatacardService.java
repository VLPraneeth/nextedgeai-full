package com.syncari.core.service;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.Tag;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.DatasetRepo;

import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static java.lang.String.format;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class DatacardService extends DraftService<Datacard>{

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    DatacardRepo datacardRepo;
    
    @Autowired
    TagService tagService;

    @Autowired
    InsightsDashboardService dashboardService;

    @Autowired
    ComponentDependencyService dependencyService;

    public Visualization createVisualizationFromDataset(Visualization visualization){
        Visualization visualizationResult = visualization.copy();
        VizConfig vizConfig = visualization.getConfig();
        String datasetId = vizConfig.getDatasetId();
        VizType vizType = visualization.getType();

        Optional<Dataset> dataset =  datasetRepo.findById(datasetId);
        dataset.ifPresent(ds -> {
            DatasetConfig dsConfig = ds.getDatasetConfig();
            String version  = ds.getVersion();
            if (StringUtils.isNotEmpty(version)){
                VizConfig updatedVizConfig = createVizConfig(vizType,vizConfig);
                updatedVizConfig.setJoins(dsConfig.getJoin());
                updatedVizConfig.setPredicate(dsConfig.getPredicate());
                updatedVizConfig.setSortList(dsConfig.getOrder());
                updatedVizConfig.setGroupingColumns(dsConfig.getAggregate());
                updatedVizConfig.setFromWithAlias(dsConfig.getFromDatasets().stream().collect(Collectors.toMap(df -> df.getDatasetId(), df->
                        StringUtils.isNotEmpty(df.getAlias()) ? df.getAlias() : df.getDatastoreName())));
                updatedVizConfig.setLimit(dsConfig.getLimit());
                updatedVizConfig.setName(vizConfig.getName());
                updatedVizConfig.setDatasetId(datasetId);
                updatedVizConfig.setPipelineDependencies(vizConfig.getPipelineDependencies());
                List<QueryField> queryFields = new ArrayList<>();
                List<Projection> projections = dsConfig.getProjectionsList();
                projections.forEach(projection -> {
                    QueryFunction queryFunction = projection.getFunction();
                    if (queryFunction instanceof NoQueryFunction){
                        QueryField field = new SimpleQField();
                        field.setQueryFunction(queryFunction);
                        field.setDisplayFormat(visualization.getDisplayFormat());
                        queryFields.add(field);
                    }else{
                        QueryField field = new ComplexQField();
                        field.setQueryFunction(queryFunction);
                        field.setDisplayFormat(visualization.getDisplayFormat());
                        queryFields.add(field);
                    }
                    log.info("QueryFunction used is {}", queryFunction );

                });
                if (CollectionUtils.isNotEmpty(vizConfig.getColumns())){
                    if (CollectionUtils.isNotEmpty(queryFields)){
                        Map<String, QueryField> queryFieldMap = queryFields.stream().collect(Collectors.toMap(QueryField :: getAlias, Function.identity()));
                        if (MapUtils.isNotEmpty(queryFieldMap)){
                            vizConfig.getColumns().forEach(qf -> {
                                if (queryFieldMap.containsKey(qf.getAlias())){
                                    QueryField qfFromDsConfig = queryFieldMap.get(qf.getAlias());
                                    qf.setQueryFunction(qfFromDsConfig.getQueryFunction());
                                }
                            });
                        }
                    }
                    updatedVizConfig.setColumns(vizConfig.getColumns());
                }else{
                    updatedVizConfig.setColumns(queryFields);
                }
                if (MapUtils.isNotEmpty(ds.getVariablesMap())){
                    updatedVizConfig.setVariablesMap(ds.getVariablesMap());
                }
                log.info("Updating viz config based on dataset. New vizconfig is {} from dataset {} and QueryFields for that is {}"
                        ,updatedVizConfig, datasetId, queryFields);
                visualizationResult.setConfig(updatedVizConfig);
            }
        });
        return visualizationResult;
    }


    private VizConfig createVizConfig(VizType vizType,VizConfig vizConfig){
        switch (vizType){
            case METRIC:
                return new MetricVizConfig();
            case TABLE:
                return new TableVizConfig();
            case LINE:
                LineVizConfig lineVizConfig = new LineVizConfig();
                lineVizConfig.setXAxis(((LineVizConfig)vizConfig).getXAxis());
                lineVizConfig.setYAxis(((LineVizConfig)vizConfig).getYAxis());
                lineVizConfig.setSeries(((LineVizConfig)vizConfig).getSeries());
                return lineVizConfig;
            case BAR:
            case COLUMN:
                BarVizConfig barVizConfig = new BarVizConfig();
                barVizConfig.setXAxis(((BarVizConfig)vizConfig).getXAxis());
                barVizConfig.setYAxis(((BarVizConfig)vizConfig).getYAxis());
                barVizConfig.setSeries(((BarVizConfig)vizConfig).getSeries());
                return barVizConfig;
            default:
                throw new UnsupportedOperationException("Not supported type of visualization " + vizType);

        }
    }

    public List<Datacard> getAllDatacards(){
        List<Datacard> datacards = datacardRepo.findAllDatacards();
        return datacards.stream().map(d -> {
            if(d.isSeeded()){
                return DatacardSeed.populateDataCard(d);
            }else {
                return d;
            }
        }).collect(Collectors.toList());
    }

    public Datacard getDatacard(String datacardId){
        return findDatacard(datacardId).orElseThrow(() -> new NotFoundException(Datacard.class, "id", datacardId));
    }

    public Optional<Datacard> findDatacard(String datacardId){
        return datacardRepo.findById(datacardId).stream().map(d -> DatacardSeed.populateDataCard(d)).findFirst();
    }

    public Optional<Datacard> findDatacardByName(String name){
        return datacardRepo.findByName(name).stream().map(d -> DatacardSeed.populateDataCard(d)).findFirst();
    }

    public Datacard getSeeded(String datacardId){
        Datacard datacard = datacardRepo.findById(datacardId).orElseThrow(() -> new NotFoundException(Datacard.class, "id", datacardId));
        return DatacardSeed.populateDataCard(datacard);
    }

    public Datacard getSeededOrFromDataset(String datacardId){
        Datacard datacard = datacardRepo.findById(datacardId).orElseThrow(() -> new NotFoundException(Datacard.class, "id", datacardId));
        log.info("Datacard from db is {} for datacardId {}", datacard, datacardId);
        if(datacard.isSeeded()) {
            datacard = DatacardSeed.populateDataCard(datacard);
            log.info("Populated Datacard is {} for datacardId {}", datacard, datacardId);
            List<Visualization> visualizationList = new ArrayList<>();
            datacard.getContents().forEach(vis -> {
                try{
                    visualizationList.add(this.createVisualizationFromDataset(vis));
                }catch (Exception e){
                    log.error("Error loading this datacard vis {}, exception is {}", vis.getDisplayName(), ExceptionUtils.getStackTrace(e));
                }
            });

            datacard.setContents(visualizationList);
        }
        log.info("After setting contents Datacard is {} for datacardId {}", datacard, datacardId);
        return datacard;
    }


	public Datacard createDatacard(Datacard datacard) {
		validateCondition(StringUtils.isEmpty(datacard.getDisplayName()), i18n("datacard_empty_displayname"));
        validateCondition(!StringUtils.isEmpty(datacard.getId()), i18n("datacard_with_id_already_exists"), datacard.getId());
        // create apiName using displayName
        String apiName = StringUtils.isEmpty(datacard.getName())
                ? TextUtil.createApiName(datacard.getDisplayName())
                : datacard.getName();
        // check for duplicate name and add numbered suffix
        Set<String> existingDatacardNames = getAllDatacards().stream().map(d -> d.getName()).collect(Collectors.toSet());
        int i = 1;
        while(existingDatacardNames.contains(apiName)){
            apiName = apiName + "_" + i++;
        }
        datacard.setName(apiName);
        datacard.setDraftStatus(DraftStatus.APPROVED);
        var saved = save(datacard);
        // save tags
        var tagMap = datacard.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.datacard, datacard.getId());
        datacard.setTags(tags);

        // update component dependencies
        updateDatacardDependencies(saved);

        return datacard;
	}


	public Datacard approveDraftDatacard(Datacard draft) {
		validateCondition(!draft.isDraft(), i18n("datacard_approve_failed_no_draft"), draft.getDisplayName());
        // TODO: add more validations for missing configs
        log.info("Approving datacard draft {}", draft.getDisplayName());
        var approved = approveDraft(draft);

        // associate draft tags tp approved datacard if ids are different
        List<Tag> tags = tagService.updateTagIds(draft.getId(), approved.getId(), Taggable.datacard);
        approved.setTags(tags);
        return approved;
	}


	public Datacard createDraftFor(Datacard model) {
        validateCondition(model.isSeeded(), i18n("datacard_create_draft_seeded"));
		validateCondition(!model.isApproved(), i18n("datacard_create_draft_missing_published"));
        validateCondition(hasDraft(model), i18n("datacard_draft_exists"), model.getDisplayName());
        Datacard draft = super.createDraftFor(model);
        // associate draft tags with approved datacard if ids are different but not remove from draft
        List<Tag> tags = tagService.cloneTags(model.getId(), draft.getId(), Taggable.datacard);
        draft.setTags(tags);
        return draft;
	}


	public void discardDraftDatacard(Datacard draft) {
		validateCondition(!draft.isDraft(), i18n("datacard_discard_failed_no_draft"), draft.getDisplayName());
        log.info("Discarding datacard draft {}", draft.getDisplayName());
        deleteDatacard(draft);
	}

    public void deleteDatacard(Datacard datacard) {
        validateCondition(null==datacard, i18n("datacard_missing_failed_delete"));
        validateCondition(datacard.isSeeded(), i18n("datacard_delete_seeded_error"));

        // find all dependent dashboards and add their name in message along with the draftStatus
        List<InsightsDashboard> dependentDashboards = new ArrayList<>();
        getDatacardDependencies(datacard.getId()).forEach(d -> {
            // retrieve datacard if exists and add it as dependency
            dashboardService.findDashboard(d.getFromId()).ifPresent(dashboard -> {
                dependentDashboards.add(dashboard);
            });
        });
        List<String> dependentDashboardNames = dependentDashboards.stream().map(d -> {
            String draftStatus = d.isDraft() ? "Draft" : "Published";
            return d.getDisplayName() + String.format("(%s)", draftStatus);
        }).collect(Collectors.toList());
        validateCondition(CollectionUtils.isNotEmpty(dependentDashboards), i18n("datacard_delete_used_in_dashboards_error", datacard.getDisplayName(), String.join(", ", dependentDashboardNames)));

        log.info("Deleting datacard  {}", datacard.getDisplayName());
        delete(datacard);
        // delete all tags associated with the draft
        tagService.removeTagsFor(Taggable.datacard, datacard.getId());

        // delete all dependencies of this datacard
        dependencyService.deleteDependenciesBy(datacard.getId(), ComponentType.datacard);
        // delete all dependencis datacard depends on
        dependencyService.deleteDependenciesOn(datacard.getId(), ComponentType.datacard);
    }


	public Datacard updateDatacard(String datacardId, Datacard incoming) {
		Datacard existing = findDatacard(datacardId).get();
		//validateCondition(existing.isSeeded(), "Cannot update seeded datacards");
        // add more validations if any
        log.info("Updating datacard draft {}", existing.getDisplayName());
        existing.setDisplayName(incoming.getDisplayName());
        existing.setDescription(incoming.getDescription());
        existing.setSeeded(incoming.isSeeded());
        existing.setName(incoming.getName());
        existing.setConfiguration(incoming.getConfiguration());
        // TODO: validate incoming contents
        existing.setContents(incoming.getContents());
        existing.setDraftStatus(DraftStatus.APPROVED);
        Datacard updated = save(existing);

        // save the newly added tags and delete the removed tags
        List<Tag> incomingTags = incoming.getTags();
        tagService.updateTagsFor(existing.getId(), Taggable.datacard, incomingTags);

        // update component dependencies
        updateDatacardDependencies(updated);

        return updated;
		
	}

    public void updateDatacardDependencies(Datacard datacard) {
        List<ComponentDependency> dependencies = new ArrayList<>();
        datacard.getContents().forEach(viz -> {
            if(null != viz.getConfig()) {
                var dep = new ComponentDependency(datacard.getId(), ComponentType.datacard, viz.getConfig().getDatasetId(), ComponentType.dataset);
                dependencies.add(dep);
            }
        });
        dependencyService.updateDependenciesFor(datacard.getId(), ComponentType.datacard, dependencies);
    }

	public Datacard save(Datacard datacard){
        return datacardRepo.save(datacard);
    }

    public List<Datacard> saveAll(List<Datacard> datacards){
        return datacardRepo.saveAll(datacards);
    }


	@Override
	protected DraftableRepo<Datacard> getDraftableRepo() {
		return datacardRepo;
	}


	@Override
	protected void processArchived(Datacard archived) {
		archived.setName(format("%s_%s_%s", archived.getName(), archived.getId(), DELETED));	
	}

	public List<ComponentDependency> getDatacardDependencies(String datacardId){
        return dependencyService.findDependenciesFor(datacardId, ComponentType.datacard);
    }
}
