package com.syncari.core.utils;

import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.jtwig.TokenEnvironmentConfig;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Slf4j
public class LookupCriteriaVisitorTest {

    private TokenHelper tokenHelper = new TokenHelper(new TokenEnvironmentConfig().tokenEnvironment());

    @Test
    public void testDiffTypeEquality() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").string("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        GraphContext graphContext = new GraphContext();
        graphContext.put("fieldVal", 123.0);
        Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("{{fieldVal}}"));
        LookupCriteriaVisitor visitor = new LookupCriteriaVisitor(graphContext, eq, tokenHelper, Map.of(field1.getId(), field1), List.of(), v -> true);

        var criteria = visitor.createCriteria();

        assertEquals("And Filter{filters=[Document{{isDeleted=false}}, Filter{fieldName='field1', value=123}]}", criteria.toString());

        graphContext.put("fieldVal", 123);
        eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("{{fieldVal}}"));
        visitor = new LookupCriteriaVisitor(graphContext, eq, tokenHelper, Map.of(field1.getId(), field1), List.of(), v -> true);
        criteria = visitor.createCriteria();

        assertEquals("And Filter{filters=[Document{{isDeleted=false}}, Filter{fieldName='field1', value=123}]}", criteria.toString());
        entityDefinition.addField(SchemaHelper.createAttribute("field2", DoubleType.VALUE, entityDefinition.getId()));
        final AttributeDefinition field2 = entityDefinition.getFieldByName("field2");

        graphContext = new GraphContext();
        graphContext.put("fieldVal", 123);
        eq = Expression.eq(Expression.var(field2.getId()), Expression.lit("{{fieldVal}}"));
        visitor = new LookupCriteriaVisitor(graphContext, eq, tokenHelper, Map.of(field2.getId(), field2), List.of(), v -> true);
        log.info(visitor.createCriteria().toString());
        assertEquals("And Filter{filters=[Document{{isDeleted=false}}, Filter{fieldName='field2', value=123.0}]}", visitor.createCriteria().toString());
    }

    @Test
    public void testSameTypeEquality() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").dbl("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        GraphContext graphContext = new GraphContext();
        graphContext.put("fieldVal", 123);
        Expression eq = Expression.eq(Expression.var(field1.getId()), Expression.lit("{{fieldVal}}"));
        LookupCriteriaVisitor visitor = new LookupCriteriaVisitor(graphContext, eq, tokenHelper, Map.of(field1.getId(), field1), List.of(), v -> true);

        var criteria = visitor.createCriteria();

        assertEquals("And Filter{filters=[Document{{isDeleted=false}}, Filter{fieldName='field1', value=123.0}]}", criteria.toString());

        entityDefinition.addField(SchemaHelper.createAttribute("field2", StringType.VALUE, entityDefinition.getId()));
        final AttributeDefinition field2 = entityDefinition.getFieldByName("field2");

        graphContext = new GraphContext();
        graphContext.put("fieldVal", "123");
        eq = Expression.eq(Expression.var(field2.getId()), Expression.lit("{{fieldVal}}"));
        visitor = new LookupCriteriaVisitor(graphContext, eq, tokenHelper, Map.of(field2.getId(), field2), List.of(), v -> true);
        log.info(visitor.createCriteria().toString());
        assertEquals("And Filter{filters=[Document{{isDeleted=false}}, Filter{fieldName='field2', value=123}]}", visitor.createCriteria().toString());
    }

    @Test
    public void testSameTypeComparison() {
        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("account").dbl("field1").getEntityDefinition();
        final AttributeDefinition field1 = entityDefinition.getFieldByName("field1");

        GraphContext graphContext = new GraphContext();
        graphContext.put("fieldVal", 123.0);
        Expression gt = Expression.gt(Expression.var(field1.getId()), Expression.lit("{{fieldVal}}"));
        LookupCriteriaVisitor visitor = new LookupCriteriaVisitor(graphContext, gt, tokenHelper, Map.of(field1.getId(), field1), List.of(), v -> true);

        var criteria = visitor.createCriteria();

        assertEquals("And Filter{filters=[Document{{isDeleted=false}}, Operator Filter{fieldName='field1', operator='$gt', value=123.0}]}", criteria.toString());

        entityDefinition.addField(SchemaHelper.createAttribute("field2", IntegerType.VALUE, entityDefinition.getId()));
        final AttributeDefinition field2 = entityDefinition.getFieldByName("field2");
        graphContext = new GraphContext();
        graphContext.put("fieldVal", 123);
        gt = Expression.gt(Expression.var(field2.getId()), Expression.lit("{{fieldVal}}"));
        visitor = new LookupCriteriaVisitor(graphContext, gt, tokenHelper, Map.of(field2.getId(), field2), List.of(), v -> true);

        criteria = visitor.createCriteria();
        assertEquals("And Filter{filters=[Document{{isDeleted=false}}, Operator Filter{fieldName='field2', operator='$gt', value=123}]}", criteria.toString());

    }

}