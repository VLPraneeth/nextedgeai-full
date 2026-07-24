package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.NotStartsWith;
import com.syncari.core.pipeline.jtwig.TokenEnvironmentConfig;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.search.querybuilder.Node;

import static org.junit.Assert.*;


import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
public class RedisLookupCriteriaTest {

    private TokenHelper tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());

    @Test
    public void testSimpleLookupQuery() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("name").string("name1").string("name2").getEntityDefinition();
        Expression expression = Expression.eq(Expression.var("name"), Expression.lit("value"));

        RedisLookupCriteriaVisitor visitor = new RedisLookupCriteriaVisitor(new GraphContext(), expression, tokenHelper, entityDefinition, List.of());

        assertEquals("(@name:{value} @isDeleted:[0 0])", visitor.createCriteria().toString());

        expression = Expression.and(Expression.eq(Expression.var("name1"), Expression.lit("value1")), Expression.eq(Expression.var("name2"), Expression.lit("value2")));

        visitor = new RedisLookupCriteriaVisitor(new GraphContext(), expression, tokenHelper, entityDefinition, List.of());

        assertEquals("((@name1:{value1} @name2:{value2}) @isDeleted:[0 0])", visitor.createCriteria().toString());
    }

    @Test
    public void stringEquality() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("123"));
        final RedisLookupCriteriaVisitor redisLookupCriteriaVisitor = new RedisLookupCriteriaVisitor(new GraphContext(), eq, tokenHelper, entityDefinition, List.of());
        final Node criteria = redisLookupCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:{123} @isDeleted:[0 0])", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringEqualityEscape() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("@123"));
        final RedisLookupCriteriaVisitor redisLookupCriteriaVisitor = new RedisLookupCriteriaVisitor(new GraphContext(), eq, tokenHelper, entityDefinition, List.of());
        final Node criteria = redisLookupCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:{\\@123} @isDeleted:[0 0])", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringEqualityNull() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(null));
        final RedisLookupCriteriaVisitor redisLookupCriteriaVisitor = new RedisLookupCriteriaVisitor(new GraphContext(), eq, tokenHelper, entityDefinition, List.of());
        final Node criteria = redisLookupCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@__nf:{field1} @isDeleted:[0 0])", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringEqualityNotNull() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.ne(Expression.var(field1.getId()), Expression.lit(null));
        final RedisLookupCriteriaVisitor redisLookupCriteriaVisitor = new RedisLookupCriteriaVisitor(new GraphContext(), eq, tokenHelper, entityDefinition, List.of());
        final Node criteria = redisLookupCriteriaVisitor.createCriteria();
        assertEquals(String.format("(-@__nf:{field1} @isDeleted:[0 0])", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void stringInMultipleWithToken() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisLookupCriteriaVisitor redisLookupCriteriaVisitor = new RedisLookupCriteriaVisitor(new GraphContext(), Expression.in(Expression.var(field1.getId()), Expression.lit(List.of("123", "{{record.values.MailingCity}}"))), tokenHelper, entityDefinition, List.of());
        final Node criteria = redisLookupCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:{123} @isDeleted:[0 0])", record.getSyncariEntityId()), criteria.toString());
    }

    //@Test
    public void testLookup() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("name").getEntityDefinition();
        final AttributeDefinition name = entityDefinition.getFieldByName("name");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());

        var expr = Expression.contains(Expression.var(name.getId()), Expression.lit("Name11"));

        final RedisLookupCriteriaVisitor redisLookupCriteriaVisitor = new RedisLookupCriteriaVisitor(new GraphContext(), expr, tokenHelper, entityDefinition, List.of());
        final Node criteria = redisLookupCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:{123} @isDeleted:[0 0])", record.getSyncariEntityId()), criteria.toString(Node.Parenthesize.ALWAYS));
    }
}
