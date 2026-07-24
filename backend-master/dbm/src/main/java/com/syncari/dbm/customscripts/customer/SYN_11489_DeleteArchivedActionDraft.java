package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.ActionDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class SYN_11489_DeleteArchivedActionDraft {

    @ChangeSet(order = "001", id = "deleteArchivedActionDraft", author = "varsha", runAlways = true)
    public void deleteArchivedActionDraft(MongoTemplate template) {
        List<String> ids = MigrationContext.getActionDefinitionRepo().findAll().stream().filter(a -> a.getDraftStatus() == DraftStatus.ARCHIVED)
                .map(a -> a.getId()).collect(Collectors.toList());
        if(ids.isEmpty()) {
            log.warn("No archived actions");
            return;
        }
        MigrationContext.getActionDefinitionRepo().deleteAllById(ids);
        log.info("Deleted all archived actions");
    }
}
