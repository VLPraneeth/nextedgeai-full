package com.syncari.core.functions;

import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.AUTO_INCREMENT)
public class AutoIncrementFunction extends DefaultFunction {

    @Autowired
    PostgresqlDatastoreService datastore;

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
        SimpleFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        if (graph == null || node == null)
			return errors;

        Object startValue = configMap.getOrDefault("startValue", "1");
        String sequenceName = configMap.getOrDefault("sequenceName", "").toString();
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                StringUtils.isBlank(sequenceName), i18n("autoIncrement-sequence-not-defined",
                        node.getName(), graph.getName()), ErrorCode.E1121.getCode()).ifPresent(e -> errors.add(e));
        return errors;
    }

    @Override
    public void extract(QuickStartContext context) {

    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode sharableNode = context.getCurrentNode();
        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
    
    @Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		return super.toUserFriendlyValue(context, configProperty);
	}
}
