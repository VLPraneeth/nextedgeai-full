package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_11489_DeleteDuplicateDraft {

    @ChangeSet(order = "001", id = "deleteDuplicateDraft", author = "varsha", runAlways = true)
    public void deleteDuplicateDraft(MongoTemplate template) {
        String parentId = System.getProperty("parentId");
        List<ActionDefinition> allDraft = MigrationContext.getActionDefinitionRepo().findAllByParentId(parentId);
        if(allDraft.isEmpty()) {
            log.warn("No drafts for {}", parentId);
            return;
        }
        ActionDefinition baseDraft = null;
        for (ActionDefinition draft : allDraft) {
            if(!draft.isDraft()) continue;
            if(baseDraft == null) {
                baseDraft = draft;
                continue;
            }
            log.info("Deleting draft {} for {}", draft.getId(), draft.getDisplayName());
            MigrationContext.getActionDefinitionRepo().deleteById(draft.getId());
        }
    }
}
