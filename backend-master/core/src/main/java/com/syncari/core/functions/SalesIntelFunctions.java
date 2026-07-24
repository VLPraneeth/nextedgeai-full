package com.syncari.core.functions;

import com.syncari.core.DataTransformer;
import com.syncari.core.enrich.salesintel.SalesIntelService;
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
public class SalesIntelFunctions extends FunctionsBase {

    @Autowired
    SalesIntelService salesIntelService;
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
    public Object salesIntelPersonEnrich(Object defaultValue, FunctionCall functionCall, GraphContext context) {
        var email = getInputValue(context, functionCall, "email", "");
        var phoneNumber = getInputValue(context, functionCall, "phoneNumber", "");
        var firstName = getInputValue(context, functionCall, "firstName", "");
        var lastName = getInputValue(context, functionCall, "lastName", "");

        //By default don't enrich if value present
        boolean enrichOnEmptyValue = getConfigOrDefault("enrichOnEmptyValue", functionCall, true, context);
        //Don't enrich if flag is sent and input is not empty
        if (enrichOnEmptyValue && defaultValue != null && !StringUtils.isBlank(defaultValue.toString())) {
            return defaultValue;
        }
        if ((StringUtils.isBlank(email)) && (StringUtils.isBlank(phoneNumber))) {
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
            criteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", returnFieldName));
            criteria.and("email", email);
            criteria.and("phoneNumber", phoneNumber);
            criteria.and("firstName", firstName);
            criteria.and("lastName", lastName);

            LookupData data = service.lookup(transformer.toConnectorInfo(enrichConnector), criteria);
            if (null != data) {
                enrichedValue = data.getValue(returnFieldName);
            }
        } catch (Exception e){
            log.error("Error Enriching Contact using {}", enrichConnector.getName());
            enrichedValue = null;
        }
        return enrichedValue == null || StringUtils.isBlank(enrichedValue.toString()) ? defaultValue : enrichedValue;
    }

    @Function
    public Object salesIntelCompanyEnrich(Object defaultValue, FunctionCall functionCall, GraphContext context) {
        var companyName = getInputValue(context, functionCall, "companyName", "");
        var companyDomain = getInputValue(context, functionCall, "companyDomain", "");
        var companyIndustries = getInputValue(context, functionCall, "companyIndustries", "");
        var companyLocationStates = getInputValue(context, functionCall, "companyLocationStates", "");
        // By default don't enrich if value present
        boolean enrichOnEmptyValue = getConfigOrDefault("enrichOnEmptyValue", functionCall, true, context);
        // Don't enrich if flag is sent and input is not empty
        if (enrichOnEmptyValue && (defaultValue != null) && !StringUtils.isBlank(defaultValue.toString())) {
            return defaultValue;
        }
        if ((StringUtils.isBlank(companyName)) && (StringUtils.isBlank(companyDomain))) {
            return defaultValue;
        }
        String serviceId = getConfig("serviceId", functionCall, context);
        String returnFieldName = getConfig("lookUpKey", functionCall, context);
        Object enrichedValue = null;

        // TODO: Remove ServiceCredential check once we migrate clearbit to new pattern
        Optional<Connector> connector = serviceId == null ? Optional.empty() : connectorService.find(serviceId);
        Connector enrichConnector = connector.get();
        try {
            LookupService service = factory.getLookupService(enrichConnector.getMetadata());
            SearchCriteria criteria = new SearchCriteria();
            criteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", returnFieldName));
            criteria.and("companyName", companyName);
            criteria.and("companyDomain", companyDomain);
            criteria.and("companyIndustries", companyIndustries);
            criteria.and("companyLocationStates", companyLocationStates);
            LookupData data = service.lookup(transformer.toConnectorInfo(enrichConnector), criteria);
            enrichedValue = data.getValue(returnFieldName);
        } catch (Exception e) {
            log.error(String.format("Error Enriching Company using %s", enrichConnector.getName()), e);
            enrichedValue = null;
        }
        return enrichedValue == null ? defaultValue : enrichedValue;
    }

    protected String getInputValue(GraphContext context, FunctionCall functionCall, String configKey, String defaultValue) {
        String attributeId = getConfig(configKey, functionCall, context);
        String contextKey = "field_" + attributeId;
        var inputValue = context.containsKey(contextKey) && context.get(contextKey)!=null ?context.get(contextKey).toString() : null;
        return StringUtils.isBlank(inputValue) ? defaultValue : inputValue;
    }
}
