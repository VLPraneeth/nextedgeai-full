package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.syncari.core.dfi.ScoreRulesSeed;
import com.syncari.core.model.AttributeDefinition;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.ConditionAssignment;
import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.RuleAssignment;
import com.syncari.core.model.RuleDefinition;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.RuleDefinition.RuleType;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.DfiRuleAssignmentRepo;
import com.syncari.core.repositories.customer.RuleDefinitionRepo;
import com.syncari.utils.DateUtil;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DfiRuleAssignmentService extends DraftService<DfiRuleAssignment> implements MonitorableService {
    @Autowired
    DfiRuleAssignmentRepo dfiRuleAssignmentRepo;
    @Autowired
    SchemaService schemaService;
    @Autowired
    DateUtil dateUtil;

    public static final int SEEDED_RULE_ASSIGNMENT_COUNT = 30;

    public Optional<DfiRuleAssignment> findDraft(String entityId) {
        return dfiRuleAssignmentRepo.findDraftByEntityId(entityId);
    }

    public Optional<DfiRuleAssignment> findPublished(String entityId) {
        return dfiRuleAssignmentRepo.findPublishedByEntityId(entityId);
    }

    public List<DfiRuleAssignment> findAllPublished() {
        return dfiRuleAssignmentRepo.findAllPublished();
    }

    public List<DfiRuleAssignment> findAll() {
        return dfiRuleAssignmentRepo.findAll();
    }

    public boolean isCustomRulesExists(){
        List<DfiRuleAssignment> all = this.findAll();
        if (CollectionUtils.isNotEmpty(all)){
            return all.size() > SEEDED_RULE_ASSIGNMENT_COUNT;
        }else{
            return false;
        }
    }

    public List<RuleDefinition> findAllRuleDefinitions() {
        return ScoreRulesSeed.getAll();
    }

    public Optional<RuleDefinition> findRuleDefinitionByNameAndScope(String name, Scope scope) {
        List<RuleDefinition> ruleDefs = findAllRuleDefinitions();
        Optional<RuleDefinition> ruleDef = ruleDefs.stream().filter(x -> x.matchesNameAndScope(name, scope)).findFirst();
        return populated(ruleDef);
    }

    private Optional<RuleDefinition> populated(Optional<RuleDefinition> findByName) {
        return findByName.map(f -> {
            return ScoreRulesSeed.populateRule(f);
        });
    }

    public Optional<RuleAssignment> findRuleByName(DfiRuleAssignment dfiRuleAssigment, String name) {
        return dfiRuleAssigment.getRules().stream().filter(x -> name.equalsIgnoreCase(x.getName())).findFirst();
    }

    public DfiRuleAssignment findOrCreateDraft(String entityId) {
        EntityDefinition entity = schemaService.getSyncariEntityById(entityId).get();
        Optional<DfiRuleAssignment> existing = dfiRuleAssignmentRepo.findDraftByEntityId(entityId);
        DfiRuleAssignment dra = null;
        if (!existing.isPresent()) {
            DfiRuleAssignment defaultDraft = new DfiRuleAssignment().setEntityId(entityId).setEntityApiName(entity.getApiName());
            dra = saveDraft(defaultDraft);
        } else {
            dra = existing.get();
        }
        return dra;
    }

    public DfiRuleAssignment saveDraft(DfiRuleAssignment dfiRuleAssignment) {
        isValid(dfiRuleAssignment);
        for (RuleAssignment rule: dfiRuleAssignment.getRules()) {
            if (StringUtils.isEmpty(rule.getId())) {
                rule.setId((new ObjectId()).toString());
                rule.setCreatedAt(new Date());
            }
            if (rule.isModified()) {
                rule.setUpdatedAt(new Date());
            }
        }
        Optional<DfiRuleAssignment> oDra = dfiRuleAssignmentRepo.findDraftByEntityId(dfiRuleAssignment.getEntityId());
        DfiRuleAssignment dra = null;
        if (!oDra.isPresent()) {
            oDra = dfiRuleAssignmentRepo.findPublishedByEntityId(dfiRuleAssignment.getEntityId());
            if (!oDra.isPresent()) {
                dra = createDraftFor(dfiRuleAssignment);
            } else {
                dra = createDraftFor(oDra.get());
            }
        } else {
            dra = oDra.get();
            dra.copyValuesFrom(dfiRuleAssignment);
        }
        dra.setStatus(Status.NEW);
        return dfiRuleAssignmentRepo.save(dra);
    }

    public DfiRuleAssignment publish(DfiRuleAssignment dfiRuleAssignment) {
        isValid(dfiRuleAssignment);

        // Upon publish mark all the rules as not-dirty, so that the fresh draft has none as modified.
        Set<RuleAssignment> rules = dfiRuleAssignment.getRules();
        rules.forEach(x -> x.setModified(false));
        dfiRuleAssignment.setRules(rules);
        dfiRuleAssignment = saveDraft(dfiRuleAssignment);
        DfiRuleAssignment published = approveDraft(dfiRuleAssignment);
        return findOrCreateDraft(published.getEntityId());
    }

    public DfiRuleAssignment deleteDraft(DfiRuleAssignment dfiRuleAssignment) {
        try {
            dfiRuleAssignmentRepo.deleteDraftByEntityId(dfiRuleAssignment.getEntityId());
        } catch (Exception e) {
            String msg = "Deleting draft DFI rule assignments for entity failed.";
            log.error(msg, e);
            throw new RuntimeException(msg);
        }
        return findOrCreateDraft(dfiRuleAssignment.getEntityId());
    }

    public boolean isValid(DfiRuleAssignment dfiRuleAssignment) {
        Set<String> conditionByFieldName = new TreeSet<>();
        EntityDefinition entity = schemaService.getSyncariEntityById(dfiRuleAssignment.getEntityId()).get();
        for (RuleAssignment rule : dfiRuleAssignment.getRules()) {
            if (StringUtils.isBlank(rule.getName())) {
                throw new SyncariValidationException(i18n("name_is_required"));
            }
            if (rule.conditions.size() < 1) {
                throw new SyncariValidationException(i18n("one_condition_required"));
            }
            rule.getSelectedFields().forEach(
                fieldName -> {
                    log.debug(String.format("fieldName: %s", fieldName));
                    if (StringUtils.isBlank(fieldName)) {
                        throw new SyncariValidationException(i18n("target_must_have_field"));
                    }
                }
            );

            for (ConditionAssignment condition : rule.getConditions()) {
                if (StringUtils.isBlank(condition.getName())) {
                    throw new SyncariValidationException(i18n("condition_must_have_rule"));
                }
                var conditionsThatRequireOneValue = Arrays.asList(RuleType.REGEX, RuleType.STRING, RuleType.INTEGER);
                if (conditionsThatRequireOneValue.contains(condition.getType())) {
                    var values = condition.getConditionValues();
                    if (values.size() != 1 || StringUtils.isBlank(values.get(0))) {
                        throw new SyncariValidationException(
                            String.format(i18n("condition_must_provide_value"), condition.getName())
                        );
                    }
                }

                var conditionsThatRequireTwoValues = Arrays.asList(RuleType.INT_RANGE, RuleType.DATE_RANGE);
                if (conditionsThatRequireTwoValues.contains(condition.getType())) {
                    var values = condition.getConditionValues();
                    if (values.size() != 2 || StringUtils.isBlank(values.get(0)) || StringUtils.isBlank(values.get(1))) {
                        throw new SyncariValidationException(
                            String.format(i18n("condition_must_provide_two_value"), condition.getName())
                        );
                    }
                }

                if (condition.getType() == RuleType.DATE_RANGE) {
                    var values = condition.getConditionValues();
                    try {
                        if (dateUtil.toEpochMilli(values.get(0)) > 0 && dateUtil.toEpochMilli(values.get(1)) > 0) {
                            // no-op;
                        }
                    } catch (Exception e) {
                        throw new SyncariValidationException(
                            String.format(i18n("invalid_date_condition"), condition.getName(), values)
                        );
                    }
                }

                if (condition.getType() == RuleType.INT_RANGE) {
                    List<String> values = condition.getConditionValues();
                    boolean isValidRange = true;
                    if (CollectionUtils.isEmpty(values) || values.get(0) == null || values.get(1) == null) {
                        isValidRange = false;
                    }
                    if (isValidRange) {
                        try {
                            Number val1 = NumberFormat.getInstance().parse(values.get(0));
                            Number val2 = NumberFormat.getInstance().parse(values.get(1));
                            if (val2.doubleValue() < val1.doubleValue()) {
                                isValidRange = false;
                            }
                        } catch (final ParseException nfe) {
                            isValidRange = false;
                        }
                    }

                    if (!isValidRange) {
                        throw new SyncariValidationException(
                            String.format(i18n("invalid_long_condition"), condition.getName(), values)
                        );
                    }
                }

                // Make sure the same field--condition combination is not conflicting across rules.
                for (String field: rule.getSelectedFields()) {
                    String fieldCondition = field + "--" + condition.getName();
                    if (conditionByFieldName.contains(fieldCondition)) {
                        AttributeDefinition attrib = entity.getAttribute(field);
                        RuleDefinition ruleDef = ScoreRulesSeed.get(condition.getName(), Scope.ATTRIBUTE);
                        throw new SyncariValidationException(
                            String.format(i18n("conflicting_field_condition"), attrib.getDisplayName(), ruleDef.getLabel())
                        );
                    }
                    conditionByFieldName.add(fieldCondition);
                }
            }
        }

        return true;
    }

    @Override
    protected DraftableRepo<DfiRuleAssignment> getDraftableRepo() {
        return dfiRuleAssignmentRepo;
    }

    @Override
    protected void processArchived(DfiRuleAssignment archived) {
        // noop, nothing to do.
    }

    public Map<String, List<RuleAssignment>> getRulesForEntityByField(String entityApiName) {
        Map<String, List<RuleAssignment>> rulesByFieldApiName = new LinkedHashMap<>();
        Optional<EntityDefinition> entityOpt = schemaService.getSyncariEntityByName(entityApiName);
        // If the entity trying to migrate is not present, skip it. This can happen due to simulationTestRuns.
        if (!entityOpt.isPresent()) { return rulesByFieldApiName; }
        EntityDefinition entity = entityOpt.get();
        Optional<DfiRuleAssignment> dra = dfiRuleAssignmentRepo.findPublishedByEntityId(entity.getId());
        if (dra.isPresent()) {
            Set<RuleAssignment> rulesForEntity = dra.get().getRules();
            rulesForEntity.forEach(ruleAssignment -> {
                if (!ruleAssignment.isDisabled()) {
                    ruleAssignment.getSelectedFields().forEach(attributeId -> {
                        AttributeDefinition attrib = entity.getAttribute(attributeId);
                        // There is a possibility where a column is deleted after used in a DFI rule. skip processing those.
                        if (attrib == null) return;
                        if (!rulesByFieldApiName.containsKey(attrib.getApiName())) {
                            rulesByFieldApiName.put(attrib.getApiName(), new ArrayList<>());
                        }
                        rulesByFieldApiName.get(attrib.getApiName()).add(ruleAssignment);
                    });
                }
            });
        }
        return rulesByFieldApiName;
    }

    public Optional<DfiRuleAssignment> getDfiRuleAssignmentForInitializingScores(String entityId) {
        Optional<DfiRuleAssignment> dfiRuleAssignment = dfiRuleAssignmentRepo.findPublishedByEntityId(entityId);
        if (dfiRuleAssignment.isEmpty()) {
            return dfiRuleAssignment;
        }
        return dfiRuleAssignmentRepo.process(dfiRuleAssignment.get().getId(), DfiRuleAssignment.class);
    }

    public void finishDfiScoreRecalculation(DfiRuleAssignment dfiRuleAssignment, Throwable exception) {
        Optional<DfiRuleAssignment> finished = null;
        if (exception != null) {
            finished = dfiRuleAssignmentRepo.finishWithError(dfiRuleAssignment.getId(), exception.getMessage(), DfiRuleAssignment.class);
        } else {
            finished = dfiRuleAssignmentRepo.finish(dfiRuleAssignment.getId(), DfiRuleAssignment.class);
        }
        if (!finished.isPresent()) {
            // unlikely case.
            log.warn("Failed to finish DFI score recalculation with id {} for entity {}.", dfiRuleAssignment.getId(),
                dfiRuleAssignment.getEntityApiName());
            return;
        }
    }

    @Override
    public void buryTheDead() {
        List<DfiRuleAssignment> stuckDfiScoringJobs = dfiRuleAssignmentRepo.getStuck(HEART_BEAT_EXPIRY_MILLIS, DfiRuleAssignment.class);
        if (stuckDfiScoringJobs.size() > 0) {
            log.info("Found {} stuck DFI scoring job for more than {}ms", stuckDfiScoringJobs.size(), HEART_BEAT_EXPIRY_MILLIS);
        } else {
            log.debug("Did not find any stuck DFI scoring job.");
        }

        // Make tests inactive and send retry message.
        stuckDfiScoringJobs.forEach(stuckDfiScoringJob -> {
            Exception exception = new RuntimeException(i18n("dfi_server_error"));
            dfiRuleAssignmentRepo.clearTheDead(stuckDfiScoringJob.getId(),  exception.getMessage(), DfiRuleAssignment.class);
        });
    }

}
