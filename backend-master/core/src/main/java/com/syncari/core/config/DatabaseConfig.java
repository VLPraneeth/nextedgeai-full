package com.syncari.core.config;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;
import com.syncari.core.model.Cluster;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Resource;
import com.syncari.core.model.ResourceType;
import com.syncari.core.service.ClusterService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatabaseConfig {
	private static final String CLUSTER_ID = "clusterId";
	@Autowired
    ClusterService clusterService;

	public void setDbConfig(Instance instance) {
		Optional<Resource> resource = instance.getResource(ResourceType.DATABASE);
		Preconditions.checkArgument(resource.isPresent(), "Missing Database Resource for " + instance);
		Cluster active = instance.isTrial() ? clusterService.findTrialProvisioningCluster() : clusterService.findActiveProvisioningCluster();
		if(active != null) {
			resource.get().getConfiguration().put(CLUSTER_ID, active.getId());
			log.info("Successfully set db config for {}", instance.getSyncariId());
		}
	}
}
