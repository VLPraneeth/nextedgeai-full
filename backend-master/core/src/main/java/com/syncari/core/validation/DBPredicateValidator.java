package com.syncari.core.validation;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

public interface DBPredicateValidator extends PredicateValidator{
    Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");
    default void validateVarExpression(VariableExpression expression, ValidationContext validationContext) {
        String variableName = expression.getVariableName();
        EntityDefinition selectedSyncariEntity = (EntityDefinition) validationContext.getData().get("syncariEntity");
		if(selectedSyncariEntity == null) {
			return;
		}
        Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
        String attributeId = attribMatcher.find() ? attribMatcher.group(1) : variableName;

        validateCondition(StringUtils.isBlank(attributeId) ||
                        selectedSyncariEntity.getAttribute(attributeId)==null,
                i18n("lookup_record_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));

    }

    default void validatePredicate(ValidationContext validationContext, Map<String, Object> predicate) {
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        validateCondition(filterExpression == null,
                i18n("lookup_record_invalid_predicate", validationContext.getNode().getName(), validationContext.getGraph().getName()));

        ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
        filterExpression.accept(visitor);
    }

}
