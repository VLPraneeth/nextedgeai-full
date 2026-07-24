package com.syncari.core.functions;

import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.core.DataTransformer;
import com.syncari.core.enrich.apexanalytix.ApexAnalytixService;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableFunctionCall;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(ApexAnalytixFunctionsSeed.APEX_ANALYTIX_COMPANY_ENRICH)
public class ApexAnalytixCompanyEnrichFunction extends DefaultFunction {
    @Autowired
    TokenHelper tokenHelper;
    @Autowired
    ApexAnalytixService service;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DataTransformer transformer;

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
		FunctionCall functionCall = functionNodeConfig.getFunctionCall();
		String companyName = functionCall.getConfig().getOrDefault("companyName", "").toString();
		String country = functionCall.getConfig().getOrDefault("country", "").toString();
		String connectorId = functionCall.getConfig().getOrDefault("serviceId", "").toString();

		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                StringUtils.isBlank(companyName) || StringUtils.isBlank(country), i18n("aa-validation-invalid-input", companyName,
				node.getName(), graph.getName()), ErrorCode.E1120.getCode()).ifPresent(e -> errors.add(e));
		Optional<Connector> connector = connectorService.find(connectorId);
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
				StringUtils.isBlank(connectorId) || connector.isEmpty(), i18n("aa-validation-missing-connector",
				node.getName(), graph.getName()), ErrorCode.E1121.getCode()).ifPresent(e -> errors.add(e));
		connector.ifPresent(c -> {
			try {
				TestConnectionResponse testConnectionResponse = service.testConnection(transformer.toConnectorInfo(c),
						List.of());
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						!testConnectionResponse.isSuccess(), i18n("invalid_apexanalytix_credentials_create", c.getName(),
						node.getName(), graph.getName()), ErrorCode.E1122.getCode()).ifPresent(e -> errors.add(e));
			} catch (Exception e) {
				log.error("validation error occured ", e);
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), true,
						i18n("invalid_apexanalytix_credentials_create", c.getName(), node.getName(), graph.getName()), ErrorCode.E1123.getCode())
								.ifPresent(ee -> errors.add(ee));

			}
		});
		return errors;

	}

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableFunctionNodeConfig functionNodeConfig = node.getTypedConfiguration();
        SharableFunctionCall functionCall = functionNodeConfig.getFunctionCall();

        String companyName = functionCall.getConfig().getOrDefault("companyName", "").toString();
        String country = functionCall.getConfig().getOrDefault("country", "").toString();
        String connectorId = functionCall.getConfig().getOrDefault("serviceId", "").toString();

        Optional<Connector> connector = context.getConnector(connectorId);
        connector.ifPresent(conn -> {
            qsConfig.addDependency(new QSDependency()
                    .setId(conn.getId())
                    .setType(QSDependency.Type.Service)
                    .setSourceValue(conn));
        });

        DependencyUtil.getTokenDependencies(companyName).forEach(d -> qsConfig.addDependency(d));
        DependencyUtil.getTokenDependencies(country).forEach(d -> qsConfig.addDependency(d));
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode();
        SharableFunctionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = functionNodeConfig.getConfigMap();

        String connectorId = configMap.getOrDefault("serviceId", "").toString();
        Connector resolvedConn = (Connector) qsConfig.getResolvedValueByType(connectorId, QSDependency.Type.Connector);
        if(resolvedConn != null){
            configMap.put("serviceId", resolvedConn.getId());
        }

        String companyName = configMap.getOrDefault("companyName", "").toString();
        String resolvedCompanyName = (String) qsConfig.getResolvedValueByType(companyName, QSDependency.Type.Token);
        if(resolvedCompanyName != null){
            configMap.put("companyName", resolvedCompanyName);
        }
        String country = configMap.getOrDefault("country", "").toString();
        String resolvedCountry = (String) qsConfig.getResolvedValueByType(country, QSDependency.Type.Token);
        if(resolvedCountry != null){
            configMap.put("country", resolvedCountry);
        }

        functionNodeConfig.getFunctionCall().setConfig(configMap);
        functionNodeConfig.getFunctionCall().setParams(resolveParams(context, functionNodeConfig));
        sharableNode.setConfiguration(functionNodeConfig);

        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }
}
