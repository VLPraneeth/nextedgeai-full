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

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class AidentifiedFunctions extends FunctionsBase {

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
    public static final List<String> optionalParams = List.of("emails", "phones", "address", "address2", "state", "city", "postalcode");
    public static final List<String> requiredParams = List.of("firstName", "lastName", "fullName", "record_id");

    @Function
    public Object aidentifiedPeopleEnrich(Object inputValue, FunctionCall functionCall, GraphContext context) {
        String serviceId = getConfig("serviceId", functionCall, context);
        String returnFieldName = getConfig("lookUpKey", functionCall, context);
        Object enrichedValue = null;
        Optional<Connector> connector = serviceId == null ? Optional.empty() : connectorService.find(serviceId);
        Connector enrichConnector = connector.get();
        try {
            LookupService service = factory.getLookupService(enrichConnector.getMetadata());
            SearchCriteria criteria = new SearchCriteria();
            criteria.setMetaFilters(Map.of("lookupEntity", "people", "lookupField", returnFieldName));
            requiredParams.forEach(p -> {
                var val = getConfig(p, functionCall, context);
                if (val != null && !StringUtils.isBlank(val.toString())) {
                    String resolvedValue = tokenHelper.resolveTokens(context, val.toString());
                    criteria.and(p, resolvedValue);
                }
            });
            optionalParams.forEach(p -> {
                var val = getConfig(p, functionCall, context);
                if(val != null && !StringUtils.isBlank(val.toString())) {
                    if(val instanceof List) {
                        criteria.and(p, val);
                    } else {
                        String resolvedValue = tokenHelper.resolveTokens(context, val.toString());
                        criteria.and(p, resolvedValue);
                    }
                }
            });

            LookupData data = service.lookup(transformer.toConnectorInfo(enrichConnector), criteria);
            if (null != data){
                enrichedValue = data.getValue(returnFieldName);
            }
        } catch (Exception e){
            log.error("Error Enriching using {}", enrichConnector.getName());
            enrichedValue = null;
        }
        return enrichedValue == null || StringUtils.isBlank(enrichedValue.toString()) ? inputValue : enrichedValue;
    }

}
