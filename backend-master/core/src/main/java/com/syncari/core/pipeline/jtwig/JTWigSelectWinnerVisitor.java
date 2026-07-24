package com.syncari.core.pipeline.jtwig;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.pipeline.expression.dedupe.*;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

@Slf4j
public class JTWigSelectWinnerVisitor extends JTwigTemplateGenerationVisitor {
    private EntityDefinition entityDefinition;

    /**
     * Requires recordSelector, fieldSelector, record and all fields and valuesof the record with a field_<attibuteId> key in the context
     * for evaluation
     */

    public JTWigSelectWinnerVisitor(TokenHelper tokenHelper, EntityDefinition entityDefinition) {
        super(tokenHelper);
        this.entityDefinition = entityDefinition;
    }

    @Override
    public void visit(Equal exp) {
        Object right = rendered.pop();
        var left = rendered.pop();
        right = getTypedValue(left, right.toString());
        rendered.push(String.format("(%s == %s)",left,right));
    }

    private Object getTypedValue(String leftValue, String rightValue) {
        if (StringUtils.isBlank(leftValue) || StringUtils.isBlank(rightValue)){
            return rightValue;
        }
        String [] splittedArray = leftValue.split("_");
        if (ArrayUtils.isEmpty(splittedArray) || (splittedArray.length < 1)){
            return rightValue;
        }
        Optional<AttributeDefinition> attributeDefinition = Optional.ofNullable(entityDefinition.getIdToAttributes().get(splittedArray[1]));
        if(attributeDefinition.isPresent()) {
            return getStringValue(attributeDefinition.get().convert(StringUtils.strip(rightValue,"\"")));
        }
        return getStringValue(rightValue);
    }

    @Override
    protected String getStringValue(Object converted) {
        if(converted == null) return null;
        if(String.class.isAssignableFrom(converted.getClass())) {
            return ("\"" + converted + "\"");
        }else if(converted instanceof ZonedDateTime) {
            return "\"" + ((ZonedDateTime)converted).toInstant() + "\"" ;
        }else if(converted instanceof Date) {
            return "\"" + ((Date)converted).toInstant() + "\"" ;
        }
        return converted.toString();
    }

    @Override
    public void visit(EqualIgnoreCase exp) {
        Object right = rendered.pop();
        var left = rendered.pop();
        right = getTypedValue(left, right.toString());
        rendered.push(String.format("%s.equalsIgnoreCase(%s)",left,right));
    }

    @Override
    public void visit(GreaterThan exp) {
        Object right = rendered.pop();
        var left = rendered.pop();
        right = getTypedValue(left, right.toString());
        rendered.push(String.format("(%s > %s)",left,right.toString()));
    }

    @Override
    public void visit(LessThan exp) {
        Object right = rendered.pop();
        var left = rendered.pop();
        right = getTypedValue(left, right.toString());
        rendered.push(String.format("(%s < %s)",left,right.toString()));
    }

    @Override
    public void visit(NotEqual exp) {
        Object right = rendered.pop();
        var left = rendered.pop();
        right = getTypedValue(left, right.toString());
        rendered.push(String.format("(%s != %s)",left,right.toString()));
    }

    @Override
    public void visit(GreaterThanEqual gteExpression) {
        Object right = rendered.pop();
        var left = rendered.pop();
        right = getTypedValue(left, right.toString());
        rendered.push(String.format("(%s >= %s)",left,right.toString()));

    }

    @Override
    public void visit(LessThanEqual lteExpression) {
        Object right = rendered.pop();
        var left = rendered.pop();
        right = getTypedValue(left, right.toString());
        rendered.push(String.format("(%s <= %s)",left,right.toString()));

    }

    public void visit(MostCompleteRecordExpression expression) {
        rendered.push("recordSelector.mostCompleteRecord(record)");
    }

    public void visit(LatestCreatedRecordExpression expression) {
        rendered.push("recordSelector.latestCreatedRecord(record)");
    }

    public void visit(LatestUpdatedRecordExpression expression) {
        rendered.push("recordSelector.latestUpdatedRecord(record)");
    }

    public void visit(OldestCreatedRecordExpression expression) {
        rendered.push("recordSelector.oldestCreatedRecord(record)");
    }

    public void visit(OldestUpdatedRecordExpression expression) {
        rendered.push("recordSelector.oldestUpdatedRecord(record)");
    }

    public void visit(LatestUpdatedValueExpression expression) {
        rendered.push(String.format("fieldSelector.latestUpdatedWithValue(\"%s\",record)", extractAttributeId(rendered.pop())));
    }

    public void visit(LatestCreatedValueExpression expression) {
        rendered.push(String.format("fieldSelector.latestCreatedWithValue(\"%s\",record)", extractAttributeId(rendered.pop())));
    }

    public void visit(OldestCreatedValueExpression expression) {
        rendered.push(String.format("fieldSelector.oldestCreatedWithValue(\"%s\",record)", extractAttributeId(rendered.pop())));
    }

    public void visit(OldestUpdatedValueExpression expression) {
        rendered.push(String.format("fieldSelector.oldestUpdatedWithValue(\"%s\",record)", extractAttributeId(rendered.pop())));
    }

    public void visit(HighestValueExpression expression) {
        rendered.push(String.format("fieldSelector.highestValue(\"%s\",record)", extractAttributeId(rendered.pop())));
    }

    public void visit(LowestValueExpression expression) {
        rendered.push(String.format("fieldSelector.lowestValue(\"%s\",record)", extractAttributeId(rendered.pop())));
    }

    public void visit(FirstMatchingValueExpression expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format("fieldSelector.firstMatchingValue(\"%s\",record,%s)",left,right));

    }
    
    public void visit(FirstNotMatchingExpression expression) {
        rendered.push(String.format("fieldSelector.firstNotMatchingValue(\"%s\",record)", extractAttributeId(rendered.pop())));
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

}
