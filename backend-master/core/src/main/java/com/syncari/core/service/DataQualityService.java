
package com.syncari.core.service;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.dfiv2.DFIResultManager;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.ResourceConflictException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.FeatureStatus;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.KeyValue;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DataQualityService {

    public static final String PREDICATES_KEY = "predicates";
    public static final String LEFT_KEY = "left";
    public static final String RIGHT_KEY = "left";
    public static final String VALUE_KEY = "value";
    public static final String VARIABLE_KEY = "variable";

    @Autowired
    SchemaService schemaService;

    @Autowired
    DataQualityCategoryRepo dataQualityCategoryRepo;

    @Autowired
    DataQualityRuleRepo dataQualityRuleRepo;

    @Autowired
    CustomDataQualityRuleRepoImpl customDataQualityRuleRepo;

    @Autowired
    Publisher publisher;

    @Autowired
    FeatureService featureService;

    @Autowired
    ReferenceDataService service;

    private boolean isDFIProvisioned() {
        return featureService.isEnabled(Features.DfiV2Provisioning);
    }

    private void errorOutIfDFIIsNotProvisioned() {
        if (!isDFIProvisioned())
            throw new RuntimeException("DFI is not provisioned");
    }

    public Map<String, List<String>> getReferenceMetaDataOptions() {
        Map<String, List<String>> options = new HashMap<>();
        List<ReferenceDataMeta> refData = service.listMeta(0);
        for(ReferenceDataMeta r: refData) {
            List<String> fields = new ArrayList<>(r.getFields().keySet());
            options.put(r.getName(), fields);
        }
        return options;
    }

    public void provisionDFI(MappingGraph graph) {
        FeatureStatus featureStatus = featureService.getOrCreateFeatureByName(Features.DfiV2Provisioning).getStatus();
        if (featureStatus == null) {
            log.error("DFI feature status is null");
            return;
        }
        if(graph.getSettings() == null) {
            log.error("pipeline settings is null for graph : "+graph.getId());
            return;
        }
        if (graph.getSettings().isDataQuality() && featureStatus.equals(FeatureStatus.inactive)) {
            log.info("Triggering DFI provisioning request");
            sendProvisionDFINotification();
        }
    }

    public String getDFIProvisionStatus(MappingGraph graph) {
        FeatureStatus dfiStatus = featureService.getOrCreateFeatureByName(Features.DfiV2Provisioning).getStatus();
        if(graph.getSettings() == null) {
            log.error("Graph settings is null for graph : "+graph.getId());
            return DFIConstants.DFI_PROVISION_STATUS_DISABLED;
        }
        boolean dfiStateOfGraph = graph.getSettings().isDataQuality();
        if (!dfiStateOfGraph || dfiStatus.equals(FeatureStatus.inactive)) {
            return DFIConstants.DFI_PROVISION_STATUS_DISABLED;
        } else if (dfiStatus.equals(FeatureStatus.activating)) {
            return DFIConstants.DFI_PROVISION_STATUS_IN_PROGRESS;
        } else {
            return DFIConstants.DFI_PROVISION_STATUS_ENABLED;
        }
    }

    public List<DataQualityCategory> getAllCategories() {
      errorOutIfDFIIsNotProvisioned();
      return dataQualityCategoryRepo.findAll();
    }

    public List<DataQualityCategory> batchUpdateCategories(List<DataQualityCategory> dqCategories) {
        errorOutIfDFIIsNotProvisioned();
        List<DataQualityCategory> toSaveAll = new ArrayList<>();
        Set<String> categoryNames = new HashSet<>();
        for (DataQualityCategory c: dqCategories) {
            if (categoryNames.contains(c.getName())) {
                throw new ResourceConflictException(String.format("Category %s already exists", c.getName()));
            } else if (c.getName().equalsIgnoreCase(DFIConstants.OTHER_CATEGORY))
                throw new ResourceConflictException(String.format("Category name %s is a default type and cannot be used for custom category", DFIConstants.OTHER_CATEGORY));
            else
                categoryNames.add(c.getName());
        }
        dqCategories.forEach(dqCategory -> {
            if (dqCategory.getId() != null) {
                var dqCatOpt = dataQualityCategoryRepo.findById(dqCategory.getId());
                if (dqCatOpt.isPresent()) {
                    var dqCat = dqCatOpt.get();
                    dqCat.setName(dqCategory.getName());
                    toSaveAll.add(dqCat);
                    return;
                }
            }
            toSaveAll.add(dqCategory);
        });
        return dataQualityCategoryRepo.saveAll(toSaveAll);
    }

    private void validatePredicate(EntityDefinition coreEntity, MappingGraph graph, DataQualityRule rule, List<Map<String, Object>> predicate, Set<String> attrs, List<ValidationError> errors) {
        for (Map<String, Object> v : predicate) {
            if (v.containsKey(PREDICATES_KEY))
                validatePredicate(coreEntity, graph, rule, (List<Map<String, Object>>) v.get(PREDICATES_KEY), attrs, errors);
            else {
                if (!v.containsKey(LEFT_KEY) || !v.containsKey(RIGHT_KEY))
                    errors.add(ValidationError.scopedError(graph.getScope(), coreEntity.getId()).withMessage(String.format("DFI rule %s has invalid condition", rule.getName())));
                Map<String, Object> left = (Map<String, Object>) v.get(LEFT_KEY);
                if (!left.containsKey(VALUE_KEY)) {
                    errors.add(ValidationError.scopedError(graph.getScope(), coreEntity.getId()).withMessage(String.format("DFI rule %s has invalid/missing left condition", rule.getName())));
                    return;
                }

                if (!(attrs.contains(left.get(VALUE_KEY).toString()) || left.get(VALUE_KEY).equals(DFIConstants.DFI_FIELD_VALUE_KEY) || TokenHelper.hasTokens((String) left.get(VALUE_KEY))))
                    errors.add(ValidationError.scopedError(graph.getScope(), coreEntity.getId()).withMessage(String.format("DFI rule %s has invalid field reference in LHS of the condition", rule.getName())));

                if (left.get(VALUE_KEY).equals(DFIConstants.DFI_FIELD_VALUE_KEY) && rule.getScope().contains(DFIConstants.RECORD_SCOPE) && rule.getScopeType().equals(DFIConstants.SCOPE_TYPE_SYSTEM))
                    errors.add(ValidationError.scopedError(graph.getScope(), coreEntity.getId()).withMessage(String.format("DFI rule %s has invalid value in LHS. Field value is not available for Record scope", rule.getName())));
            }
        }
    }

    private void validateRuleConfig(EntityDefinition coreEntity, MappingGraph graph, DataQualityRule rule, List<ValidationError> errors) {

        if (!rule.getRuleConfig().containsKey(PREDICATES_KEY)) {
            errors.add(ValidationError
                    .scopedError(graph.getScope(), coreEntity.getId()).withMessage(String.format("DFI Rule %s has invalid configuration", rule.getName())));
            return;
        }

        Set<String> fieldValues = coreEntity.getActiveAttributes().stream().map(UUIDAuditModel::getId).collect(Collectors.toSet());

        List<Map<String, Object>> predicate = (List<Map<String, Object>>) rule.getRuleConfig().get(PREDICATES_KEY);
        validatePredicate(coreEntity, graph, rule, predicate, fieldValues, errors);
    }

    private List<ValidationError> validateDFIRule(EntityDefinition coreEntity, MappingGraph graph) {
        List<ValidationError> errors = new ArrayList<>();
        List<DataQualityRule> allRules = getAllRules(graph);
        for (DataQualityRule rule: allRules){
            if (rule.getScopeType().equals(DFIConstants.SCOPE_TYPE_SYSTEM) && rule.getScope().size() > 1) {
                String err = String.format("DFI Rule %s has invalid scopes selected", rule.getName());
                errors.add(ValidationError.scopedError(graph.getScope(), coreEntity.getId()).withMessage(err));
            }
            validateRuleConfig(coreEntity, graph, rule, errors);
        }
        return errors;
    }

    public List<ValidationError> validateDFIRules(EntityDefinition coreEntity, MappingGraph graph) {
        if (!isDFIProvisioned())
            return List.of();
        String coreNodeId = coreEntity.getId();
        if (coreNodeId == null) {
            log.error("DFI validation skipped as core node cannot be found for graph {}", graph.getName());
            return List.of();
        }
        return validateDFIRule(coreEntity, graph);
    }

    public void deleteCategory(String id) {
        if (!isDFIProvisioned())
            return;
        Optional<DataQualityCategory> requestedCategory = dataQualityCategoryRepo.findById(id);
        if (requestedCategory.isEmpty()) {
            log.error("Category with id {} does not exist");
            return;
        }
        if (requestedCategory.get().getName().equals(DFIConstants.OTHER_CATEGORY)) {
            log.error("Category {} belong to System type Other which cannot be deleted", DFIConstants.OTHER_CATEGORY);
            throw new RuntimeException("Other Category cannot be deleted");
        }
        Optional<DataQualityCategory> otherCategory = dataQualityCategoryRepo.findByName(DFIConstants.OTHER_CATEGORY);
        if (otherCategory.isEmpty()) {
            log.error("Cannot delete since Other category cannot be found");
            throw new RuntimeException("Cannot delete since Other category cannot be found");
        }
        customDataQualityRuleRepo.moveRulesToOtherCategory(id, otherCategory.get().getId());
        dataQualityCategoryRepo.deleteById(id);
    }

    public Optional<DataQualityCategory> findCategoryById(String id) {
        errorOutIfDFIIsNotProvisioned();
        return dataQualityCategoryRepo.findById(id);
    }

    public DataQualityCategory getCategoryById(String id) {
        errorOutIfDFIIsNotProvisioned();
        var dqCategory = findCategoryById(id);
        if (dqCategory.isPresent()) {
            return dqCategory.get();
        }
        return null;
    }

    public List<DataQualityRule> getRulesByAttribute(String attrId, String graphId) {
        errorOutIfDFIIsNotProvisioned();
        return dataQualityRuleRepo.findByGraphAttrId(graphId, attrId);
    }

    public List<DataQualityRule> getRecordRules(String graphId) {
        errorOutIfDFIIsNotProvisioned();
        return dataQualityRuleRepo.findRecordRulesByGraphId(graphId);
    }

    public List<DataQualityRule> getAllRules(MappingGraph graph) {
        errorOutIfDFIIsNotProvisioned();
        return dataQualityRuleRepo.findByGraphId(graph.getId());
    }

    public List<DataQualityRule> getAllRules(String graphId) {
        errorOutIfDFIIsNotProvisioned();
        return dataQualityRuleRepo.findByGraphId(graphId);
    }

    public List<DataQualityRule> getRecordRules(List<DataQualityRule> allRules) {
        return allRules.stream()
            .filter(rule -> !Boolean.TRUE.equals(rule.getIsDeleted()))
            .filter(rule -> DFIConstants.SCOPE_TYPE_SYSTEM.equals(rule.getScopeType()))
            .filter(rule -> rule.getScope() != null && rule.getScope().contains(DFIConstants.RECORD_SCOPE))
            .collect(Collectors.toList());
    }

    public List<DataQualityRule> getRulesByAttribute(String attrId, List<DataQualityRule> allRules) {
        return allRules.stream()
            .filter(rule -> !Boolean.TRUE.equals(rule.getIsDeleted()))
            .filter(rule -> {
                // System rule with 'all_fields' scope
                boolean isAllFieldsSystemRule = DFIConstants.SCOPE_TYPE_SYSTEM.equals(rule.getScopeType())
                    && rule.getScope() != null
                    && rule.getScope().contains(DFIConstants.ALL_FIELDS_VALUE);

                // Attribute rule with this specific attrId in scope
                boolean isAttributeRule = DFIConstants.ATTRIBUTE_SCOPE.equals(rule.getScopeType())
                    && rule.getScope() != null
                    && rule.getScope().contains(attrId);

                return isAllFieldsSystemRule || isAttributeRule;
            })
            .collect(Collectors.toList());
    }

    public DataQualityRule saveRule(MappingGraph graph, DataQualityRule dqRule) {
        errorOutIfDFIIsNotProvisioned();
        if (dqRule.getId() != null) {
            var ruleOpt = dataQualityRuleRepo.findById(dqRule.getId());
            if (ruleOpt.isPresent()) {
                var rule = ruleOpt.get();
            dqRule.setOriginalId(rule.getOriginalId());
            dqRule.setIsDeleted(false);
            dqRule.setPassed(rule.getPassed());
            dqRule.setFailed(rule.getFailed());
            return dataQualityRuleRepo.save(dqRule);
            }
        } else {
            if (dataQualityRuleRepo.findByName(dqRule.getEntityId(), dqRule.getName(), graph.getId()).isPresent()) {
                log.error("Rule with name {} already exists for entity {}", dqRule.getName(), dqRule.getEntityId());
                throw new ResourceConflictException(String.format("Rule with name %s already exists in this entity", dqRule.getName()));
            }
            String userId = (SyncariContext.getUser() == null ? null : SyncariContext.getUser().getId());
            dqRule.setOriginalId(dqRule.getId());
            dqRule.setIsDeleted(false);
            dqRule.setCreatedBy(userId);
            return dataQualityRuleRepo.save(dqRule);
        }
        return dqRule;
    }

    public void deleteRule(String entityId, String ruleId) {
        errorOutIfDFIIsNotProvisioned();
        var ruleOpt = dataQualityRuleRepo.findById(ruleId);
        if (ruleOpt.isEmpty()) {
            log.error("rule deletion failed. rule with id {} doesn't exist", ruleId);
            return;
        }
        var rule = ruleOpt.get();
        rule.setIsDeleted(true);
        dataQualityRuleRepo.save(rule);
        sendDeleteDFIRuleNotification(ruleId, entityId);
    }

    public KeyValue getCreateRuleMetadata(String syncariEntityId, List<KeyValue> tempVars) {
        errorOutIfDFIIsNotProvisioned();
        List<KeyValue> scopes = new ArrayList<>(syncariEntityId != null
                ? schemaService.findEntity(syncariEntityId).map(e -> e.getActiveAttributes().stream()
                .map(a ->
                        new KeyValue(DFIConstants.VALUE_KEY, a.getId())
                                .set("type", VARIABLE_KEY)
                                .set("datatype", a.getDataType().getName())
                                .set("label", a.getDisplayName() + " (" + a.getApiName() + ")"))
                .collect(Collectors.toList())).orElse(List.of())
                : new ArrayList<>());
        var fieldValues = new ArrayList<>(scopes);
        for (KeyValue tempVar: tempVars) {
            fieldValues.add(new KeyValue(
                    DFIConstants.VALUE_KEY, tempVar.get("token"),
                    "type", VARIABLE_KEY,
                    "datatype", tempVar.get("datatype"),
                    "label", tempVar.getOrDefault("shortLabel", tempVar.get("label"))));
        }
        var predicate = new KeyValue("fieldValues", fieldValues);

        scopes.add(0, new KeyValue(DFIConstants.VALUE_KEY, DFIConstants.ALL_FIELDS_VALUE)
                .set("label", DFIConstants.ALL_FIELDS_LABEL)
                .set("helpSummary", "All the fields will be recorded or rejected")
                .set("scope_type", DFIConstants.SCOPE_TYPE_SYSTEM));

        scopes.add(0, new KeyValue(DFIConstants.VALUE_KEY, DFIConstants.Record_VALUE)
                .set("label", DFIConstants.Record_LABEL)
                .set("helpSummary", "All the fields will be recorded or rejected")
                .set("scope_type", DFIConstants.SCOPE_TYPE_SYSTEM));

        return new KeyValue(DFIConstants.POLICIES_KEY, List.of(
                new KeyValue(DFIConstants.VALUE_KEY, "report").set("label", "Report")),
                DFIConstants.SCOPES_KEY, scopes, "predicate", predicate);
    }

    public Optional<KeyValue> getPolicyById(String policyId) {
      if (!isDFIProvisioned())
        return Optional.empty();
      var metadata = getCreateRuleMetadata(null, List.of());
      @SuppressWarnings("unchecked")
      List<KeyValue> policies = metadata.get(DFIConstants.POLICIES_KEY);
      if (policies != null ) {
        return policies.stream().filter(scope -> ((String)scope.get(DFIConstants.VALUE_KEY)).equalsIgnoreCase(policyId)).findFirst();
      }
      return null;
    }

    private void sendProvisionDFINotification() {
        publisher.publishToDFIResultQueue(new Event().setType(EventTypes.PROVISION_DFI)
                .setDetails(Map.of()));
    }

    private void sendDeleteDFIRuleNotification(String ruleId, String entityId) {
        publisher.publishToDFIResultQueue(new Event().setType(EventTypes.DFI_RULE_DELETED)
                .setDetails(Map.of("ruleId", ruleId, "entityId", entityId, "deletedAt", DFIResultManager.getCurrTimeStamp())));
    }

}
