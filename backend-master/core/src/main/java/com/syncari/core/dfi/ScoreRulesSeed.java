package com.syncari.core.dfi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.RuleDefinition;
import com.syncari.core.model.RuleDefinition.Impact;
import com.syncari.core.model.RuleDefinition.RuleType;
import com.syncari.core.model.util.Scope;

public class ScoreRulesSeed {
    private static Map<String, RuleDefinition> attributeRules = new HashMap<>();
    private static Map<String, RuleDefinition> entityRules = new HashMap<>();

    static {
        if(attributeRules.isEmpty()) {
            attributeRules.put(RuleConstants.IS_CAMEL_CASED, isCamelCased());
            attributeRules.put(RuleConstants.IS_NOT_EMPTY, isNotEmpty());
            attributeRules.put(RuleConstants.IS_PHONE_FORMATTED, isPhoneFormatted());
            attributeRules.put(RuleConstants.IS_NOT_STALE, updatedWithin30Days());
            attributeRules.put(RuleConstants.IS_VALID_EMAIL, isValidEmail());
            attributeRules.put(RuleConstants.IS_NUMBER, isNumber());
            attributeRules.put(RuleConstants.MATCHES_REGEX, matchesRegex());
            attributeRules.put(RuleConstants.WITHIN_NUMERIC_RANGE, withinNumericRange());
            attributeRules.put(RuleConstants.WITHIN_LENGTH_RANGE, withinLengthRange());
            attributeRules.put(RuleConstants.WITHIN_DATE_RANGE, withinDateRange());
        }
        if(entityRules.isEmpty()) {
        }
    }

    public static RuleDefinition get(String name, Scope scope) {
        if(Scope.ENTITY.equals(scope)){
            return entityRules.get(name);
        }
        return attributeRules.get(name);
    }

    public static List<RuleDefinition> getAll() {
        List<RuleDefinition> results = new ArrayList<>();
        attributeRules.forEach((k, v) -> {
            results.add(populateRule(v));
        });
        return results;
    }

    public static RuleDefinition populateRule(RuleDefinition rule){
        RuleDefinition fromSeed = ScoreRulesSeed.get(rule.getName(), rule.getScope());
        if(fromSeed != null) {
            rule.setName(fromSeed.getName()).setLabel(fromSeed.getLabel()).setScope(fromSeed.getScope())
                    .setDescription(fromSeed.getDescription())
                    .setWeight(fromSeed.getWeight())
                    .setSeeded(fromSeed.isSeeded());
        }
        return rule;
    }

    private static RuleDefinition isCamelCased() {
        return new RuleDefinition().setName(RuleConstants.IS_CAMEL_CASED).setLabel("Is camel cased")
                .setDescription("Rule to check if the string value is camel cased").setScope(Scope.ATTRIBUTE)
                .setWeight(40).setSeeded(true).setType(RuleType.BOOLEAN).setDefaultImpact(Impact.HIGH);
    }

    private static RuleDefinition isNotEmpty() {
        return new RuleDefinition().setName(RuleConstants.IS_NOT_EMPTY).setLabel("Has value")
                .setDescription("Rule to check if the value is not empty").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.BOOLEAN).setDefaultImpact(Impact.HIGH);
    }

    private static RuleDefinition isPhoneFormatted() {
        return new RuleDefinition().setName(RuleConstants.IS_PHONE_FORMATTED).setLabel("Is formatted phone")
                .setDescription("Rule to check if the phone number is formated").setScope(Scope.ATTRIBUTE).setWeight(70)
                .setSeeded(true).setType(RuleType.BOOLEAN).setDefaultImpact(Impact.HIGH);
    }

    private static RuleDefinition isValidEmail() {
        return new RuleDefinition().setName(RuleConstants.IS_VALID_EMAIL).setLabel("Has correct email format")
                .setDescription("Rule to check if the email address is valid").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.BOOLEAN).setDefaultImpact(Impact.HIGH);
    }

    private static RuleDefinition updatedWithin30Days() {
        return new RuleDefinition().setName(RuleConstants.IS_NOT_STALE).setLabel("Last updated within 30 days")
                .setDescription("Rule to check if the record has been updated in last 30 days")
                .setScope(Scope.ATTRIBUTE).setWeight(50).setType(RuleType.BOOLEAN).setSeeded(true).setDefaultImpact(Impact.MEDIUM);
    }

    private static RuleDefinition isNumber() {
        return new RuleDefinition().setName(RuleConstants.IS_NUMBER).setLabel("Is number")
                .setDescription("Rule to check if the value is a valid number").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.BOOLEAN).setDefaultImpact(Impact.MEDIUM);
    }

    // TODO: Support matchesFilter in future version
    private static RuleDefinition matchesFilter() {
        return new RuleDefinition().setName(RuleConstants.MATCHES_FILTER).setLabel("Matches a filter")
                .setDescription("Rule to check if the value matches the filter condition").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.FILTER_CONDITION).setDefaultImpact(Impact.MEDIUM);
    }

    private static RuleDefinition matchesRegex() {
        return new RuleDefinition().setName(RuleConstants.MATCHES_REGEX).setLabel("Matches a regex")
                .setDescription("Rule to check if the value matches the regex filter").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.REGEX).setDefaultImpact(Impact.HIGH);
    }

    private static RuleDefinition withinNumericRange() {
        return new RuleDefinition().setName(RuleConstants.WITHIN_NUMERIC_RANGE).setLabel("Within numeric range")
                .setDescription("Rule to check if the value is within the numeric range").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.INT_RANGE).setDefaultImpact(Impact.MEDIUM);
    }

    private static RuleDefinition withinLengthRange() {
        return new RuleDefinition().setName(RuleConstants.WITHIN_LENGTH_RANGE).setLabel("Within string length range")
                .setDescription("Rule to check if the string value is within the length range").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.INT_RANGE).setDefaultImpact(Impact.MEDIUM);
    }

    private static RuleDefinition withinDateRange() {
        return new RuleDefinition().setName(RuleConstants.WITHIN_DATE_RANGE).setLabel("Within date range")
                .setDescription("Rule to check if the date value is within the date range").setScope(Scope.ATTRIBUTE).setWeight(0)
                .setSeeded(true).setType(RuleType.DATE_RANGE).setDefaultImpact(Impact.MEDIUM);
    }
}
