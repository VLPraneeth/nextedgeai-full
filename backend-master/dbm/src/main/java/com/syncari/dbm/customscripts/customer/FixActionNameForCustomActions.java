package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.CustomActionDefinition;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.Fragment;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.repositories.customer.FragmentRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FixActionNameForCustomActions {

    @ChangeSet(order = "001", id = "setNameInActionDefinition", author = "abhinav", runAlways = true)
    public void setNameInActionDefinition(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        ActionDefinitionRepo actionRepo = MigrationContext.getActionDefinitionRepo();

        var customActions = actionRepo.findEditableActions();
        customActions.forEach(ad -> {
            var apiName = ((CustomActionDefinition) ad).getApiName();
            log.info("Updating custom action definition {}({}) name as {}", ad.getDisplayName(), ad.getId(), apiName);
            ad.setName(apiName);
        });

        // save all custom action definition back with updated name
        if(!dryRun) {
            actionRepo.saveAll(customActions);
        }
    }

    @ChangeSet(order = "002", id = "setNameInCustomActionNode", author = "abhinav", runAlways = true)
    public void setNameInCustomActionNode(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        MappingNodeRepo nodeRepo = MigrationContext.getMappingNodeRepo();
        ActionDefinitionRepo actionRepo = MigrationContext.getActionDefinitionRepo();

        var customActions = actionRepo.findEditableActions();
        Map<String, CustomActionDefinition> actionMap = customActions.stream().filter(a -> a.isCustom()).map(a -> (CustomActionDefinition) a).collect(Collectors.toMap(a -> a.getId(), a -> a));


        List<MappingNode> customActionNodes = nodeRepo.findByApiName(ActionConstants.HTTP_ACTION);
        customActionNodes.forEach(node -> {
            GenericActionConfig actionConfig = node.getTypedConfiguration();
            String actionId = actionConfig.getConfigMap().get("configId").toString();
            CustomActionDefinition customAction = actionMap.get(actionId);
            if(customAction != null) {
                log.info("Updating custom action node {}({}) with apiName as {}", node.getName(), node.getId(), customAction.getApiName());
                actionConfig.setName(customAction.getApiName());
                node.setApiName(customAction.getApiName());
            }
            node.setConfiguration(actionConfig);
        });

        // save all nodes back with updated apiName
        if(!dryRun) {
            nodeRepo.saveAll(customActionNodes);
        }
    }


    @ChangeSet(order = "003", id = "setNameInFragmentNode", author = "abhinav", runAlways = true)
    public void setNameInFragmentNode(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        FragmentRepo fragmentRepo = MigrationContext.getFragmentRepo();
        ActionDefinitionRepo actionRepo = MigrationContext.getActionDefinitionRepo();

        var customActions = actionRepo.findEditableActions();
        Map<String, CustomActionDefinition> actionMap = customActions.stream().filter(a -> a.isCustom()).map(a -> (CustomActionDefinition) a).collect(Collectors.toMap(a -> a.getId(), a -> a));

        // check all fragments  and corresponding custom action node if found then update the name
        List<Fragment> fragments = fragmentRepo.findAll();
        fragments.forEach(f -> {
            f.getFragmentGraph().getNodes().forEach(node -> {
                if(MappingNodeType.ACTION.equals(node.getType())){
                    GenericActionConfig actionConfig = node.getTypedConfiguration();
                    String actionId = actionConfig.getConfigMap().get("configId").toString();
                    CustomActionDefinition customAction = actionMap.get(actionId);
                    if(customAction != null && customAction.isCustom()) {
                        log.info("Updating fragment {}({}) custom action node {} with apiName as {}", f.getName(), f.getId(), node.getName(), customAction.getApiName());
                        actionConfig.setName(customAction.getApiName());
                        node.setApiName(customAction.getApiName());
                    }
                    node.setConfiguration(actionConfig);
                }
            });
        });

        // save all nodes back with updated apiName
        if(!dryRun) {
            fragmentRepo.saveAll(fragments);
        }
    }
}
