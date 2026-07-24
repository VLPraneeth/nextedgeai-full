package com.syncari.core.enrich;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.syncari.connector.config.AuthConfig;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.utils.DateUtil;

import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;

@Slf4j
public abstract class AbstractEnrichmentService {

    private static final int CACHE_EXPIRY_DAYS = 30;
    private static final int WAIT_TIMEOUT_MILLIS = 5000;
    private static final List<String> SUPPORTED_ENTITIES = List.of("contact", "company");

    public static final String LOOKUP_ENTITY = "lookupEntity";
    public static final String LOOKUP_FIELD = "lookupField";

    @Autowired
    public EnrichmentCacheRepo cacheRepo;
    
    protected void validateInput(SearchCriteria criteria) {
        log.debug("Search Criteria: {}", criteria.toString());
        if(criteria == null) throw new RuntimeException("Search Criteria for lookup not defined");
        if(MapUtils.isEmpty(criteria.getMetaFilters())) throw new RuntimeException("No Filters provided in Search Criteria");
        if(!criteria.getMetaFilters().containsKey(LOOKUP_ENTITY)) throw new RuntimeException("Missing lookupEntity in Search filter");
        String lookupEntity = criteria.getMetaFilters().get(LOOKUP_ENTITY).toString();
        if(!getSupportedEntities().contains(lookupEntity))
            throw new RuntimeException(String.format("Lookup Entity %s not Supported by Enrichment Service.", lookupEntity));
        if(!criteria.getMetaFilters().containsKey(LOOKUP_FIELD)) throw new RuntimeException("Missing lookupField in Search filter");
        if(MapUtils.isEmpty(criteria.getSearchFieldNameValues())) throw new RuntimeException("Missing fields to lookup in SearchCriteria");
    }

    protected List<String> getSupportedEntities() {
        return SUPPORTED_ENTITIES;
    }

    protected HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + authConf.getAccessToken());
        headers.addAll(authHeaders);
        return headers;
    }

    protected Optional<EnrichmentCache> getCachedValue(String connectorId, String entityName, String key) {
        Optional<EnrichmentCache> cached = cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(connectorId, entityName, key);
        if(cached.isPresent() && cached.get().getCreatedAt() != null &&
                cached.get().getCreatedAt().before(DateUtil.subtractDaysFromToday(CACHE_EXPIRY_DAYS))) {
            cacheRepo.delete(cached.get());
            return Optional.empty();
        }
        return cached;
    }

    protected void saveToCache(String connectorId, String entityName, String key, String jsonBody) {
        EnrichmentCache toBeCached = new EnrichmentCache().setServiceId(connectorId).setEntityName(entityName)
            .setEnrichKey(key).setEnrichValue(jsonBody);
        try {
            cacheRepo.save(toBeCached);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    protected void validateResponse(ResponseEntity resp, String entityName, SearchCriteria criteria) {
        if (resp.getStatusCode() == HttpStatus.OK) {
            return;
        }
        String errorMsg = "Unable to Enrich %s for Search Criteria %s, %s, StatusCode: %s Response: %s";
        String reason = "Unknown";
        if (resp.getStatusCode() == HttpStatus.BAD_REQUEST) {
            reason = "Invalid query parameters.";
        }
        if (resp.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            reason = "Authorization failed.";
        }
        if (resp.getStatusCode() == HttpStatus.METHOD_NOT_ALLOWED) {
            reason = "Method not allowed.";
        }
        // We could choose to throttle here.
        if (resp.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            reason = "Too many requests.";
        }
        errorMsg = String.format(errorMsg, entityName, criteria.toString(), reason, resp.getStatusCode(), resp.getBody());
        log.error(errorMsg);
        throw new RuntimeException(errorMsg);
    }
}
