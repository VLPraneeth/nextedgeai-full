package com.syncari.api.core.util;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.expression.VizConfigExpression;
import com.syncari.core.pipeline.*;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.pipeline.expression.dedupe.*;
import com.syncari.utils.KeyValue;

import java.util.List;
import java.util.Map;
import java.util.Stack;

public class PredicateSerializingVisitor implements ExpressionVisitor {
    Stack<KeyValue> rendered = new Stack<>();
    private KeyValue serialized;

    @Override
    public void visit(If exp) {
        exp.getFalseValue().accept(this);
        exp.getTrueValue().accept(this);
        exp.getCondition().accept(this);
        rendered.push(new KeyValue("operator", "if").
                set("condition", rendered.pop()).
                set("trueValue", rendered.pop()).
                set("falseValue", rendered.pop())
        );
    }

    public Map<String, Object> nested(String key, Map<String, Object> parent){
        return (Map<String, Object>) parent.get(key);
    }
    public <T> List<T> list(String key, Map<String, Object> parent){
        return (List<T>) parent.get(key);
    }

    public Expression fromMap(Map<String, Object> expressionMap){
        String operator =  expressionMap.getOrDefault("operator",
                expressionMap.getOrDefault("type","invalid")).toString().toLowerCase();
        List<Map<String,Object>> predicates = list("predicates",expressionMap);
        switch (operator){
            case "or":
                return predicates.stream().map(p->fromMap(p)).reduce((e1,e2)->Expression.or(e1,e2))
                        .orElseThrow(()->new SyncariValidationException("No conditions found for AND"));
            case "and":
                return predicates.stream().map(p->fromMap(p)).reduce((e1,e2)->Expression.and(e1,e2))
                        .orElseThrow(()->new SyncariValidationException("No conditions found for OR"));
            case "lt": return Expression.lt(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "lte": return Expression.lte(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "eq": return Expression.eq(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "ieq": return Expression.ieq(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "ne": return Expression.ne(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "gt": return Expression.gt(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "gte": return Expression.gte(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "starts_with": return Expression.startsWith(fromMap(nested("left",expressionMap)),fromMap(nested("right",expressionMap)));
            case "not":
                //AND is a placeholder - we generally have only one in the predicates list
                return predicates.stream().map(p->fromMap(p)).reduce((e1,e2)->Expression.and(e1,e2)).map(e->Expression.not(e))
                        .orElseThrow(()->new SyncariValidationException("No conditions found for OR"));
            case "between":
                Map<String, Object> right = nested("right", expressionMap);
                return Expression.between(fromMap(nested("left",expressionMap)),fromMap(nested("start", right)),fromMap(nested("end", right)));
            case "renderedLiteral": return Expression.renderedLit(expressionMap.get("value"));
            case "literal": return Expression.lit(expressionMap.get("value"));
            case "variable": return Expression.var(expressionMap.get("value").toString(),extractDatatype(expressionMap));
            default: throw new SyncariValidationException("Unknown operator %s",operator);
        }
    }

    protected Datatype extractDatatype(Map<String, Object> expressionMap) {
        if(expressionMap.get("datatype")!=null){
            if(Map.class.isAssignableFrom(expressionMap.get("datatype").getClass())){
                return DatatypeFactory.getDatatype(Map.class.cast(expressionMap.get("datatype")).getOrDefault("name","string").toString());
            }else{
                return DatatypeFactory.getDatatype(expressionMap.getOrDefault("datatype","string").toString());
            }
        }
        return StringType.VALUE;
    }

    @Override
    public void visit(And exp) {
        visitBinaryOp(And.NAME, exp);
    }

    @Override
    public void visit(Or exp) {
        visitBinaryOp(Or.NAME, exp);
    }

    @Override
    public void visit(Not exp) {
        rendered.push(new KeyValue("operator", "not").set("expression", rendered.pop()));
    }

    @Override
    public void visit(FunctionExpression exp) {

    }

    private void visitBinaryOp(String operator, BinaryExpression exp) {
        rendered.push(new KeyValue("operator", operator)
                .set("right", rendered.pop())
                .set("left", rendered.pop()));

    }

    @Override
    public void visit(Equal equal) {
        visitBinaryOp(Equal.NAME, equal);
    }
    
    @Override
    public void visit(EqualIgnoreCase equalIgnoreCase) {
        visitBinaryOp(EqualIgnoreCase.NAME, equalIgnoreCase);
    }

    @Override
    public void visit(NotEqual notEqual) {
        visitBinaryOp(NotEqual.NAME, notEqual);

    }

    @Override
    public void visit(GreaterThan greaterThan) {
        visitBinaryOp(GreaterThan.NAME, greaterThan);
    }

    @Override
    public void visit(LessThan lessThan) {
        visitBinaryOp(LessThan.NAME, lessThan);
    }

    @Override
    public void visit(LiteralExpression literalExpression) {
        if (literalExpression.isRendered()) {
            rendered.push(new KeyValue("value", literalExpression.getValue()).set("operator","renderedLiteral"));
        } else {
            rendered.push(new KeyValue("value", "\"" + literalExpression.getValue() + "\"").set("operator","literal"));
        }
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        rendered.push(new KeyValue("value", variableExpression.getVariableName()).set("type","variable").set("operator","variable"));
    }

    @Override
    public void visit(BetweenExpression betweenExpression) {

        //Ordering of these is IMPORTANT!
        rendered.push(new KeyValue("operator", "between")
                .set("upper", rendered.pop())
                .set("lower", rendered.pop())
                .set("expression", rendered.pop()));

    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        visitBinaryOp(GreaterThanEqual.NAME, gteExpression);
    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        visitBinaryOp(LessThanEqual.NAME, lteExpression);
    }

    @Override
    public void visit(StartsWith startsWithExpression) {
        visitBinaryOp(StartsWith.NAME,startsWithExpression);
    }
    @Override
    public void visit(Empty expression) {
        rendered.push(new KeyValue("operator", Empty.NAME)
                .set("expression",rendered.pop()));
    }

    @Override
    public void visit(NotEmpty isNotEmptyExpression) {
        rendered.push(new KeyValue("operator", NotEmpty.NAME)
                .set("expression",rendered.pop()));

    }

    @Override
    public void visit(UnaryExpression unaryExpression) {

    }

    @Override
    public void visit(Latest expression) {

    }

    @Override
    public void visit(Earliest expression) {

    }

    @Override
    public void visit(MostComplete expression) {

    }

    @Override
    public void visit(LeastComplete expression) {

    }

    @Override
    public void visit(Min expression) {

    }

    @Override
    public void visit(Max expression) {

    }

    @Override
    public void visit(LatestExisting expression) {

    }

    @Override
    public void visit(Contains expression) {
        visitBinaryOp(Contains.NAME, expression);
    }

    @Override
    public void visit(NotContains expression) {
        visitBinaryOp(NotContains.NAME, expression);
    }

    @Override
    public void visit(NotStartsWith expression) {
        visitBinaryOp(NotStartsWith.NAME, expression);
    }

    @Override
    public void visit(NotIn expression) {
        visitBinaryOp(NotIn.NAME, expression);
    }

    @Override
    public void visit(In expression) {
        visitBinaryOp(In.NAME, expression);
    }

    @Override
    public void visit(VizConfigExpression expression) {

    }

    @Override
    public void visit(NotBetweenExpression expression) {

    }

    public KeyValue serialized() {
        if (serialized == null) {
            if (rendered.isEmpty() || rendered.size() > 1) {
                throw new SyncariValidationException("Expression not fully visited");
            }
            serialized = rendered.pop();
        }
        return serialized;
    }

    @Override
    public void visit(FirstMatchingValueExpression expression){

    }

    @Override
    public void visit(FirstMatchingValueIgnoreCaseExpression expression) {

    }

    @Override
    public void visit(FirstNotMatchingExpression expression){

    }

    @Override
    public void visit(FirstNotMatchingIgnoreCaseExpression expression){

    }

    @Override
    public void visit(FieldLevelExpression fieldLevelExpression) {

    }

    @Override
    public void visit(ConcatExpression literalExpression) {

    }

    @Override
    public void visit(LatestUpdatedValueBinaryExpression latestUpdatedValueBinaryExpression) {

    }

    @Override
    public void visit(LatestCreatedValueBinaryExpression latestCreatedValueBinaryExpression) {

    }

    @Override
    public void visit(OldestUpdatedValueBinaryExpression oldestUpdatedValueBinaryExpression) {

    }

    @Override
    public void visit(OldestCreatedValueBinaryExpression oldestCreatedValueBinaryExpression) {

    }

    @Override
    public void visit(LeastFrequentValueBinaryExpression leastFrequentValueBinaryExpression) {

    }

    @Override
    public void visit(MostFrequestValueBinaryExpression mostFrequestValueBinaryExpression) {

    }

    @Override
    public void visit(HighestValueBinaryExpression highestValueBinaryExpression) {

    }

    @Override
    public void visit(LowestValueBinaryExpression lowestValueBinaryExpression) {

    }

    @Override
    public void visit(PhoneNumber phoneNumber) {

    }

    @Override
    public void visit(MatchesRegex regexExpression) {

    }

    @Override
    public void visit(Email email) {

    }

    @Override
    public void visit(DFIExpression exp) {

    }

    @Override
    public void visit(IsInReferenceData isInReferenceData) {

    }

    @Override
    public void visit(IsInReferenceDataCaseInsensitive isInReferenceDataCaseInsensitive) {

    }

    @Override
    public void visit(UniqueLookUpExpression uniqueLookUpExpression) {

    }
}
