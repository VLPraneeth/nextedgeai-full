package com.syncari.connector.service.def;

import java.util.Optional;

import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

public interface RestClientService {

    default SyncariEntityDataRestClient getRestClient () {
        return new SyncariEntityDataRestClient();
    }
    
    default SyncariEntityDataRestClient getRestClient (ProxyConfig proxy) {
        return new SyncariEntityDataRestClient(proxy);
    }
}
