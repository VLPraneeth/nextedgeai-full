package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.validation.ValidationContext;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.DATE_PARSE)
public class ToDateFunction extends DefaultFunction {

	private static final String FORMAT = "format";

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
		var format = configMap.get(FORMAT);
		if (format == null || StringUtils.isBlank(format.toString()))
			return errors;

		// check if the format is one of the picklist of values
		List<String> allowedFormats = DateFunctionsSeed.dateTimeFormats().stream().map(m -> m.get("value")).collect(Collectors.toList());
		try {
			if (!allowedFormats.contains(format.toString())) {
				DateTimeFormatter.ofPattern(format.toString());
			}
		} catch (IllegalArgumentException e) {
			log.error("validation error occured ", e);
			String msg = String.format(i18n("invalid_date_format"), format.toString(), node.getName(),
					validationContext.getGraph().getName());
			errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(msg));
		}
		
		return errors;
	}
}
