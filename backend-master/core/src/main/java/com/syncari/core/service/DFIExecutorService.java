package com.syncari.core.service;

import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.dfiv2.DFIResultManager;
import com.syncari.core.dfiv2.DFIRuleExecutionResult;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.DataQualityCategory;
import com.syncari.core.model.DataQualityRule;
import com.syncari.core.model.Event;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.FilterEvaluationVisitor;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.CustomStagedBatchRecordRepoImpl;
import com.syncari.core.repositories.customer.DataQualityCategoryRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class DFIExecutorService {

    @Autowired
    TokenHelper tokenHelper;

    @Autowired
    private Publisher publisher;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ReferenceDataService referenceDataService;

    @Autowired
    EntityRepo entityRepo;

    @Autowired
    DataQualityCategoryRepo dataQualityCategoryRepo;

    @Autowired
    CustomStagedBatchRecordRepoImpl customStagedBatchRecordRepo;

    public void sendDFIResultNotification(DFIResultManager mgr) {
        if (mgr.isEmpty()) {
            log.debug("no dfi results to process");
            return;
        }
        Iterable<Map<String, Object>> transformedBatches = mgr.transformResultBatchesIterable();
        for (Map<String, Object> batchPayload : transformedBatches) {
            Event event = new Event().setType(EventTypes.DFI_RESULT_NOTIFICATION).setDetails(batchPayload);
            publisher.publishToDFIResultQueue(event);
        }
    }

    public void addCategoryToMap(String categoryId, Map<String, String> categoryMap) {
        if (!categoryMap.containsKey(categoryId)) {
            Optional<DataQualityCategory> categoryOpt = dataQualityCategoryRepo.findById(categoryId);
            String categoryName = categoryOpt.isPresent() ? categoryOpt.get().getName() : "";
            categoryMap.put(categoryId, categoryName);
        }
    }

    public DFIRuleExecutionResult evaluateRule(String recordId, AttributeDefinition attrDefn, String scope, DataQualityRule rule, Map<String, Object> context, Map<String, String> categoryMap) {
        DFIRuleExecutionResult result = new DFIRuleExecutionResult().setRuleId(rule.getId()).setCategoryId(rule.getCategory());
        if (StringUtils.isBlank(recordId)) {
            String errorMsg = String.format("Cannot execute DFI rule %s as record id is invalid", rule.getId());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        if ((scope.equals(DFIConstants.ATTRIBUTE_SCOPE) && StringUtils.isBlank(attrDefn.getId()))) {
            String errorMsg = String.format("Cannot execute DFI rule %s for recordId %s as attribute id is invalid", rule.getId(), recordId);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        if (scope.equals(DFIConstants.ATTRIBUTE_SCOPE))
            result.setSyncariAttributeId(attrDefn.getId());
        addCategoryToMap(rule.getCategory(), categoryMap);
        result.setRuleId(rule.getId()).setCategoryId(rule.getCategory()).setSyncariRecordId(recordId).setCategoryName(categoryMap.get(rule.getCategory()));
        result.setRuleName(rule.getName());

        Map<String, Object> predicate = rule.getRuleConfig();
        Expression filterExpression = new PredicateParser().fromDFIRuleConfig(recordId, attrDefn, predicate);

        var evaluator = new FilterEvaluationVisitor(context, tokenHelper, schemaService, referenceDataService, entityRepo, customStagedBatchRecordRepo);
        var expression = Expression.DFIExpression(filterExpression);
        expression.accept(new DynamicDispatchVisitor(evaluator));
        Object finalValue = evaluator.getValue();
        boolean evalResult = finalValue instanceof Boolean && (boolean) finalValue;
        result.setResult(evalResult);
        return result;
    }

}
