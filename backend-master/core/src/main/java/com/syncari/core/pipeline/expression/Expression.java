package com.syncari.core.pipeline.expression;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.*;

//Expression is needed to render UI, among other things
public interface Expression {
    static Expression ifElse(Expression condition, Expression trueValue, Expression falseValue) {
        return new If(condition, trueValue, falseValue);
    }

    static Expression DFIExpression(Expression expression) {
        return new DFIExpression(expression);
    }

    static Expression function(FunctionCall functionCall) {
        return new FunctionExpression(functionCall);
    }

    static Expression and(Expression left, Expression right) {
        return new And(left, right);
    }

    static Expression or(Expression left, Expression right) {
        return new Or(left, right);
    }

    static Expression not(Expression arg) {
        return new Not(arg);
    }

    static Expression latest(Expression arg) {
        return new Latest(arg);
    }
    static Expression max(Expression arg) {
        return new Max(arg);
    }
    static Expression min(Expression arg) {
        return new Min(arg);
    }
    static Expression mostComplete(Expression arg) {
        return new MostComplete(arg);
    }

    static LiteralExpression renderedLit(Object literal) {
        return new LiteralExpression(literal,true);
    }
    static Expression lit(Object literal) {
        return new LiteralExpression(literal,false);
    }

    static Expression renderedVar(String name) {
        return new VariableExpression(name,true);
    }

    static Expression filterFailed(String name) {
        return new FilterFailedExpression(name);
    }

    static VariableExpression var(String name) {
        return new VariableExpression(name,false, StringType.VALUE);
    }

    static UniqueLookUpExpression uniqueLookUp(String name, String datatype, String entityId, String attrId, String recordId) {
        return new UniqueLookUpExpression(name,false, datatype, entityId, attrId, recordId);
    }

    static IsInReferenceData isInReferenceData(Expression left, Expression right) {
        return new IsInReferenceData(left, right);
    }

    static IsInReferenceDataCaseInsensitive isInReferenceDataCaseInsensitive(Expression left, Expression right) {
        return new IsInReferenceDataCaseInsensitive(left, right);
    }

    static VariableExpression var(String name, Datatype datatype) {
        return new VariableExpression(name,false, datatype);
    }

    static Expression eq(Expression left, Expression right) {
        return new Equal(left, right);
    }

    static Expression ieq(Expression left, Expression right) {
        return new EqualIgnoreCase(left, right);
    }

    static Expression ne(Expression left, Expression right) {
        return new NotEqual(left, right);
    }

    static Expression gt(Expression left, Expression right) {
        return new GreaterThan(left, right);
    }
    static Expression empty(Expression exp) {
        return new Empty(exp);
    }

    static Expression notEmpty(Expression exp) {
        return new NotEmpty(exp);
    }

    static Expression phone(Expression exp) { return new PhoneNumber(exp); }

    static Expression email(Expression exp) { return new Email(exp); }

    static Expression regex(Expression left, Expression right) { return new MatchesRegex(left, right); }


    static Expression startsWith(Expression left, Expression right) {
        return new StartsWith(left, right);
    }

    static Expression contains(Expression left, Expression right) {
        return new Contains(left, right);
    }

    static Expression notContains(Expression left, Expression right) {
        return new NotContains(left, right);
    }

    static Expression gte(Expression left, Expression right) {
        return new GreaterThanEqual(left, right);
    }

    static Expression lt(Expression left, Expression right) {
        return new LessThan(left, right);
    }

    static Expression lte(Expression left, Expression right) {
        return new LessThanEqual(left, right);
    }

    static Expression between(Expression operand, Expression lower, Expression upper) {
        return new BetweenExpression(operand, lower, upper);
    }
    static Expression notBetween(Expression operand, Expression lower, Expression upper) {
        return new NotBetweenExpression(operand, lower, upper);
    }
    static Expression in(Expression left, Expression right) {
        return new In(left, right);
    }

    static Expression notIn(Expression left, Expression right) {
        return new NotIn(left, right);
    }

    default void accept(ExpressionVisitor visitor){

    }

    default void accept(DynamicExpressionVisitor visitor){
        visitor.visit(this);
    }

}
