package com.syncari.core.pipeline;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.pipeline.expression.DFIExpression;
import com.syncari.core.pipeline.expression.UniqueLookUpExpression;
import com.syncari.core.repositories.customer.CustomStagedBatchRecordRepoImpl;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.ReferenceDataService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jtwig.value.Undefined;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Slf4j
public class FilterEvaluationVisitor extends SimpleExpressionVisitor {

    protected Map<String, Object> context;
    protected TokenHelper tokenHelper;
    Stack<Object> values = new Stack<>();
    private Object value;
    protected FilterValueComparator comparator;
    private SchemaService schemaService;
    private ReferenceDataService referenceDataService;
    private EntityRepo entityRepo;
    private CustomStagedBatchRecordRepoImpl customStagedBatchRecordRepo;
    private Map<String, String> dataTypeMap = new HashMap<>();
    private boolean foundEmptyValue = false;

    public Object getValue() {
        return value == null ? values.pop() : value;
    }

    public FilterEvaluationVisitor(Map<String, Object> context, TokenHelper tokenHelper) {
        this.context = context;
        this.tokenHelper = tokenHelper;
        this.comparator = new FilterValueComparator();
    }

    public FilterEvaluationVisitor(Map<String, Object> context, TokenHelper tokenHelper, SchemaService schemaService) {
        this.context = context;
        this.tokenHelper = tokenHelper;
        this.comparator = new FilterValueComparator();
        this.schemaService = schemaService;
    }

    public FilterEvaluationVisitor(Map<String, Object> context, TokenHelper tokenHelper, SchemaService schemaService,
                                   ReferenceDataService referenceDataService, EntityRepo entityRepo, CustomStagedBatchRecordRepoImpl customStagedBatchRecordRepo) {
        this.context = context;
        this.tokenHelper = tokenHelper;
        this.comparator = new FilterValueComparator();
        this.schemaService = schemaService;
        this.referenceDataService = referenceDataService;
        this.entityRepo = entityRepo;
        this.customStagedBatchRecordRepo = customStagedBatchRecordRepo;
    }

    public void visit(If exp) {
        // evaluate condition
        var falseValue = values.pop();
        var trueExp = values.pop();
        var cond = values.pop();
        values.push(Boolean.TRUE.equals(cond) ? trueExp : falseValue);
    }

    public void visit(DFIExpression exp) {
        var cond = values.pop();
        values.push(Boolean.TRUE.equals(cond));
    }

    public void visit(Equal exp) {
        compare(exp);
    }
    
    public void visit(EqualIgnoreCase exp) {

        var right = values.pop();
        var left = values.pop();

        boolean value = false;
        if (!Objects.isNull(right) && !Objects.isNull(left)) {
            value = right.toString().equalsIgnoreCase(left.toString());
        }
        values.push(value);
    }

    public void visit(And exp) {
        var right = values.pop();
        var left = values.pop();
        values.push(Boolean.TRUE.equals(right) && Boolean.TRUE.equals(left));
    }

    public void visit(Or exp) {

        var right = values.pop();
        var left = values.pop();
        values.push(Boolean.TRUE.equals(right) || Boolean.TRUE.equals(left));
    }

    public void visit(GreaterThan exp) {
       compare(exp);
    }


    private void compare(BinaryExpression exp) {
        var right = values.pop();
        var left = values.pop();
        values.push(compare(left, right, exp));
    }

    private boolean compare(Object left, Object right, BinaryExpression expression) {
        switch(expression.getName()) {
            case GreaterThan.NAME:
                return comparator.compare(left, right) > 0;
            case LessThan.NAME:
                return comparator.compare(left, right) < 0;
            case GreaterThanEqual.NAME:
                return comparator.compare(left, right) >= 0;
            case LessThanEqual.NAME:
                return comparator.compare(left, right) <= 0;
            case Equal.NAME:
                return comparator.compare(left, right) == 0;
            case NotEqual.NAME:
                return comparator.compare(left, right) != 0;
            default:
                throw new RuntimeException("Unsupported operation " + expression.getName());
        }
    }

    public void visit(LessThan exp) {
        compare(exp);
    }

    public void visit(NotEqual exp) {
        compare(exp);
    }

    public void visit(Not exp) {
        values.push(Boolean.TRUE.equals(values.pop()));
    }

    public void visit(LiteralExpression exp) {

        // this is a literal expression
        // if not a string, return as it is
        Object resolvedValue = null;
        if (exp.getValue() != null) {
            var value = exp.getValue();
            if (List.class.isAssignableFrom(value.getClass())) {
            	List<Object> valueList = List.class.cast(value);
            	List<Object> literalValues = valueList.stream().map(m -> getLiteralValue(m)).collect(Collectors.toList());
            	foundEmptyValue = foundEmptyValue || literalValues.isEmpty() || literalValues.stream()
                        .allMatch(m-> StringUtils.isBlank(Objects.toString(m,null)));
                resolvedValue = literalValues;
            } else {
                resolvedValue = getLiteralValue(value);
                foundEmptyValue = foundEmptyValue || StringUtils.isBlank(Objects.toString(resolvedValue,null));
            }
        } else {
        	foundEmptyValue = true;
        }
        values.push(resolvedValue);
    }

    protected Object getLiteralValue(Object value) {

        if (value instanceof String) {
            if(tokenHelper.hasTokens(Objects.toString(value,null))) {
                return tokenHelper.resolveTokens(context, Objects.toString(value, null)).getY();
            }
        }
        return value;
    }

    protected String getStringValue(Object value) {
        if(String.class.isAssignableFrom(value.getClass())) {
            return ("\"" + value + "\"");
        }else{
            return value.toString();
        }
    }
    
    public boolean foundEmptyValuedPredicates(){
        return foundEmptyValue;
    }

    public void visit(UniqueLookUpExpression exp) {
        String entityId = exp.getEntityId();
        String attrId = exp.getAttributeId();
        Object lookUpValue = evaluateFieldVariable(exp.getVariableName());
        if (attrId == null || attrId.isEmpty()) {
            log.error("Expression {} incomplete for entity lookup. Missing attribute ID for lookup", exp);
            values.push(false);
            return;
        }
        GraphContext graphContext = (GraphContext) context;
        if (entityId == null || entityId.isEmpty()) {
            if (graphContext.getSyncariEntity() == null) {
                log.error("Entity ID is not available for exp {}. Cannot fetch entity ID from context", exp);
                values.push(false);
                return;
            }
            entityId = graphContext.getSyncariEntity().getId();
        }
        CurrentBatch currBatch = graphContext.getCurrentBatch();
        List<String> currentBatchIds = currBatch.getStagedBatchRepo().findByCurrentBatchId(currBatch.getCurrentBatchId()).stream().map(StagedBatch::getId).collect(Collectors.toList());
        String syncariId = graphContext.getCurrentSyncariId();
        List<StagedBatchRecord> records = customStagedBatchRecordRepo.getStagedRecordBySyncariId(syncariId, currentBatchIds);
        if (records.size() != 1) {
            log.error("Cannot get the current staged record. result size : {}", records.size());
            values.push(false);
            return;
        }
        StagedBatchRecord stagedBatchRecord = records.get(0);
        boolean result = isUniqueCheckCurrentBatch(attrId, syncariId, lookUpValue) &&
                isUniqueCheckEntityData(entityId, attrId, lookUpValue, stagedBatchRecord.isNew());
        values.push(result);
    }

    private boolean isUniqueCheckCurrentBatch(String attrId, String recId, Object value){
        GraphContext graphContext = (GraphContext) context;
        CurrentBatch currBatch = graphContext.getCurrentBatch();
        List<String> currentBatchIds = currBatch.getStagedBatchRepo().findByCurrentBatchId(currBatch.getCurrentBatchId()).stream().map(StagedBatch::getId).collect(Collectors.toList());
        AttributeDefinition attrDfn = graphContext.getSyncariEntity().getAttribute(attrId);
        boolean lookUpResult = customStagedBatchRecordRepo.exists(currentBatchIds, recId, attrDfn != null ? attrDfn.getApiName() : "", value);
        return !lookUpResult;
    }

    private boolean isUniqueCheckEntityData(String entityId, String attrId, Object value, boolean isNewRecord) {
        if (schemaService == null || entityRepo == null) {
            log.error("Schema service and entityRepo is required for unique expression lookup");
            return false;
        }
        EntityDefinition entity = schemaService.getEntity(entityId);
        if (entity == null) {
            log.error("Is unique lookup failed because entity not found for id {}", entityRepo);
            return false;
        }
        AttributeDefinition attr = entity.getAttribute(attrId);
        if (attr == null) {
            log.error("Is unique lookup failed because entity field not found for entity id {}, attribute id {}", entityId, attrId);
            return false;
        }
        int count = entityRepo.countByAttributeWithMaxLimit(entity.getApiName(), attr.getApiName(), value, 2);
        return isNewRecord ? count == 0 : count == 1;
    }

    private Object evaluateFieldVariable(String variableName) {
        Object value;
        if(tokenHelper.hasTokens(variableName))
            value = getLiteralValue(variableName);
        else {
            value = evaluateVariable(variableName);
            if(schemaService != null && variableName != null && variableName.startsWith("field_")) {
                log.debug("Fetching variable expression datatype for {}", variableName);
                String[] splitStr = variableName.split("_");
                Optional<Pair> valueDataTypePair = Optional.empty();
                Optional<String> attributeId = Optional.empty();
                if (splitStr.length == 2) attributeId = Optional.of(splitStr[1]);
                if (attributeId.isPresent()) {
                    Optional<AttributeDefinition> attributeDefinition = schemaService.findAttribute(attributeId.get());
                    if (attributeDefinition.isPresent()) {
                        Datatype datatype = attributeDefinition.get().getDataType();
                        log.debug("Datatype found for field {}", variableName);
                        valueDataTypePair = Optional.of(Pair.of(value, datatype));
                    }
                }
                if (valueDataTypePair.isEmpty()) {
                    return value;
                } else {
                    return valueDataTypePair.get().getX();
                }
            } else {
                return value;
            }
        }
        return value;

    }

    public void visit(VariableExpression exp) {
        String variableName = exp.getVariableName();
        if(tokenHelper.hasTokens(variableName)) {
        	Object value = getLiteralValue(variableName);
        	values.push(value);
        } else {
        	Object value = evaluateVariable(variableName);
        	if(schemaService != null && variableName != null && variableName.startsWith("field_")) {
        		log.debug("Fetching variable expression datatype for {}", variableName);
        		String[] splitStr = variableName.split("_");
        		Optional<Pair> valueDataTypePair = Optional.empty();
        		Optional<String> attributeId = Optional.empty();
        		if (splitStr.length == 2) attributeId = Optional.of(splitStr[1]);
        		if (attributeId.isPresent()) {
        			Optional<AttributeDefinition> attributeDefinition = schemaService.findAttribute(attributeId.get());
        			if (attributeDefinition.isPresent()) {
        				Datatype datatype = attributeDefinition.get().getDataType();
        				log.debug("Datatype found for field {}", variableName);
        				valueDataTypePair = Optional.of(Pair.of(value, datatype));
        			}
        		}
        		if (valueDataTypePair.isEmpty()) {
        			values.push(value);
        		} else {
        			values.push(valueDataTypePair.get());
        		}
        	} else {
        		values.push(value);
        	}
        }
    }

    Object evaluateVariable(String variableName) {
        if (StringUtils.isBlank(variableName)) {
            return null;
        }

        //TODO: Remove this once we remove fall back to v2
        if (variableName.indexOf('.') < 0) {
            return context.get(variableName);
        } else {
            if (variableName.startsWith("output_")) {
                String[] parts = variableName.split("\\.");
                if (parts.length == 2 && parts[1].equals("x")) {
                    return ((Pair) context.get(parts[0])).getX();
                }
            }
        }
        return tokenHelper.resolveTokens(context, String.format("{{%s}}", variableName)).getY();
    }

    public void visit(FilterFailedExpression exp) {
        values.push(new FilterFailedResult(evaluateVariable(exp.getVariableName())));
    }

    public void visit(IsInReferenceData exp) {
        var right = values.pop();
        var left = values.pop();
        values.push(isInReferenceData(left, right, false));
    }

    public void visit(IsInReferenceDataCaseInsensitive exp) {
        var right = values.pop();
        var left = values.pop();
        values.push(isInReferenceData(left, right, true));
    }

    private boolean isInReferenceData(Object left, Object right, boolean ignoreCase) {
        if (referenceDataService == null) {
            log.error("reference data service is required for reference data lookup");
            return false;
        }
        if (!(right instanceof String || String.valueOf(right).split("/").length != 2)) {
            log.error("Invalid right value {} for isInReferenceData expression", right);
            return false;
        }
        String[] rightValue = String.valueOf(right).split("/");
        String datasetName = rightValue[0];
        String fieldName = rightValue[1];
        Object leftValue;
        if (left instanceof Pair)
            leftValue = ((Pair<?, ?>) left).getX();
        else
            leftValue = left;
        try {
            long count = referenceDataService.count(datasetName, fieldName, leftValue, ignoreCase);
            return count > 0;
        } catch (Exception e) {
            log.error("Error executing isInReferenceData function for dataset {} field {} and value {}. ERror : ", datasetName, fieldName, rightValue, e);
            return false;
        }
    }
    public void visit(Email exp) {
        values.push(isEmail(values.pop()));
    }

    private boolean isEmail(Object value) {
        if (value instanceof String)
            return TextUtil.isValidEmail((String) value);
        else if (value instanceof Pair) {
            Pair<Object, Datatype> valueDataType = (Pair<Object, Datatype>) value;
            Object emailObject = valueDataType.getX();
            if (!(emailObject instanceof String)) {
                log.info("invalid object, given email is not string :{}", emailObject);
                return false;
            }
            return TextUtil.isValidEmail((String) emailObject);
        } else {
            log.info("Invalid object : {} for email check",value);
            return false;
        }
    }

    public void visit(PhoneNumber exp) {
        values.push(isPhoneNumber(values.pop()));
    }

    private boolean isPhoneNumber(Object value) {
        if (value instanceof String)
            return PhoneNumber.isValidPhoneNumber((String) value);
        Pair<Object, Datatype> valueDataType = (Pair<Object, Datatype>) value;
        Object phoneObject = valueDataType.getX();
        if (!(phoneObject instanceof String || phoneObject instanceof Number)){
            log.info("invalid object. give phone number is not a string or number:{}", phoneObject);
            return false;
        }
        return PhoneNumber.isValidPhoneNumber(phoneObject instanceof String ? (String) phoneObject : String.valueOf(phoneObject));
    }

    public void visit(Empty exp) {
        Object value = values.pop();
        if (value instanceof Pair) {
            Pair<Object, Datatype> valuePair = (Pair<Object, Datatype>) value;
            values.push(isEmpty(valuePair.getX()));
        } else
            values.push(isEmpty(value));
    }

    private boolean isEmpty(Object value) {
        return value == null || value == Undefined.UNDEFINED || (value instanceof String && StringUtils.isEmpty(value.toString()))
                || (List.class.isAssignableFrom(value.getClass()) && List.class.cast(value).isEmpty());
    }

    public void visit(NotEmpty exp) {
        Object value = values.pop();
        if (value instanceof Pair) {
            Pair<Object, Datatype> valuePair = (Pair<Object, Datatype>) value;
            values.push(!isEmpty(valuePair.getX()));
        } else
            values.push(!isEmpty(value));
    }

    @Override
    public void visit(BetweenExpression betweenExpression) {

        Expression gte = Expression.gte(betweenExpression.getExpression(),betweenExpression.getLower());
        Expression lt = Expression.lt(betweenExpression.getExpression(),betweenExpression.getUpper());

        var filterVisitor = new FilterEvaluationVisitor(context, tokenHelper);
        Expression.and(gte,lt).accept(filterVisitor);

        var evaluator = new FilterEvaluationVisitor(context, tokenHelper);
        Expression.and(gte,lt).accept(new DynamicDispatchVisitor(evaluator));
        foundEmptyValue = foundEmptyValue || evaluator.foundEmptyValue;
        values.push(evaluator.value);
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        compare(gteExpression);
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        compare(lteExpression);
    }

    @Override
    public void visit(Contains expression) {

        var right = values.pop();
        var leftObj = values.pop();

        var left = leftObj instanceof Pair ? ((Pair<?, ?>) leftObj).getX() : leftObj;

        boolean value = false;
        if (left != null && right != null) {
            var rType = right.getClass();
            var lType = left.getClass();
            if (rType == lType && rType == String.class) {
                value = ((String) left).contains(right.toString());
            } else if (List.class.isAssignableFrom(lType)) {
                var list = List.class.cast(left);
                value = list.stream().anyMatch(f -> comparator.compare(f, right) == 0);
            }
        }
        values.push(value);
    }

    @Override
    public void visit(MatchesRegex regexExpression) {
        values.push(matchesRegex());
    }

    private boolean matchesRegex() {
        var right = values.pop();
        var leftVal = values.pop();
        String left;
        if (leftVal instanceof Pair) {
            Pair<Object, Datatype> valueDataType = (Pair<Object, Datatype>) leftVal;
            Object valueObject = valueDataType.getX();
            if (!(valueObject instanceof String || valueObject instanceof Number)){
                log.error("Invalid object type for left (expected String or numbers in Pair): {}", valueObject != null ? valueObject.getClass().getName() : "null");
                return false;
            }
            left = valueObject instanceof String ? (String) valueObject : String.valueOf(valueObject);
        } else if (leftVal instanceof String) {
            left = (String) leftVal;
        } else {
            log.error("Invalid type for left (expected String or Pair): {}", leftVal != null ? leftVal.getClass().getName() : "null");
            return false;
        }
        String patternString;
        if (right instanceof String) {
            String rawPatternFromForm = (String) right;
            patternString = rawPatternFromForm.replaceAll("\\\\\\\\", "\\\\");
        } else {
            log.error("Error: Expected 'right' to be a String containing the regex pattern, but found: {}", right != null ? right.getClass().getName() : "null");
            return false;
        }
        boolean result;
        try {
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(left);
            result = matcher.matches();
        } catch (PatternSyntaxException e) {
            log.error("Invalid regex pattern syntax provided: \"{}\". Error: {}", patternString, e.getMessage());
            return false;
        }
        return result;
    }

    @Override
    public void visit(StartsWith startsWithExpression) {
        startsWith(true);
    }

    private void startsWith(boolean starts) {
        var right = values.pop();
        var leftObj = values.pop();
        var left = leftObj instanceof Pair ? ((Pair<?, ?>) leftObj).getX() : leftObj;

        boolean value = false;
        if (left != null && right != null) {
            var rType = right.getClass();
            var lType = left.getClass();
            if (rType == lType && rType == String.class) {
                value = ((String) left).startsWith(right.toString());
            } else if (List.class.isAssignableFrom(lType)) {
                value = ((List)left).contains(right);
                value = starts ? value : !value;
            }
        }
        values.push(value);
    }


    public void visit(NotStartsWith startsWithExpression) {
        startsWith(false);
    }

    @Override
    public void visit(NotIn expression) {
        var right = values.pop();
        var leftObj = values.pop();
        var left = leftObj instanceof Pair ? ((Pair<?, ?>) leftObj).getX() : leftObj;

        boolean value = false;
        if (right != null && List.class.isAssignableFrom(right.getClass())) {
            var list = List.class.cast(right);
            value = !list.stream().anyMatch(f -> comparator.compare(f, left) == 0);
        }
        values.push(value);
    }

    @Override
    public void visit(In expression) {
        var right = values.pop();
        var leftObj = values.pop();
        var left = leftObj instanceof Pair ? ((Pair<?, ?>) leftObj).getX() : leftObj;

        boolean value = false;
        if (right != null && List.class.isAssignableFrom(right.getClass())) {
            var list = List.class.cast(right);
            value = list.stream().anyMatch(f -> comparator.compare(f, left) == 0);
        }
        values.push(value);
    }
}
