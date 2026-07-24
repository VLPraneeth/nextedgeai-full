package com.syncari.core.actions;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AbstractActionConfig;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.service.ActionService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import com.syncari.utils.TextUtil;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(ActionConstants.SEND_EMAIL)
public class SendEmailAction extends DefaultAction {

    @Autowired
    ActionService actionService;

    private static final String RECIPIENTS = "recipients";
    private static final String SUBJECT = "subject";
    private static final String BODY = "body";

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

        GenericActionConfig actionNodeConfig = node.getTypedConfiguration();
        ActionDefinition funcDef = actionService.getAction(node.getApiName()).get();
        Map<String, String> configNameLabelMap = funcDef.getConfiguration().stream()
                .collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        var recipients = configMap.get(RECIPIENTS);
        var subject = configMap.get(SUBJECT);
        var body = configMap.get(BODY);

        // validate recipient list
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), recipients == null || !(recipients instanceof List),
                i18n("invalid_config_in_node_null", node.getName(), graph.getName(), configNameLabelMap.get(RECIPIENTS),
                        recipients == null ? null : recipients.toString()), ErrorCode.E1072.getCode()).ifPresent(e -> errors.add(e));

		if (recipients instanceof List) {
			List<String> recipientList = (List<String>) recipients;
			recipientList.forEach(email -> {
				if (!TokenHelper.hasTokens(email)) {
					validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
							!TextUtil.isValidEmail(email),
							i18n("invalid_recipient_email", email, node.getName(), graph.getName()), ErrorCode.E1073.getCode())
									.ifPresent(e -> errors.add(e));
				}
			});
		}

        // subject should be non empty
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), subject == null || subject.toString().isEmpty(),
                i18n("invalid_config_in_node_null", node.getName(), graph.getName(), configNameLabelMap.get(SUBJECT),
                        subject == null ? null : subject.toString()), ErrorCode.E1074.getCode()).ifPresent(e -> errors.add(e));
        
        return errors;
    }
    
    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
    	if (BODY.equals(configProperty) && context != null && context.getCurrentNode() != null) {
    		AbstractActionConfig actionConfig = context.getCurrentNode().getTypedConfiguration();
			Map<String, Object> configMap = actionConfig.getConfigMap();
			if(configMap == null) {
				configMap = Map.of();
			}
    		var body = configMap.getOrDefault(configProperty, null);
    		if(body != null) {
    			try {
    				return List.of(Pair.of(configProperty, new String(Base64.getDecoder().decode(body.toString()))));
    			}catch (Exception e) {
					log.error("Base64 decode failed", e);
				}
    		}
    	}
    	return super.toUserFriendlyValue(context, configProperty);
    }

}
