package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

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
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.customer.TagRepo;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class TagServiceTest extends AbstractSyncariTest {
	@Autowired
	TagService service;
	@Autowired
	SchemaService schemaService;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	TagRepo tagRepo;

	@Override
	public void setUp() {
		super.setUp();
	}

	@Override
	public void tearDown() {
		resetRepos(tagRepo);
	}

	@Test
	public void assignValidations() {
		try {
			service.assign(Map.of(), null, null);
			fail();
		} catch (Exception e) {
			assertEquals("Tagged id is required", e.getMessage());
		}
		try {
			service.assign(Map.of(), null, "123");
			fail();
		} catch (Exception e) {
			assertEquals("Taggable is required", e.getMessage());
		}
		EntityDefinition entity = schemaService.getEntities(connectorService.getSyncariConnector().getId()).get(0);
		service.assign(Map.of(), Taggable.entity, entity.getId());
		assertEquals(0, service.findTagsFor(Taggable.entity, entity.getId()).size());
		
		assertFalse(service.hasTag("pii", Taggable.entity, entity.getId()));
	}
	
	@Test
	public void assignTags() {
		EntityDefinition entity = schemaService.getEntities(connectorService.getSyncariConnector().getId()).get(0);
		service.assign(Map.of("pii", true), Taggable.entity, entity.getId());
		
		assertTrue(service.hasTag("pii", Taggable.entity, entity.getId()));
	}

	@Test
	public void removeValidations() {
		try {
			service.remove(null, null, null);
			fail();
		} catch (Exception e) {
			assertEquals("Tagged id is required", e.getMessage());
		}
		try {
			service.remove("", null, "123");
			fail();
		} catch (Exception e) {
			assertEquals("Taggable is required", e.getMessage());
		}
		EntityDefinition entity = schemaService.getEntities(connectorService.getSyncariConnector().getId()).get(0);
		service.remove(null, Taggable.entity, entity.getId());
		assertEquals(0, service.findTagsFor(Taggable.entity, entity.getId()).size());
	}
	
	@Test
	public void remove() {
		EntityDefinition entity = schemaService.getEntities(connectorService.getSyncariConnector().getId()).get(0);
		service.assign(Map.of("pii", true), Taggable.entity, entity.getId());
		assertTrue(service.hasTag("pii", Taggable.entity, entity.getId()));
		service.remove("pii", Taggable.entity, entity.getId());
		assertFalse(service.hasTag("pii", Taggable.entity, entity.getId()));
	}
	
	@Test
	public void removeAllValidations() {
		try {
			service.removeTagsFor(null, null);
			fail();
		} catch (Exception e) {
			assertEquals("Tagged id is required", e.getMessage());
		}
		try {
			service.removeTagsFor(null, "123");
			fail();
		} catch (Exception e) {
			assertEquals("Taggable is required", e.getMessage());
		}
	}
	
	@Test
	public void removeAll() {
		EntityDefinition entity = schemaService.getEntities(connectorService.getSyncariConnector().getId()).get(0);
		service.assign(Map.of("pii", true), Taggable.entity, entity.getId());
		service.assign(Map.of("dept", "eng"), Taggable.entity, entity.getId());
		assertTrue(service.hasTag("pii", Taggable.entity, entity.getId()));
		assertEquals(2, service.findTagsFor(Taggable.entity, entity.getId()).size());
		
		service.removeTagsFor(Taggable.entity, entity.getId());
		assertFalse(service.hasTag("pii", Taggable.entity, entity.getId()));
		assertEquals(0, service.findTagsFor(Taggable.entity, entity.getId()).size());
	}
	
	@Test
	public void findTagsLike() {
		EntityDefinition entity = schemaService.getEntities(connectorService.getSyncariConnector().getId()).get(0);
		service.assign(Map.of("pii", true), Taggable.entity, entity.getId());
		service.assign(Map.of("dept", "eng"), Taggable.entity, entity.getId());
		assertTrue(service.hasTag("pii", Taggable.entity, entity.getId()));
		assertEquals(0, service.findTagsLike("").size());
		assertEquals(2, service.findTagsLike("p").size());
		assertEquals(1, service.findTagsLike("i").size());
		assertEquals(1, service.findTagsLike("d").size());
		assertEquals(1, service.findTagsLike("t").size());
		assertEquals(0, service.findTagsLike("z").size());
		assertEquals(0, service.findTagsLike(null).size());
	}
}
