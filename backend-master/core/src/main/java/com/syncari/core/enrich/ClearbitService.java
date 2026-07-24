package com.syncari.core.enrich;

import com.clearbit.client.api.CombinedApi;
import com.clearbit.client.api.CompanyApi;
import com.clearbit.client.model.Company;
import com.clearbit.client.model.PersonCompany;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.core.config.AppConfig;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.model.ServiceCredential;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.core.service.ProvisioningService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Slf4j
@Service
public class ClearbitService {
    @Autowired
    AppConfig appConfig;
    @Autowired
    ProvisioningService provService;
    @Autowired
    EnrichmentCacheRepo cacheRepo;
    private static final int CACHE_EXPIRY_DAYS = 30;

    private static final String GET_COMPANY_BY_IP = "https://reveal.clearbit.com/v1/companies/find?ip=%s";
    private static final int READ_TIMEOUT = 5000;

    ObjectMapper mapper = new ObjectMapper();

    public Object lookUpLead(String email, String fieldName, String serviceId) {
        if (StringUtils.isBlank(email))
            return Map.of();
            CombinedApi api = new CombinedApi();
        try {
            String jsonBody = "";
            Optional<EnrichmentCache> cached = getCachedValue(serviceId, email, "lead");
            if (cached.isPresent()) {
                jsonBody = cached.get().getEnrichValue();
            } else {
                String apiKey = getApiKey(serviceId);
                api.getApiClient().setUsername(apiKey);
                PersonCompany personCompany = api.streamingLookup(email);
                if (personCompany == null || personCompany.getPerson() == null)
                    return null;
                jsonBody = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(personCompany);
                saveToCache(email, serviceId, jsonBody, "lead");
            }
            return find(fieldName, mapper.readValue(jsonBody, Map.class));
        } catch (Exception e) {
            log.error(e.getMessage());
            log.debug(e.getMessage(), e);
            // TODO: Handle API Errors and convert into Retriable and NonRetriables
            throw new RuntimeException(e);
        }
    }

    public Object lookUpCompany(String domain, String fieldName, String serviceId) {
        if (StringUtils.isBlank(domain))
            return Map.of();
        CompanyApi api = new CompanyApi();
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonBody = "";
            Optional<EnrichmentCache> cached = getCachedValue(serviceId, domain, "company");
            if (cached.isPresent()) {
                jsonBody = cached.get().getEnrichValue();
            } else {
                String apiKey = getApiKey(serviceId);
                api.getApiClient().setUsername(apiKey);
                Company company = api.lookup(domain);
                if (company == null)
                    return null;
                jsonBody = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(company);
                saveToCache(domain, serviceId, jsonBody, "company");
            }
            Map companyMap = mapper.readValue(jsonBody, Map.class);
            return find(fieldName, companyMap);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.debug(e.getMessage(), e);
            // TODO: Handle API Errors and convert into Retriable and NonRetriables
            throw new RuntimeException(e);
        }
    }

    public Object lookUpCompanyByIPAddress(String ipAddress, String fieldName, String serviceId) {
        if (StringUtils.isBlank(ipAddress))
            return null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonBody = "";
            // check in cache
            Optional<EnrichmentCache> cached = getCachedValue(serviceId, ipAddress, "company");
            if (cached.isPresent()) {
                jsonBody = cached.get().getEnrichValue();
            }else {

                RestTemplate restTemplate = getTemplate();
                String url = String.format(GET_COMPANY_BY_IP, ipAddress);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(getHeaders(getApiKey(serviceId))),
                        String.class);

                // validate response
                checkResponse(response);
                jsonBody = response.getBody();
            }
            Map responseMap = mapper.readValue(jsonBody, Map.class);
            // check if company data is retrieved
            Map company = (Map) responseMap.get("company");
            if(company == null || company.isEmpty()){
                return null;
            }

            // put in cache
            saveToCache(ipAddress, serviceId, jsonBody, "company");

            return find(fieldName, company);
        }catch (Exception e) {
            log.error(e.getMessage());
            log.debug(e.getMessage(), e);
            // TODO: Handle API Errors and convert into Retriable and NonRetriables
            throw new RuntimeException(e);
        }
    }

    private void saveToCache(String key, String serviceId, String jsonBody, String entityName) {
        EnrichmentCache toBeCached = new EnrichmentCache().setServiceId(serviceId).setEntityName(entityName)
                .setEnrichKey(key).setEnrichValue(jsonBody);
        try {
            cacheRepo.save(toBeCached);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private String getApiKey(String serviceId) {
        // TODO remove the default
        String apiKey = appConfig.getClearbitApiKey();
        if (!StringUtils.isBlank(serviceId)) {
            Optional<ServiceCredential> credentials = provService.getCredentials(serviceId);
            if (credentials.isPresent() && !StringUtils.isBlank(credentials.get().getApiKey())) {
                apiKey = credentials.get().getApiKey();
            }
        }
        return apiKey;
    }

    private Object find(String fieldName, Map map) {
        if (map == null)
            return null;
        String[] fieldParts = fieldName.split("\\.");
        var currentMap = map;
        for (int i = 0; i < fieldParts.length; i++) {
            if (currentMap.get(fieldParts[i]) == null)
                return null;
            if (Map.class.isAssignableFrom(currentMap.get(fieldParts[i]).getClass())) {
                currentMap = (Map) currentMap.get(fieldParts[i]);
            } else {
                return currentMap.get(fieldParts[i]);
            }
        }
        return null;
    }

    private Optional<EnrichmentCache> getCachedValue(String serviceId, String key, String entityName) {
        Optional<EnrichmentCache> cached = cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(serviceId, entityName,
                key);
        if(cached.isPresent() && cached.get().getCreatedAt() != null && cached.get().getCreatedAt().before(DateUtil.subtractDaysFromToday(CACHE_EXPIRY_DAYS))) {
            cacheRepo.delete(cached.get());
            return Optional.empty();
        }
        return cached;
    }

    private RestTemplate getTemplate() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(READ_TIMEOUT);
        clientHttpRequestFactory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(clientHttpRequestFactory);
    }

    public HttpHeaders getHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    public void checkResponse(ResponseEntity<String> response) {
        if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.NO_CONTENT) {
            return;
        }
        log.info("Got response code: " + response.getStatusCode().name());
        List<HttpStatus> retriable = List.of(HttpStatus.GATEWAY_TIMEOUT, HttpStatus.SERVICE_UNAVAILABLE,
                HttpStatus.TOO_MANY_REQUESTS, HttpStatus.REQUEST_TIMEOUT, HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);
        if (retriable.contains(response.getStatusCode())) {
            throw new RetriableException(response.getStatusCode().name(), response.getBody(),
                    String.valueOf(response.getStatusCode()));
        }
        throw new NonRetriableException(response.getStatusCode().name(), response.getBody(),
                String.valueOf(response.getStatusCode()));
    }

    public Map<String, String> getInputFields(String entity){
        switch (entity){
            case "contact":
                return Map.of("email", "Email");

            case "company":
                return Map.of("domain", "Domain", "ip", "IP Address");

            default:
                return Map.of();
        }
    }

    public Map<String, String> getOutputFields(String entity){
        switch (entity){
            case "contact":
                return getLeadFields();

            case "company":
                return getCompanyFields();

            default:
                return Map.of();
        }
    }

    private Map<String, String> getLeadFields() {
        Map<String, String> fields = new TreeMap<>();
        fields.put("person.id", "Id");
        fields.put("person.name.fullName", "Full Name");
        fields.put("person.name.givenName", "Given Name");
        fields.put("person.name.familyName", "Family Name");
        fields.put("person.email", "Email");
        fields.put("person.gender", "Gender");
        fields.put("person.location", "Location");
        fields.put("person.timeZone", "TimeZone");
        fields.put("person.utcOffset", "Utc Offset");
        fields.put("person.geo.city", "City");
        fields.put("person.geo.state", "State");
        fields.put("person.geo.stateCode", "State Code");
        fields.put("person.geo.country", "Country");
        fields.put("person.geo.countryCode", "Country Code");
        fields.put("person.geo.lat", "Latitude");
        fields.put("person.geo.lng", "Longitude");
        fields.put("person.bio", "Bio");
        fields.put("person.site", "Site");
        fields.put("person.avatar", "Avatar");
        fields.put("person.employment.name", "Employment Name");
        fields.put("person.employment.title", "Employment Title");
        fields.put("person.employment.domain", "Employment Domain");
        fields.put("person.employment.role", "Employment Role");
        fields.put("person.employment.seniority", "Employment Seniority");
        fields.put("person.facebook.handle", "Facebook Handle");
        fields.put("person.github.handle", "Github Handle");
        fields.put("person.github.id", "Github Id");
        fields.put("person.github.avatar", "Github Avatar");
        fields.put("person.github.company", "Github Company");
        fields.put("person.github.blog", "Github Blog");
        fields.put("person.github.followers", "Github Followers");
        fields.put("person.github.following", "Github Following");
        fields.put("person.twitter.handle", "Twitter Handle");
        fields.put("person.twitter.id", "Twitter Id");
        fields.put("person.twitter.bio", "Twitter Bio");
        fields.put("person.twitter.followers", "Twitter Followers");
        fields.put("person.twitter.following", "Twitter Following");
        fields.put("person.twitter.statuses", "Twitter Statuses");
        fields.put("person.twitter.favorites", "Twitter Favorites");
        fields.put("person.twitter.location", "Twitter Location");
        fields.put("person.twitter.site", "Twitter Site");
        fields.put("person.twitter.avatar", "Twitter Avatar");
        fields.put("person.linkedin.handle", "Linkedin Handle");
        fields.put("person.aboutme", "Aboutme");
        fields.put("company.id", "Company Id");
        fields.put("company.name", "Company Name");
        fields.put("company.legalName", "Company Legal Name");
        fields.put("company.domain", "Company Domain");
        fields.put("company.domainAliases", "Company Domain Aliases");
        fields.put("company.logo", "Company Logo");
        fields.put("company.site.phoneNumbers", "Company Phone Numbers");
        fields.put("company.site.emailAddresses", "Company Email Addresses");
        fields.put("company.category.sector", "Company Sector");
        fields.put("company.category.industryGroup", "Company Industry Group");
        fields.put("company.category.industry", "Company Industry");
        fields.put("company.category.subIndustry", "Company Sub Industry");
        fields.put("company.description", "Company Description");
        fields.put("company.foundedYear", "Company Founded Year");
        fields.put("company.location", "Company Location");
        fields.put("company.timeZone", "Company TimeZone");
        fields.put("company.geo.postalCode", "Company Postal Code");
        fields.put("company.geo.streetNumber", "Company Street Number");
        fields.put("company.geo.streetName", "Company Street Name");
        fields.put("company.geo.city", "Company City");
        fields.put("company.geo.state", "Company State");
        fields.put("company.geo.stateCode", "Company State Code");
        fields.put("company.geo.country", "Company Country");
        fields.put("company.geo.countryCode", "Company Country Code");
        return fields;
    }

    private Map<String, String> getCompanyFields() {
        Map<String, String> fields = new TreeMap<>();
        fields.put("id", "Id");
        fields.put("name", "Name");
        fields.put("legalName", "LegalName");
        fields.put("domain", "Domain");
        fields.put("domainAliases", "DomainAliases");
        fields.put("logo", "Logo");
        fields.put("site.title", "Title");
        fields.put("site.phoneNumbers", "Phone Numbers");
        fields.put("site.emailAddresses", "Email Addresses");
        fields.put("tags", "tags");
        fields.put("category.sector", "Sector");
        fields.put("category.industryGroup", "IndustryGroup");
        fields.put("category.industry", "Industry");
        fields.put("category.subIndustry", "SubIndustry");
        fields.put("description", "Description");
        fields.put("foundedYear", "FoundedYear");
        fields.put("location", "Location");
        fields.put("timeZone", "TimeZone");
        fields.put("utcOffset", "UtcOffset");
        fields.put("geo.streetNumber", "StreetNumber");
        fields.put("geo.streetName", "StreetName");
        fields.put("geo.city", "City");
        fields.put("geo.state", "State");
        fields.put("geo.stateCode", "StateCode");
        fields.put("geo.postalCode", "PostalCode");
        fields.put("geo.country", "Country");
        fields.put("geo.countryCode", "CountryCode");
        fields.put("metrics.alexaUsRank", "Alexa US Rank");
        fields.put("metrics.alexaGlobalRank", "Alexa Global Rank");
        fields.put("metrics.employees", "Employees");
        fields.put("metrics.employeesRange", "Employees Range");
        fields.put("metrics.marketCap", "MarketCap");
        fields.put("metrics.raised", "Raised");
        fields.put("metrics.annualRevenue", "Annual Revenue");
        fields.put("metrics.fiscalYearEnd", "Fiscal Year End");
        fields.put("metrics.estimatedAnnualRevenue", "Estimated Annual Revenue");
        fields.put("facebook.handle", "Facebook Handle");
        fields.put("linkedin.handle", "Linkedin handle");
        fields.put("twitter.handle", "Twitter handle");
        fields.put("twitter.id", "Twitter Id");
        fields.put("twitter.bio", "Twitter Bio");
        fields.put("twitter.followers", "Twitter Followers");
        fields.put("twitter.following", "Twitter Following");
        fields.put("twitter.location", "Twitter Location");
        fields.put("twitter.site", "Twitter Site");
        fields.put("twitter.avatar", "Twitter Avatar");
        fields.put("crunchbase.handle", "Crunchbase Handle");
        fields.put("emailProvider", "Email Provider");
        fields.put("type", "Type");
        fields.put("ticker", "Ticker");
        fields.put("phone", "Phone");
        fields.put("indexedAt", "Indexed At");
        fields.put("tech", "Tech");
        fields.put("parent.domain", "Parent Domain");
        return fields;
    }
}
