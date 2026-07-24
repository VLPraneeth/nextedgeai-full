package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.syncari.core.model.ComponentDependency;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.TestConfig;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.repositories.customer.ComponentDependencyRepo;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class ComponentDependancyServiceTest extends AbstractSyncariTest {
	@Autowired
	ComponentDependencyService service;
	@Autowired
	ComponentDependencyRepo repo;

	@Override
	public void setUp() {
		super.setUp();
	}

	@Override
	public void tearDown() {
		resetRepos(repo);
	}

	@Test
	public void addDependency() {
		try {
			service.addDependency(null, null, null, null);
			fail();
		} catch (Exception e) {
			assertEquals("fromId is required to add dependency", e.getMessage());
		}
		try {
			service.addDependency("123", null, null, null);
			fail();
		} catch (Exception e) {
			assertEquals("fromComponent is required to add dependency", e.getMessage());
		}
		try {
			service.addDependency("123", ComponentType.attribute, null, null);
			fail();
		} catch (Exception e) {
			assertEquals("toId is required to add dependency", e.getMessage());
		}
		try {
			service.addDependency("123", ComponentType.attribute, "123", null);
			fail();
		} catch (Exception e) {
			assertEquals("toComponent is required to add dependency", e.getMessage());
		}
		service.addDependency("123", ComponentType.attribute, "123", ComponentType.entity);
		
		assertEquals(1, service.findDependencies("123", ComponentType.entity, ComponentType.attribute).size());
	}

	@Test
	public void deleteDependencyFromPipeline(){
		service.addDependency("graphId", ComponentType.pipeline, "referenceId", ComponentType.referencedata);
		assertEquals(1, service.findDependenciesBy("graphId", ComponentType.pipeline).size());
		service.deleteDependenciesBy("graphId", ComponentType.pipeline);
		assertEquals(0, service.findDependenciesBy("graphId", ComponentType.pipeline).size());

	}

	@Test
	public void findDependencies(){
		service.addDependency("graphId", ComponentType.pipeline, "referenceId", ComponentType.referencedata);
		assertEquals(1, service.findDependenciesBy("graphId", ComponentType.pipeline).size());
		assertEquals(1, service.findDependenciesFor("referenceId", ComponentType.referencedata).size());
		service.deleteDependenciesOn("referenceId", ComponentType.referencedata);
		assertEquals(0, service.findDependenciesBy("graphId", ComponentType.pipeline).size());
		assertEquals(0, service.findDependenciesFor("referenceId", ComponentType.referencedata).size());

	}

	@Test
	public void updateDependenciesFor(){
		service.addDependency("dashboard1", ComponentType.dashboard, "datacard1", ComponentType.datacard);
		assertEquals(1, service.findDependenciesBy("dashboard1", ComponentType.dashboard).size());
		assertEquals(1, service.findDependenciesFor("datacard1", ComponentType.datacard).size());

		service.updateDependenciesFor("dashboard1", ComponentType.dashboard,
				List.of(new ComponentDependency("dashboard1", ComponentType.dashboard, "datacard2", ComponentType.datacard)));
		assertEquals(1, service.findDependenciesBy("dashboard1", ComponentType.dashboard).size());
		assertEquals(0, service.findDependenciesFor("datacard1", ComponentType.datacard).size()); // the earlier datacard1 dependency is deleted
		assertEquals(1, service.findDependenciesFor("datacard2", ComponentType.datacard).size()); // new datacard2 dependency is added

		service.updateDependenciesFor("dashboard1", ComponentType.dashboard,
				List.of(new ComponentDependency("dashboard1", ComponentType.dashboard, "datacard1", ComponentType.datacard),
						new ComponentDependency("dashboard1", ComponentType.dashboard, "datacard2", ComponentType.datacard)));
		assertEquals(2, service.findDependenciesBy("dashboard1", ComponentType.dashboard).size());
		assertEquals(1, service.findDependenciesFor("datacard1", ComponentType.datacard).size());
		assertEquals(1, service.findDependenciesFor("datacard2", ComponentType.datacard).size());
	}
	
}
