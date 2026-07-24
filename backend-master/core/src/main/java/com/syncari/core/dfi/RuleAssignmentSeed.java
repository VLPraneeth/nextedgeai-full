package com.syncari.core.dfi;

import java.util.HashMap;
import java.util.Map;

import com.syncari.core.model.RuleAssignment;

@Deprecated
public class RuleAssignmentSeed {
    public static Map<String, Map<String, Integer>> attributeRules = new HashMap<>();

    static {
        if(attributeRules.isEmpty()) {
            attributeRules.put("account_name", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_CAMEL_CASED, 40));
            attributeRules.put("account_phone", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_PHONE_FORMATTED, 70));
            attributeRules.put("account_website", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("account_numberofemployees", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("account_domain", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("account_annualrevenue", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("account_billingcity", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("account_billingcountry", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("account_billingstate", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("account_billingpostalcode", Map.of(RuleConstants.IS_NOT_EMPTY, 0));

            attributeRules.put("lead_firstname", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_CAMEL_CASED, 40));
            attributeRules.put("lead_lastname", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_CAMEL_CASED, 40));
            attributeRules.put("lead_email", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_VALID_EMAIL, 0));
            attributeRules.put("lead_mobilephone", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_PHONE_FORMATTED, 70));
            attributeRules.put("lead_title", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("lead_company", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("lead_industry", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("lead_city", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("lead_state", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("lead_country", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("lead_postalcode", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            
            attributeRules.put("contact_firstname", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_CAMEL_CASED, 40));
            attributeRules.put("contact_lastname", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_CAMEL_CASED, 40));
            attributeRules.put("contact_email", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_VALID_EMAIL, 0));
            attributeRules.put("contact_mobilephone", Map.of(RuleConstants.IS_NOT_EMPTY, 0, RuleConstants.IS_PHONE_FORMATTED, 70));
            attributeRules.put("contact_title", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("contact_mailingcity", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("contact_mailingstate", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("contact_mailingcountry", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
            attributeRules.put("contact_mailingpostalcode", Map.of(RuleConstants.IS_NOT_EMPTY, 0));
        }
    }

    public static RuleAssignment get(String entityApiName, String fieldApiName) {
        RuleAssignment r = new RuleAssignment();
        r.setEntityApiName(entityApiName);
        r.setFieldApiName(fieldApiName);
        r.setRules(attributeRules.get(entityApiName.toLowerCase()+"_"+fieldApiName.toLowerCase()));
        return r;
    }
    
    public static RuleAssignment populateRule(RuleAssignment rule){
        RuleAssignment fromSeed = RuleAssignmentSeed.get(rule.getEntityApiName(), rule.getFieldApiName());
        if(fromSeed != null) {
            rule.setEntityApiName(fromSeed.getEntityApiName()).setFieldApiName(fromSeed.getFieldApiName())
                    .setSeeded(fromSeed.isSeeded())
                    .setRules(fromSeed.getRules());
        }
        return rule;
    }

}
