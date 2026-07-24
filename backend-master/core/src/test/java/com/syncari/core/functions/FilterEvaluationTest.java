package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.model.MappingNode;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.FilterEvaluationVisitor;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.Assert.*;


public class FilterEvaluationTest extends AbstractSyncariTest {


    @Autowired
    private TokenHelper tokenHelper;

    @Test
    public void dfiFilterEvaluation() {
        GraphContext graphContext = new GraphContext();
        FilterEvaluationVisitor filterEvaluationVisitor;

        //email operator
        graphContext.put("field_email_field1", "sample@email.com");
        graphContext.put("field_email_field2", "sample.mail@email.com");
        graphContext.put("field_email_field3", "sample.mail.com");

        Expression validEmailExp1 = Expression.email(Expression.var("field_email_field1"));
        Expression validEmailExp2 = Expression.email(Expression.var("field_email_field2"));
        Expression invalidEmailExp1 = Expression.email(Expression.var("field_email_field3"));

        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        validEmailExp1.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        var value = filterEvaluationVisitor.getValue();
        assertNotNull(value);
        assertEquals(value, true);

        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        validEmailExp2.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        value = filterEvaluationVisitor.getValue();
        assertNotNull(value);
        assertEquals(value, true);

        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        invalidEmailExp1.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        value = filterEvaluationVisitor.getValue();
        assertNotNull(value);
        assertEquals(value, false);

        //regex operator
        graphContext.put("field_int_regex_0", "0");
        graphContext.put("field_int_regex_1", "10");
        graphContext.put("field_int_regex_3", "101");

        Expression numExpr =  Expression.regex(Expression.var("field_int_regex_0"),Expression.lit("^[1-9][0-9]?$|^100$"));
        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        numExpr.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        value = filterEvaluationVisitor.getValue();
        assertNotNull(value);
        assertEquals(value, false);

        Expression numExpr1 =  Expression.regex(Expression.var("field_int_regex_1"),Expression.lit("^[1-9][0-9]?$|^100$"));
        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        numExpr1.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        value = filterEvaluationVisitor.getValue();
        assertNotNull(value);
        assertEquals(value, true);

        Expression numExpr2 =  Expression.regex(Expression.var("field_int_regex_3"),Expression.lit("^[1-9][0-9]?$|^100$"));
        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        numExpr2.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        value = filterEvaluationVisitor.getValue();
        assertNotNull(value);
        assertEquals(value, false);

        //phone operator
        List<String> validPhoneNumbers = List.of(
                "(123) 456-7890", "123-456-7890", "123.456.7890", "1234567890", //US
                "+91 98765 43210", "09876543210", "9876543210", //Ind
                "+44 20 7183 8750", "020 7183 8750" //uk
        );

        List<String> invalidPhoneNumbers = List.of("1234567", "(123) 456", "abc1234567");

        for (String phone: validPhoneNumbers) {
            graphContext.put("field_phone", phone);
            Expression phoneExpr = Expression.phone(Expression.var("field_phone"));
            filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
            phoneExpr.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
            value = filterEvaluationVisitor.getValue();
            assertNotNull(value);
            assertEquals(value, true);
        }

        for (String phone: invalidPhoneNumbers) {
            graphContext.put("field_phone", phone);
            Expression phoneExpr = Expression.phone(Expression.var("field_phone"));
            filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
            phoneExpr.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
            value = filterEvaluationVisitor.getValue();
            assertNotNull(value);
            assertEquals(value, false);
        }

    }

    @Test
    public void filterEvaluation() {

        GraphContext graphContext = new GraphContext();
        graphContext.put("field_sfdc_account_owner_id","0056Q000007Z945QAC");
        graphContext.put("field_act_src","Outbound-G2");
        graphContext.put("field_sfdc_created_date", ZonedDateTime.now().minusDays(3));

        var functionResult = "output_" + ObjectId.get().toHexString();
        var functionResultVariableName = functionResult + ".x";
        graphContext.put(functionResult, Pair.of(new FunctionResult(null, null), new MappingNode()));

        FilterEvaluationVisitor filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        Expression sfAccountOwnerId = Expression.eq(Expression.var("field_sfdc_account_owner_id"),Expression.lit("0056Q000007Z945QAC"));
        Expression sdrAssignmentStatus = Expression.empty(Expression.var("field_sdr"));
        Expression billingCountry = Expression.empty(Expression.var("field_country"));
        Expression actSource =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Clearbit Created Account"));
        Expression actSource2 =  Expression.eq(Expression.var("field_act_src"),Expression.lit("Outbound-G2"));
        Expression createdDate =  Expression.gte(Expression.var("field_sfdc_created_date"),Expression.lit("before 48 hours"));
        Expression filterExp = Expression.and(Expression.and(Expression.and(Expression.and(sfAccountOwnerId, sdrAssignmentStatus),Expression.or(actSource, actSource2)),billingCountry),createdDate);

        Expression trueValue = Expression.renderedVar(functionResultVariableName);
        Expression falseValue = Expression.filterFailed(functionResultVariableName);

        var expr = Expression.ifElse(filterExp, trueValue, falseValue);
        expr.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        var v = filterEvaluationVisitor.getValue();

        assertTrue(v != null);

        sfAccountOwnerId = Expression.ne(Expression.var("field_sfdc_account_owner_id"),Expression.lit("0056Q000007Z945QAC"));
        filterExp = Expression.and(sfAccountOwnerId, sdrAssignmentStatus);

        expr = Expression.ifElse(filterExp, trueValue, falseValue);
        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        expr.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        v = filterEvaluationVisitor.getValue();
        assertTrue(v != null);
        assertTrue(v instanceof FilterFailedResult);
        
        graphContext.setTempVariable("filter_test", "This is a test value");
        var tokenExp = Expression.eq(Expression.var("{{syncari.temp.filter_test}}"),Expression.lit("This is a test value"));
        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        tokenExp.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        v = filterEvaluationVisitor.getValue();
        assertTrue(v instanceof Boolean);
        assertTrue( (Boolean)v);
        
        graphContext.setTempVariable("filter_test1", "This is a test value");
        tokenExp = Expression.eq(Expression.var("{{syncari.temp.filter_test1}}"),Expression.lit("This is a test value1"));
        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        tokenExp.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        v = filterEvaluationVisitor.getValue();
        assertTrue(v instanceof Boolean);
        assertFalse( (Boolean)v);
        assertFalse(filterEvaluationVisitor.foundEmptyValuedPredicates());
        
        graphContext.setTempVariable("filter_test2", "This is a test value");
        var blankExp = Expression.eq(Expression.var("{{syncari.temp.filter_test2}}"),Expression.lit(""));
        filterEvaluationVisitor = new FilterEvaluationVisitor(graphContext, tokenHelper);
        blankExp.accept(new DynamicDispatchVisitor(filterEvaluationVisitor));
        v = filterEvaluationVisitor.getValue();
        assertTrue(v instanceof Boolean);
        assertFalse( (Boolean)v);
        assertTrue(filterEvaluationVisitor.foundEmptyValuedPredicates());
    }

}
