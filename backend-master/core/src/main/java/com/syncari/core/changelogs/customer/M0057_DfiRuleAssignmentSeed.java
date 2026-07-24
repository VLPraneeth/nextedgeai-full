package com.syncari.core.changelogs.customer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.changelogs.syncari.M0004_InitialUsers;
import com.syncari.core.dfi.RuleAssignmentSeed;
import com.syncari.core.dfi.ScoreRulesSeed;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.ConditionAssignment;
import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.RuleAssignment;
import com.syncari.core.model.RuleDefinition;
import com.syncari.core.model.User;
import com.syncari.core.model.RuleDefinition.Impact;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.DfiRuleAssignmentService;

import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0057")
public class M0057_DfiRuleAssignmentSeed {

    @ChangeSet(order = "001", id = "addDfiRuleAssignmentSeed", author = "sudee")
    public void addDfiRuleAssignmentSeed(MongoTemplate template) {
        migrate("account");
        migrate("lead");
        migrate("contact");
    }

    public void migrate(String entityApiName) {
        DfiRuleAssignmentService dfiRuleAssignmentService = MigrationContext.getDfiRuleAssignmentService();
        User previous = SyncariContext.getUser();
        Optional<User> activeUser = MigrationContext.getUserRepo().findByActiveByEmail(M0004_InitialUsers.SUPER_ADMIN_EMAIL);
        SyncariContext.setUser(activeUser.get());
        Optional<EntityDefinition> entityOpt = MigrationContext.getSchemaService().getSyncariEntityByName(entityApiName);
        // If the entity trying to migrate is not present, skip it. This can happen due to simulationTestRuns.
        if (!entityOpt.isPresent()) { return; }
        EntityDefinition entity = entityOpt.get();
        Set<RuleAssignment> newRules = new TreeSet<>();
        List<RuleAssignment> rules = getAllRulesForExistingEntity(entityApiName);
        for (RuleAssignment rule: rules) {
            migrateRule(entity, rule, newRules);
        }
        DfiRuleAssignment draft = dfiRuleAssignmentService.findOrCreateDraft(entity.getId());
        draft.setRules(newRules);
        dfiRuleAssignmentService.publish(draft);
        SyncariContext.setUser(previous);
    }

    private void migrateRule(EntityDefinition entity, RuleAssignment rule, Set<RuleAssignment> newRules) {
        try {
            AttributeDefinition attribute = entity.getFieldByName(rule.getFieldApiName());
            rule.getRules().forEach((k, v) -> {
                RuleDefinition ruleDef = ScoreRulesSeed.get(k, Scope.ATTRIBUTE);
                Optional<RuleAssignment> foundRule = newRules.stream().filter(x -> x.getName() == ruleDef.getLabel()).findFirst();
                if (!foundRule.isPresent()) {
                    RuleAssignment newRule = new RuleAssignment().setName(ruleDef.getLabel()).setEntityApiName(rule.getEntityApiName());
                    newRules.add(newRule);
                    foundRule = Optional.of(newRule);
                }
                foundRule.get().getSelectedFields().add(attribute.getId());
                // keep it simple and determine the impact directly from the value.
                Impact impact = Impact.HIGH;
                if (v == 40) {
                    impact = Impact.MEDIUM;
                } else if (v == 70) {
                    impact = Impact.LOW;
                }
                foundRule.get().getConditions().add(new ConditionAssignment().setName(k)
                    .setRuleName(ruleDef.getLabel()).setConditionMatches(true).setImpact(impact).setType(ruleDef.getType()));
            });
        } catch (SyncariValidationException e) {
            log.warn("Skipping migrating field {}, since it is not found", rule.getFieldApiName(), e);
            return;
        }
    }

    public List<RuleAssignment> getAllRulesForExistingEntity(String entityApiName) {
        List<RuleAssignment> findByEntity = getScaffoldsByEntityName(entityApiName);
        List<RuleAssignment> results = new ArrayList<>();
        findByEntity.stream().forEach(f -> {
            results.add(Optional.of(f).map(f1 -> {
                return RuleAssignmentSeed.populateRule(f1);
            }).get());
        });
        return results;
    }

    public List<RuleAssignment> getScaffoldsByEntityName(String entityApiName) {
        Map<String, List<RuleAssignment>> dfiFieldsByEntity = new HashMap<>();
        for (String entityField: RuleAssignmentSeed.attributeRules.keySet()) {
            String[] parts = entityField.split("_");
            if (!dfiFieldsByEntity.containsKey(parts[0])) {
                dfiFieldsByEntity.put(parts[0], new ArrayList<>());
            }
            dfiFieldsByEntity.get(parts[0]).add(RuleAssignmentSeed.get(parts[0], parts[1]));
        }
        return dfiFieldsByEntity.get(entityApiName);
    }
}
