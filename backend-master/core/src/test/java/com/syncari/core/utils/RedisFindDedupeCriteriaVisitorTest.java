package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.NotStartsWith;
import org.bson.types.ObjectId;
import org.junit.Test;
import redis.clients.jedis.search.querybuilder.Node;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RedisFindDedupeCriteriaVisitorTest {
    @Test
    public void stringEquality() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("123"));
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, eq, entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:{123} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringEqualityEscape() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("@123"));
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, eq, entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:{\\@123} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringEqualityNull() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(null));
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, eq, entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@_id:{null} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringEqualityNotNull() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final Expression eq = Expression.ne(Expression.var(field1.getId()), Expression.lit(null));
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, eq, entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(-@__nf:{field1} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringNotEquals() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.ne(Expression.var(field1.getId()), Expression.lit("123")), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(-@field1:{123} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringStartsWith() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.startsWith(Expression.var(field1.getId()), Expression.lit("12")), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1_i:{12*} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());

    }

    @Test
    public void stringNotStartsWith() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, new NotStartsWith(Expression.var(field1.getId()), Expression.lit("12")), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(-@field1_i:{12*} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void stringInMultiple() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.in(Expression.var(field1.getId()), Expression.lit(List.of("123", "456"))), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:{123 | 456} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void stringNotInMultiple() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.notIn(Expression.var(field1.getId()), Expression.lit(List.of("123", "456"))), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(-@field1:{123 | 456} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void stringContains() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.contains(Expression.var(field1.getId()), Expression.lit("23")), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1_i:{*23*} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void stringNotContains() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.notContains(Expression.var(field1.getId()), Expression.lit("23")), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(-@field1_i:{*23*} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void dateTimeEquality() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.eq(Expression.var(field1.getId()), Expression.lit(now)), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        System.out.println(now.toInstant().toEpochMilli());
        assertEquals(String.format("(@field1:[%s %s] @isDeleted:[0 0] -@_id:{%s})", now.toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void dateTimeNull() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.eq(Expression.var(field1.getId()), Expression.lit(null)), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@_id:{null} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void dateTimeInequality() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.ne(Expression.var(field1.getId()), Expression.lit(now)), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        System.out.println(now.toInstant().toEpochMilli());
        assertEquals(String.format("(-@field1:[%s %s] @isDeleted:[0 0] -@_id:{%s})", now.toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void dateTimeComparison() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.gt(Expression.var(field1.getId()), Expression.lit(field1.getId())), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        System.out.println(now.toInstant().toEpochMilli());
        assertEquals(String.format("(@__nf:{field1} @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void dateTimeGTE() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.gte(Expression.var(field1.getId()), Expression.lit(now)), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:[%s inf] @isDeleted:[0 0] -@_id:{%s})", now.toInstant().toEpochMilli(), record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void dateTimeGT() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.gt(Expression.var(field1.getId()), Expression.lit(now)), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(((@field1:[(%s inf])) ((@isDeleted:[0 0])) -(@_id:{%s}))", now.toInstant().toEpochMilli(), record.getSyncariEntityId()), criteria.toString(Node.Parenthesize.ALWAYS));
    }

    @Test
    public void dateTimeLTE() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.lte(Expression.var(field1.getId()), Expression.lit(now)), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(((@field1:[-inf %s])) ((@isDeleted:[0 0])) -(@_id:{%s}))", now.toInstant().toEpochMilli(), record.getSyncariEntityId()), criteria.toString(Node.Parenthesize.ALWAYS));
    }

    @Test
    public void dateTimeLT() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.lt(Expression.var(field1.getId()), Expression.lit(now)), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(((@field1:[-inf (%s])) ((@isDeleted:[0 0])) -(@_id:{%s}))", now.toInstant().toEpochMilli(), record.getSyncariEntityId()), criteria.toString(Node.Parenthesize.ALWAYS));
    }

    @Test
    public void longEquality() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").integer("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.eq(Expression.var(field1.getId()), Expression.lit("456")), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        System.out.println(now.toInstant().toEpochMilli());
        assertEquals(String.format("(@field1:[%s %s] @isDeleted:[0 0] -@_id:{%s})", 456, 456, record.getSyncariEntityId()), criteria.toString());
    }

    @Test
    public void boolConditions() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").bool("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, Expression.eq(Expression.var(field1.getId()), Expression.lit("true")), entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("(@field1:[1 1] @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria.toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor2 = new RedisFindDedupeCriteriaVisitor(record, Expression.eq(Expression.var(field1.getId()), Expression.lit(true)), entityDefinition);
        final Node criteria2 = redisFindDedupeCriteriaVisitor2.createCriteria();
        assertEquals(String.format("(@field1:[1 1] @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria2.toString());
        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor3 = new RedisFindDedupeCriteriaVisitor(record, Expression.eq(Expression.var(field1.getId()), Expression.lit(false)), entityDefinition);
        final Node criteria3 = redisFindDedupeCriteriaVisitor3.createCriteria();
        assertEquals(String.format("(@field1:[0 0] @isDeleted:[0 0] -@_id:{%s})", record.getSyncariEntityId()), criteria3.toString());
    }

    //@Test
    public void complexCondition() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account")
                .integer("field1")
                .string("field2")
                .bool("field3")
                .datetime("field4")
                .date("field5")
                .getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final AttributeDefinition field2 = entityDefinition.getFieldByName("field2");
        final AttributeDefinition field3 = entityDefinition.getFieldByName("field3");
        final AttributeDefinition field4 = entityDefinition.getFieldByName("field4");
        final AttributeDefinition field5 = entityDefinition.getFieldByName("field5");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        final ZonedDateTime now = ZonedDateTime.now();
        final Expression expression1 = Expression.gte(Expression.var(field1.getId()), Expression.lit(300));
        final Expression expression2 = Expression.contains(Expression.var(field2.getId()), Expression.lit("stringcheck"));
        final Expression expression3 = Expression.eq(Expression.var(field3.getId()), Expression.lit(true));
        final Expression expression4 = Expression.lte(Expression.var(field4.getId()), Expression.lit(now));
        final Date today = new Date();
        final Expression expression5 = Expression.gt(Expression.var(field5.getId()), Expression.lit(today));

        //(4 AND (2 AND (NOT (1 AND (3 OR 5)))))
        final Expression expression = Expression.or(expression4, Expression.and(expression2, Expression.not(Expression.and(expression1, Expression.or(expression3, expression5)))));

        final RedisFindDedupeCriteriaVisitor redisFindDedupeCriteriaVisitor = new RedisFindDedupeCriteriaVisitor(record, expression, entityDefinition);
        final Node criteria = redisFindDedupeCriteriaVisitor.createCriteria();
        assertEquals(String.format("((((@field4:[-inf %s]))|(((@field2_i:{*stringcheck*})) -(@field1:[300 inf] @field3:[1 1]|@field5:[(%s inf]))) ((@isDeleted:[0 0])) -(@_id:{%s}))", now.toInstant().toEpochMilli(), today.getTime(), record.getSyncariEntityId()), criteria.toString(Node.Parenthesize.ALWAYS));
    }
}