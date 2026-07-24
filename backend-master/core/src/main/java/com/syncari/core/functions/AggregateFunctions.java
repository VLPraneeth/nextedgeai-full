package com.syncari.core.functions;

import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.LookupCriteriaVisitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class AggregateFunctions extends FunctionsBase {
    @Autowired
    SchemaService schemaService;
    @Autowired
    TokenHelper tokenHelper;
    @Autowired
    EntityRepoService entityService;
    @Autowired
    EntityRepo entityRepo;

    @Function
    public FunctionResult sumRecordsOnField(Object input, FunctionCall functionCall, GraphContext context) {
        return sumRecords(input, functionCall,context);
    }

    @Function
    public FunctionResult countRecordsOnField(Object input, FunctionCall functionCall, GraphContext context) {
        return countRecords(input, functionCall,context);
    }

    @Function
    public FunctionResult avgRecordsOnField(Object input, FunctionCall functionCall, GraphContext context) {
        return avgRecords(input, functionCall,context);
    }

    @Function
    public FunctionResult stdDevRecordsOnField(Object input, FunctionCall functionCall, GraphContext context) {
        return stdDevRecords(input, functionCall,context);
    }

    @Function
    public FunctionResult sumRecords(Object input, FunctionCall functionCall, GraphContext context) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        Map<String, Object> predicates = getConfig("predicate", functionCall, context);
        try {
            EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
            Expression expression = MapUtils.isEmpty(predicates) ? Expression.notEmpty(Expression.var("lastModified")) :
                    new PredicateParser(StringUtils.EMPTY).fromMap(predicates);
            Optional<LookupCriteriaVisitor> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                    entity.getIdToAttributes(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entity, key)));
            Optional<AttributeDefinition> attribute = functionCall.getConfig("fieldId", StringType.VALUE).flatMap(a -> Optional.ofNullable(entity.getAttribute(a)));
            attribute.ifPresent(a -> {
                if (IntegerType.VALUE.equals(a.getDataType()) || DoubleType.VALUE.equals(a.getDataType())) {
                    double sum = entityService.sum(entity, a, mongoCriteria);
                    context.put("previousValue", sum);
                    context.put("Value From " + context.getCurrentNode().getName(), sum);
                    log.info("Results of sum (Node {}) for syncariEntity {} field {} and found {}"
                            , context.getCurrentNode().getId(), entity.getApiName(), a.getApiName(), sum);
                }

            });
            return new FunctionResult(input, ObjectType.VALUE);
        } catch (Exception e) {
            log.error("Error in advancedLookUpSyncariRecord", e);
        }
        return new FunctionResult(input, ObjectType.VALUE, null);
    }

    @Function
    public FunctionResult avgRecords(Object input, FunctionCall functionCall, GraphContext context) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        Map<String, Object> predicates = getConfig("predicate", functionCall, context);
        try {
            EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
            Expression expression = MapUtils.isEmpty(predicates) ? Expression.notEmpty(Expression.var("lastModified")) :
                    new PredicateParser(StringUtils.EMPTY).fromMap(predicates);
            Optional<LookupCriteriaVisitor> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                    entity.getIdToAttributes(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entity, key)));
            Optional<AttributeDefinition> attribute = functionCall.getConfig("fieldId", StringType.VALUE).flatMap(a -> Optional.ofNullable(entity.getAttribute(a)));
            attribute.ifPresent(a -> {
                if (IntegerType.VALUE.equals(a.getDataType()) || DoubleType.VALUE.equals(a.getDataType())) {
                    double avg = entityService.avg(entity, a, mongoCriteria);
                    context.put("previousValue", avg);
                    context.put("Value From " + context.getCurrentNode().getName(), avg);
                    log.info("Results of avg (Node {}) for syncariEntity {} on field {} and found {}"
                            , context.getCurrentNode().getId(), context.getCurrentNode().getName(), a.getApiName(), avg);
                }

            });
            return new FunctionResult(input, ObjectType.VALUE);
        } catch (Exception e) {
            log.error("Error in advancedLookUpSyncariRecord", e);
        }
        return new FunctionResult(input, ObjectType.VALUE, null);
    }

    @Function
    public FunctionResult countRecords(Object input, FunctionCall functionCall, GraphContext context) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        Map<String, Object> predicates = getConfig("predicate", functionCall, context);
        long startTime = System.currentTimeMillis();
        try {
            EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
            Expression expression = MapUtils.isEmpty(predicates) ? Expression.notEmpty(Expression.var("lastModified")) :
                    new PredicateParser(StringUtils.EMPTY).fromMap(predicates);
            Optional<LookupCriteriaVisitor> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                    entity.getIdToAttributes(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entity, key)));
            double count = entityService.count(entity, mongoCriteria);
            context.put("previousValue", count);
            context.put("Value From " + context.getCurrentNode().getName(), count);
            log.info("Results of count ( Node {}) for syncariEntity {} and found {}. Took {} ms"
                    , context.getCurrentNode().getId(), entity.getApiName(), count, System.currentTimeMillis() - startTime);
            return new FunctionResult(input, ObjectType.VALUE);
        } catch (Exception e) {
            log.error("Error in advancedLookUpSyncariRecord", e);
        }
        return new FunctionResult(input, ObjectType.VALUE, null);
    }

    @Function
    public FunctionResult stdDevRecords(Object input, FunctionCall functionCall, GraphContext context) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        Map<String, Object> predicates = getConfig("predicate", functionCall, context);
        try {
            EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
            Expression expression = MapUtils.isEmpty(predicates) ? Expression.notEmpty(Expression.var("lastModified")) :
                    new PredicateParser(StringUtils.EMPTY).fromMap(predicates);
            Optional<LookupCriteriaVisitor> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                    entity.getIdToAttributes(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entity, key)));
            Optional<AttributeDefinition> attribute = functionCall.getConfig("fieldId", StringType.VALUE).flatMap(a -> Optional.ofNullable(entity.getAttribute(a)));
            attribute.ifPresent(a -> {
                if (IntegerType.VALUE.equals(a.getDataType()) || DoubleType.VALUE.equals(a.getDataType())) {
                    double stdDev = entityService.stdDev(entity, a, mongoCriteria);
                    context.put("previousValue", stdDev);
                    context.put("Value From " + context.getCurrentNode().getName(), stdDev);
                    log.info("Results of std (Node {})for syncariEntity {} on field {} and found {}"
                            , context.getCurrentNode().getId(), entity.getApiName(), a.getApiName(), stdDev);
                }

            });
            return new FunctionResult(input, ObjectType.VALUE);
        } catch (Exception e) {
            log.error("Error in advancedLookUpSyncariRecord", e);
        }
        return new FunctionResult(input, ObjectType.VALUE, null);
    }
}
