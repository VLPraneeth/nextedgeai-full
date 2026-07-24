package com.syncari.core.pipeline.jtwig;

import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.pipeline.expression.dedupe.*;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class JTWigFieldMergeVisitor extends JTwigTemplateGenerationVisitor {


    private final GraphContext context;
    private String attributeId;

    /**
     * Requires FieldMergen, fieldSelector, record and all fields and valuesof the record with a field_<attibuteId> key in the context
     * for evaluation
     */

    public JTWigFieldMergeVisitor(TokenHelper tokenHelper, GraphContext context) {
        super(tokenHelper);
        this.context = context;
    }

    public void visit(SumExpression expression) {
        rendered.push(String.format("fieldMerge.sum(\"%s\")", extractAttributeId(rendered.pop())));
    }

    public void visit(VariableExpression expression) {
        attributeId = extractAttributeId(expression.getVariableName());
        super.visit(expression);
    }

    public void visit(SetValueExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        String value = tokenHelper.resolveTokens(context, right);
        rendered.push(String.format("fieldMerge.setValue(\"%s\",%s)", extractAttributeId(left),value));
    }

    public void visit(ConcatExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.concat(\"%s\",%s)", extractAttributeId(left),right));
    }

    public void visit(LatestUpdatedValueExpression expression) {
        rendered.push(String.format("fieldMerge.latestUpdatedWithValue(\"%s\")", extractAttributeId(rendered.pop())));
    }

    public void visit(LatestUpdatedValueBinaryExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.latestUpdatedWithValue(\"%s\",%s)", extractAttributeId(left), right));
    }

    public void visit(LatestCreatedValueBinaryExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.latestCreatedWithValue(\"%s\", %s)",extractAttributeId(left), right));
    }

    public void visit(LatestCreatedValueExpression expression) {
        rendered.push(String.format("fieldMerge.latestCreatedWithValue(\"%s\")", extractAttributeId(rendered.pop())));
    }

    public void visit(OldestCreatedValueBinaryExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.oldestCreatedWithValue(\"%s\", %s)", extractAttributeId(left), right));
    }

    public void visit(OldestCreatedValueExpression expression) {
        rendered.push(String.format("fieldMerge.oldestCreatedWithValue(\"%s\")", extractAttributeId(rendered.pop())));
    }

    public void visit(OldestUpdatedValueBinaryExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.oldestUpdatedWithValue(\"%s\",%s)", extractAttributeId(left), right));

    }

    public void visit(OldestUpdatedValueExpression expression) {
        rendered.push(String.format("fieldMerge.oldestUpdatedWithValue(\"%s\")", extractAttributeId(rendered.pop())));
    }

    public void visit(HighestValueExpression expression) {
        var right = rendered.pop();
        rendered.push(String.format("fieldMerge.highestValue(\"%s\")", extractAttributeId(right)));
    }

    public void visit(HighestValueBinaryExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.highestValue(\"%s\",%s)", extractAttributeId(left), right));
    }

    public void visit(LowestValueExpression expression) {
        rendered.push(String.format("fieldMerge.lowestValue(\"%s\")", extractAttributeId(rendered.pop())));
    }

    public void visit(LowestValueBinaryExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.lowestValue(\"%s\",%s)", extractAttributeId(left), right));
    }

    public void visit(FirstMatchingValueExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.firstMatchingValue(\"%s\",%s)",extractAttributeId(left),right));

    }

    public void visit(FirstMatchingValueIgnoreCaseExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.firstMatchingValueIgnoreCase(\"%s\",%s)",extractAttributeId(left),right));

    }

    public void visit(FirstNotMatchingExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.firstNotMatchingValue(\"%s\",%s)", extractAttributeId(left),right));
    }

    public void visit(FirstNotMatchingIgnoreCaseExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldMerge.firstNotMatchingValueIgnoreCase(\"%s\",%s)", extractAttributeId(left),right));
    }

    public void visit(MostFrequentValueExpression expression) {
        rendered.push(String.format("fieldMerge.mostFrequentValue(\"%s\")", extractAttributeId(rendered.pop())));
    }

    public void visit(LeastFrequentValueExpression expression) {
        rendered.push(String.format("fieldMerge.leastFrequentValue(\"%s\")", extractAttributeId(rendered.pop())));
    }

    protected String extractAttributeId(String expression) {
        if(expression.startsWith("field_")){
            return expression.replaceFirst("field_","");
        }
        return expression;
    }

    public String getGeneratedBody() {
        String expressionTemplate = super.getGeneratedBody();
        return String.format("{{%s}}",expressionTemplate);
    }

    public String getAttributeId() {
        return attributeId;
    }
}
