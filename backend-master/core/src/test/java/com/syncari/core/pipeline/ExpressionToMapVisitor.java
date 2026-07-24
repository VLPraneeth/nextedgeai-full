package com.syncari.core.pipeline;

import com.syncari.core.pipeline.expression.*;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.pipeline.expression.dedupe.HighestValueExpression;

import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ExpressionToMapVisitor extends SimpleExpressionVisitor {
    Stack<Map<String, Object>> current = new Stack<>();

    public Map<String, Object> getMap() {
        return current.pop();
    }

    public void visit(Contains exp) {
        visitBinary(exp);
    }

    public void visit(NotContains exp) {
        visitBinary(exp);
    }

    public void visit(Or exp) {
        var right = current.pop();
        var left = current.pop();
        current.push(Map.of("operator", "OR", "predicates", List.of(left, right)));
    }

    @Override
    public void visit(And exp) {
        var right = current.pop();
        var left = current.pop();
        current.push(Map.of("operator", "AND", "predicates", List.of(left, right)));
    }

    @Override

    public void visit(Empty exp) {
        var e = current.pop();
        current.push(Map.of("operator", "empty", "left", e));
    }

    public void visit(NotEmpty exp) {
        var e = current.pop();
        current.push(Map.of("operator", "not_empty", "left", e));
    }

    public void visit(HighestValueExpression exp) {
        var e = current.pop();
        current.push(Map.of("operator", "with_highest_value", "left", e));
    }


    @Override
    public void visit(VariableExpression variableExpression) {
        current.push(Map.of("type", "variable", "value", variableExpression.getVariableName()));
    }

    @Override
    public void visit(LiteralExpression lit) {
        current.push(Map.of("type", "literal", "value", lit.getValue()));
    }


    public void visitBinary(BinaryExpression bin) {
        var right = current.pop();
        var left = current.pop();
        current.push(Map.of("operator", bin.getName(), "left", left, "right", right));

    }

    @Override
    public void visit(Equal equal) {
        visitBinary(equal);
    }

    @Override
    public void visit(NotEqual notEqual) {
        visitBinary(notEqual);
    }

    @Override
    public void visit(StartsWith e) {
        visitBinary(e);
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        visitBinary(lteExpression);
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        visitBinary(gteExpression);
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        visitBinary(greaterThan);
    }

    @Override
    public void visit(LessThan lessThan) {
        visitBinary(lessThan);
    }

    @Override
    public void visit(NotIn expression) {
        visitBinary(expression);
    }

    @Override
    public void visit(In expression) {
        visitBinary(expression);
    }

    public void visit(FirstMatchingValueExpression expression) {
        visitBinary(expression);
    }
}
