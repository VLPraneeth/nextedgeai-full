package com.syncari.core.enrich;

import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.core.model.Connector;
import com.syncari.core.service.LookupService;

import java.util.Map;

public interface EnrichmentService extends LookupService, AuthenticationService {

    /**
     * Validates a service
     */
    public void validate(Connector connector);

}
