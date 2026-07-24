package com.syncari.core.model;

import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.pipeline.PipelinePublishedEvent;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.service.FeatureService;
import com.syncari.core.utils.MongoUtils;
import com.syncari.core.utils.RedisUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
@Slf4j
public class AdvancedDedupeConfig {

    public static final String MAX_DUPLICATES = "100";
    private Map<String, Object> fieldLevelOverrides = Map.of();

    private Map<String, Object> findDupes = Map.of();

    private Map<String, Object> selectWinner = Map.of();

    private Map<String, Object> fieldMergePolicies = Map.of();

    private List<WinnerSelection> winnerSelections;
    private WinnerOverridePolicy defaultWinnerOverridePolicy = WinnerOverridePolicy.NEVER;
    private WinnerValueSelectionPolicy defaultWinnerValueSelectionPolicy = WinnerValueSelectionPolicy.EARLIEST_WITH_VALUE;
    private MergeAction mergeAction = MergeAction.MERGE;
    private String maximumAllowedDupes;
    private boolean isProgressiveWinnerSelection;
    private Map<String, Object> skipWhen = Map.of();

    public Map<String, Object> getConfigMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("selectWinner", selectWinner);
        config.put("findDupes", findDupes);
        config.put("fieldLevelOverrides", fieldLevelOverrides);
        config.put("fieldMergePolicies", fieldMergePolicies);
        config.put("defaultOverridePolicy", defaultWinnerOverridePolicy.name());
        config.put("defaultMergePolicy", defaultWinnerValueSelectionPolicy.name());
        config.put("selectMergeAction", this.isReportOnly());
        config.put("maxDupes", this.getMaximumAllowedDupes());
        config.put("progressiveSelection", this.isProgressiveWinnerSelection);
		config.put("skipWhen", MapUtils.isEmpty(skipWhen) ? null : skipWhen);
        return config;
    }

    public boolean isReportOnly(){
        return this.mergeAction  == MergeAction.REPORT_ONLY;
    }

    public List<Expression> findDupesCriteria() {
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) findDupes.getOrDefault("compositeValues", List.of());
        PredicateParser parser = new PredicateParser("");
        return compositeValues.stream().map(p -> {
            Map<String, Object> findDupePredicate = (Map<String, Object>) p.getOrDefault("findDupesPredicate", Map.of());
            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
            return parser.fromMap(predicate);
        }).filter(expression->expression!=null).collect(Collectors.toList());
    }
    
    public Expression skipWhenCriteria() {
        return new PredicateParser().fromMap(MapUtils.emptyIfNull(this.skipWhen));
       
    }

    public String getDedupeHash() {
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) findDupes.getOrDefault("compositeValues", List.of());
        return DigestUtils.md5Hex(compositeValues.stream().map(p -> {
            Map<String, Object> findDupePredicate = (Map<String, Object>) p.getOrDefault("findDupesPredicate", Map.of());
            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
            return predicate.toString();
        }).reduce("", (a, b) -> a + b));
    }

    public void postPublish(PipelinePublishedEvent context, EntityDefinition entityDefinition) {
        createIndexes(entityDefinition, context.getMongoUtils(), context.getRedisUtils(), context.getFeatureService());
    }

    public void createIndexes(EntityDefinition entityDefinition, MongoUtils mongoUtils, RedisUtils redisUtils, FeatureService featureService) {
        var dedupeCriteria = findDupesCriteria();
        List<CacheIndexAttribute> cacheIndexAttributes = new ArrayList<>();

        if (featureService.isEnabled(Features.EntityCaching)) {
            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("_id", StringType.VALUE, false));
            cacheIndexAttributes.add(redisUtils.createSystemIndexAttribute("isDeleted", BooleanType.VALUE, false));
        }
        var visitor = new SimpleExpressionVisitor() {
            public void visit(VariableExpression expression) {
                String variableName = expression.getVariableName();
                mongoUtils.constructIndexes(variableName,true, Optional.of(entityDefinition));
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName, false, Optional.of(entityDefinition)));
                }
            }

            public void visit(EqualIgnoreCase expression){
                Expression left = expression.getLeft();
                if(left instanceof VariableExpression){
                    String variableName =  ((VariableExpression) left).getVariableName();
                    mongoUtils.constructIndexes(variableName,false, Optional.of(entityDefinition));
                    if (featureService.isEnabled(Features.EntityCaching)) {
                        cacheIndexAttributes.add(redisUtils.createCacheIndexAttribute(variableName, true, Optional.of(entityDefinition)));
                    }
                }
            }

            public void visit(Empty expression){
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createNullField());
                }
            }

            public void visit(NotEmpty expression){
                if (featureService.isEnabled(Features.EntityCaching)) {
                    cacheIndexAttributes.add(redisUtils.createNullField());
                }
            }
        };
        dedupeCriteria.forEach(expr -> expr.accept(visitor));
        if(featureService.isEnabled(Features.EntityCaching)) {
            redisUtils.constructOrAlterIndex(SyncariContext.getInstance().getSyncariId(), entityDefinition, cacheIndexAttributes);
        }
    }

    public List<WinnerSelection> getWinnerSelectionPolicies() {
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinner.getOrDefault("compositeValues", List.of());
        return compositeValues.stream().map(p -> {
            Map<String, Object> winnerSelectionType = (Map<String, Object>) p.getOrDefault("winnerSelectionType", Map.of());
            Map<String, Object> winnerSelectionValue = (Map<String, Object>) p.getOrDefault("winnerSelectionValue", Map.of());
            return new WinnerSelection().setWinnerSelectionType(Objects.toString(winnerSelectionType.get("value"), null))
                    .setWinnerSelectionValue((Objects.toString(winnerSelectionValue.get("value"), null)));
        }).filter(w -> w.getWinnerSelectionType() != null && w.getWinnerSelectionValue() != null).collect(Collectors.toList());
    }

    public List<Expression> getWinnerSelectionPredicates() {

        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinner.getOrDefault("compositeValues", List.of());
        return compositeValues.stream().map(p -> {
            Map<String, Object> winnerSelectionPredicate = (Map<String, Object>) p.getOrDefault("winnerSelectionPredicate", Map.of());
            Map<String, Object> predicate = (Map<String, Object>) winnerSelectionPredicate.getOrDefault("value", Map.of());
            return new SelectWinnerPredicateParser().fromMap(predicate);
        }).filter(f->f!=null).collect(Collectors.toList());
    }

    public List<WinningAttributeOverride> getFieldOverrides() {
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) fieldLevelOverrides.getOrDefault("compositeValues", List.of());
        return compositeValues.stream().map(p -> {
            Map<String, Object> field = (Map<String, Object>) p.getOrDefault("field", Map.of());
            Map<String, Object> fieldMergePolicy = (Map<String, Object>) p.getOrDefault("fieldMergePolicy", Map.of());
            Map<String, Object> fieldOverridePolicy = (Map<String, Object>) p.getOrDefault("fieldOverridePolicy", Map.of());
            return new WinningAttributeOverride()
                    .setValueSelectionPolicy(WinnerValueSelectionPolicy.valueOf(Objects.toString(fieldMergePolicy.get("value"), null)))
                    .setOverridePolicy(WinnerOverridePolicy.valueOf(Objects.toString(fieldOverridePolicy.get("value"), null)))
                    .setAttributeId(Objects.toString(field.get("value"), null));
        }).filter(w -> w.getAttributeId() != null && w.getValueSelectionPolicy() != null && w.getOverridePolicy() != null).collect(Collectors.toList());
    }

    public List<FieldMergePolicy> getFieldMergePolicies() {
        try {
            if (fieldMergePolicies.isEmpty()) {
                return getFieldOverrides().stream().flatMap(m -> m.toMergePolicy().stream()).collect(Collectors.toList());
            }
            List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) fieldMergePolicies.getOrDefault("compositeValues", List.of());
            return compositeValues.stream().map(p -> {
                Map<String, Object> fieldMergePredicate = (Map<String, Object>) p.getOrDefault("fieldMergePredicate", Map.of());
                Map<String, Object> predicate = (Map<String, Object>) fieldMergePredicate.getOrDefault("value", Map.of());
                Map<String, Object> fieldOverridePolicy = (Map<String, Object>) p.getOrDefault("fieldOverridePolicy", Map.of());
                return new FieldMergePolicy().setExpresson(new FieldMergePolicyParser().fromMap(predicate))
                        .setOverridePolicy(WinnerOverridePolicy.valueOf(Objects.toString(fieldOverridePolicy.get("value"),
                                WinnerOverridePolicy.WHEN_BLANK.name())))
                        .setExpressionMap(predicate);
            }).filter(f -> f != null).collect(Collectors.toList());
        } catch (SyncariValidationException e){
            if(e.getMessage().contains("Invalid filter condition")) {
                log.error(e.getMessage(), e);
                throw new SyncariValidationException(i18n("invalid_filter_field_predicates"));
            }
            throw e;
        }
    }

    public Map<String, Object> getFieldMergePoliciesMap(){
        return fieldMergePolicies;
    }

    public Map<String, Object> getDedupPredicate(int expressionIndex) {
        return getPredicateMap(findDupes, expressionIndex, "findDupesPredicate", new PredicateParser(""));
    }

    private Map<String, Object> getPredicateMap(Map<String, Object> predicateMap, int expressionIndex, String predicateKey, PredicateParser parser) {
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) predicateMap.getOrDefault("compositeValues", List.of());
        int predicateIndex = 0;
        for (Map<String, Object> value : compositeValues) {
            Map<String, Object> p = (Map<String, Object>) value.getOrDefault(predicateKey, Map.of());
            Map<String, Object> predicate = (Map<String, Object>) p.getOrDefault("value", Map.of());
            if (predicateIndex == expressionIndex) {
                return predicate;
            }
            if (parser.fromMap(predicate) != null) predicateIndex++;
        }
        return Map.of();
    }

    private Map<String, Object> getLegacyWinnerSelection(int expressionIndex) {
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinner.getOrDefault("compositeValues", List.of());
        int predicateIndex = 0;
        for (Map<String, Object> value : compositeValues) {
            if (predicateIndex == expressionIndex) {
                return value;
            }
            predicateIndex++;
        }
        return Map.of();
    }

    public Map<String, Object> getWinnerSelectionPredicate(int expressionIndex) {
        if (getWinnerSelectionPredicates().size() > 0) {
            return getPredicateMap(selectWinner, expressionIndex, "winnerSelectionPredicate", new SelectWinnerPredicateParser());
        } else {
            return getLegacyWinnerSelection(expressionIndex);
        }
    }
}
