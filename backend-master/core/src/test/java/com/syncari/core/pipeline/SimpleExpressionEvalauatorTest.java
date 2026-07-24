package com.syncari.core.pipeline;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.token.TokenHelper;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.Environment;
import org.jtwig.environment.EnvironmentFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.syncari.core.pipeline.expression.Expression.*;
import static org.junit.Assert.*;

public class SimpleExpressionEvalauatorTest extends AbstractSyncariTest {

    @Autowired
    TokenHelper tokenHelper;

    @Test
    public void testSimpleExpressionGeneratesCorrectTemplate() {
        Expression expression = ifElse(eq(var("zz"), lit("qq")), renderedLit("true"), renderedLit("false"));

        Map<String, Object> context = new HashMap<>();
        context.put("zz", "qa");
        FilterEvaluationVisitor visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("false", visitor.getValue());

        expression = ifElse(eq(var("zz"), lit("qq")), renderedLit("true"), renderedLit("false"));
        new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("false", visitor.getValue());

        context.clear();

        context.put("zz", "qq");
        context.put("aa", "bb");

        Expression andExpr = ifElse(and(eq(var("zz"), lit("qq")), eq(var("aa"), lit("bb"))), renderedLit("true"), renderedLit("false"));
        visitor = new FilterEvaluationVisitor(context, tokenHelper);
        andExpr.accept(visitor);
        assertEquals("true", visitor.getValue());
    }

    @Test
    public void testContainsExpression() {
        Expression expression = ifElse(contains(var("env"), lit("qa")), renderedLit("true"), renderedLit("false"));

        Map<String, Object> context = new HashMap<>();

        List<String> list = new ArrayList<>();
        list.add("qa");
        list.add("int");
        list.add("perf");
        context.put("env", list);
        FilterEvaluationVisitor visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("true", visitor.getValue());

        expression = ifElse(contains(var("env"), lit("qq")), renderedLit("true"), renderedLit("false"));
        expression.accept(visitor);
        assertEquals("false", visitor.getValue());

        expression = ifElse(startsWith(var("env"), lit("qa")), renderedLit("true"), renderedLit("false"));
        expression.accept(visitor);
        assertEquals("true", visitor.getValue());

        expression = ifElse(startsWith(var("env"), lit("perf")), renderedLit("true"), renderedLit("false"));
        expression.accept(visitor);
        assertEquals("true", visitor.getValue());

        expression = ifElse(contains(var("env"), lit("123")), renderedLit("true"), renderedLit("false"));
        List<Long> list1 = new ArrayList<Long>();
        list1.add(123L);
        list1.add(456L);
        list1.add(783L);
        context.put("env", list1);
        visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("true", visitor.getValue());
    }

    @Test
    public void testInExpression() {
        Expression expression = ifElse(in(var("elem"), var("list")), renderedLit("true"), renderedLit("false"));

        Map<String, Object> context = new HashMap<>();

        List<String> list = new ArrayList<>();
        list.add("qa");
        list.add("int");
        list.add("perf");
        context.put("list", list);
        context.put("elem", "qa");
        FilterEvaluationVisitor visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("true", visitor.getValue());

        context.put("elem", "qq");
        visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("false", visitor.getValue());

       List<Long> list1 = new ArrayList<Long>();
        list1.add(123L);
        list1.add(456L);
        list1.add(783L);
        context.put("list", list1);
        context.put("elem", "456");
        visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("true", visitor.getValue());

        expression = ifElse(notIn(var("elem"), var("list")), renderedLit("true"), renderedLit("false"));

        context = new HashMap<>();

        list = new ArrayList<>();
        list.add("qa");
        list.add("int");
        list.add("perf");
        context.put("list", list);
        context.put("elem", "qa");
        visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("false", visitor.getValue());

        context.put("elem", "qq");
        visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("true", visitor.getValue());

        list1 = new ArrayList<Long>();
        list1.add(123L);
        list1.add(456L);
        list1.add(783L);
        context.put("list", list1);
        context.put("elem", "456");
        visitor = new FilterEvaluationVisitor(context, tokenHelper);
        expression.accept(visitor);
        assertEquals("false", visitor.getValue());
    }

}
