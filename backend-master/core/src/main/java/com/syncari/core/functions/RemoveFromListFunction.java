package com.syncari.core.functions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.quickstart.v2.dependency.DependencyService;
import com.syncari.core.validation.ValidationContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.REMOVE_FROM_LIST)
public class RemoveFromListFunction extends ListMutateFunctions implements DependencyService {

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
        FunctionDefinition funcDef = functionNodeConfig.getFunctionCall().getFunctionDefinition();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream().collect(Collectors.toMap(c->c.getName(), c->c.getLabel()));
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        String listIndex = Optional.ofNullable(configMap.get(LIST_INDEX)).map(Objects::toString).orElse("");
        String value = Optional.ofNullable(configMap.get(VALUE)).map(Objects::toString).orElse("");

		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				!StringUtils.isBlank(listIndex) && !StringUtils.isBlank(value), i18n("remove_list_index_value"),
                ErrorCode.E1186.getCode(), node.getName(), graph.getName()).ifPresent(e -> errors.add(e));

		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				StringUtils.isBlank(listIndex) && StringUtils.isBlank(value), i18n("remove_list_index_value"),
                ErrorCode.E1187.getCode(), node.getName(), graph.getName()).ifPresent(e -> errors.add(e));
		
		return errors;
    }

}
