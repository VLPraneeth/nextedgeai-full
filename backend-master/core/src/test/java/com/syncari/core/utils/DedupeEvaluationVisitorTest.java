package com.syncari.core.utils;

import com.syncari.connector.EntityData;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.utils.DateUtil;
import org.bson.types.ObjectId;
import org.junit.Test;

import javax.xml.crypto.Data;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DedupeEvaluationVisitorTest {

    @Test
    public void stringEqualityTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "123");
        final EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "123");

        // literal string eq
        Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("123"));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        // literal string eq negative
        eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("456"));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(false, visitor.getValue());

        eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

    }

    @Test
    public void booleanEqualityTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").bool("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", true);
        final EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", true);

        // literal string eq
        Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(true));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    @Test
    public void dateTimeComparisonTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").datetime("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        ZonedDateTime now = ZonedDateTime.now();

        EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", now);
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", now);

        // literal string eq
        Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", now.minusDays(1));
        Expression gt = Expression.gt(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        gt.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", now.plusDays(1));
        Expression lt = Expression.lt(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        lt.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    @Test
    public void dateComparisonTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").date("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        Date today = new Date();

        EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", today);
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", today);

        // literal string eq
        Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", DateUtil.addDaysToDate(today, -1));
        Expression gt = Expression.gt(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        gt.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", DateUtil.addDaysToDate(today, 1));
        Expression lt = Expression.lt(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        lt.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        gt = Expression.gt(Expression.var(field1.getId()), Expression.lit("2022-01-01"));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        gt.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

    }

    @Test
    public void stringEqualityNullTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "123");
        final EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "123");

        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(null));
        //Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("123"));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(false, visitor.getValue());
    }

    @Test
    public void stringIgnoreCaseEqualityTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "IgNoReCaSe");
        final EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "ignoreCase");

        final Expression eq = Expression.ieq(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        //Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("123"));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    @Test
    public void equalityWithDifferentApiNames() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").string("field2").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final AttributeDefinition field2 = entityDefinition.getFieldByName("field2");

        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "value1");
        final EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field2", "value1");

        final Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit(field2.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    @Test
    public void stringContainsTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "This is a test");
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "is a tes");

        final Expression eq = Expression.contains(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "isatest");
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(false, visitor.getValue());
    }

    @Test
    public void stringNotContainsTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "This is a test");
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "is a tes");

        final Expression eq = Expression.notContains(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(false, visitor.getValue());

        incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "isatest");
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        eq.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    @Test
    public void andExpressionTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").string("field2").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final AttributeDefinition field2 = entityDefinition.getFieldByName("field2");

        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value1").addValue("field2", "Value2");
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value1").addValue("field2", "Value2");

        final Expression and = Expression.and(Expression.eq(Expression.var(field1.getId()), Expression.lit(field1.getId())),
                Expression.eq(Expression.var(field2.getId()), Expression.lit(field2.getId())));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        and.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    @Test
    public void orExpressionTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").string("field2").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");
        final AttributeDefinition field2 = entityDefinition.getFieldByName("field2");

        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value1").addValue("field2", "Value2");
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value1").addValue("field2", "Value3");

        final Expression or = Expression.or(Expression.eq(Expression.var(field1.getId()), Expression.lit(field1.getId())),
                Expression.eq(Expression.var(field2.getId()), Expression.lit(field2.getId())));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        or.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    // in

    @Test
    public void inExpressionTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        // test null
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value1");
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value123");

        Expression in = Expression.in(Expression.var(field1.getId()), Expression.lit(null));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        in.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(false, visitor.getValue());

        // test in list

        in = Expression.in(Expression.var(field1.getId()), Expression.lit(List.of("Value2", "Value1", "Value3")));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        in.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        // test contains
        in = Expression.in(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        in.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    @Test
    public void notInExpressionTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        // test null
        final EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value4");
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value123");

        Expression notIn = Expression.notIn(Expression.var(field1.getId()), Expression.lit(null));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        notIn.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        // test in list

        notIn = Expression.notIn(Expression.var(field1.getId()), Expression.lit(List.of("Value2", "Value1", "Value3")));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        notIn.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        // test contains
        notIn = Expression.notIn(Expression.var(field1.getId()), Expression.lit(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        notIn.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    // is empty
    @Test
    public void isEmptyTest() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value123");

        Expression empty = Expression.empty(Expression.var(field1.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        empty.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());

        // test in list
        record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value1");
        empty = Expression.empty(Expression.var(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        empty.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(false, visitor.getValue());
    }

    @Test
    public void isNotEmpty() {

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        EntityData record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString());
        EntityData incomingRecord = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value123");

        Expression empty = Expression.notEmpty(Expression.var(field1.getId()));
        DedupeEvaluationVisitor visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        empty.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(false, visitor.getValue());

        // test in list
        record = new EntityData("account").setSyncariEntityId(ObjectId.get().toString()).addValue("field1", "Value1");
        empty = Expression.notEmpty(Expression.var(field1.getId()));
        visitor = new DedupeEvaluationVisitor(record, entityDefinition, incomingRecord);
        empty.accept(new DynamicDispatchVisitor(visitor));
        assertEquals(true, visitor.getValue());
    }

    // startswith

    // not
}
