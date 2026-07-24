package com.syncari.core.functions;

import com.syncari.core.DataTransformer;
import com.syncari.core.enrich.apexanalytix.ApexAnalytixService;
import com.syncari.core.model.Connector;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.LookupService;
import com.syncari.core.service.UserService;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class ApexAnalytixFunctions extends FunctionsBase {

    @Autowired
    ApexAnalytixService service;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DataTransformer transformer;
    @Autowired
    DataServiceFactory factory;
    @Autowired
    TokenHelper tokenHelper;
    @Autowired
    UserService userService;

    @Function
    public Object apexAnalytixCompanyEnrich(Object inputValue, FunctionCall functionCall, GraphContext context) {
        var companyName = getConfig("companyName", functionCall, context);
        var country = getConfig("country", functionCall, context);
        return execute("company", companyName, Optional.of(country), inputValue, functionCall, context);
    }

    private Object execute(String entity, Object companyName, Optional<Object> country,
                           Object defaultValue, FunctionCall functionCall, GraphContext context) {
        if ((companyName == null || StringUtils.isBlank(companyName.toString()))) {
            return defaultValue;
        }
        String serviceId = getConfig("serviceId", functionCall, context);
        String returnFieldName = getConfig("lookUpKey", functionCall, context);
        Object enrichedValue = null;
        Optional<Connector> connector = serviceId == null ? Optional.empty() : connectorService.find(serviceId);
        Connector enrichConnector = connector.get();
        try {
            LookupService service = factory.getLookupService(enrichConnector.getMetadata());
            SearchCriteria criteria = new SearchCriteria();
            criteria.setMetaFilters(Map.of("lookupEntity", entity, "lookupField", returnFieldName));
            criteria.and("companyName", companyName);
            if (country.isPresent()) {
                criteria.and("country", country.get());
            }

            LookupData data = service.lookup(transformer.toConnectorInfo(enrichConnector), criteria);
            if (null != data) {
                enrichedValue = data.getValue(returnFieldName);
            }
        } catch (Exception e){
            log.error("Error Enriching using {}", enrichConnector.getName());
            enrichedValue = null;
        }
        return enrichedValue == null || StringUtils.isBlank(enrichedValue.toString()) ? defaultValue : enrichedValue;
    }

    protected String getInputValue(GraphContext context, FunctionCall functionCall, String configKey, String defaultValue) {
        String attributeId = getConfig(configKey, functionCall, context);
        String contextKey = "field_" + attributeId;
        var inputValue = context.containsKey(contextKey) && context.get(contextKey)!=null ?context.get(contextKey).toString() : null;
        return StringUtils.isBlank(inputValue) ? defaultValue : inputValue;
    }
}
