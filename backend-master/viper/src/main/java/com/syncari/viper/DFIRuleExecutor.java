package com.syncari.viper;

import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.dfiv2.DFIRuleExecutionResult;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.DataQualityRule;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.DFIExecutorService;
import com.syncari.core.service.DataQualityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DFIRuleExecutor {

    @Autowired
    DataQualityService dataQualityService;

    @Autowired
    DFIExecutorService dfiExecutorService;

    public List<DataQualityRule> getRecordRulesForGraph(String graphId){
        return dataQualityService.getRecordRules(graphId);
    }

    public List<DataQualityRule> getRulesByAttribute(String attribId, String graphId){
        return dataQualityService.getRulesByAttribute(attribId,graphId);
    }

    public List<DFIRuleExecutionResult> executeDFIFieldRules(String recordId, AttributeDefinition attrDefn,
                                                             GraphContext graphContext, Map<String, String> categoryMap,List<DataQualityRule> configuredRules) {
        return executeRules(recordId, attrDefn, DFIConstants.ATTRIBUTE_SCOPE, configuredRules, graphContext, categoryMap);
    }

    public List<DFIRuleExecutionResult> executeDFIRecordRules(String recordId,
                                                              GraphContext graphContext, Map<String, String> categoryMap,List<DataQualityRule> configuredRules) {
        return executeRules(recordId, null, DFIConstants.RECORD_SCOPE, configuredRules, graphContext, categoryMap);
    }


    public List<DFIRuleExecutionResult> executeRules(String recordId, AttributeDefinition attrDefn, String scope,
                                                     List<DataQualityRule> rules, GraphContext graphContext,
                                                     Map<String, String> categoryMap) {
        List<DFIRuleExecutionResult> results = new ArrayList<>();
        for (DataQualityRule rule: rules) {
            try {
                results.add(dfiExecutorService.evaluateRule(recordId, attrDefn, scope, rule, graphContext, categoryMap));
            } catch (Exception e) {
                // TODO : check for alert mechanisms
                log.error("Error while executing dfi rule {} for record {}. error : ",
                        rule.getId(), recordId, e);
            }
        }
        return results;
    }


}
