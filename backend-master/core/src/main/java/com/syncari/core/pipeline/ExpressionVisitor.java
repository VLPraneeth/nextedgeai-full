package com.syncari.core.pipeline;

import com.syncari.core.insights.expression.VizConfigExpression;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.pipeline.expression.dedupe.*;

public interface ExpressionVisitor {
    void visit(If exp);

    void visit(And exp);

    void visit(Or exp);

    void visit(Not exp);

    void visit(FunctionExpression exp);

    void visit(Equal equal);
    
    void visit(EqualIgnoreCase equal);

    void visit(NotEqual notEqual);

    void visit(GreaterThan greaterThan);

    void visit(LessThan lessThan);

    void visit(LiteralExpression literalExpression);

    void visit(VariableExpression variableExpression);

    void visit(BetweenExpression betweenExpression);

    void visit(GreaterThanEqual gteExpression);

    void visit(LessThanEqual lteExpression);

    void visit(StartsWith startsWithExpression);

    void visit(Empty isEmptyExpression);

    void visit(NotEmpty isNotEmptyExpression);

    void visit(UnaryExpression unaryExpression);

    void visit(Latest expression);

    void visit(Earliest expression);

    void visit(MostComplete expression);

    void visit(LeastComplete expression);

    void visit(Min expression);

    void visit(Max expression);

    void visit(LatestExisting expression);

    void visit(Contains expression);

    void visit(NotContains expression);

    void visit(NotStartsWith expression);

    void visit(NotIn expression);

    void visit(In expression);

    void visit(VizConfigExpression expression);

    void visit(NotBetweenExpression expression);

    void visit(FirstMatchingValueExpression expression);

    void visit(FirstMatchingValueIgnoreCaseExpression expression);

    void visit(FirstNotMatchingExpression expression);

    void visit(FirstNotMatchingIgnoreCaseExpression expression);

    void visit(FieldLevelExpression fieldLevelExpression);

    void visit(ConcatExpression literalExpression);

    void visit(LatestUpdatedValueBinaryExpression latestUpdatedValueBinaryExpression);

    void visit(LatestCreatedValueBinaryExpression latestCreatedValueBinaryExpression);

    void visit(OldestUpdatedValueBinaryExpression oldestUpdatedValueBinaryExpression);

    void visit(OldestCreatedValueBinaryExpression oldestCreatedValueBinaryExpression);

    void visit(LeastFrequentValueBinaryExpression leastFrequentValueBinaryExpression);

    void visit(MostFrequestValueBinaryExpression mostFrequestValueBinaryExpression);

    void visit(HighestValueBinaryExpression highestValueBinaryExpression);

    void visit(LowestValueBinaryExpression lowestValueBinaryExpression);

    void visit(PhoneNumber phoneNumber);

    void visit(MatchesRegex regexExpression);

    void visit(Email email);

    void visit(DFIExpression exp);

    void visit(IsInReferenceData isInReferenceData);

    void visit(IsInReferenceDataCaseInsensitive isInReferenceDataCaseInsensitive);

    void visit(UniqueLookUpExpression uniqueLookUpExpression);
}

