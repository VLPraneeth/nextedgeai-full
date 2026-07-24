package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.model.RuleDefinition;
import com.syncari.core.service.DfiRuleAssignmentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class DFIV1EnabledCheck {

    @ChangeSet(order = "001", id = "isDfiV1Enabled", author = "rohit", runAlways = true)
    public void isDfiV1Enabled(MongoTemplate template) {
        DfiRuleAssignmentService dfiRuleAssignmentService = MigrationContext.getDfiRuleAssignmentService();
        List<DfiRuleAssignment> def = dfiRuleAssignmentService.findAll();
        if (CollectionUtils.isNotEmpty(def) && def.size() > 30){
            log.info("Enabled");
            log.info("Instance {} with syncariId {} and organization Id {}, org name {}, custom rules exists",
                    SyncariContext.getInstance().getDisplayName(),
                    SyncariContext.getSyncariId(), SyncariContext.getOrganziation().getId(), SyncariContext.getOrganziation().getName());
        }else{
            log.info("Not active");

        }


    }
}
