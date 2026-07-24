package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.jtwig.TokenEnvironmentConfig;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.Test;
import redis.clients.jedis.search.querybuilder.Node;

import java.util.List;

import static org.junit.Assert.assertEquals;

@Slf4j
public class ReferenceLookupCriteriaTest {

    @Test
    public void testExactMatch() {

        Expression expression = Expression.eq(Expression.var("name"), Expression.lit("value"));
        ReferenceLookupCriteriaVisitor referenceLookupCriteriaVisitor = new ReferenceLookupCriteriaVisitor(expression);
        assertEquals("Filter{fieldName='name', value=value}", referenceLookupCriteriaVisitor.createCriteria().toString());
        log.info(referenceLookupCriteriaVisitor.createCriteria().toString());
    }

    @Test
    public void testExactMatchIgnoreCase() {

        Expression expression = Expression.ieq(Expression.var("name"), Expression.lit("value"));
        ReferenceLookupCriteriaVisitor referenceLookupCriteriaVisitor = new ReferenceLookupCriteriaVisitor(expression);
        assertEquals("Operator Filter{fieldName='name', operator='$eq', value=BsonRegularExpression{pattern='^\\Qvalue\\E$', options='i'}}", referenceLookupCriteriaVisitor.createCriteria().toString());
    }

    @Test
    public void testContains() {

        Expression expression = Expression.contains(Expression.var("name"), Expression.lit("value"));
        ReferenceLookupCriteriaVisitor referenceLookupCriteriaVisitor = new ReferenceLookupCriteriaVisitor(expression);
        assertEquals("Filter{fieldName='name', value=\\Qvalue\\E}", referenceLookupCriteriaVisitor.createCriteria().toString());
    }

    @Test
    public void testIn() {
        Expression expression = Expression.in(Expression.var("name"), Expression.lit("value"));
        ReferenceLookupCriteriaVisitor referenceLookupCriteriaVisitor = new ReferenceLookupCriteriaVisitor(expression);
        assertEquals("Filter{fieldName='$expr', value=Document{{$gt=[Document{{$indexOfCP=[value, $name]}}, -1]}}}", referenceLookupCriteriaVisitor.createCriteria().toString());
    }

}
