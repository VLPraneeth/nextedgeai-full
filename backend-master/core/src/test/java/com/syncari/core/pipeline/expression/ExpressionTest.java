package com.syncari.core.pipeline.expression;

import com.syncari.core.exceptions.SyncariValidationException;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ExpressionTest {

    @Test
    public void expressionEquality(){
        Expression e1 = Expression.eq(Expression.var("lhs"), Expression.lit("something"));
        Expression e2 = Expression.eq(Expression.var("lhs"), Expression.lit("something"));
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    public void parsedExpressionEquality(){
        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "name"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "{{previous.values.AccountName}}")
        );
        Expression e1 = new PredicateParser().fromMap(eq);
        Expression e2 = new PredicateParser().fromMap(eq);
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test(expected = SyncariValidationException.class)
    public void throwsUnknownOperatorErrorOnEmptyPredicate() {
        var emptyPredicate = Map.of("predicateId", (Object) "predicateId");
        new PredicateParser().fromMap(emptyPredicate);
    }

}