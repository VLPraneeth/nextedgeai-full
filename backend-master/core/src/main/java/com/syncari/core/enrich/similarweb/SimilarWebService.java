package com.syncari.core.enrich.similarweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.ConnectorType;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.core.service.LookupService;
import com.syncari.core.utils.EnrichUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.SIMILAR_WEB)
public class SimilarWebService implements LookupService, AuthenticationService, SynapseInfoService {
    @Autowired
    EnrichmentCacheRepo cacheRepo;
    protected SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient();
    protected ObjectMapper mapper = new ObjectMapper();
    public static final String DISPLAY_NAME = "SimilarWeb";
    protected String capabiltiesURL = "https://api.similarweb.com/capabilities?api_key=${api_key}";

    protected String trafficMetricURLTemplate = "https://api.similarweb.com/v1/website/${domain}/${api_category}/${api_name}?api_key=" +
            "${api_key}&start_date=${start_date}&end_date=${end_date}&country=${country}" +
            "&granularity=monthly&main_domain_only=false&format=json&show_verified=false";

    protected String describeCategoryTemplate = "https://api.similarweb.com/v1/website/${domain}/${api_category}/describe";

    protected String technographicsURLTemplate = "https://api.similarweb.com/v1/website/${domain}/${api_category}/${api_name}?api_key=" +
            "${api_key}&format=json";


    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        String apiKey = config.getAuthConfig().getToken();
        String url = StrSubstitutor.replace(capabiltiesURL, Map.of("api_key", apiKey));

        try {
            ResponseEntity<String> response = restClient.getResponse(url, new AuthConfig());
            Map<String, Object> capabilities = mapper.readValue(response.getBody(), HashMap.class);
            if (capabilities.containsKey("remaining_hits")) {
                return new TestConnectionResponse();
            } else {
                return new TestConnectionResponse(response.getBody(), "ERROR", List.of());
            }
        } catch (Exception e) {
            log.error("Similarweb Authentication failed. Error: {}", e.getMessage());
            return new TestConnectionResponse(e.getMessage(), ConnectorErrorCodes.CONNECTION_ERROR, List.of());
        }
    }

    public List<VisitMetric> trafficMetrics(ConnectorInfo config, String domain, String startYearMonth,
                                            String endYearMonth, String country,
                                            SimilarWebAPICategory category, SimilarWebAPIName apiName) {
        Map<String, String> parameters = Map.of("domain", domain, "start_date", startYearMonth,
                "end_date", endYearMonth, "country", country, "api_category", category.getApiCategory(), "api_name", apiName.getApiName(),
                "api_key", config.getAuthConfig().getToken()
        );
        return retrieveMetrics(config, parameters, StrSubstitutor.replace(trafficMetricURLTemplate, parameters), apiName.getMetricName());
    }

    public List<VisitMetric> retrieveMetrics(ConnectorInfo config,Map<String, String> parameters, String endpoint,
                                             String metricName) {
        String url = StrSubstitutor.replace(endpoint, parameters);
        try {
            //TODO: Use Cache
            List<VisitMetric> visitMetrics = new ArrayList<>();
            String response = getRawResponse(config.getId(),url);
            if(SimilarWebAPIName.DESKTOP_VISITS.getMetricName().equals(metricName)
                    || SimilarWebAPIName.MOBILE_VISITS.getMetricName().equals(metricName)){
                Double splitShare = extractDesktopOrMobileShareMetric(response, metricName);
                if(splitShare != null) {
                    visitMetrics.add(new VisitMetric(parameters.get("start_date"), splitShare));
                }
            } else {
                visitMetrics = extractVisitMetrics(response, metricName);
            }
            if (visitMetrics.isEmpty()) {
                log.warn("No data found in SimilarWeb at endpoint {} for parameters {} and metric {}", url, parameters, metricName);
            }
            return visitMetrics;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    public MetricRange getMetricRange(ConnectorInfo config, String domain, SimilarWebAPICategory category){
        // date range can't be obtained for
        if(SimilarWebAPICategory.TECHNOGRAPHICS.equals(category)){
            log.warn("Can't retrieve date range for SimilarWeb Category: {}", category.name());
            return null;
        }

        // date range is not available for some api categories but can use counterpart's date range
        if(category.equals(SimilarWebAPICategory.COUNTRY_RANK)){
            category = SimilarWebAPICategory.GLOBAL_RANK;
        } else if(category.equals(SimilarWebAPICategory.MOBILE_WEB_TRAFFIC)){
            category = SimilarWebAPICategory.DESKTOP_TRAFFIC;
        }

        Map<String, String> parameters = Map.of("domain", domain,"api_category", category.getApiCategory());
        String url = StrSubstitutor.replace(describeCategoryTemplate, parameters);
        try {
            log.info("Date Range Request: {}", url);
            String response = getRawResponse(config.getId(),url);
            log.info("Date Range Response: {}", response);
            return extractMetricRange(response, category.getApiCategory());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private MetricRange extractMetricRange(String response, String apiCategory) throws IOException {
        Map<String, Object> values = mapper.readValue(response, HashMap.class);
        // the response for describe contains the apiCategory string with "-" replaced by "_"
        String categoryValue = apiCategory.equals(SimilarWebAPICategory.LEAD_ENRICHEMNT.getApiCategory())
                ? "lead_enrichment_country_values" // special handling for LEAD_ENRICHMENT as response is different than other api categories
                : apiCategory.replace("-", "_");
        var startDate = EnrichUtil.findInResponseBody(String.format("response.%s.countries.%s.start_date", categoryValue, "world"), values);
        var endDate = EnrichUtil.findInResponseBody(String.format("response.%s.countries.%s.end_date", categoryValue, "world"), values);

        if(startDate == null || endDate == null){
            log.error("Unable to retrieve date range for metric {}", apiCategory);
            return null;
        }
        return new MetricRange(Objects.toString(startDate, ""), Objects.toString(endDate, ""));
    }

    public String leadEnrichment(ConnectorInfo config, String domain, String startYearMonth,
                                 String endYearMonth, String country,
                                 SimilarWebAPICategory category, SimilarWebAPIName apiName){
        Map<String, String> parameters = Map.of("domain", domain, "start_date", startYearMonth,
                "end_date", endYearMonth, "country", country, "api_category", category.getApiCategory(), "api_name", apiName.getApiName(),
                "api_key", config.getAuthConfig().getToken());
        String url = StrSubstitutor.replace(trafficMetricURLTemplate, parameters);
        try {
            String response = getRawResponse(config.getId(),url);
            return extractLeadEnrichemnt(response, apiName.getMetricName());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public String retrieveTechnographics(ConnectorInfo config, String domain,
                                         SimilarWebAPICategory category, SimilarWebAPIName apiName){
        Map<String, String> parameters = Map.of("domain", domain,"api_category", category.getApiCategory(),
                "api_name", apiName.getApiName(), "api_key", config.getAuthConfig().getToken());
        String url = StrSubstitutor.replace(technographicsURLTemplate, parameters);
        try {
            String response = getRawResponse(config.getId(),url);
            List<String> technologies = extractTechnographics(response, apiName.getMetricName());
            return String.join(", ", technologies);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    protected String getRawResponse(String serviceCredentialId, String url) {
        Optional<EnrichmentCache> trafficData = cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(serviceCredentialId, "trafficData", url);
        return trafficData.map(m->m.getEnrichValue()).orElseGet(()->{
            String body = restClient.getResponse(url, new AuthConfig()).getBody();
            cacheRepo.save(new EnrichmentCache().setEnrichKey(url).
                    setEntityName("trafficData").setServiceId(serviceCredentialId).setEnrichValue(body));
            return body;
        });
    }

    protected List<VisitMetric> extractVisitMetrics(String response, String metricName) throws IOException {
        Map<String, Object> capabilities = mapper.readValue(response, HashMap.class);
        Map<String, Object> meta = (Map<String, Object>) capabilities.get("meta");
        if (capabilities.containsKey(metricName) && "Success".equalsIgnoreCase(meta.getOrDefault("status","").toString())) {
            List<Map> visits = (List<Map>) capabilities.get(metricName);
            return visits.stream().map(v -> new VisitMetric(v.get("date").toString(), Double.parseDouble(v.get(metricName).toString()))).collect(Collectors.toList());
        }
        return List.of();
    }

    protected String extractLeadEnrichemnt(String response, String metricName) throws IOException {
        Map<String, Object> capabilities = mapper.readValue(response, HashMap.class);
        Map<String, Object> meta = (Map<String, Object>) capabilities.get("meta");
        if (capabilities.containsKey(metricName) && "Success".equalsIgnoreCase(meta.getOrDefault("status","").toString())) {
            return capabilities.getOrDefault(metricName, "").toString();
        }
        return null;
    }

    protected List<String> extractTechnographics(String response, String metricName) throws IOException {
        Map<String, Object> capabilities = mapper.readValue(response, HashMap.class);
        Map<String, Object> meta = (Map<String, Object>) capabilities.get("meta");
        if (capabilities.containsKey(metricName)) {
            List<Map> technographics = (List<Map>) capabilities.get(metricName);
            return technographics.stream()
                    .map(v -> v.getOrDefault("technology_name", "").toString())
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
        }
        return null;
    }

    protected Double extractDesktopOrMobileShareMetric(String response, String metricName) throws IOException {
        Map<String, Object> capabilities = mapper.readValue(response, HashMap.class);
        Map<String, Object> meta = (Map<String, Object>) capabilities.get("meta");
        if (capabilities.containsKey(metricName) && "Success".equalsIgnoreCase(meta.getOrDefault("status","").toString())) {
            var splitShare = capabilities.get(metricName);
            return splitShare == null ? null : Double.parseDouble(splitShare.toString());
        }
        return null;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        AuthField key = new AuthField();
        key.setDataType("password");
        key.setName("apiKey");
        key.setLabel("API Key");
        return List.of(new AuthMetadata(AuthType.ApiKey, List.of(key), "Api Key", ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.SIMILAR_WEB;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/similarweb.svg")
                .setDisplayName(DISPLAY_NAME)
                .setBackgroundColor("#f2fbff")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public String getCategory() {
        return "EnrichService";
    }

    @Override
    public ConnectorType getType() {
        return ConnectorType.Enrich;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public LookupData lookup(ConnectorInfo connector, SearchCriteria criteria) {
        return new LookupData();
    }

    @Override
    public EntitySchema describe(DescribeRequest request) {
        return new EntitySchema();
    }

    @Override
    public Map<String, String> getInputFields(ConnectorInfo connectorInfo, String entityName) {
        return Map.of();
    }

    @Override
    public Map<String, String> getOutputFields(ConnectorInfo connectorInfo, String entityName) {
        return Map.of();
    }
}