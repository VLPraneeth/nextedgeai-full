package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.FilterEvaluationVisitor;
import com.syncari.core.pipeline.FilterValueComparator;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.BiFunction;

import org.apache.commons.lang3.StringUtils;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
public class DedupeEvaluationVisitor extends SimpleExpressionVisitor {

    protected Map<String, Object> context;
    Stack<Object> values = new Stack<>();
    protected FilterValueComparator comparator;
    private EntityDefinition entityDefinition;
    private EntityData incomingRecord;

    public Object getValue() {
        return values.pop();
    }

    public DedupeEvaluationVisitor(EntityData record, EntityDefinition entityDefinition, EntityData incomingRecord) {
        this.context = record.getValues();
        this.comparator = new FilterValueComparator();
        this.entityDefinition = entityDefinition;
        this.incomingRecord = incomingRecord;
    }

    @Override
    public void visit(If exp) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_if"));
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

    @Override
    public void visit(Not exp) {
        values.push(Boolean.TRUE.equals(values.pop()));
    }

    @Override
    public void visit(FunctionExpression exp) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_function"));
    }

    private void binaryOp(BinaryExpression exp, BiFunction<Object, Object, Object> biFunction) {
        Object right = values.pop();

        Object left = values.pop();

        String apiName = left.toString();

        // get right value from incoming record
        Object rightValue = getTypedValue(right, apiName);
        Object leftValue = getLeftValue(left);

        values.push(biFunction.apply(leftValue, rightValue));
    }

    private Object getLeftValue(Object left) {
        String apiName = left.toString();
        return context.get(apiName);
    }

    @Override
    public void visit(Equal equal) {
        binaryOp(equal, (left, right) -> {
            if (right == null) {
                return false;
            } else {
                return compare(left, right, equal);
            }
        });
    }

    @Override
    public void visit(EqualIgnoreCase equal) {

        binaryOp(equal, (left, right) -> {
            boolean value = false;
            if (!Objects.isNull(right) && !Objects.isNull(left)) {
                value = right.toString().equalsIgnoreCase(left.toString());
            }
            return value;
        });
    }

    @Override
    public void visit(NotEqual notEqual) {
        binaryOp(notEqual, (left, right) -> {
            return compare(left, right, notEqual);
        });
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        binaryOp(greaterThan, (left, right) -> {
            return compare(left, right, greaterThan);
        });
    }

    @Override
    public void visit(LessThan lessThan) {
        binaryOp(lessThan, (left, right) -> {
            return compare(left, right, lessThan);
        });
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        values.push(literalExpression.getValue());
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

    @Override
    public void visit(VariableExpression variableExpression) {

        String fieldId = variableExpression.getVariableName();
        AttributeDefinition attributeDefinition = entityDefinition.getIdToAttributes().get(fieldId);
        if(attributeDefinition == null){
            throw new SyncariValidationException(format("Could not find attribute for id %s in entity %s",fieldId, entityDefinition.getApiName()));
        }

        values.push(attributeDefinition.getApiName());
    }

    @Override
    public void visit(BetweenExpression betweenExpression) {
        throw new UnsupportedOperationException(i18n("unsupported_dedupe_operator_between"));
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        binaryOp(gteExpression, (left, right) -> {
            return compare(left, right, gteExpression);
        });
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        binaryOp(lteExpression, (left, right) -> {
            return compare(left, right, lteExpression);
        });
    }

    @Override
    public void visit(StartsWith startsWithExpression) {

        binaryOp(startsWithExpression, (left, right) -> {
            boolean value = false;
            if (right == null || (right instanceof String && StringUtils.isBlank((String) right))) {
                return false;
            }
            if (!Objects.isNull(right) && !Objects.isNull(left)) {
                value = left.toString().startsWith(right.toString());
            }
            return value;
        });
    }

    @Override
    public void visit(Empty isEmptyExpression) {
        Object leftValue = getLeftValue(values.pop());

        if (leftValue instanceof String && leftValue != null) {
            values.push(leftValue.toString().isEmpty());
        } else {
            values.push(Objects.isNull(leftValue));
        }
    }

    @Override
    public void visit(NotEmpty isNotEmptyExpression) {
        Object leftValue = getLeftValue(values.pop());
        if (leftValue instanceof String && leftValue != null) {
            values.push(!leftValue.toString().isEmpty());
        } else {
            values.push(!Objects.isNull(leftValue));
        }
    }

    @Override
    public void visit(Contains contains) {
        binaryOp(contains, (left, right) -> {
            boolean value = false;

            if (right == null || (right instanceof String && StringUtils.isBlank((String) right))) {
                return false;
            }
            if (!Objects.isNull(right) && !Objects.isNull(left)) {
                value = left.toString().contains(right.toString());
            }
            return value;
        });
    }

    @Override
    public void visit(NotContains contains) {
        binaryOp(contains, (left, right) -> {
            boolean value = true;

            if (right == null) {
                return true;
            }

            if (!Objects.isNull(right) && !Objects.isNull(left)) {
                value = !left.toString().contains(right.toString());
            }
            return value;
        });
    }

    @Override
    public void visit(NotIn exp) {
        Object right = values.pop();

        Object left = values.pop();
        String apiName = left.toString();

        Object leftValue = context.get(apiName);

        Object value = true;
        if (right == null) {
            value = true;
        } else if (List.class.isAssignableFrom(right.getClass())) {
            var list = List.class.cast(right);
            value = !list.stream().anyMatch(f -> comparator.compare(leftValue, getTypedValue(f, apiName)) == 0);
        } else {
            Object rightValue = getTypedValue(right, apiName);
            if (Objects.nonNull(rightValue) && Objects.nonNull(leftValue)) {
                value = !rightValue.toString().contains(leftValue.toString());
            }
        }
        values.push(value);

    }

    @Override
    public void visit(In exp) {

        Object right = values.pop();

        Object left = values.pop();
        String apiName = left.toString();

        Object leftValue = context.get(apiName);

        Object value = false;
        if (right == null) {
            value = false;
        } else if (List.class.isAssignableFrom(right.getClass())) {
            var list = List.class.cast(right);
            value = list.stream().anyMatch(f -> comparator.compare(leftValue, getTypedValue(f, apiName)) == 0);
        } else {
            Object rightValue = getTypedValue(right, apiName);
            if (Objects.nonNull(rightValue) && Objects.nonNull(leftValue)) {
                value = rightValue.toString().contains(leftValue.toString());
            }
        }
        values.push(value);
    }

    private Object getTypedValue(Object value, String key) {

        return entityDefinition.getField(key)
                .map(attrib -> {
                    // if value is not null
                    return Optional.ofNullable(value).map(va -> {
                        Optional<AttributeDefinition> attributeDefinition = Optional.ofNullable(entityDefinition.getIdToAttributes().get(va.toString()));
                        Object targetValue = va;
                        if(attributeDefinition.isPresent()) {
                            targetValue = attributeDefinition.get().convert(incomingRecord.getValue(attributeDefinition.get().getApiName()));
                        } else {
                            targetValue = attrib.convert(va);
                        }
                        return targetValue;
                    }).orElse(attrib.convert(null));
                })
                .orElse(value);
    }
}
