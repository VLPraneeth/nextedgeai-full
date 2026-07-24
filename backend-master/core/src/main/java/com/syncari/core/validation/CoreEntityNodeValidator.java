package com.syncari.core.validation;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.syncari.core.model.FieldMergePolicy;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.token.TokenHelper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ValidationError;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CoreEntityNodeValidator implements ValidationService, PredicateValidator {

	private final Pattern FIELD_OUTPUT_PATTERN = Pattern.compile("field_(\\w+)");

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
    	List<ValidationError> errors = new ArrayList<>();

        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        
        if (graph == null || node == null)
			return errors;
        
        CoreEntityNodeConfig coreNodeConfig = node.getTypedConfiguration();
		EntityDefinition entity = coreNodeConfig.getEntityDefinition();
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), entity == null,
				i18n("invalid_core_node", node.getName(), graph.getName()), ErrorCode.E1157.getCode()).ifPresent(e -> errors.add(e));
		if (entity != null) {
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					entity.isArchived() || entity.isDeleted(),
					i18n("deleted_core_node_entity", node.getName(), graph.getName()), ErrorCode.E1158.getCode()).ifPresent(e -> errors.add(e));
		}

		errors.addAll(coreNodeConfig.validateWithoutException(node.getScope(), graph.getName(), node.getId(), node.getName()));
        // validate dedupe config if any
        if(coreNodeConfig.getAdvancedDedupeConfig() != null) {
        	try {
        		List<Expression> dupesPredicates = coreNodeConfig.getAdvancedDedupeConfig().findDupesCriteria();
				String maxAllowedDupes = coreNodeConfig.getAdvancedDedupeConfig().getMaximumAllowedDupes();
				if (StringUtils.isNotEmpty(maxAllowedDupes)){
					try{
						Integer.parseInt(maxAllowedDupes);
					}catch (NumberFormatException numberFormatException){
						errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("dedupe_max_dupes_not_number",maxAllowedDupes)));
					}
					if (!(Integer.parseInt(maxAllowedDupes) > 0)){
						errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("dedupe_max_dupes_not_number",maxAllowedDupes)));
					}
				}
				
				Expression skipWhen = coreNodeConfig.getAdvancedDedupeConfig().skipWhenCriteria();
				if(skipWhen != null) {
					validationContext.setAllowToken(true);
					validateExpression(skipWhen, validationContext, errors);
					validationContext.setAllowToken(false);
				}
				
        		dupesPredicates.forEach(findDupExpr -> validateExpression(findDupExpr, validationContext, errors));

        		List<Expression> winnerSelectionPredicates = coreNodeConfig.getAdvancedDedupeConfig().getWinnerSelectionPredicates();
        		winnerSelectionPredicates.forEach(expression -> validateExpression(expression, validationContext, errors));
        		coreNodeConfig.getAdvancedDedupeConfig().getDefaultWinnerOverridePolicy();
        		coreNodeConfig.getAdvancedDedupeConfig().getDefaultWinnerValueSelectionPolicy();

				List<FieldMergePolicy> fieldMergePolicies = coreNodeConfig.getAdvancedDedupeConfig().getFieldMergePolicies();
				fieldMergePolicies.forEach(policy -> validateExpression(policy.getExpresson(), validationContext, errors));
        		coreNodeConfig.getAdvancedDedupeConfig().getFieldOverrides();

			} catch (SyncariValidationException e) {
				log.error("validation error occured ", e);
				if(e.getMessage().contains("Unknown operator ")) {
					errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(i18n("dedupe_operator_required",
							validationContext.getNode().getName(), validationContext.getGraph().getName())));
				} else {
					errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
				}
			}
        }
        
        return errors;
    }

    private void validateExpression(Expression expression, ValidationContext validationContext, List<ValidationError> errors){
		if (expression == null)
		{
			errors.add(ValidationError.scopedError(validationContext.getNode().getScope(), validationContext.getNode().getId()).withMessage(i18n("empty_field_level_merge_policy")));
			return;
		}
		ExpressionValidatorVisitor visitor = new ExpressionValidatorVisitor(this, validationContext);
		try {
			expression.accept(visitor);
		} catch (SyncariValidationException e) {
			log.error("validation error occured ", e);
			errors.add(ValidationError.scopedError(validationContext.getNode().getScope(), validationContext.getNode().getId()).withMessage(e.getMessage()));
		}
	}

	@Override
	public void validateVarExpression(VariableExpression expression, ValidationContext validationContext) {
		String variableName = expression.getVariableName();
		validateVariableName(variableName, validationContext);
	}

	private void validateVariableName(String variableName, ValidationContext validationContext){
		if(validationContext.isAllowToken() && TokenHelper.hasTokens(variableName)) {
			return;
		}
		Matcher attribMatcher = FIELD_OUTPUT_PATTERN.matcher(variableName);
		String attributeId = attribMatcher.find() ? attribMatcher.group(1) : variableName;
		EntityDefinition syncariEntity = validationContext.getCoreEntity();
		if(syncariEntity != null) {
			validateCondition(!StringUtils.isBlank(attributeId) &&
							!syncariEntity.getAttributes().stream().anyMatch(a -> a.getId().equals(attributeId)),
					i18n("dedupe_config_invalid_predicate_variable", attributeId, validationContext.getNode().getName(), validationContext.getGraph().getName()));
		}

	}
}
