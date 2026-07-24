package com.syncari.core.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.database.PostgresService;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.Cluster;
import com.syncari.core.repositories.syncari.ClusterRepo;

@Component
public class ClusterService {
    @Autowired
    ClusterRepo clusterRepo;
    @Autowired
    AppConfig appConfig;
    private static Map<String, Cluster> clusterMap = new ConcurrentHashMap<>();

    public Optional<Cluster> findById(String clusterId) {
        populate();
        return clusterMap.containsKey(clusterId) ? Optional.of(clusterMap.get(clusterId)) : Optional.empty();
    }
    
    public Cluster findActiveProvisioningCluster() {
    	// for now pick the first one
        populate();
    	List<Cluster> list = clusterMap.values().stream().filter(c -> c.isProvisionActive() && !c.isTrial()).collect(Collectors.toList());
        if(list.isEmpty()) {
            Optional<Cluster> first = clusterMap.values().stream().filter(c -> c.isHasSyncariDb()).findFirst();
            return first.isPresent() ? first.get() : null;
        }
		return list.get(0);
    }

    public Cluster findTrialProvisioningCluster() {
        populate();
        List<Cluster> list = clusterMap.values().stream().filter(c -> c.isProvisionActive() && c.isTrial()).collect(Collectors.toList());
        if(list.isEmpty()) {
            return findActiveProvisioningCluster();
        }
        return list.get(0);
    }

    private void populate() {
        if(clusterMap.isEmpty()) {
            clusterRepo.findAll().stream().forEach(cluster -> clusterMap.put(cluster.getId(), cluster));
        }
    }

    protected void invalidateCache() {
        clusterMap = new ConcurrentHashMap<>();
    }
}
