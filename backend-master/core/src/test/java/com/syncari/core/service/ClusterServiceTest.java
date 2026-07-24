package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.Cluster;
import com.syncari.core.repositories.syncari.ClusterRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClusterServiceTest extends AbstractSyncariTest {
	@Autowired
	ClusterService service;
	@Autowired
	ClusterRepo clusterRepo;

	@Override
	public void setUp() {
		super.setUp();
		Cluster c = new Cluster();
		c.setHasSyncariDb(true);
		c.setHost("localhost");
		c.setProvisionActive(false);
		Cluster c1 = new Cluster();
		c1.setHost("localhost");
		c1.setHasSyncariDb(false);
		c1.setProvisionActive(true);
		clusterRepo.save(c);
		clusterRepo.save(c1);
	}

	@Override
	public void tearDown() {
		service.invalidateCache();
		clusterRepo.deleteAll();
		super.tearDown();
	}

	@Test
	public void findById() {
		Cluster c2 = new Cluster();
		c2.setHasSyncariDb(false);
		c2.setHost("localhost");
		c2.setProvisionActive(true);
		c2 = clusterRepo.save(c2);
		assertTrue(service.findById(c2.getId()).isPresent());
	}

	@Test
	public void findActiveProvisioningCluster() {
		assertNotNull(service.findActiveProvisioningCluster());
	}
	
}
