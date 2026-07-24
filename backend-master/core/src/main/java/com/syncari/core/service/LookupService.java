package com.syncari.core.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.exception.RetriableException;
import com.syncari.core.model.LookupData;
import com.syncari.core.model.SearchCriteria;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.Map;

public interface LookupService {

    /**
     * Lookup data by applying filter specified in search criteria
     */
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public LookupData lookup(ConnectorInfo connector, SearchCriteria criteria);

    /**
     * Describe the lookup entity (company, person etc)
     */
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public EntitySchema describe(DescribeRequest request);

    /**
     * Retrieves supported Input fields for a given Entity
     */
    public Map<String, String> getInputFields(ConnectorInfo connectorInfo, String entityName);

    /**
     * Retrieves supported Output fields for a given Entity
     */
    public Map<String, String> getOutputFields(ConnectorInfo connectorInfo, String entityName);

}
