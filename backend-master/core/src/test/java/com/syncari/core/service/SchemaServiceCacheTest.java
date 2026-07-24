package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.*;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.util.Optional;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@Ignore
public class SchemaServiceCacheTest extends AbstractSyncariTest {

/*	@Autowired
	SchemaService service;

	@MockBean
	@Qualifier("entityDefinitionRepo")
	EntityDefinitionRepo entityDefinitionRepo;

	@Before
	public void setUp() {
		super.setUp();
	}

	@After
	public void tearDown() {
	}

	@Test
	@Ignore
	public void testEntityDefinition() {
		String entityDefId = ObjectId.get().toHexString();
		EntityDefinition entityDefinition = new EntityDefinition("testEntity", "Test Entity");

		// doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(Optional.of(entityDefinition)).when(entityDefinitionRepo).findById(entityDefId);

		service.findEntity(entityDefId);
		var entityDef = service.findEntity(entityDefId);
		assertTrue(entityDef.isPresent());

		assertEquals("testEntity", entityDef.get().getApiName());
		assertEquals("Test Entity", entityDef.get().getDisplayName());

		verify(entityDefinitionRepo, times(1)).findById(entityDefId);
	}*/

}
