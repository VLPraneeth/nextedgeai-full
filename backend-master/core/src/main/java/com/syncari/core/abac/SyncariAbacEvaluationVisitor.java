package com.syncari.core.abac;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jtwig.value.Undefined;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.FilterValueComparator;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.And;
import com.syncari.core.pipeline.expression.BetweenExpression;
import com.syncari.core.pipeline.expression.BinaryExpression;
import com.syncari.core.pipeline.expression.Contains;
import com.syncari.core.pipeline.expression.Empty;
import com.syncari.core.pipeline.expression.Equal;
import com.syncari.core.pipeline.expression.EqualIgnoreCase;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.GreaterThan;
import com.syncari.core.pipeline.expression.GreaterThanEqual;
import com.syncari.core.pipeline.expression.If;
import com.syncari.core.pipeline.expression.In;
import com.syncari.core.pipeline.expression.LessThan;
import com.syncari.core.pipeline.expression.LessThanEqual;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.Not;
import com.syncari.core.pipeline.expression.NotEmpty;
import com.syncari.core.pipeline.expression.NotEqual;
import com.syncari.core.pipeline.expression.NotIn;
import com.syncari.core.pipeline.expression.NotStartsWith;
import com.syncari.core.pipeline.expression.Or;
import com.syncari.core.pipeline.expression.StartsWith;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;

public class SyncariAbacEvaluationVisitor extends SimpleExpressionVisitor {

    protected Map<String, Object> context;
    private AbacAttributeRepo attributeRepo;
    protected TokenHelper tokenHelper;
    protected FilterValueComparator comparator;
    Stack<Object> values = new Stack<>();
    private Object value;

    public Object getValue() {
        return value == null ? values.pop() : value;
    }

    public SyncariAbacEvaluationVisitor(Map<String, Object> context, AbacAttributeRepo attributeRepo, TokenHelper tokenHelper) {
        this.context = context;
        this.attributeRepo = attributeRepo;
        this.tokenHelper = tokenHelper;
        this.comparator = new FilterValueComparator();
    }

    public void visit(If exp) {
        // evaluate condition
        var falseValue = values.pop();
        var trueExp = values.pop();
        var cond = values.pop();
        values.push(Boolean.TRUE.equals(cond) ? trueExp : falseValue);
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
                resolvedValue = literalValues;
            } else {
                resolvedValue = getLiteralValue(value);
            }
        }
        values.push(resolvedValue);
    }

    protected Object getLiteralValue(Object value) {
        if (value instanceof String) {
            if(TokenHelper.hasTokens(Objects.toString(value,null))) {
                return tokenHelper.resolveTokens(context, Objects.toString(value, null)).getY();
            }
        }
        return value;
    }

    public void visit(VariableExpression exp) {
      String variableName = exp.getVariableName();
      if (TokenHelper.hasTokens(variableName)) {
        Object value = getLiteralValue(variableName);
        values.push(value);
      } else {

        if (variableName != null && variableName.startsWith("field_")) {
          variableName = variableName.substring("field_".length());
        }
        if (attributeRepo != null) {
          var attrOpt = attributeRepo.findById(variableName);
          if (attrOpt.isPresent()) {
            var attr = attrOpt.get();
            if (attr.getResourceType() == ResourceType.USER) {
              variableName = String.format("%s.%s_%s", "user", SyncariContext.getSyncariId(),
                  attr.getApiName());
            } else {
              variableName = String.format("%s.%s", "resource", attr.getApiName());
            }
            Object value =
                tokenHelper.resolveTokens(context, String.format("{{%s}}", variableName)).getY();
            values.push(value);
          }
        }
      }
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

        var filterVisitor = new SyncariAbacEvaluationVisitor(context, attributeRepo, tokenHelper);
        Expression.and(gte,lt).accept(filterVisitor);

        var evaluator = new SyncariAbacEvaluationVisitor(context, attributeRepo, tokenHelper);
        Expression.and(gte,lt).accept(new DynamicDispatchVisitor(evaluator));
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
