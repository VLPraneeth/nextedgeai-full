package com.syncari.core.functions;

import static com.syncari.utils.I18n.i18n;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(FunctionConstants.DATE_FORMAT)
public class DateFormatFunction extends DefaultFunction {

	private static final String PATTERN = "pattern";

	@Override
	public void validate(ValidationContext validationContext) {
		var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
	}
	
	@Override
	public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
		List<ValidationError> errors = new ArrayList<ValidationError>();
    	errors.addAll(super.validateWithoutException(validationContext));
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();
        
        if (graph == null || node == null)
			return errors;
        
		SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
		Map<String, Object> configMap = functionNodeConfig.getConfigMap();
		var pattern = configMap.get(PATTERN);
		if (pattern == null || StringUtils.isBlank(pattern.toString())) {
			String msg = String.format(i18n("date_format_required"), node.getName(),
					validationContext.getGraph().getName());
			errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(msg));
			return errors;
		}

		// validate if pattern is supported
		try {
			DateTimeFormatter.ofPattern(pattern.toString());
		} catch (IllegalArgumentException e) {
			log.error("validation error occured ", e);
			String msg = String.format(i18n("invalid_date_format"), pattern.toString(), node.getName(),
					validationContext.getGraph().getName());
			errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(msg));
		}
		
		return errors;
	}
}
