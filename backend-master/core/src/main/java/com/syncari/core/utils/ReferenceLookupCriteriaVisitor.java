package com.syncari.core.utils;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.*;
import static java.lang.String.format;

public class ReferenceLookupCriteriaVisitor extends SimpleExpressionVisitor implements MongoCriteria {

    private Expression expression;

    public ReferenceLookupCriteriaVisitor(Expression expression) {
        this.expression = expression;
    }

    @Override
    public Bson createCriteria() {
        expression.accept(this);
        if (expressionNodes.empty()) {
            throw new SyncariValidationException("No Dedupe Expressions found");
        }
        if (expressionNodes.size() > 1) {
            throw new SyncariValidationException("Dedupe Expression could not be fully parsed");
        }
        Bson exp = (Bson) expressionNodes.pop();
        //exclude incoming record
        return exp;
    }


    private Stack<Object> expressionNodes = new Stack<>();

    @Override
    public boolean hasCaseInsensitiveIndexField() {
        return false;
    }

    @Override
    public void visit(Equal equal) {
        binaryOp(equal, (left, right) -> {
            return eq(left, right);
        });
    }

    @Override
    public void visit(EqualIgnoreCase equal) {
        binaryOp(equal, (left, right) -> {
            return regex(left, format("^%s$", Pattern.quote(right)), "i");
        });
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        expressionNodes.push(literalExpression.getValue());
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        expressionNodes.push(variableExpression.getVariableName());
    }

    @Override
    public void visit(Contains contains) {
        binaryOp(contains, (left, right) -> {
            return regex(left, Pattern.compile(Pattern.quote(right), Pattern.CASE_INSENSITIVE));
        });
    }

    @Override
    public void visit(In exp) {
        binaryOp(exp, (left, right) -> {
            return expr(Document.parse(String.format("{$gt:[{$indexOfCP: ['%s', '$%s']},-1]}", right, left)));
        });

    }

    private void binaryOp(BinaryExpression exp, BiFunction<String, String, Bson> biFunction) {
        String right = expressionNodes.pop().toString();
        String left = expressionNodes.pop().toString();
        expressionNodes.push(biFunction.apply(left, right));
    }
}
