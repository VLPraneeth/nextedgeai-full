package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.service.ComponentDependencyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class DeleteComponentDependency {

    @ChangeSet(order = "001", id = "deleteComponentDependency", author = "rohit", runAlways = true)
    public void deleteComponentDependency(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var depId = System.getProperty("toId");
        var component = System.getProperty("toComponent");
        ComponentDependencyService service = MigrationContext.getComponentDependencyService();
        if (StringUtils.isNotEmpty(depId) && StringUtils.isNotEmpty(component)){
            List<ComponentDependency> dependencyList =  service.findDependenciesFor(depId, ComponentType.valueOf(component));
            if (CollectionUtils.isNotEmpty(dependencyList)){
                log.info("dependencyList is not empty for depId {} and component {}, list is {}", depId, component, dependencyList);
                if (!dryRun){
                    service.deleteDependenciesOn(depId,ComponentType.valueOf(component));
                    log.info("Deleted dependencies running in dry run mode");
                }else{
                    log.info("Not deleting dependencies running in dry run mode");
                }
            }else{
                log.info("dependencyList is empty for depId {} and component {}", depId, component);
            }

        }



    }

    }
