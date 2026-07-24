package com.syncari.viper.simulation;

import com.syncari.connector.service.def.*;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.LookupService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimulationDataServiceFactory extends DataServiceFactory {
    private final DataServiceFactory delegate;
    private Map<String, ReadOnlyDataService> dataServiceMap = new ConcurrentHashMap<>();
    private Map<String, ReadonlyMetadataService> metadataServiceMap = new ConcurrentHashMap<>();


    @Override
    public DataService getDataService(ConnectorMetadata metadata) {
        return dataServiceMap.computeIfAbsent(metadata.getId(), key -> new ReadOnlyDataService(delegate.getDataService(metadata)));
    }

    @Override
    public MetadataService getSchemaService(ConnectorMetadata metadata) {
        return metadataServiceMap.computeIfAbsent(metadata.getId(), key -> new ReadonlyMetadataService(delegate.getSchemaService(metadata)));
    }

    @Override
    public AuthenticationService getAuthenticationService(ConnectorMetadata metadata) {
        return delegate.getAuthenticationService(metadata);
    }

    @Override
    public OauthAuthenticationService getOauthAuthenticationService(ConnectorMetadata metadata) {
        return delegate.getOauthAuthenticationService(metadata);
    }

    @Override
    public SynapseInfoService getSynapseService(ConnectorMetadata metadata) {
        return delegate.getSynapseService(metadata);
    }

    @Override
    public LookupService getLookupService(ConnectorMetadata metadata) {
        return delegate.getLookupService(metadata);
    }

    public SimulationDataServiceFactory(DataServiceFactory delegate) {
        super(null);
        this.delegate = delegate;
    }

}

