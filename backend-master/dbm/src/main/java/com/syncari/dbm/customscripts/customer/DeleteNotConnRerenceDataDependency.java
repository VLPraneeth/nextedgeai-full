package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.service.ComponentDependencyService;
import com.syncari.core.service.ReferenceDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class DeleteNotConnRerenceDataDependency {

    @ChangeSet(order = "001", id = "deleteNotConnecRefDataComponentDependency", author = "rohit", runAlways = true)
    public void deleteNotConnecRefDataComponentDependency(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var component = "referencedata";
        ComponentDependencyService compService = MigrationContext.getComponentDependencyService();
        ReferenceDataService referenceDataServices = MigrationContext.getReferenceDataService();
        MappingGraphRepo repo = MigrationContext.getMappingGraphRepo();
        List<ReferenceDataMeta> referenceDataMetas = referenceDataServices.findAll();
        for (ReferenceDataMeta referenceDataMeta : referenceDataMetas) {
            String referenceMetaId = referenceDataMeta.getId();
            if (StringUtils.isNotEmpty(referenceMetaId) && StringUtils.isNotEmpty(component)){
                List<ComponentDependency> dependencyList =  compService.findDependenciesFor(referenceMetaId, ComponentType.valueOf(component));
                if (CollectionUtils.isNotEmpty(dependencyList)){
                    log.info("dependencyList is not empty for depId {} and component {}, list is {}", referenceMetaId, component, dependencyList);
                    // Filter list only for pipeline
                    List<ComponentDependency> pipelineDepList = dependencyList.stream().filter(f -> f.getFromComponent().name().equals(ComponentType.pipeline.name())).collect(Collectors.toList());
                    for (ComponentDependency componentDependency : pipelineDepList) {
                        String graphId = componentDependency.getFromId();
                        Optional<MappingGraph> graph = repo.findById(graphId);
                        graph.ifPresentOrElse(g -> {
                            if (g.getDraftStatus().name().equals(DraftStatus.ARCHIVED.name())){
                                if (!dryRun){
                                    compService.deleteById(componentDependency.getId());
                                    log.info("Deleted dependencies running in dry run mode");
                                }else{
                                    log.info("Not deleting dependencies running in dry run mode");
                                }
                            }else{
                                log.info("Draft status is not archived, it is {}, so not deleting the dependency for graphId {}", g.getDraftStatus(), g.getId());
                            }
                        },() -> {
                            log.info("Graph with id {}, is not present so deleting all its dependencies", graphId);
                            if (!dryRun){
                                compService.deleteById(componentDependency.getId());
                                log.info("Deleted dependencies running in dry run mode");
                            }else{
                                log.info("Not deleting dependencies running in dry run mode");
                            }
                        });

                    }
                }else{
                    log.info("dependencyList is empty for depId {} and component {}", referenceMetaId, component);
                }

            }
        }
    }
}
