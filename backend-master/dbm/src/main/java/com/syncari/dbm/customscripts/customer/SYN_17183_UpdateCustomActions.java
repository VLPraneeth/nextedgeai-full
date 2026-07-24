package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.actions.CustomActionDefinition;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SYN_17183_UpdateCustomActions {

    @ChangeSet(order = "001", id = "updateCustomActions", author = "rohit", runAlways = true)
    public void updateCustomActions(MongoTemplate template) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var actionIds = System.getProperty("actionIds");
        List<String> actionIdList =  Stream.of(actionIds.split(":")).collect(Collectors.toList());

        ActionDefinitionRepo repo = MigrationContext.getActionDefinitionRepo();
        actionIdList.forEach(aId -> {
            Optional<ActionDefinition> actionDefinition = repo.findById(aId);
            actionDefinition.ifPresentOrElse(a -> {
                if (a instanceof CustomActionDefinition){
                    if (!dryRun){
                        ((CustomActionDefinition)a).setGlobalSharedItemId(null);
                        repo.save(a);
                    }else{
                        log.info("Running in dry run mode, not updating custom action with id {}", aId);
                    }
                }else{
                    log.info("action definition is not an Instance of CustomActionDefinition for id {}",aId);
                }
            },()-> log.info("CustomActionDefinition is not present for Id {}",aId));
        });
    }
}
