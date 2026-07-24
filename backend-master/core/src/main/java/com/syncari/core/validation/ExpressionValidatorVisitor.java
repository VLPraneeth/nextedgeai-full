package com.syncari.core.validation;

import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
public class ExpressionValidatorVisitor extends SimpleExpressionVisitor {

    private ValidationContext validationContext;
    private PredicateValidator predicateValidator;

    public ExpressionValidatorVisitor(PredicateValidator validator, ValidationContext validationContext){
        this.validationContext = validationContext;
        this.predicateValidator = validator;
    }

    @Override
    public void visit(VariableExpression variableExpression) {
        predicateValidator.validateVarExpression(variableExpression, validationContext);
    }

    /**
     * Validates that a literal expression value is not empty.
     * Used by comparison operators to ensure meaningful comparisons.
     */
    private void validateLiteralNotEmpty(Expression expression, String operatorName) {
        if (expression instanceof LiteralExpression) {
            LiteralExpression literal = (LiteralExpression) expression;
            Object value = literal.getValue();

            boolean isEmpty = false;
            if (value == null) {
                isEmpty = true;
            } else if (value instanceof String) {
                isEmpty = StringUtils.isBlank((String) value);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                isEmpty = list.isEmpty() || list.stream()
                        .allMatch(item -> item == null || (item instanceof String && StringUtils.isBlank((String) item)));
            }

            if (isEmpty) {
                validateCondition(true,
                        i18n("empty_condition_value_error", operatorName));
            }
        }
    }
    @Override
    public void visit(Equal expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "equals");
    }

    @Override
    public void visit(EqualIgnoreCase expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "equals (ignore case)");
    }

    @Override
    public void visit(NotEqual expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "not equals");
    }

    // ============== String Operators ==============

    @Override
    public void visit(Contains expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "contains");
    }

    @Override
    public void visit(NotContains expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "not contains");
    }

    @Override
    public void visit(StartsWith expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "starts with");
    }

    @Override
    public void visit(NotStartsWith expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "not starts with");
    }

    @Override
    public void visit(In expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "in");
    }

    @Override
    public void visit(NotIn expression) {
        expression.getLeft().accept(this);
        expression.getRight().accept(this);
        validateLiteralNotEmpty(expression.getRight(), "not in");
    }

}
