package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.TestSynapseService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.store.FieldDefinition;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.PipelineStatus;
import com.syncari.core.schema.Schema;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SchemaServiceTest extends AbstractSyncariTest {

	@Autowired
	SchemaService service;
	@Mock
	SchemaService mockSchemaService;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	ConnectorRepo connectorRepo;
	@Autowired
	SyncDetailRepo syncRepo;
	Connector connector;
	@Autowired
	EntityDefinitionRepo entityDefinitionCache;
	@Autowired
	AttributeRepo attributeProxyRepo;
	@Mock
	DataServiceFactory factory;
	@Mock
	MetadataService metaService;
	@Autowired
	DataTransformer transformer;
    @Autowired
    EndSystemConfig config;
	@Autowired
	MappingGraphService mappingGraphService;
	@Mock
	MappingGraphService mapGraph;

	@Autowired
	DataServiceFactory dataServiceFactory;
	@Autowired
	MappingGraphRepo mappingGraphRepo;
	@Autowired
	StreamRepo streamRepo;
	@Autowired
	UnresolvedReferenceRepo unresolvedReferenceRepo;
	@Autowired
	TagService tagService;
	Connector activatedConnector;
	@Autowired
	MappingNodeRepo nodeRepo;
	@Autowired
	EdgeRepo edgeRepo;
	@Autowired
	TestSynapseService testSynapseService;

	@Before
	public void setUp() {
		super.setUp();
		connectorService.publisher = publisher;
//		when(mockMappingGraphService.initializeEntityGraph(any(), any())).thenReturn(null);
//		service.setMappingGraphService(mockMappingGraphService);
        connectorService.setSchemaService(service);
        connector = new Connector("test", connectorService.describe("salesforce").getId(), "http://test.salesforce.com");
        connector = connectorService.save(connector);
        connector = connectorService.find(connector.getId()).get();
	}

	@After
	public void tearDown() {
		service.setMappingGraphService(mappingGraphService);
		service.connectorService = connectorService;
		service.factory = dataServiceFactory;
		service.entityProxyRepo = entityDefinitionCache;
		service.attributeProxyRepo = attributeProxyRepo;
		connectorService.setSchemaService(service);
		//resetRepos(syncRepo, entityDefinitionCache, attributeRepo, connectorRepo, streamRepo);
		//super.tearDown();
	}

	@Test
	public void refreshSchemaUpdatesDataTypesWhereNecessary() {
		ConnectorService mockConnectorService = mock(ConnectorService.class);
		MetadataService mockDataService = mock(MetadataService.class);
		DataServiceFactory mockDataServiceFactor = mock(DataServiceFactory.class);
		SynapseInfoService mockSynapseInfoService = mock(SynapseInfoService.class);
		EntitySchema entitySchema = new EntitySchema();
		entitySchema.setApiName("account");
		entitySchema.setCustom(false);
		entitySchema.setDisplayName("Account");
		AttributeSchema attribute = new AttributeSchema();
		attribute.setApiName("HasOptedOutOfEmail");
		attribute.setDisplayName("Email Opt Out");
		attribute.setDataType("string");
		entitySchema.setAttributes(List.of(attribute));
		Connector connector = new Connector();
		connector.setMetadata(new ConnectorMetadata());

		when(mockConnectorService.find("salesforceConnector")).thenReturn(Optional.of(connector));
		when(mockDataServiceFactor.getSchemaService(any())).thenReturn(mockDataService);
		when(mockDataServiceFactor.getSynapseService(any())).thenReturn(mockSynapseInfoService);
		when(mockDataService.describeAll(any())).thenReturn(List.of(entitySchema));

		service.connectorService = mockConnectorService;
		service.factory =mockDataServiceFactor;

		List<EntityDefinition> entities = service.refreshSynapseSchema("salesforceConnector");
		assertEquals(1, entities.size());
		assertEquals(1, entities.get(0).getAttributes().size());
		assertEquals(StringType.VALUE, entities.get(0).getAttributes().get(0).getDataType());
		verify(mockConnectorService).find("salesforceConnector");
		verify(mockDataService).describeAll(any());
		verify(mockDataServiceFactor).getSchemaService(any());
		//Now change data type to boolean
		attribute.setDataType("boolean");
		//Also change entity pluralname.
		entitySchema.setPluralName("accounts");
		reset(mockConnectorService, mockDataService, mockDataServiceFactor);

		when(mockConnectorService.find("salesforceConnector")).thenReturn(Optional.of(connector));
		when(mockDataServiceFactor.getSchemaService(any())).thenReturn(mockDataService);
		when(mockDataServiceFactor.getSynapseService(any())).thenReturn(mockSynapseInfoService);
		when(mockDataService.describeAll(any())).thenReturn(List.of(entitySchema));


		entities = service.refreshSynapseSchema("salesforceConnector");
		assertEquals(1, entities.size());
		assertEquals("accounts", entities.get(0).getPluralName());
		assertEquals(1, entities.get(0).getAttributes().size());
		assertEquals(BooleanType.VALUE, entities.get(0).getAttributes().get(0).getDataType());

		verify(mockConnectorService).find("salesforceConnector");
		verify(mockDataService).describeAll(any());
		verify(mockDataServiceFactor).getSchemaService(any());
	}

	@Test
	public void toApiName() {
		assertEquals("syncari_postgres_post_synap_acc_id", service.toApiName("syncari_postgres_post synap_acc id"));
	}

	@Test
	public void createEntityLikeTest(){
		Connector localConnector = getConnector();
		Schema oldSchema = service.getSchemaFor(localConnector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "Contact");
		e.setDisplayName("Contact");

		EntityDef ac = new EntityDef("4567", "Account");
		ac.setDisplayName("Account");
		entities.add(ac);

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(localConnector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);
		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("contact")) {
				AttributeSchema attributeDef = new AttributeSchema("new_added_field", "reference");
				attributeDef.setId("new_added_field");
				attributeDef.setDataType("reference");
				attributeDef.setDisplayName("new_field");
				attributeDef.setReferenceTo("account");
				e2.addField(attributeDef);
			}
		});

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		String entityNameA = "account";
		List<AttributeDefinition> attributesAcc = service.getActiveAttributes(localConnector.getId(), entityNameA);
		EntityDefinition entityDefinitionAcc = new EntityDefinition(entityNameA,entityNameA);
		entityDefinitionAcc.setAttributes(attributesAcc);
		entityDefinitionAcc.setConnectorId(localConnector.getId());
		entityDefinitionCache.save(entityDefinitionAcc);
		attributeProxyRepo.saveAll(attributesAcc);
		EntityDefinition syncariEntityAcc = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc);
		assertTrue(syncariEntityAcc.getWatermarkField().isPresent());
		assertFalse(syncariEntityAcc.getWatermarkField().get().isNillable());
		assertEquals("account__c", syncariEntityAcc.getDataStoreName());

		// assert reference field length is more than 24
		syncariEntityAcc.getAttributes().stream().filter(a -> a.isReference()).forEach(r -> assertTrue(r.getLength() >= 24));

		EntitySchema contactSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		EntityDefinition contactEntity = service.getEntity(localConnector.getId(),"contact");
		doReturn(Optional.of(contactSchema)).when(metaService).describe(any());

		service.refreshSynapseSchema(localConnector.getId(), contactEntity, localConnector.getId());
		Schema newSchema = service.getSchemaFor(localConnector.getId());
		EntityDef oldAcc = oldSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		EntityDef newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		assertEquals(oldAcc.getFields().size()+1, newAcc.getFields().size());
		assertTrue(newAcc.getFields().stream().anyMatch(f -> "new_added_field".equals(f.getApiName())));

		String entityName = "contact";
		List<AttributeDefinition> attributes = service.getActiveAttributes(localConnector.getId(), entityName);
		EntityDefinition entityDefinition = new EntityDefinition(entityName,entityName);
		entityDefinition.setAttributes(attributes);
		entityDefinition.setConnectorId(localConnector.getId());
		EntityDefinition syncariEnt = service.createEntityLike(entityDefinition,null);
		assertNotNull(syncariEnt);
		assertTrue(syncariEnt.getWatermarkField().isPresent());
		assertFalse(syncariEnt.getWatermarkField().get().isNillable());

		Optional<AttributeDefinition> def = syncariEnt.getField("new_added_field");
		def.ifPresent( entdef -> {
			assertEquals(entdef.getDataType().getName(),"reference");
			assertEquals(entdef.getReferenceTo(),"account");
			assertEquals(entdef.getReferenceTargetField(),"id");
		});
	}

	@Test
	public void testSanitizedFieldName(){
		assertEquals("apiName", service.sanitizedFieldName("_apiName_"));
		assertEquals("apiName", service.sanitizedFieldName("-apiName-"));
		assertEquals("apiName", service.sanitizedFieldName("-apiName_"));
		assertEquals("apiName", service.sanitizedFieldName("--apiName__"));
		assertEquals("api_name", service.sanitizedFieldName("_api_name_"));
		assertEquals("api-name", service.sanitizedFieldName("_api-name_"));
		assertEquals("api_name", service.sanitizedFieldName("api.name_"));
	}

	@Test
	public void testReferringEntities(){
		Connector syncariConnector = connectorService.getSyncariConnector();

		EntityDefinition account = new EntityDefinition("account_c", "Account_c")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId()).setStatus(Status.ACTIVE);
		account = service.createDraftEntity(account, false);
		AttributeDefinition idField = account.getAttributes().stream().filter(a -> a.isIdField()).findFirst().get();
		idField.setApiName("account_id");
		attributeProxyRepo.save(idField);
		service.approveDraftEntity(account);

		EntityDefinition contact = new EntityDefinition("contact_c", "Contact_c")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId()).setStatus(Status.ACTIVE);
		contact = service.createDraftEntity(contact, false);

		AttributeDefinition referenceField = new AttributeDefinition().setEntityId(contact.getId())
				.setDataType(ReferenceType.VALUE).setApiName("account").setDisplayName("Account")
				.setReferenceTo("account_c").setReferenceTargetField("account_id").setStatus(Status.ACTIVE);

		var reference = service.createDraftAttribute(contact.getId(), referenceField);
		service.approveDraftEntity(contact);
		service.approveDraftAttributeList(List.of(reference), contact.getId(), true);

		var references = service.getReferringAttributes(account);
		assertEquals(1, references.size());

		assertEquals(contact.getId(), references.get(0).getFromEntity().getId());
		assertEquals("account", references.get(0).getFromAttribute().getApiName());
		assertEquals(account.getId(), references.get(0).getToEntity().getId());
		assertEquals("account_id", references.get(0).getToAttribute().getApiName());
		assertEquals(idField.getId(), references.get(0).getToAttribute().getId());
	}

	@Test
	public void createEntityFor() {
		Connector localConnector = getConnector();
		EntityDefinition entity = service.getEntity(service.getSyncariSchema().getEntities().get(0).getId());
		String apiname = entity.getAttributes().get(0).getApiName();
		entity.getAttributes().get(0).setUpdatable(false);
		EntityDefinition newEntity = service.createEntityFor(entity, localConnector, DraftStatus.NEW);
		assertTrue(newEntity.getFieldByName(apiname).isUpdatable());
		assertFalse(newEntity.getFieldByName(apiname).isSystem());
	}

	@Test
	public void createEntityForSpeicalCharApiName() {
		Connector localConnector = getConnector();
		EntityDefinition entity = SchemaHelper.createEntityDefinition("testEntity")
				.string("nam|e").string("_nam|e_").string("-nam|e-").id().getEntityDefinition();

		EntityDefinition newEntity = service.createEntityFor(entity, localConnector, DraftStatus.APPROVED);
		assertTrue(newEntity.hasField("name"));
		assertTrue(newEntity.hasField("name__c"));
		assertTrue(newEntity.hasField("name__c1"));
		assertEquals("nam|e", newEntity.getField("name").get().getDisplayName());
		assertEquals("_nam|e_", newEntity.getField("name__c").get().getDisplayName());
		assertEquals("-nam|e-", newEntity.getField("name__c1").get().getDisplayName());
	}

	@Test
	public void testCopyFields(){
		Connector syncariConnector = getConnector();
		EntityDefinition ent = new EntityDefinition("testEntity", "testEntity").setConnectorId(syncariConnector.getId()).setStatus(Status.ACTIVE);
		ent.setDraftStatus(DraftStatus.APPROVED);
		ent = entityDefinitionCache.save(ent);
		AttributeDefinition idField = new AttributeDefinition().setApiName("tst").setDisplayName("tst").setDataType(new StringType())
				.setEntityId(ent.getId()).setStatus(Status.ACTIVE).setIdField(true).setDataStoreName("tst");
		AttributeDefinition wmField = new AttributeDefinition().setApiName("tst1").setDisplayName("tst1").setDataType(new StringType())
				.setEntityId(ent.getId()).setStatus(Status.ACTIVE).setWatermarkField(true).setDataStoreName("tst1");
		idField.setDraftStatus(DraftStatus.APPROVED);
		idField = attributeProxyRepo.save(idField);
		wmField.setDraftStatus(DraftStatus.APPROVED);
		wmField = attributeProxyRepo.save(wmField);
		ent.setAttributes(List.of(idField, wmField));
		entityDefinitionCache.save(ent);
		String name = "test1";
		String desc = "test run";
		Set<String> tags = Set.of("t1", "t2");
		EntityDef newEntityDef = new EntityDef(name, name, name, desc, tags);

		EntityDef res = service.copyFields(ent.getId(), newEntityDef);
		Set<String> srcFields = new HashSet<>();
		Set<String> newFields = new HashSet<>();
		for (AttributeDef field: newEntityDef.getFields()){
			newFields.add(field.getApiName());
		}
		for (AttributeDefinition field: ent.getAttributes()){
			srcFields.add(field.getApiName());
		}
        assertEquals(res.getApiName(), name);
		assertEquals(res.getDisplayName(), name);
		assertEquals(res.getDataStoreName(), name);
		assertEquals(res.getDescription(), desc);
		assertEquals(res.getTags(), tags);
        assertEquals(srcFields, newFields);
	}

	@Test
	public void createEntityFor_WithPotentialDuplicateFieldNames() {
		Connector localConnector = getConnector();
		EntityDefinition entity = SchemaHelper.createEntityDefinition("testEntity")
				.string("name").string("_name_").string("-name-").id().getEntityDefinition();


		EntityDefinition newEntity = service.createEntityFor(entity, localConnector, DraftStatus.APPROVED);
		assertTrue(newEntity.hasField("name"));
		assertTrue(newEntity.hasField("name__c"));
		assertTrue(newEntity.hasField("name__c1"));
		assertEquals("name", newEntity.getField("name").get().getDisplayName());
		assertEquals("_name_", newEntity.getField("name__c").get().getDisplayName());
		assertEquals("-name-", newEntity.getField("name__c1").get().getDisplayName());
	}

	@Test
	public void getSchemaForTest(){
		Connector localConnector = getConnector();
		Schema oldSchema = service.getSchemaFor(localConnector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "Contact");
		e.setDisplayName("Contact");
		EntityDef ac = new EntityDef("4567", "Account");
		ac.setDisplayName("Account");
		entities.add(ac);

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(localConnector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);
		assertNotNull(describeAll);

		String entityNameA = "account";
		List<AttributeDefinition> attributesAcc = service.getActiveAttributes(localConnector.getId(), entityNameA);
		EntityDefinition entityDefinitionAcc = new EntityDefinition("accounttest","accounttest");
		entityDefinitionAcc.setAttributes(attributesAcc);
		entityDefinitionAcc.setConnectorId(localConnector.getId());
		entityDefinitionAcc.setStatus(Status.ACTIVE);
		entityDefinitionAcc.setDraftStatus(DraftStatus.APPROVED);
		entityDefinitionCache.save(entityDefinitionAcc);
		attributeProxyRepo.saveAll(attributesAcc);


		Schema newSchema = service.getSchemaFor(localConnector.getId());
		assertNotNull(newSchema);
		assertNotNull(newSchema.getEntities());
		assertEquals(newSchema.getEntities().size(), 3);

		List<AttributeDefinition> attributesAcc1 = service.getActiveAttributes(localConnector.getId(), entityNameA);
		EntityDefinition entityDefinitionAcc1 = new EntityDefinition("accounttest1","accounttest1");
		entityDefinitionAcc.setAttributes(attributesAcc1);
		entityDefinitionAcc.setConnectorId(localConnector.getId());
		entityDefinitionAcc.setStatus(Status.INACTIVE);
		entityDefinitionAcc.setDraftStatus(DraftStatus.APPROVED);
		entityDefinitionCache.save(entityDefinitionAcc1);
		attributeProxyRepo.saveAll(attributesAcc1);


		newSchema = service.getSchemaFor(localConnector.getId());
		assertNotNull(newSchema);
		assertNotNull(newSchema.getEntities());
		assertEquals(newSchema.getEntities().size(), 3);
	}

	@Test
	public void createEntityLikeTestMultipleTimes(){
		Connector localConnector = getConnector();
		Schema oldSchema = service.getSchemaFor(localConnector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "Contact");
		e.setDisplayName("Contact");

		EntityDef ac = new EntityDef("4567", "Account");
		ac.setDisplayName("Account");
		entities.add(ac);

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(localConnector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);
		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("contact")) {
				AttributeSchema attributeDef = new AttributeSchema("new_added_field", "reference");
				attributeDef.setId("new_added_field");
				attributeDef.setDataType("reference");
				attributeDef.setDisplayName("new_field");
				attributeDef.setReferenceTo("account");
				e2.addField(attributeDef);
			}
		});

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		String entityNameA = "account";
		List<AttributeDefinition> attributesAcc = service.getActiveAttributes(localConnector.getId(), entityNameA);
		EntityDefinition entityDefinitionAcc = new EntityDefinition(entityNameA,entityNameA);
		entityDefinitionAcc.setAttributes(attributesAcc);
		entityDefinitionAcc.setConnectorId(localConnector.getId());
		entityDefinitionAcc.setStatus(Status.ACTIVE);
		entityDefinitionCache.save(entityDefinitionAcc);
		attributeProxyRepo.saveAll(attributesAcc);
		entityDefinitionAcc.setId("");
		EntityDefinition syncariEntityAcc = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc);
		assertTrue(syncariEntityAcc.getWatermarkField().isPresent());
		assertFalse(syncariEntityAcc.getWatermarkField().get().isNillable());
		assertEquals(entityDefinitionAcc.getApiName().concat("__c"), syncariEntityAcc.getApiName());
		EntityDefinition syncariEntityAcc1 = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc1);
		assertTrue(syncariEntityAcc1.getWatermarkField().isPresent());
		assertFalse(syncariEntityAcc1.getWatermarkField().get().isNillable());
		assertEquals(entityDefinitionAcc.getApiName().concat("__c1"),syncariEntityAcc1.getApiName());

		EntityDefinition syncariEntityAcc2 = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc2);
		assertTrue(syncariEntityAcc2.getWatermarkField().isPresent());
		assertFalse(syncariEntityAcc2.getWatermarkField().get().isNillable());
		assertEquals(entityDefinitionAcc.getApiName().concat("__c2"),syncariEntityAcc2.getApiName());
	}

	@Test
	public void createEntityLikeTestMultipleTimesWithNumber(){
		Connector localConnector = getConnector();
		Schema oldSchema = service.getSchemaFor(localConnector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "Contact");
		e.setDisplayName("Contact");

		EntityDef ac = new EntityDef("4567", "Account");
		ac.setDisplayName("Account");
		entities.add(ac);

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(localConnector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);
		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("contact")) {
				AttributeSchema attributeDef = new AttributeSchema("new_added_field", "reference");
				attributeDef.setId("new_added_field");
				attributeDef.setDataType("reference");
				attributeDef.setDisplayName("new_field");
				attributeDef.setReferenceTo("account");
				e2.addField(attributeDef);
			}
		});
		String entityNameA = "acco12unt";
		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		accountSchema.setApiName(entityNameA);
		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;


		List<AttributeDefinition> attributesAcc = service.getActiveAttributes(localConnector.getId(), entityNameA);
		EntityDefinition entityDefinitionAcc = new EntityDefinition(entityNameA,entityNameA);
		entityDefinitionAcc.setAttributes(attributesAcc);
		entityDefinitionAcc.setConnectorId(localConnector.getId());
		entityDefinitionAcc.setStatus(Status.ACTIVE);
		entityDefinitionCache.save(entityDefinitionAcc);
		attributeProxyRepo.saveAll(attributesAcc);
		EntityDefinition syncariEntityAcc = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc);
		assertEquals(entityDefinitionAcc.getApiName(), syncariEntityAcc.getApiName());
		EntityDefinition syncariEntityAcc1 = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc1);
		assertEquals(entityDefinitionAcc.getApiName().concat("__c"),syncariEntityAcc1.getApiName());

		EntityDefinition syncariEntityAcc2 = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc2);
		assertEquals(entityDefinitionAcc.getApiName().concat("__c1"),syncariEntityAcc2.getApiName());
	}

	@Test
	public void addAttributeLikeTest(){
		Connector localConnector = getConnector();
		Schema oldSchema = service.getSchemaFor(localConnector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "Contact");
		e.setDisplayName("Contact");

		EntityDef ac = new EntityDef("4567", "Account");
		ac.setDisplayName("Account");
		entities.add(ac);

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(localConnector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		String entityNameA = "account";
		List<AttributeDefinition> attributesAcc = service.getActiveAttributes(localConnector.getId(), entityNameA);
		EntityDefinition entityDefinitionAcc = new EntityDefinition(entityNameA,entityNameA);
		entityDefinitionAcc.setAttributes(attributesAcc);
		entityDefinitionAcc.setConnectorId(localConnector.getId());
		entityDefinitionCache.save(entityDefinitionAcc);
		attributeProxyRepo.saveAll(attributesAcc);
		EntityDefinition syncariEntityAcc = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc);
		assertTrue(syncariEntityAcc.getWatermarkField().isPresent());
		assertFalse(syncariEntityAcc.getWatermarkField().get().isNillable());

		EntitySchema contactSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		EntityDefinition contactEntity = service.getEntity(localConnector.getId(),"contact");
		doReturn(Optional.of(contactSchema)).when(metaService).describe(any());

		String entityName = "contact";
		List<AttributeDefinition> attributes = service.getActiveAttributes(localConnector.getId(), entityName);
		EntityDefinition entityDefinition = new EntityDefinition(entityName,entityName);
		entityDefinition.setAttributes(attributes);
		entityDefinition.setConnectorId(localConnector.getId());
		EntityDefinition syncariEnt = service.createEntityLike(entityDefinition,null);
		assertNotNull(syncariEnt);
		assertTrue(syncariEnt.getWatermarkField().isPresent());
		assertFalse(syncariEnt.getWatermarkField().get().isNillable());

		AttributeSchema attributeDef = new AttributeSchema("new_added_field", "reference");
		attributeDef.setId("new_added_field");
		attributeDef.setDataType("reference");
		attributeDef.setDisplayName("new_field");
		attributeDef.setReferenceTo("account");

		AttributeSchema attributeDef1 = new AttributeSchema("new_added_field1", "string");
		attributeDef1.setId("new_added_field1");
		attributeDef1.setDataType("string");
		attributeDef1.setDisplayName("new_field1");

		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("contact")) {
				e2.addField(attributeDef);
				e2.addField(attributeDef1);
			}
		});

		service.refreshSynapseSchema(localConnector.getId(), contactEntity, localConnector.getId());
		Schema newSchema = service.getSchemaFor(localConnector.getId());
		EntityDef oldAcc = oldSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		EntityDef newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		assertEquals(oldAcc.getFields().size()+2, newAcc.getFields().size());
		assertTrue(newAcc.getFields().stream().anyMatch(f -> "new_added_field".equals(f.getApiName())));
		assertTrue(newAcc.getFields().stream().anyMatch(f -> "new_added_field1".equals(f.getApiName())));
		AttributeDefinition srcdef = service.getAttributeByName(contactEntity.getId(),attributeDef.getApiName());

		AttributeDefinition defFrSyncari = service.addAttributeToSyncariEntity(srcdef, "new_added_field","new_field",Optional.empty(),syncariEnt);
		assertNotNull(defFrSyncari);
		assertNotEquals(defFrSyncari,srcdef);


		Optional<AttributeDefinition> def = syncariEnt.getField("new_added_field");
		def.ifPresent( entdef -> {
			assertEquals(entdef.getDataType().getName(),"reference");
			assertEquals(entdef.getReferenceTo(),"account");
			assertEquals(entdef.getReferenceTargetField(),"id");
		});

		Optional<AttributeDefinition> def1 = syncariEnt.getField("new_added_field1");
		def1.ifPresent( entdef -> {
			assertEquals(entdef.getDataType().getName(),"string");
		});

		// try and create new id field
		srcdef.setIdField(true).setNillable(false).setUnique(true);
		defFrSyncari = service.addAttributeToSyncariEntity(srcdef, "new_added_field2","new_field2",Optional.empty(),syncariEnt);
		assertNotNull(defFrSyncari);
		assertNotEquals(defFrSyncari,srcdef);
		Optional<AttributeDefinition> def2 = syncariEnt.getField("new_added_field");
		assertTrue(def2.isPresent());
		assertTrue(srcdef.isIdField());
		assertFalse(def2.get().isIdField());
	}


	@Test
	public void addAttributeLikeWithCompositeKeyTest(){
		Connector localConnector = getConnector();
		Schema oldSchema = service.getSchemaFor(localConnector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "Contact");
		e.setDisplayName("Contact");

		EntityDef ac = new EntityDef("4567", "Account");
		ac.setDisplayName("Account");
		entities.add(ac);

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(localConnector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		String entityNameA = "account";
		List<AttributeDefinition> attributesAcc = service.getActiveAttributes(localConnector.getId(), entityNameA);
		EntityDefinition entityDefinitionAcc = new EntityDefinition(entityNameA,entityNameA);
		entityDefinitionAcc.setAttributes(attributesAcc);
		entityDefinitionAcc.setConnectorId(localConnector.getId());
		entityDefinitionCache.save(entityDefinitionAcc);
		attributeProxyRepo.saveAll(attributesAcc);
		EntityDefinition syncariEntityAcc = service.createEntityLike(entityDefinitionAcc,null);
		assertNotNull(syncariEntityAcc);

		EntitySchema contactSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		EntityDefinition contactEntity = service.getEntity(localConnector.getId(),"contact");
		doReturn(Optional.of(contactSchema)).when(metaService).describe(any());

		String entityName = "contact";
		List<AttributeDefinition> attributes = service.getActiveAttributes(localConnector.getId(), entityName);
		EntityDefinition entityDefinition = new EntityDefinition(entityName,entityName);
		entityDefinition.setAttributes(attributes);
		entityDefinition.setConnectorId(localConnector.getId());
		EntityDefinition syncariEnt = service.createEntityLike(entityDefinition,null);
		assertNotNull(syncariEnt);

		AttributeSchema attributeDef = new AttributeSchema("new_added_field", "string");
		attributeDef.setDataType("string");
		attributeDef.setDisplayName("new_field");

		AttributeSchema attributeDef2 = new AttributeSchema("new_added_field2", "string");
		attributeDef2.setDataType("string");
		attributeDef2.setDisplayName("new_added_field2");

		AttributeSchema attributeDef1 = new AttributeSchema("new_added_field1", "string");
		attributeDef1.setId("new_added_field1");
		attributeDef1.setCompositeKey("new_added_field|new_added_field2");
		attributeDef1.setDataType("id");
		attributeDef1.setDisplayName("new_field1");

		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("contact")) {
				e2.addField(attributeDef);
				e2.addField(attributeDef2);
				e2.addField(attributeDef1);

			}
		});

		service.refreshSynapseSchema(localConnector.getId(), contactEntity, localConnector.getId());
		Schema newSchema = service.getSchemaFor(localConnector.getId());
		EntityDef oldContact = oldSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		EntityDef newContact = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("contact")).findFirst().get();
		assertEquals(oldContact.getFields().size()+3, newContact.getFields().size());
		assertTrue(newContact.getFields().stream().anyMatch(f -> "new_added_field".equals(f.getApiName())));
		assertTrue(newContact.getFields().stream().anyMatch(f -> "new_added_field1".equals(f.getApiName())));
		AttributeDefinition srcdef = service.getAttributeByName(contactEntity.getId(),attributeDef.getApiName());

		AttributeDefinition defFrSyncari = service.addAttributeToSyncariEntity(srcdef, "new_added_field","new_field",Optional.empty(),syncariEnt);
		assertNotNull(defFrSyncari);
		assertNotEquals(defFrSyncari,srcdef);

		Optional<AttributeDefinition> def1 = syncariEnt.getField("new_added_field1");
		def1.ifPresent( entdef -> {
			assertEquals(entdef.getDataType().getName(),"id");
			assertEquals("new_added_field|new_added_field2",entdef.getCompositeKey());
		});

	}

	@Test
	public void refreshSchemaUpdatesRefFieldLengthCorrectly() {
		ConnectorService mockConnectorService = mock(ConnectorService.class);
		MetadataService mockDataService = mock(MetadataService.class);
		DataServiceFactory mockDataServiceFactor = mock(DataServiceFactory.class);
		SynapseInfoService mockSynapseInfoService = mock(SynapseInfoService.class);
		EntitySchema entitySchema = new EntitySchema();
		entitySchema.setApiName("account");
		entitySchema.setCustom(false);
		entitySchema.setDisplayName("Account");
		AttributeSchema attribute = new AttributeSchema();
		attribute.setApiName("HasOptedOutOfEmail");
		attribute.setDisplayName("Email Opt Out");
		attribute.setDataType("reference");
		attribute.setReferenceTo("Account");
		attribute.setReferenceTargetField("Id");
		attribute.setLength(16);
		entitySchema.setAttributes(List.of(attribute));

		Connector connector = new Connector();
		connector.setMetadata(new ConnectorMetadata());

		when(mockConnectorService.find("salesforceConnector")).thenReturn(Optional.of(connector));
		when(mockDataServiceFactor.getSchemaService(any())).thenReturn(mockDataService);
		when(mockDataServiceFactor.getSynapseService(any())).thenReturn(mockSynapseInfoService);
		when(mockDataService.describeAll(any())).thenReturn(List.of(entitySchema));

		service.connectorService = mockConnectorService;
		service.factory =mockDataServiceFactor;


		List<EntityDefinition> entities = service.refreshSynapseSchema("salesforceConnector");
		assertEquals(1, entities.size());
		assertEquals(1, entities.get(0).getAttributes().size());
		assertEquals(ReferenceType.VALUE, entities.get(0).getAttributes().get(0).getDataType());
		assertEquals(24, entities.get(0).getAttributes().get(0).getLength());
		verify(mockConnectorService).find("salesforceConnector");
		verify(mockDataService).describeAll(any());
		verify(mockDataServiceFactor).getSchemaService(any());
		//Now set field length to 32
		attribute.setLength(32);
		reset(mockConnectorService, mockDataService, mockDataServiceFactor);

		when(mockConnectorService.find("salesforceConnector")).thenReturn(Optional.of(connector));
		when(mockDataServiceFactor.getSchemaService(any())).thenReturn(mockDataService);
		when(mockDataServiceFactor.getSynapseService(any())).thenReturn(mockSynapseInfoService);
		when(mockDataService.describeAll(any())).thenReturn(List.of(entitySchema));


		entities = service.refreshSynapseSchema("salesforceConnector");
		assertEquals(1, entities.size());
		assertEquals(1, entities.get(0).getAttributes().size());
		assertEquals(ReferenceType.VALUE, entities.get(0).getAttributes().get(0).getDataType());
		assertEquals(32, entities.get(0).getAttributes().get(0).getLength());

		verify(mockConnectorService).find("salesforceConnector");
		verify(mockDataService).describeAll(any());
		verify(mockDataServiceFactor).getSchemaService(any());
	}

	@Test
	public void getActiveAttributes() {
		connectorService.authenticated(connector.getId());
		
		Connector connector2 = getConnector();
        List<AttributeDefinition> attributes = service.getActiveAttributes(connector2.getId(), "account");
		assertFalse(attributes.isEmpty());
		
		try {
			service.getActiveAttributes("unknown-connector", "Account");
		} catch (Exception e) {
			assertEquals("Entity definition for unknown-connector and Account not found", e.getMessage());
		}
		try {
			service.getActiveAttributes(connector2.getId(), "unknown-entity");
		} catch (Exception e) {
			assertEquals("Entity definition for " + connector2.getId() + " and unknown-entity not found",
					e.getMessage());
		}
	}

	@Test
	public void refreshSchemaValidations() {
		try {
			service.refreshSynapseSchema("123");
			fail();
		} catch (Exception e) {
			assertEquals("Connector with Id 123 not found", e.getMessage());
		}
	}
	
	@Test
	public void refreshSchemaNewFieldAdded() {
		Connector connector = getConnector();
		Schema oldSchema = service.getSchemaFor(connector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "Account");
		e.setDisplayName("Account");
        entities.add(e);
		
        DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
                oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
        List<EntitySchema> describeAll = testSynapseService.describeAll(request);
        describeAll.forEach(e2 -> {
            if(e2.getApiName().equalsIgnoreCase("account")) {
                AttributeSchema attributeDef = new AttributeSchema("new_added_field", "string");
                attributeDef.setDataType("string");
                attributeDef.setDisplayName("new_field");
                e2.addField(attributeDef);
            }
        });

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		EntityDefinition accountEntity = service.getEntity(connector.getId(),"account");
		
		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		Schema newSchema = service.getSchemaFor(connector.getId());
		EntityDef oldAcc = oldSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		EntityDef newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		assertEquals(oldAcc.getFields().size()+1, newAcc.getFields().size());
		assertTrue(newAcc.getFields().stream().anyMatch(f -> "new_added_field".equals(f.getApiName())));
	}

	@Test
	public void refreshSchemaFieldRecreated() {
		Connector connector = getConnector();
		Schema oldSchema = service.getSchemaFor(connector.getId());

		List<EntityDef> entities = new ArrayList<>();
		EntityDef e = new EntityDef("1234", "account");
		e.setDisplayName("Account");
		entities.add(e);

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);
		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("account")) {
				AttributeSchema attributeDef = new AttributeSchema("new_added_field", "string");
				attributeDef.setDataType("string");
				attributeDef.setDisplayName("new_field");
				e2.addField(attributeDef);
			}
		});

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		EntityDefinition accountEntity = service.getEntity(connector.getId(),"account");

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		Schema newSchema = service.getSchemaFor(connector.getId());
		EntityDef oldAcc = oldSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		EntityDef newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		assertEquals(oldAcc.getFields().size()+1, newAcc.getFields().size());
		var newField = newAcc.getFields().stream().filter(f -> "new_added_field".equals(f.getApiName())).findFirst();
		assertTrue(newField.isPresent());
		assertEquals(Status.ACTIVE, newField.get().getStatus());

		// remove the field
		request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		describeAll = testSynapseService.describeAll(request);
		accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		accountEntity = service.getEntity(connector.getId(),"account");

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		newSchema = service.getSchemaFor(connector.getId());
		newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		assertEquals(oldAcc.getFields().size(), newAcc.getFields().size());
		assertFalse(newAcc.getFields().stream().anyMatch(f -> "new_added_field".equals(f.getApiName())));

		var deletedField = service.getAttribute(newField.get().getId());
		assertEquals(DraftStatus.APPROVED, deletedField.getDraftStatus());
		assertEquals(Status.INACTIVE, deletedField.getStatus());

		request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		describeAll = testSynapseService.describeAll(request);
		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("account")) {
				AttributeSchema attributeDef = new AttributeSchema("new_added_field", "string");
				attributeDef.setDataType("string");
				attributeDef.setDisplayName("new_field");
				e2.addField(attributeDef);
			}
		});

		accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		accountEntity = service.getEntity(connector.getId(),"account");

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		newSchema = service.getSchemaFor(connector.getId());
		oldAcc = oldSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		assertEquals(oldAcc.getFields().size()+1, newAcc.getFields().size());
		newField = newAcc.getFields().stream().filter(f -> "new_added_field".equals(f.getApiName())).findFirst();
		assertTrue(newField.isPresent());
		assertEquals(Status.ACTIVE, newField.get().getStatus());
	}

	@Test
	public void refreshSchemaFielDeletedFromSyncariDraft() {
		Connector connector = getConnector();
		Schema oldSchema = service.getSchemaFor(connector.getId());

		EntityDefinition account = service.getEntity(connector.getId(), "account");
		//create a draft of the entity
		service.createEntityDraftFor(account.getId());

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);
		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("account")) {
				AttributeSchema attributeDef = new AttributeSchema("new_added_field", "string");
				attributeDef.setDataType("string");
				attributeDef.setDisplayName("new_field");
				e2.addField(attributeDef);
			}
		});

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		EntityDefinition accountEntity = service.getEntity(connector.getId(),"account").setConnectorTypeId(connector.getMetadataId());

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		EntityDefinition draftAccount = service.getDraftEntity(connector.getId(), "account");
		EntityDefinition approved = service.getEntity(account.getId());
		//make sure draft has the new field as well
		assertTrue(approved.hasField("new_added_field"));
		assertTrue(draftAccount.hasField("new_added_field"));

		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("account")) {
				AttributeSchema attributeDef = new AttributeSchema("new_added_field", "string");
				//change datatype
				attributeDef.setDataType("integer");
				attributeDef.setDisplayName("new_field");
				e2.addField(attributeDef);
			}
		});
		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		//reload draft successfully
		draftAccount = service.getDraftEntity(connector.getId(), "account");
		assertTrue(draftAccount.hasField("new_added_field"));
		assertEquals(IntegerType.VALUE, draftAccount.getFieldByName("new_added_field").getDataType());
	}

  @Test
	public void deleteSyncariDefinedFieldNotUsedInPipeline() {
		Connector connector = getConnector();
		// create entity in connector and its draft
		EntityDefinition testEntity = new EntityDefinition("testEntity", "Test Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		testEntity.setDraftStatus(DraftStatus.APPROVED);
		testEntity = entityDefinitionCache.save(testEntity);
		AttributeDefinition testField = new AttributeDefinition()
				.setApiName("testField")
				.setDisplayName("Test Field")
				.setDataType(new StringType())
				.setEntityId(testEntity.getId())
				.setStatus(Status.ACTIVE);
		testField.setDraftStatus(DraftStatus.APPROVED);
		testField = attributeProxyRepo.save(testField);

		try {
			// Field is not on Syncari entity and is not Syncari defined
			service.canDeleteField(testField);
			fail();
		} catch (Exception e) {
			assertEquals(i18n("only_delete_syncari_defined_fields"), e.getMessage());
		}

		testField.setSyncariDefined(true);
		testField = attributeProxyRepo.save(testField);

		// Expect this not to throw
		service.canDeleteField(testField);

		service.deleteField(testField.getEntityId(), testField.getId());
		assertFalse(attributeProxyRepo.findById(testField.getId()).isPresent());
	}

	@Test
	public void deleteFieldAndApproveEntityTest() {
		Connector connector = getConnector();
		// create entity in connector and its draft
		EntityDefinition testEntity = new EntityDefinition("testEntity", "Test Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		testEntity.setDraftStatus(DraftStatus.APPROVED);
		testEntity = entityDefinitionCache.save(testEntity);
		AttributeDefinition testField = new AttributeDefinition()
				.setApiName("testField")
				.setDisplayName("Test Field")
				.setDataType(new StringType())
				.setEntityId(testEntity.getId())
				.setStatus(Status.ACTIVE);
		testField.setDraftStatus(DraftStatus.APPROVED);
		testField.setSyncariDefined(true);
		final AttributeDefinition testFieldSaved = attributeProxyRepo.save(testField);

		AttributeDefinition wfField = new AttributeDefinition()
				.setApiName("wfField")
				.setDisplayName("Test wfField")
				.setDataType(new IntegerType())
				.setEntityId(testEntity.getId())
				.setStatus(Status.ACTIVE).setSyncariDefined(true).setSystem(true).setWatermarkField(true);
		final AttributeDefinition testWFSaved = attributeProxyRepo.save(wfField);

		AttributeDefinition idField = new AttributeDefinition()
				.setApiName("idField")
				.setDisplayName("Test idField")
				.setDataType(new IntegerType())
				.setEntityId(testEntity.getId())
				.setStatus(Status.ACTIVE).setSyncariDefined(true).setSystem(true).setIdField(true);
		final AttributeDefinition testIdSaved = attributeProxyRepo.save(idField);


		// Expect this not to throw
		service.canDeleteField(testField);
		var draft = service.createEntityDraftFor(testEntity.getId());
		Optional<AttributeDefinition> testFieldDraft = draft.getAttributes().stream().filter(x -> x.getApiName().equals(testFieldSaved.getApiName().toString())).collect(Collectors.toList()).stream().findFirst();
		testFieldDraft.ifPresent(tFD -> service.deleteField(tFD.getEntityId(), tFD.getId()));

		assertTrue(attributeProxyRepo.findById(testField.getId()).isPresent());
		draft = service.getEntity(draft.getId());
		service.approveDraftEntity(draft);
		assertFalse(attributeProxyRepo.findById(testField.getId()).isPresent());
	}
  
    @Test
    public void deleteSyncariDefinedFielUsedInPipeline() {
        Connector connector = getConnector();
        // create entity in connector and its draft
        EntityDefinition testEntity = new EntityDefinition("testEntity", "Test Entity")
                .setConnectorId(connector.getId()).setStatus(Status.ACTIVE);
        testEntity.setDraftStatus(DraftStatus.APPROVED);
        testEntity = entityDefinitionCache.save(testEntity);
        AttributeDefinition testField = new AttributeDefinition().setApiName("testField").setDisplayName("Test Field")
                .setDataType(new StringType()).setEntityId(testEntity.getId()).setStatus(Status.ACTIVE);
        testField.setDraftStatus(DraftStatus.APPROVED);
        testField.setSyncariDefined(true);
        testField = attributeProxyRepo.save(testField);
        
        EntityDefinition entity = service.getSyncariEntityByName("account").get();
        AttributeDefinition attributeDefinition = entity.getField("industry").get();
        MappingGraph attrGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Test").setScope(Scope.ATTRIBUTE).setTargetId(attributeDefinition.getId()));
        MappingNode core = new MappingNode().setScope(Scope.ATTRIBUTE).setMappingGraphId(attrGraph.getId()).setApiName("core").setName("core")
                .setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(attributeDefinition));
        MappingNode source = new MappingNode().setScope(Scope.ATTRIBUTE).setMappingGraphId(attrGraph.getId()).setApiName("source")
                .setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(testField)).setName("source");
        source = nodeRepo.save(source);
        core = nodeRepo.save(core);
        attrGraph.addNode(source);
        attrGraph.addNode(core);
        Edge edge = edgeRepo.save(new Edge().setSourceStage(source).setGraphId(attrGraph.getId()).setDestinationStage(core));
        attrGraph.addEdge(edge);
        mappingGraphRepo.save(attrGraph);

        try {
            service.canDeleteField(testField);
            fail();
        } catch (Exception e) {
            assertEquals(i18n("external_field_used_in_pipeline", 1, "Account Test"), e.getMessage());
        }
        assertTrue(attributeProxyRepo.findById(testField.getId()).isPresent());
        mappingGraphService.delete(attrGraph);
        service.deleteField(testField.getEntityId(), testField.getId());
        assertFalse(attributeProxyRepo.findById(testField.getId()).isPresent());
    }

	@Test
	public void deleteSyncariDefinedFielUsedInVersionedPipeline() {
		Connector connector = getConnector();
		// create entity in connector and its draft
		EntityDefinition testEntity = new EntityDefinition("testEntity", "Test Entity")
				.setConnectorId(connector.getId()).setStatus(Status.ACTIVE);
		testEntity.setDraftStatus(DraftStatus.APPROVED);
		testEntity = entityDefinitionCache.save(testEntity);
		AttributeDefinition testField = new AttributeDefinition().setApiName("testField").setDisplayName("Test Field")
				.setDataType(new StringType()).setEntityId(testEntity.getId()).setStatus(Status.ACTIVE);
		testField.setDraftStatus(DraftStatus.APPROVED);
		testField.setSyncariDefined(true);
		testField = attributeProxyRepo.save(testField);

		EntityDefinition entity = service.getSyncariEntityByName("account").get();
		AttributeDefinition attributeDefinition = entity.getField("industry").get();
		MappingGraph attrGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Test").setScope(Scope.ATTRIBUTE).setTargetId(attributeDefinition.getId()));
		attrGraph.setVersionInfo(new Version().setVersionNumber(1));
		MappingNode core = new MappingNode().setScope(Scope.ATTRIBUTE).setMappingGraphId(attrGraph.getId()).setApiName("core").setName("core")
				.setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(attributeDefinition));
		MappingNode source = new MappingNode().setScope(Scope.ATTRIBUTE).setMappingGraphId(attrGraph.getId()).setApiName("source")
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(testField)).setName("source");
		source = nodeRepo.save(source);
		core = nodeRepo.save(core);
		attrGraph.addNode(source);
		attrGraph.addNode(core);
		Edge edge = edgeRepo.save(new Edge().setSourceStage(source).setGraphId(attrGraph.getId()).setDestinationStage(core));
		attrGraph.addEdge(edge);
		mappingGraphRepo.save(attrGraph);

		try {
			service.canDeleteField(testField);
		} catch (Exception e) {
			fail();
		}
		assertTrue(attributeProxyRepo.findById(testField.getId()).isPresent());
		mappingGraphService.delete(attrGraph);
		service.deleteField(testField.getEntityId(), testField.getId());
		assertFalse(attributeProxyRepo.findById(testField.getId()).isPresent());
	}

    @Test
    public void deleteSyncariFieldUsedInPipeline() {
        Connector connector = connectorService.getSyncariConnector();
        String entityId = service.getEntity(connector.getId(), "account").getId();
        AttributeDefinition testField = new AttributeDefinition().setApiName("testField").setDisplayName("Test Field")
                .setDataType(new StringType()).setEntityId(entityId)
                .setStatus(Status.ACTIVE);
        testField.setDraftStatus(DraftStatus.APPROVED);
        testField = attributeProxyRepo.save(testField);

        MappingGraph attrGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Test").setScope(Scope.ATTRIBUTE).setTargetId(testField.getId()));
        MappingNode core = new MappingNode().setScope(Scope.ATTRIBUTE).setMappingGraphId(attrGraph.getId()).setApiName("core").setName("core")
                .setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(testField));
        core = nodeRepo.save(core);
        attrGraph.addNode(core);
        mappingGraphRepo.save(attrGraph);

        try {
            service.canDeleteField(testField);
            fail();
        } catch (Exception e) {
            assertEquals("A pipeline exists for 'testField'. Please remove the pipeline before deleting.", e.getMessage());
        }

        assertTrue(attributeProxyRepo.findById(testField.getId()).isPresent());
        mappingGraphRepo.delete(attrGraph);
        service.deleteField(testField.getEntityId(), testField.getId());
        assertFalse(attributeProxyRepo.findById(testField.getId()).isPresent());
    }

    @Test
    public void deleteSyncariFieldNotUsedInPipeline() {
        Connector connector = connectorService.getSyncariConnector();
        AttributeDefinition testField = new AttributeDefinition().setApiName("testField").setDisplayName("Test Field")
                .setDataType(new StringType()).setEntityId(service.getEntity(connector.getId(), "account").getId())
                .setStatus(Status.ACTIVE);
        testField.setDraftStatus(DraftStatus.APPROVED);
        testField.setSyncariDefined(true);
        testField = attributeProxyRepo.save(testField);
        assertTrue(attributeProxyRepo.findById(testField.getId()).isPresent());
        service.deleteField(testField.getEntityId(), testField.getId());
        assertFalse(attributeProxyRepo.findById(testField.getId()).isPresent());
    }
  
	@Test
	public void refreshSchemaSyncariDefinedNestedField() {
		Connector connector = getConnector();
		Schema oldSchema = service.getSchemaFor(connector.getId());

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		List<EntitySchema> describeAll = testSynapseService.describeAll(request);
		describeAll.forEach(e2 -> {
			if(e2.getApiName().equalsIgnoreCase("account")) {
				AttributeSchema attributeDef = new AttributeSchema("nested_field", "nested");
				attributeDef.setDataType("nested");
				attributeDef.setDisplayName("nested_field");
				attributeDef.setSyncariDefined(true);
				attributeDef.setReferenceTo("account").setReferenceTargetField("description");
				e2.addField(attributeDef);
			}
		});

		EntitySchema accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		EntityDefinition accountEntity = service.getEntity(connector.getId(),"account");

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		Schema newSchema = service.getSchemaFor(connector.getId());
		EntityDef oldAcc = oldSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		EntityDef newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		assertEquals(oldAcc.getFields().size()+1, newAcc.getFields().size());
		assertTrue(newAcc.getFields().stream().anyMatch(f -> "nested_field".equals(f.getApiName())));

		// remove the field from synapse
		request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		describeAll = testSynapseService.describeAll(request);
		accountSchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("account")).findFirst().get();
		accountEntity  = service.getEntity(connector.getId(),"account");

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(accountSchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), accountEntity, connector.getId());
		newSchema = service.getSchemaFor(connector.getId());
		newAcc = newSchema.getEntities().stream().filter(e3 -> e3.getApiName().equalsIgnoreCase("account")).findFirst().get();
		//assertEquals(oldAcc.getFields().size(), newAcc.getFields().size());
		assertTrue(newAcc.getFields().stream().anyMatch(f -> "nested_field".equals(f.getApiName())));
	}

	@Test
	public void deleteIdField() {
		Connector connector = connectorService.getSyncariConnector();
		// create entity in syncari connector and its draft
		EntityDefinition testEntity = new EntityDefinition("testEntity", "Test Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		testEntity.setDraftStatus(DraftStatus.APPROVED);
		testEntity = entityDefinitionCache.save(testEntity);
		AttributeDefinition testField = new AttributeDefinition()
				.setApiName("testIdField")
				.setDisplayName("Test Id Field")
				.setDataType(new StringType())
				.setEntityId(testEntity.getId())
				.setIdField(true).setNillable(false).setUnique(true)
				.setStatus(Status.ACTIVE);
		testField.setDraftStatus(DraftStatus.APPROVED);
		testField = attributeProxyRepo.save(testField);

		try {
			// Field is not on Syncari entity and is not Syncari defined
			service.canDeleteField(testField);
			fail();
		} catch (Exception e) {
			assertEquals("Test Id Field is the only Id field in the entity and cannot be deleted.", e.getMessage());
		}
		assertTrue(attributeProxyRepo.findById(testField.getId()).isPresent());

		// second id field can be deleted
		AttributeDefinition testField2 = new AttributeDefinition()
				.setApiName("testIdField2")
				.setDisplayName("Test Id Field2")
				.setDataType(new StringType())
				.setEntityId(testEntity.getId())
				.setIdField(true).setNillable(false).setUnique(true)
				.setStatus(Status.ACTIVE);
		testField2.setDraftStatus(DraftStatus.APPROVED);
		testField2 = attributeProxyRepo.save(testField2);
		service.canDeleteField(testField2);

		service.deleteField(testField2.getEntityId(), testField2.getId());
		assertFalse(attributeProxyRepo.findById(testField2.getId()).isPresent());
	}

	@Test
	public void refreshSchemaEntityWithDraft() {
		Connector connector = getConnector();

		// create entity in connector and its draft
		EntityDefinition testEntity = new EntityDefinition("testEntity", "Test Entity")
				.setConnectorId(connector.getId()).setConnectorTypeId(connector.getMetadataId()).setStatus(Status.ACTIVE);
		testEntity.setDraftStatus(DraftStatus.APPROVED);


		testEntity = service.save(testEntity);

		AttributeDefinition testField = new AttributeDefinition().setApiName("testField").setDisplayName("Test Field")
				.setDataType(new StringType()).setEntityId(testEntity.getId()).setStatus(Status.ACTIVE);
		testField.setDraftStatus(DraftStatus.APPROVED);
		testField = attributeProxyRepo.save(testField);

		EntityDefinition draftEntity = service.createEntityDraftFor(testEntity.getId());
		assertEquals("Test Entity", draftEntity.getDisplayName());
		assertTrue(draftEntity.hasField("testField"));
		assertEquals("Test Field", draftEntity.getFieldByName("testField").getDisplayName());
		assertEquals(DraftStatus.NEW, draftEntity.getDraftStatus());

		EntityDefinition retrieved = service.getEntity(connector.getId(), "testEntity");
		assertEquals("Test Entity", retrieved.getDisplayName());
		assertTrue(retrieved.hasField("testField"));
		assertEquals("Test Field", retrieved.getFieldByName("testField").getDisplayName());
		assertEquals(DraftStatus.APPROVED, retrieved.getDraftStatus());
		assertTrue(entityDefinitionCache.findActiveDraftFor(retrieved.getId()).isPresent());
		assertEquals(draftEntity.getId(), entityDefinitionCache.findActiveDraftFor(retrieved.getId()).get().getId());

		Schema oldSchema = service.getSchemaFor(connector.getId());

		DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connector),
				oldSchema.getEntities().stream().map(e1 -> e1.getApiName()).collect(Collectors.toList()));
		EntitySchema updatedSchema = transformer.toEntitySchema(retrieved, connector);
		updatedSchema.setDisplayName("Test Entity Updated");
		updatedSchema.getField("testField").get().setDisplayName("Test Field Updated");
		List<EntitySchema> describeAll = List.of(updatedSchema);

		EntitySchema entitySchema = describeAll.stream().filter(en -> en.getApiName().equalsIgnoreCase("testEntity")).findFirst().get();

		doReturn(describeAll).when(metaService).describeAll(any());
		doReturn(Optional.of(entitySchema)).when(metaService).describe(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;

		service.refreshSynapseSchema(connector.getId(), retrieved, connector.getId());

		EntityDefinition refreshed = service.getEntity(connector.getId(), "testEntity");
		assertEquals(refreshed.getId(), retrieved.getId());
		assertEquals("Test Entity Updated", refreshed.getDisplayName());
		assertTrue(refreshed.hasField("testField"));
		assertEquals(DraftStatus.APPROVED, refreshed.getDraftStatus());
		assertEquals("Test Field Updated", refreshed.getFieldByName("testField").getDisplayName());
		assertEquals(DraftStatus.APPROVED, refreshed.getFieldByName("testField").getDraftStatus());

		EntityDefinition refreshedDraft = service.getEntity(draftEntity.getId());
		assertEquals(draftEntity.getId(), refreshedDraft.getId());
		assertEquals("Test Entity Updated", refreshedDraft.getDisplayName());
		assertTrue(refreshedDraft.hasField("testField"));
		assertEquals(DraftStatus.NEW, refreshedDraft.getDraftStatus());
		assertEquals("Test Field Updated", refreshedDraft.getFieldByName("testField").getDisplayName());
		assertEquals(DraftStatus.NEW, refreshedDraft.getFieldByName("testField").getDraftStatus());
	}
	
	@Test
	public void refreshSchemaNewEntityAdded() {
		Connector connector = new Connector("test1", connectorService.describe("salesforce").getId(), "http://test.salesforce.com");
		connector = connectorService.save(connector);
		connectorService.authenticated(connector.getId());
	    doNothing().when(mockSchemaService).activateMapping(ArgumentMatchers.any());
	    connectorService.setSchemaService(mockSchemaService);
		connectorService.activate(connector.getId());
		
		List<EntityDef> entities = service.getSchemaFor(connector.getId()).getEntities();
		EntityDef newEntity = new EntityDef("123", "new_entity");
		newEntity.setDisplayName("newEntity");
		AttributeDef attributeDef = new AttributeDef("12345", "new_field");
		attributeDef.setDataType("string");
		attributeDef.setDisplayName("new_field");
		newEntity.getFields().add(attributeDef);
		entities.add(newEntity);

		List<EntitySchema> entitySchemaList = transformer.toEntitySchemaFrom(entities, connector);
		doReturn(entitySchemaList).when(metaService).describeAll(any());
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;
		
		Schema oldSchema = service.getSchemaFor(connector.getId());
		assertEquals(0, oldSchema.getEntities().size());
		service.refreshSynapseSchema(connector.getId());
		Schema newSchema = service.getSchemaFor(connector.getId());
		assertEquals(1, newSchema.getEntities().size());
		var entity = newSchema.getEntities().get(0);
		assertEquals(1, entity.getFields().size());
		assertEquals(DraftStatus.APPROVED, entity.getDraftStatus());

		var attribute = entity.getFields().get(0);
		assertEquals("new_field", attribute.getApiName());
		assertEquals(DraftStatus.APPROVED, attribute.getDraftStatus());
	}

	@Test(expected = NonRetriableException.class)
	public void refreshSchemaDisabledEntity() {
		/*Connector connector = new Connector("mkto1", connectorService.describe("marketo"), null, null, null);
		connector.getMetaConfig().put("munchkin", "183-LYQ-451");
		connector.setAuthType(AuthType.SimpleOAuth);
		connector.getAuthConfig().setClientId(System.getenv().getOrDefault("TEST_MARKETO_CLIENT_ID", "REPLACE_ME"));
		connector.getAuthConfig().setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
		connector.getAuthConfig().setConsumerKey("MKTOWS_183-LYQ-451_1");
		connector.getAuthConfig().setConsumerSecret(System.getenv().getOrDefault("TEST_MARKETO_CONSUMER_SECRET", "REPLACE_ME"));
		connector = connectorService.save(connector);
		connectorService.testConnection(connector.getId());
		connectorService.activate(connector.getId());*/
		Connector connector = getConnector();

		List<EntityDefinition> entities = service.getEntities(connector.getId());
		var companyEntity = service.getEntity(connector.getId(), "account");
		entities.remove(companyEntity);

		List<EntitySchema> entitySchemaList = transformer.toEntitySchema(entities, connector);

		when(metaService.describe(any())).thenThrow(new NonRetriableException(ErrorCodes.SCHEMA_ERROR.name(), "Entity Disabled", "DISABLED_STATUS"));
		when(metaService.describeAll(any())).thenReturn(entitySchemaList);
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;
		assertEquals(service.refreshSynapseSchema(connector.getId()).size(), entities.size());
		service.refreshSynapseSchema(connector.getId(), companyEntity, connector.getId());
	}

	@Test
	public void refreshSchemaUpdatesMultivaluedPicklist() {
		ConnectorService mock = mock(ConnectorService.class);
		connector.setStatus(ConnectorStatus.ACTIVE);
		service.connectorService = mock;
		EntityDefinition company = new EntityDefinition("company", "Company");
		company.setConnectorId(connector.getId());
		company.setDraftStatus(DraftStatus.APPROVED);
		company.setStatus(Status.ACTIVE);
		company = entityDefinitionCache.save(company);
		AttributeDefinition multiValuedFalse = new AttributeDefinition();
		multiValuedFalse.setApiName("multiValuedFirst");
		multiValuedFalse.setDisplayName("Multi Valued");
		multiValuedFalse.setId(ObjectId.get().toHexString());
		multiValuedFalse.setDataType(new PicklistType());
		multiValuedFalse.setEntityId(company.getId());
		multiValuedFalse.setDraftStatus(DraftStatus.APPROVED);
		multiValuedFalse.setStatus(Status.ACTIVE);
		multiValuedFalse = attributeProxyRepo.save(multiValuedFalse);

		company.setAttributes(List.of(multiValuedFalse));
		List<EntitySchema> entitySchemaList = transformer.toEntitySchema(List.of(company), connector);

		when(metaService.describeAll(any())).thenReturn(entitySchemaList);
		when(mock.find(connector.getId())).thenReturn(Optional.of(connector));
		doReturn(metaService).when(factory).getSchemaService(any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
		service.factory = factory;
		
		List<EntityDefinition> entityDefinitions = service.refreshSynapseSchema(connector.getId());

		assertEquals(entityDefinitions.size(), 1);
		assertEquals(entityDefinitions.get(0).getAttributes().size(), 1);
		assertFalse(entityDefinitions.get(0).getAttributes().get(0).isMultiValueField());


		//set multivalued to true
		entitySchemaList.get(0).getAttributes().get(0).setMultiValueField(true);
		entityDefinitions = service.refreshSynapseSchema(connector.getId());

		assertEquals(entityDefinitions.size(), 1);
		assertEquals(entityDefinitions.get(0).getAttributes().size(), 1);
		assertTrue(entityDefinitions.get(0).getAttributes().get(0).isMultiValueField());

		verify(metaService,times(2)).describeAll(any());
		verify(factory,times(2)).getSchemaService(any());
	}

    @Test
    public void addReferenceField() {
        ConnectorService mock = mock(ConnectorService.class);
        connector.setStatus(ConnectorStatus.ACTIVE);
        service.connectorService = mock;
        EntityDefinition company = new EntityDefinition("company", "Company");
        company.setConnectorId(connector.getId());
        company.setDraftStatus(DraftStatus.APPROVED);
        company.setStatus(Status.ACTIVE);
        company = entityDefinitionCache.save(company);
        AttributeDefinition sourceAttr = new AttributeDefinition().setApiName("someref")
                .setDataType(new ReferenceType()).setLength(18).setDisplayName("somref")
                .setEntityId(company.getId());
        sourceAttr.setDraftStatus(DraftStatus.APPROVED);
        sourceAttr.setStatus(Status.ACTIVE);
        sourceAttr = attributeProxyRepo.save(sourceAttr);
        List<EntitySchema> entitySchemaList = transformer.toEntitySchema(List.of(company), connector);

        when(metaService.describeAll(any())).thenReturn(entitySchemaList);
        when(mock.get(connector.getId())).thenReturn(connector);
        doReturn(metaService).when(factory).getSchemaService(any());
        doNothing().when(mapGraph).initializeAttrGraph(any(), any(), any(), any(), any(), any());
		doReturn(testSynapseService).when(factory).getSynapseService(any());
        service.factory = factory;
        service.mappingGraphService = mapGraph;
        
        List<EntityDefinition> entities = entityDefinitionCache
                .findByConnectorId(connectorService.getSyncariConnector().getId());
        Optional<String> referenceEntityId = Optional.of(entities.get(1).getId());
        EntityDefinition syncariEntity = entities.get(0);
        AttributeDefinition created = service.createAttributeLike(sourceAttr, referenceEntityId, syncariEntity,
                Map.of(), List.of());
        assertEquals(32, created.getLength());
    }

	@Test
	public void initializeEndSystemSchema() {
		Connector connector = getConnector();
		// since connector is activated which implicitly calls initializeEndSystemSchema
		// just validate entities and attributes
		List<EntityDefinition> entities = service.getEntities(connector.getId());
		assertFalse(entities.isEmpty());
		entities.forEach(e -> {
			assertEquals(DraftStatus.APPROVED, e.getDraftStatus());
			assertEquals(Status.ACTIVE, e.getStatus());
			assertEquals(connector.getId(), e.getConnectorId());

			e.getAttributes().forEach(a -> {
				assertEquals(DraftStatus.APPROVED, a.getDraftStatus());
				assertEquals(Status.ACTIVE, a.getStatus());
				assertEquals(e.getId(), a.getEntityId());
			});
		});
	}
	
	@Test
	public void getEntity() {
		String entityDefinitionId = entityDefinitionCache.findAll().get(0).getId();
		EntityDefinition entityDefinition = service.getEntity(entityDefinitionId);
		assertNotNull(entityDefinition);
		assertEquals("account", entityDefinition.getApiName());
	}
	
	@Test
	public void getFunctions() {
		List<FunctionDefinition> attributes = service.getFunctions(Scope.ENTITY);
		assertTrue(attributes.size() > 0);
	}

	@Test
	public void activateMapping() {
	    Connector syncariConnector = connectorService.getSyncariConnector();
	    Map<String, String> entityMappings = testSynapseService.getEntityMappings();
		long mappings = mappingGraphRepo.count();
		assertTrue(mappings == 0);
        entityMappings.forEach((k, v) -> {
            EntityDefinition syncariE = service.getEntityByName(syncariConnector.getId(), k).get();
            List<MappingGraph> entityGraphs = mappingGraphRepo.findEntityGraphs(syncariE.getId());
            assertTrue(entityGraphs.size() == 0);
        });

		
		Connector connector2 = getConnector();
		int entities = service.getEntities(connector2.getId()).size();
		assertTrue(entities > 0);
		
		entityMappings.forEach((k, v) -> {
            EntityDefinition syncariE = service.getEntityByName(syncariConnector.getId(), k).get();
		    List<MappingGraph> entityGraphs = mappingGraphRepo.findEntityGraphs(syncariE.getId());
		    assertTrue(entityGraphs.size() == 1);
		});
		
		Schema schemaForSfdc = service.getSchemaFor(connector2.getId());
        assertNotNull(schemaForSfdc);
        assertFalse(schemaForSfdc.getEntities().isEmpty());
	}

	@Test
	public void getSyncariSchema() {
		Schema syncariSchema = service.getSyncariSchema();
		assertEquals(9, syncariSchema.getEntities().size());
	}

	@Test
	public void getSyncariEntity() {
		Connector syncariConnector = connectorService.getSyncariConnector();
		var id = service.getEntityByName(syncariConnector.getId(), "account");
		Schema syncariSchema = service.getSchemaByEntityId(id.get().getId());
		assertEquals(1, syncariSchema.getEntities().size());
		assertEquals("account", syncariSchema.getEntities().stream().findFirst().get().getApiName());
	}

	@Test
	public void getPipelineStatusForUnmappedEntity(){
		EntityDef def= service.getSyncariSchema().getEntities().get(0);
		assertEquals(PipelineStatus.UNMAPPED, def.getPipelineStatus());
	}

	@Test
	public void getPipelineStatus(){
		service.setMappingGraphService(mappingGraphService);
		EntityDefinition syncariEntity = service.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		EntityDef accountEntityDef = service.getSyncariSchema().getEntities()
				.stream()
				.filter(e -> e.getId().equals(syncariEntity.getId()))
				.findFirst()
				.get();
		assertEquals(PipelineStatus.DRAFT, accountEntityDef.getPipelineStatus());

		var approved = mappingGraphService.approveDraft(defaultEntityGraph);
		accountEntityDef = service.getSyncariSchema().getEntities()
				.stream()
				.filter(e -> e.getId().equals(syncariEntity.getId()))
				.findFirst()
				.get();
		assertEquals(PipelineStatus.PUBLISHED, accountEntityDef.getPipelineStatus());

		MappingGraph graph2 = mappingGraphService.createDraftFor(approved);
		accountEntityDef = service.getSyncariSchema().getEntities()
				.stream()
				.filter(e -> e.getId().equals(syncariEntity.getId()))
				.findFirst()
				.get();
		assertEquals(PipelineStatus.PUBLISHED_WITH_DRAFT, accountEntityDef.getPipelineStatus());

	}

	@Test
	public void deleteEntityReferenced(){
		service.setMappingGraphService(mappingGraphService);

		Schema syncariSchema = service.getSyncariSchema();
		EntityDef contact = syncariSchema.getEntities().stream().filter(e -> e.getApiName().equals("contact")).findFirst().get();

		try{
			service.deleteEntity(contact.getId());
			fail();
		}catch (Exception e){
			e.printStackTrace();
			assertTrue(e.getMessage().contains("entity cannot be deleted because it is referred"));
		}
	}

	@Test
	public void deleteEntityDraftAndPublished(){
		service.setMappingGraphService(mappingGraphService);
		EntityDefinition syncariEntity = entityDefinitionCache
				.findByConnectorId(connectorService.getSyncariConnector().getId())
				.stream()
				.filter(e -> e.getApiName().equals("ticket"))
				.map(e -> service.findEntity(e.getId()).get())
				.findFirst()
				.get();

		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		EntityDef ticketEntityDef = service.getSyncariSchema().getEntities()
				.stream()
				.filter(e -> e.getId().equals(syncariEntity.getId()))
				.findFirst()
				.get();
		assertEquals(PipelineStatus.DRAFT, ticketEntityDef.getPipelineStatus());
		try{
			service.deleteEntity(ticketEntityDef.getId());
		}catch (Exception e){
			assertTrue(e.getMessage().contains("entity is mapped and cannot be deleted"));
		}

		mappingGraphService.approveDraft(defaultEntityGraph);
		ticketEntityDef = service.getSyncariSchema().getEntities()
				.stream()
				.filter(e -> e.getId().equals(syncariEntity.getId()))
				.findFirst()
				.get();
		assertEquals(PipelineStatus.PUBLISHED, ticketEntityDef.getPipelineStatus());
		try{
			service.deleteEntity(ticketEntityDef.getId());
		}catch (Exception e){
			assertTrue(e.getMessage().contains("entity is mapped and cannot be deleted"));
		}
	}

	@Test
	public void deleteEntityClearUnresolvedReference(){
		service.setMappingGraphService(mappingGraphService);

		Connector connector2 = getConnector();
		EntityDefinition entity = SchemaHelper.createEntityDef("newEntity", "Entity To Delete", connector2);
		entity = entityDefinitionCache.save(entity);

		UnresolvedReference reference = new UnresolvedReference();
		reference.setConnectorId(connector2.getId());
		reference.setSyncariEntityDefId(entity.getId());
		reference.setExternalRefEntityName("account");
		reference.setSyncariRecordId("123");
		reference.setExternalRefRecordId("456");
		reference.setSyncariAttributeName("Name");
		unresolvedReferenceRepo.save(reference);

		assertFalse(unresolvedReferenceRepo.getBySyncariEntityDefId(entity.getId()).isEmpty());
		service.deleteEntity(entity.getId());

		assertTrue(unresolvedReferenceRepo.getBySyncariEntityDefId(entity.getId()).isEmpty());
	}

	@Test
	public void deleteEntityUnmapped(){
		service.setMappingGraphService(mappingGraphService);

		Connector connector2 = getConnector();
		EntityDefinition entity = SchemaHelper.createEntityDef("newEntity", "Entity To Delete", connector2);
		entity = entityDefinitionCache.save(entity);

		int initialSize = service.getEntities(connector2.getId()).size();
		assertEquals(initialSize, service.getEntities(connector2.getId()).size());

		service.deleteEntity(entity.getId());

		assertEquals(initialSize - 1, service.getEntities(connector2.getId()).size());
	}
	
	@Test
	public void addDraftEntityIncorrectDraftStatus() {
		try {
			var e = new EntityDefinition("account", "account");
			e.setDraftStatus(DraftStatus.APPROVED);
			service.createDraftEntity(e, false);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Cannot add/update non draft Entity account", e2.getMessage());
		}

		try {
			var e = new EntityDefinition("account", "account");
			e.setDraftStatus(DraftStatus.ARCHIVED);
			service.createDraftEntity(e, false);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Cannot add/update non draft Entity account", e2.getMessage());
		}

		try {
			var e = new EntityDefinition("account", "account");
			service.createDraftEntity(e, false);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Entity with api name account already exists", e2.getMessage());
		}
		//apiNames are case insensitive
		try {
			var e = new EntityDefinition("Account", "account");
			service.createDraftEntity(e, false);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Entity with api name Account already exists", e2.getMessage());
		}
	}

	@Test
	public void updateDraftEntityIncorrectDraftStatus() {
		try {
			var e = new EntityDefinition("account", "account");
			e.setDraftStatus(DraftStatus.APPROVED);
			service.updateDraftEntity(e);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Cannot add/update non draft Entity account", e2.getMessage());
		}

		try {
			var e = new EntityDefinition("account", "account");
			e.setDraftStatus(DraftStatus.ARCHIVED);
			service.updateDraftEntity(e);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Cannot add/update non draft Entity account", e2.getMessage());
		}
	}

	@Test
	public void upsertSyncariEntityWithoutAttributes() {
		// there is an existing approved account entity
		Connector syncariConnector = connectorService.getSyncariConnector();
		var approvedAccEntity = service.getEntity(connectorService.getSyncariConnector().getId(), "account");
		assertTrue(approvedAccEntity.isApproved());
		approvedAccEntity.getAttributes().forEach(a -> assertTrue(a.isApproved()));

		// create new entity with same api name as existing - failure
		try {
			var e = new EntityDefinition("account", "Account");
			service.createDraftEntity(e, false);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Entity with api name account already exists", e2.getMessage());
		}

		// create new entity without attributes
		var e = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		e.setDraftStatus(DraftStatus.NEW);
		var saved = service.createDraftEntity(e, false);
		var retrieved = service.getEntity(saved.getId());
		assertTrue(retrieved.isDraft());
		// entity has default attributes
		assertEquals(3, retrieved.getAttributes().size());
		retrieved.getAttributes().forEach( a -> {
			assertTrue(a.isSystem());
			assertFalse(a.isNillable());
		});
		var a = retrieved.getFieldByName("Id");
		assertTrue(a.isIdField());
		a = retrieved.getFieldByName("LastModifiedDate");
		assertTrue(a.isWatermarkField());
		a = retrieved.getFieldByName("CreatedDate");
		assertTrue(a.isCreatedAtField());

		assertEquals("testEntity", retrieved.getApiName());
		assertEquals(1, tagService.findTagsFor(Taggable.entity, retrieved.getId()).size());
		assertEquals(Status.ACTIVE, retrieved.getStatus());

		// error updating apiName for entity
		try{
			retrieved.setApiName("testEntityUpdated");
			service.updateDraftEntity(retrieved);
			fail();
		} catch (SyncariValidationException e2){
			assertEquals("Cannot update API Name for entity testEntity", e2.getMessage());
		}


		// update draft entity - change display name
		retrieved.setApiName("testEntity");
		retrieved.setDisplayName("NewDisplayName");
		retrieved.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		saved = service.updateDraftEntity(retrieved);
		retrieved = service.getEntity(saved.getId());
		assertTrue(retrieved.isDraft());
		assertEquals("testEntity", retrieved.getApiName());
		assertEquals("NewDisplayName", retrieved.getDisplayName());
		assertEquals(1, tagService.findTagsFor(Taggable.entity, retrieved.getId()).size());

		try {
			e = new EntityDefinition("account", "account");
			e.setDraftStatus(DraftStatus.NEW);
			e.setId(approvedAccEntity.getId());
			service.updateDraftEntity(e);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals(String.format("NEW Entity with Id %s does not exist", approvedAccEntity.getId()), e2.getMessage());
		}

		// updateDraft by using incorrectId
		try {
			e = new EntityDefinition("account", "account");
			e.setDraftStatus(DraftStatus.NEW);
			e.setId("randomId");
			service.updateDraftEntity(e);
			fail();
		} catch (RuntimeException e2) {
			assertEquals("Entity with id randomId not found", e2.getMessage());
		}
	}

	@Test
	public void upsertSyncariEntityWithAttributes() {
		Connector syncariConnector = connectorService.getSyncariConnector();

		// create new entity with attributes
		var e = new EntityDefinition("testEntity2", "TestEntity2")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		e.setDraftStatus(DraftStatus.NEW);
		var attrib = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null)));
		e.addField(attrib);
		var saved = service.createDraftEntity(e, false);
		var retrieved = service.getEntity(saved.getId());
		assertTrue(retrieved.isDraft());
		assertEquals("testEntity2", retrieved.getApiName());
		assertEquals(Status.ACTIVE, retrieved.getStatus());
		var tags = tagService.findTagsFor(Taggable.entity, retrieved.getId());
		assertEquals(1, tags.size());
		assertEquals("testEntityTag", tags.get(0).getName());
		assertEquals(4, retrieved.getAttributes().size());
		assertTrue(retrieved.hasField("testAttribute"));
		String entityId = retrieved.getId();
		var a = retrieved.getFieldByName("testAttribute");
		assertTrue(a.isDraft());
		assertEquals(entityId, a.getEntityId());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());
		assertEquals(Status.ACTIVE, a.getStatus());

		// update draft entity - remove testAttribute and add testAttribute2
		List<AttributeDefinition> idAndWmFields = retrieved.getAttributes().stream()
				.filter(atr -> atr.isIdField() || atr.isWatermarkField())
				.collect(Collectors.toList());
		retrieved.setAttributes(idAndWmFields); // only keep id and wm fields
		var attrib2 = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType());
		attrib.setDraftStatus(DraftStatus.NEW);
		retrieved.addField(attrib2);
		// remove
		retrieved.setTags(List.of(new Tag("testEntityTag2", true, Taggable.entity, null)));
		saved = service.updateDraftEntity(retrieved);
		retrieved = service.getEntity(saved.getId());
		assertTrue(retrieved.isDraft());
		assertEquals("testEntity2", retrieved.getApiName());
		assertEquals(Status.ACTIVE, retrieved.getStatus());
		tags = tagService.findTagsFor(Taggable.entity, retrieved.getId());
		assertEquals(1, tags.size());
		assertEquals("testEntityTag2", tags.get(0).getName());
		assertFalse(retrieved.hasField("testAttribute"));
		assertTrue(retrieved.hasField("testAttribute2"));
		// new attribute has no tags
		a = retrieved.getFieldByName("testAttribute2");
		assertTrue(tagService.findTagsFor(Taggable.attribute, a.getId()).isEmpty());
	}

	@Test
	public void upsertDraftAttribute(){
		var syncariConnector = connectorService.getSyncariConnector();
		// add attribute to approved entity - failure
		var approvedAccEntity = service.getEntity(connectorService.getSyncariConnector().getId(), "account");
		var attributeToSave = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null)));
		try {
			service.createDraftAttribute(approvedAccEntity.getId(), attributeToSave);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("New Field can only be added/updated in draft entity", e2.getMessage());
		}

		EntityDefinition entity = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		entity = service.createDraftEntity(entity, false);
		assertEquals(1, tagService.findTagsFor(Taggable.entity, entity.getId()).size());

		// add attribute to draft entity
		var draftAttrib = service.createDraftAttribute(entity.getId(), attributeToSave);
		assertTrue(draftAttrib.isDraft());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, draftAttrib.getId()).size());

		var retrieved = service.getEntity(entity.getId());
		assertTrue(retrieved.isDraft());
		assertEquals(4, retrieved.getAttributes().size());
		assertEquals(Status.ACTIVE, retrieved.getStatus());
		var a = retrieved.getFieldByName("testAttribute");
		assertTrue(a.isDraft());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());
		assertEquals(Status.ACTIVE, a.getStatus());


		// save another attribute to entity with same name
		try{
			service.createDraftAttribute(retrieved.getId(), attributeToSave);
			fail();
		} catch (SyncariValidationException e2){
			assertEquals("Attribute with api name testAttribute already exist in entity testEntity", e2.getMessage());
		}

		// error updating api name
		try{
			draftAttrib.setApiName("testAttributeUpdated");
			service.updateDraftAttribute(retrieved.getId(), draftAttrib);
			fail();
		} catch (SyncariValidationException e2){
			assertEquals("Cannot update API Name for attribute testAttribute", e2.getMessage());
		}
		
		// error updating datastore name
		try{
		    draftAttrib.setDataStoreName("invalid name");
		    draftAttrib.setApiName("testAttribute");
		    service.updateDraftAttribute(retrieved.getId(), draftAttrib);
		    fail();
		} catch (SyncariValidationException e2){
		    assertEquals("Invalid datastore name invalid name. Ensure there are no spaces and special characters.", e2.getMessage());
		}

        // error creating duplicate datastore name
        try{
            var attributeToSave1 = new AttributeDefinition().setApiName("testAttribute1").setDisplayName("TestAttribute1").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag1", true, Taggable.attribute, null)));
            attributeToSave1.setDataStoreName("testAttribute1");
            service.createDraftAttribute(retrieved.getId(), attributeToSave1);
            var attributeToSave2 = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag2", true, Taggable.attribute, null)));
            attributeToSave2.setDataStoreName("testAttribute1");
            service.createDraftAttribute(retrieved.getId(), attributeToSave2);
            fail();
        } catch (SyncariValidationException e2){
            assertEquals("Attribute with datastore name testAttribute1 already exist in entity testEntity", e2.getMessage());
        }

        // error updating duplicate datastore name
        try{
            // successfully create one more.
            var attributeToSave2 = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag2", true, Taggable.attribute, null)));
            var draftAttrib2 = service.createDraftAttribute(retrieved.getId(), attributeToSave2);
            // updating the draft
            draftAttrib2.setDataStoreName("testAttribute1");
            service.updateDraftAttribute(retrieved.getId(), draftAttrib2);
            fail();
        } catch (SyncariValidationException e2){
            assertEquals("Attribute with datastore name testAttribute1 already exist in entity testEntity", e2.getMessage());
        }

		draftAttrib.setDataStoreName("testAttribute");
		draftAttrib.setDisplayName("NewTestAttrib");
		draftAttrib.setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null),
				new Tag("testAttributeTag2", true, Taggable.attribute, null))); // remove tag
		var updatedAttrib = service.updateDraftAttribute(retrieved.getId(), draftAttrib);
		assertTrue(updatedAttrib.isDraft());
		assertEquals(draftAttrib.getId(), updatedAttrib.getId());
		assertEquals("NewTestAttrib", updatedAttrib.getDisplayName());
		assertEquals(2, tagService.findTagsFor(Taggable.attribute, updatedAttrib.getId()).size());

		// remove tag
		draftAttrib.setTags(List.of()); // remove tag
		updatedAttrib = service.updateDraftAttribute(retrieved.getId(), draftAttrib);
		assertTrue(updatedAttrib.isDraft());
		assertEquals(draftAttrib.getId(), updatedAttrib.getId());
		assertTrue(tagService.findTagsFor(Taggable.attribute, updatedAttrib.getId()).isEmpty());

		// error updating a Syncari synapse ID field
		try{
			AttributeDefinition idField = entity.getIdField().get();

			idField.setDisplayName("XYZ");
			service.updateDraftAttribute(retrieved.getId(), idField);
			fail();
		} catch (SyncariValidationException e2){
			assertEquals("Changes are not allowed on a Syncari ID field", e2.getMessage());
		}

        
        AttributeDefinition idField = entity.getIdField().get();
        // Resetting an Id field should not throw an error. There are scenarios where we end up with multiple id fields. 
        // We allow resetting one or many of those fields.
        idField.setIdField(false);
        idField.setDisplayName("XYZ");
        service.updateDraftAttribute(retrieved.getId(), idField);
	}

	@Test
	public void addCompositeKeyAttribute(){
		var syncariConnector = connectorService.getSyncariConnector();
		// add attribute to approved entity - failure
		var approvedAccEntity = service.getEntity(connectorService.getSyncariConnector().getId(), "account");
		var attributeToSave = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null)));
		try {
			service.createDraftAttribute(approvedAccEntity.getId(), attributeToSave);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("New Field can only be added/updated in draft entity", e2.getMessage());
		}

		EntityDefinition entity = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		entity = service.createDraftEntity(entity, false);
		assertEquals(1, tagService.findTagsFor(Taggable.entity, entity.getId()).size());

		// add attribute to draft entity
		var draftAttrib = service.createDraftAttribute(entity.getId(), attributeToSave);

		var attributeToSave1 = new AttributeDefinition().setApiName("testAttribute1").setDisplayName("TestAttribute1").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag1", true, Taggable.attribute, null)));
		attributeToSave1.setDataStoreName("testAttribute1");

		service.createDraftAttribute(entity.getId(), attributeToSave1);
		var attributeToSave2 = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag2", true, Taggable.attribute, null)));
		attributeToSave2.setDataStoreName("testAttribute2");
		service.createDraftAttribute(entity.getId(), attributeToSave2);
		Optional<AttributeDefinition> attdef = entity.getAttributes().stream().filter(a -> a.isIdField()).collect(Collectors.toList()).stream().findFirst();
		attdef.ifPresent(a -> {a.setIdField(false);
			a.setDataType(IntegerType.VALUE);
			attributeProxyRepo.save(a);});

		var attributeToSave3 = new AttributeDefinition().setApiName("testAttribute3").setDisplayName("TestAttribute3").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag3", true, Taggable.attribute, null))).setIdField(true).setCompositeKey("testAttribute1|testAttribute2")
				.setNillable(false).setUpdatable(false).setUnique(true);
		attributeToSave2.setDataStoreName("testAttribute3");
		service.createDraftAttribute(entity.getId(), attributeToSave3);


		AttributeDefinition def = service.getAttributeByName(entity.getId(), "testAttribute3");
		assertNotNull(def);
		assertNotNull(def.getCompositeKey());
		assertEquals("testAttribute1|testAttribute2", def.getCompositeKey());
	}

	@Test
	public void approveDraftEntityWithoutAttributes(){
		var syncariConnector = connectorService.getSyncariConnector();
		var e = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId());

		try{
			service.approveDraftEntity(e);
			fail();
		} catch(SyncariValidationException e2){
			assertEquals("Cannot approve draft entity without attributes", e2.getMessage());
		}
	}

	@Test
	public void approveDraftAttribute(){
		var syncariConnector = connectorService.getSyncariConnector();
		EntityDefinition entity = SchemaHelper.createEntityDef("newEntityForAttrChangeTest", "Entity To Delete", syncariConnector);
		entity.setDraftStatus(DraftStatus.NEW);
		AttributeDefinition attr = SchemaHelper.createAttribute("newAttr", new StringType(), entity.getId());
		attr.setDraftStatus(DraftStatus.NEW);
		entity.addField(attr);
		service.mappingGraphService = mapGraph;
		service.upsertEntity(entity);
		service.upsertField(attr);
		try{
			doNothing().when(mapGraph).updateSyncariAttributeChangeForGivenGraph(any(),any());
			service.approveDraftEntity(entity);

			verify(mapGraph,times(2)).updateSyncariAttributeChangeForGivenGraph(any(),any());
		} finally {
			service.mappingGraphService = mappingGraphService;
		}
	}

	@Test
	public void approveDraftEntity(){
		var syncariConnector = connectorService.getSyncariConnector();
		var e = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		e.setDraftStatus(DraftStatus.APPROVED);
		try {
			// Trying to approve non draft entity
			service.approveDraftEntity(e);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Cannot approve non draft entity testEntity", e2.getMessage());
		}

		// save entity with attribute
		e.setDraftStatus(DraftStatus.NEW);
		var attrib = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null)));
		attrib.setDraftStatus(DraftStatus.NEW);
		e.addField(attrib);
		var saved = service.createDraftEntity(e, false);
		var draft = service.getEntity(saved.getId());
		assertTrue(draft.isDraft());
		assertEquals("testEntity", draft.getApiName());
		assertTrue(draft.hasField("testAttribute"));
		assertEquals(1, tagService.findTagsFor(Taggable.entity, draft.getId()).size());
		assertEquals(Status.ACTIVE, draft.getStatus());
		var a = draft.getFieldByName("testAttribute");
		assertTrue(a.isDraft());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());
		assertEquals(Status.ACTIVE, a.getStatus());

		// approve new entity draft
		service.approveDraftEntity(draft);
		var approved = service.getEntity(draft.getId());
		assertTrue(approved.isApproved());
		assertEquals("testEntity", approved.getApiName());
		assertTrue(approved.hasField("testAttribute"));
		assertEquals(1, tagService.findTagsFor(Taggable.entity, approved.getId()).size());
		assertEquals(Status.ACTIVE, approved.getStatus());
		a = approved.getFieldByName("testAttribute");
		assertTrue(a.isApproved());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());
		assertEquals(Status.ACTIVE, a.getStatus());


		// approve draft of published entity
		saved = service.createEntityDraftFor(approved.getId());
		var newDraft = service.getEntity(saved.getId());
		assertFalse(newDraft.equals(draft));
		assertTrue(newDraft.isDraft());
		assertEquals("testEntity", newDraft.getApiName());
		assertEquals(1, tagService.findTagsFor(Taggable.entity, newDraft.getId()).size());
		assertTrue(newDraft.hasField("testAttribute"));
		a = newDraft.getFieldByName("testAttribute");
		assertTrue(a.isDraft());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());

		// add new attribute in new draft entity
		var attrib2 = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType());
		newDraft.addField(attrib2);
		service.createDraftAttribute(newDraft.getId(), attrib2);
		newDraft = service.getEntity(saved.getId());
		assertTrue(newDraft.hasField("testAttribute2"));

		// approved draft will remain unchanged
		approved = service.getEntity(draft.getId());
		assertTrue(approved.isApproved());
		assertEquals("testEntity", approved.getApiName());
		assertTrue(approved.hasField("testAttribute"));
		assertEquals(1, tagService.findTagsFor(Taggable.entity, approved.getId()).size());
		a = approved.getFieldByName("testAttribute");assertTrue(a.isApproved());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());
		assertFalse(approved.hasField("testAttribute2"));

		service.approveDraftEntity(newDraft);
		var newApproved = service.getEntity(draft.getId());
		assertTrue(newApproved.equals(approved));
		assertTrue(newApproved.getId().equals(approved.getId()));
		assertTrue(newApproved.hasField("testAttribute"));
		assertTrue(newApproved.hasField("testAttribute2"));
		a = newApproved.getFieldByName("testAttribute");assertTrue(a.isApproved());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());
		var a2 = newApproved.getFieldByName("testAttribute2");assertTrue(a2.isApproved());

		var archivedEntity = entityDefinitionCache.findById(newDraft.getId());
		assertTrue(archivedEntity.isPresent());
		assertEquals(DraftStatus.ARCHIVED, archivedEntity.get().getDraftStatus());
		assertEquals(String.format("%s_%s_%s", "testEntity", archivedEntity.get().getId(), "DELETED"), archivedEntity.get().getApiName());
		assertTrue(tagService.findTagsFor(Taggable.entity, archivedEntity.get().getId()).isEmpty());

		var archivedAttribute = attributeProxyRepo.findById(newDraft.getFieldByName("testAttribute").getId());
		assertTrue(archivedAttribute.isPresent());
		assertEquals(DraftStatus.ARCHIVED, archivedAttribute.get().getDraftStatus());
		assertEquals(String.format("%s_%s_%s", "testAttribute", archivedAttribute.get().getId(), "DELETED"), archivedAttribute.get().getApiName());
		assertTrue(tagService.findTagsFor(Taggable.attribute, archivedAttribute.get().getId()).isEmpty());

		// testAttribute2 was not created from existing approved hence will not be archived
		var archivedAttribute2 = attributeProxyRepo.findById(newDraft.getFieldByName("testAttribute2").getId());
		assertTrue(archivedAttribute2.isPresent());
		assertEquals(DraftStatus.APPROVED, archivedAttribute2.get().getDraftStatus());
		assertEquals(newApproved.getId(), archivedAttribute2.get().getEntityId());
	}

	@Test
	public void discardDraftEntity(){
		var syncariConnector = connectorService.getSyncariConnector();
		var e = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		e.setDraftStatus(DraftStatus.APPROVED);
		try {
			// Trying to approve non draft entity
			service.discardDraftEntity(e);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Cannot discard non draft entity testEntity", e2.getMessage());
		}

		// save entity with attribute
		e.setDraftStatus(DraftStatus.NEW);
		var attrib = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType());
		attrib.setDraftStatus(DraftStatus.NEW);
		e.addField(attrib);
		var saved = service.createDraftEntity(e, false);
		var retrieved = service.getEntity(saved.getId());
		assertTrue(retrieved.isDraft());
		assertEquals("testEntity", retrieved.getApiName());
		assertTrue(retrieved.hasField("testAttribute"));
		assertEquals(1, tagService.findTagsFor(Taggable.entity, retrieved.getId()).size());
		retrieved.getAttributes().forEach(a -> {
			assertTrue(a.isDraft());
		});

		service.discardDraftEntity(retrieved);
		assertFalse(entityDefinitionCache.findById(retrieved.getId()).isPresent());
		assertFalse(attributeProxyRepo.findById(retrieved.getAttributes().get(0).getId()).isPresent());
		assertTrue(tagService.findTagsFor(Taggable.entity, retrieved.getId()).isEmpty());
	}

	@Test
	public void discardDraftAttribute(){
		var syncariConnector = connectorService.getSyncariConnector();
		EntityDefinition entity = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId());;
		entity = service.createDraftEntity(entity, false);
		var attrib = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
				.setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null)));
		attrib.setDraftStatus(DraftStatus.APPROVED);
		try{
			service.discardDraftAttribute(entity.getId(), attrib);
			fail();
		} catch (SyncariValidationException e2){
			assertEquals("Cannot discard non draft attribute testAttribute", e2.getMessage());
		}
		// discard id field fails
		var idField = entity.getIdField().get();
		try{
			service.discardDraftAttribute(entity.getId(), idField);
			fail();
		} catch (SyncariValidationException e2){
			assertEquals("TestEntity Id is the only Id field in the entity and cannot be deleted.", e2.getMessage());
		}

		attrib.setDraftStatus(DraftStatus.NEW);
		attrib = service.createDraftAttribute(entity.getId(), attrib);
		attrib.setIdField(true).setNillable(false).setUnique(true); // second id field can be discarded
		assertTrue(attrib.isDraft());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, attrib.getId()).size());

		entity = service.getEntity(entity.getId());

		// discard draft attribute
		service.discardDraftAttribute(entity.getId(), attrib);
		assertFalse(attributeProxyRepo.findById(attrib.getId()).isPresent());
		assertTrue(tagService.findTagsFor(Taggable.attribute, attrib.getId()).isEmpty());

		entity = service.getEntity(entity.getId());

		// discard non existing field - failure
		try{
			service.discardDraftAttribute(entity.getId(), attrib);
			fail();
		} catch (SyncariValidationException e2){
			assertEquals("Attribute testAttribute does not exist in entity testEntity", e2.getMessage());
		}
	}

	@Test
	public void createEntityDraftFor(){
		var syncariConnector = connectorService.getSyncariConnector();
		var e = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));

		var attrib = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute")
				.setDataType(new StringType()).setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null)));
		e.addField(attrib);
		var draft = service.createDraftEntity(e, false);

		// create draft from draft entity - failure
		try {
			service.createEntityDraftFor(draft.getId());
		} catch (SyncariValidationException e2){
			assertEquals(String.format("APPROVED Entity with Id %s does not exist", draft.getId()), e2.getMessage());
		}

		service.approveDraftEntity(draft);
		var approved = service.getEntity(draft.getId());
		assertTrue(approved.isApproved());
		assertEquals(1, tagService.findTagsFor(Taggable.entity, approved.getId()).size());
		var a = approved.getFieldByName("testAttribute");
		assertTrue(a.isApproved());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());


		var newDraft = service.createEntityDraftFor(approved.getId());
		assertTrue(newDraft.isDraft());
		assertFalse(approved.getId().equals(newDraft.getId()));
		assertTrue(approved.getId().equals(newDraft.getParentId()));
		assertEquals(1, tagService.findTagsFor(Taggable.entity, newDraft.getId()).size());
		assertFalse(newDraft.getAttributes().isEmpty());
		a = newDraft.getFieldByName("testAttribute");
		assertTrue(a.isDraft());
		assertEquals(newDraft.getId(), a.getEntityId());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());

		// approved remains as is
		approved = service.getEntity(approved.getId());
		assertTrue(approved.isApproved());
		assertEquals(1, tagService.findTagsFor(Taggable.entity, approved.getId()).size());
		a = approved.getFieldByName("testAttribute");
		assertTrue(a.isApproved());
		assertEquals(1, tagService.findTagsFor(Taggable.attribute, a.getId()).size());

	}

	@Test
	public void validateEntityMetadataChanges(){
		var syncariConnector = connectorService.getSyncariConnector();
		var e = new EntityDefinition("testEntity", "TestEntity")
				.setDescription("TestEntity Description").setDataStoreName("TestEntityDatastore")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));

		var attrib = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute")
				.setDataType(new StringType()).setTags(List.of(new Tag("testAttributeTag", true, Taggable.attribute, null)));
		e.addField(attrib);
		var draft = service.createDraftEntity(e, false);

		// change draft entity
		draft.setDisplayName(draft.getDisplayName() + " - Changed");
		draft.setDescription(draft.getDescription() + " - Changed");
		draft.setDataStoreName(draft.getDataStoreName() + "Changed");

		var savedDraft = service.updateDraftEntity(draft);

		service.approveDraftEntity(savedDraft);

		var approved = service.getEntity(savedDraft.getId());
		assertTrue(approved.isApproved());
		assertEquals("TestEntity - Changed", approved.getDisplayName());
		assertEquals("TestEntity Description - Changed", approved.getDescription());
		assertEquals("TestEntityDatastoreChanged", approved.getDataStoreName());

		var newDraft = service.createEntityDraftFor(approved.getId());
		newDraft.setDisplayName(approved.getDisplayName() + " - Changed Again");
		newDraft.setDescription(approved.getDescription() + " - Changed Again");
		newDraft.setDataStoreName(approved.getDataStoreName() + "ChangedAgain");

		savedDraft = service.updateDraftEntity(newDraft);

		service.approveDraftEntity(savedDraft);
		approved = service.getEntity(savedDraft.getId());

		assertEquals("TestEntity - Changed - Changed Again", approved.getDisplayName());
		assertEquals("TestEntity Description - Changed - Changed Again", approved.getDescription());
		assertEquals("TestEntityDatastoreChangedChangedAgain", approved.getDataStoreName());
	}

	@Test
	public void validateSynapseDraftAttribute(){
		var sfdcConnector = getConnector();
		EntityDefinition entity = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(sfdcConnector.getId()).setConnectorTypeId(sfdcConnector.getMetadataId());

		var idField = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
				.setIdField(true);
		// field can't be idfield and wmField both
		try{
			idField.setWatermarkField(true);
			service.validateDraftAttribute(entity, idField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("An attribute can't be IdField and WatermarkField both", e.getMessage());
		}

		// IdField should be required and unique
		try{
			idField.setWatermarkField(false);
			service.validateDraftAttribute(entity, idField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("IdField should be Required and Unique", e.getMessage());
		}
		try{
			idField.setNillable(false);
			idField.setUnique(false);
			service.validateDraftAttribute(entity, idField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("IdField should be Required and Unique", e.getMessage());
		}

		// duplicate id field check
		var secondIdField = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType())
				.setIdField(true);
		entity.setAttributes(List.of(idField));
		secondIdField.setIdField(true).setNillable(false).setUnique(true);
		try{
			service.validateDraftAttribute(entity, secondIdField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("Field testAttribute is an IdField. Cannot create duplicate IdField in an Entity", e.getMessage());
		}

		// WM field should be required and readonly
		var wmField = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
				.setWatermarkField(true).setDataStoreName("WM datastore name");
		try{
			service.validateDraftAttribute(entity, wmField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("WatermarkField should be Required and ReadOnly", e.getMessage());
		}

		try{
			wmField.setNillable(false);
			service.validateDraftAttribute(entity, wmField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("WatermarkField should be Required and ReadOnly", e.getMessage());
		}

		// duplicate id field check
		var secondWmField = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType())
				.setWatermarkField(true);
		entity.setAttributes(List.of(wmField));
		secondWmField.setNillable(false).setUpdatable(false);
		try{
			service.validateDraftAttribute(entity, secondWmField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("Field testAttribute is an WatermarkField. Cannot create duplicate WatermarkField in an Entity", e.getMessage());
		}

	}
	
	@Test
	public void validateSyncariDraftAttribute(){
	    var syncariConnector = connectorService.getSyncariConnector();
	    EntityDefinition entity = new EntityDefinition("testEntity", "TestEntity")
	            .setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId());
	    
	    var idField = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
	            .setIdField(true);
	    // field can't be idfield and wmField both
	    try{
	        idField.setWatermarkField(true);
	        service.validateDraftAttribute(entity, idField);
			fail();
		} catch (SyncariValidationException e){
	    	assertEquals("An attribute can't be IdField and WatermarkField both", e.getMessage());
	    }
	    
	    // IdField should be required and unique
	    try{
	        idField.setWatermarkField(false);
	        service.validateDraftAttribute(entity, idField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("IdField should be Required and Unique", e.getMessage());
		}

	    try{
	        idField.setNillable(false);
	        idField.setUnique(false);
	        service.validateDraftAttribute(entity, idField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("IdField should be Required and Unique", e.getMessage());
	    }
	    
	    // duplicate id field check
	    var secondIdField = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType())
	            .setIdField(true);
	    entity.setAttributes(List.of(idField));
	    secondIdField.setIdField(true).setNillable(false).setUnique(true);
	    try{
	        service.validateDraftAttribute(entity, secondIdField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("Field testAttribute is an IdField. Cannot create duplicate IdField in an Entity", e.getMessage());
	    }
	    
	    // WM field should be required and readonly
	    var wmField = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute").setDataType(new StringType())
	            .setWatermarkField(true).setDataStoreName("WM datastore name");
	    try{
	        service.validateDraftAttribute(entity, wmField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("WatermarkField should be Required and ReadOnly", e.getMessage());
	    }
	    
	    try{
	        wmField.setNillable(false);
	        service.validateDraftAttribute(entity, wmField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("WatermarkField should be Required and ReadOnly", e.getMessage());
	    }
	    
	    wmField.setUpdatable(false);
	    service.validateDraftAttribute(entity, wmField);
	    
	    // duplicate id field check
	    var secondWmField = new AttributeDefinition().setApiName("testAttribute2").setDisplayName("TestAttribute2").setDataType(new StringType())
	            .setWatermarkField(true);
	    entity.setAttributes(List.of(wmField));
	    secondWmField.setNillable(false).setUpdatable(false);
	    try{
	        service.validateDraftAttribute(entity, secondWmField);
			fail();
		} catch (SyncariValidationException e){
			assertEquals("Field testAttribute is an WatermarkField. Cannot create duplicate WatermarkField in an Entity", e.getMessage());
	    }
	}

	@Test
	public void validateDraftAttributeInSynapseEntity(){
		Connector sfdcConnector = getConnector();
		EntityDefinition entity = new EntityDefinition("testEntity", "TestEntity")
				.setConnectorId(sfdcConnector.getId()).setConnectorTypeId(sfdcConnector.getMetadataId());
		entity = entityDefinitionCache.save(entity);
		var parentField = new AttributeDefinition().setApiName("parentAttribute").setDisplayName("ParentAttribute")
				.setDataType(new StringType()).setEntityId(entity.getId());
		parentField = attributeProxyRepo.save(parentField);
		entity.addField(parentField);

		EntityDefinition entity2 = new EntityDefinition("testEntity2", "TestEntity2")
				.setConnectorId(sfdcConnector.getId()).setConnectorTypeId(sfdcConnector.getMetadataId());

		// save field with parent
		var field = new AttributeDefinition().setApiName("testAttribute").setDisplayName("TestAttribute")
				.setDataType(new StringType()).setParentAttributeId("INVALID");
		try{
			// syncariDefined flag need to be set for fields in synapse entity
			service.validateDraftAttribute(entity, field);
			fail();
		} catch (RuntimeException e){
			assertEquals("Only syncari defined fields can be added/updated in synapse entity", e.getMessage());
		}
		field.setSyncariDefined(true);
		try{
			// field with INVALID parentAttributeId not allowed
			service.validateDraftAttribute(entity, field);
			fail();
		} catch (RuntimeException e){
			assertEquals("Attribute with id INVALID not found", e.getMessage());
		}

		field.setParentAttributeId(parentField.getId());

		try{
			// field with parentAttributeId of different entity not allowed
			service.validateDraftAttribute(entity2, field);
			fail();
		} catch (SyncariValidationException e){
			assertEquals(String.format("Parent attribute with id %s is not found in entity %s", parentField.getId(), entity2.getDisplayName()), e.getMessage());
		}
		assertNotNull(field.getParentAttributeId());
		service.validateDraftAttribute(entity, field);
		assertTrue(field.isSyncariDefined());
		assertNotNull(field.getParentAttributeId());
	}

	@Test
	public void populateApiName(){
		Connector syncariConnector = connectorService.getSyncariConnector();
		EntityDefinition sourceEntity = new EntityDefinition("lead", "New Lead");

		String newApiName = service.populateApiName(sourceEntity, syncariConnector);
		assertEquals("lead__c", newApiName);

		sourceEntity = new EntityDefinition("Lead", "New Lead");
		newApiName = service.populateApiName(sourceEntity, syncariConnector);
		assertEquals("Lead__c", newApiName);

	}

	@Test
	public void populateApiName_WithDatasetNameCollision(){
		Connector syncariConnector = connectorService.getSyncariConnector();
		EntityDefinitionCache mockEntityDefinitionRepo = mock(EntityDefinitionCache.class);
		DatasetService mockDatasetService = mock(DatasetService.class);
		var originalEntityDefRepo = service.entityProxyRepo;
		var originalDatasetService = service.datasetService;
		service.entityProxyRepo = mockEntityDefinitionRepo;
		service.datasetService = mockDatasetService;

		try {
			// Mock entity repo to return empty list (no entity collision)
			doReturn(Collections.emptyList())
					.when(mockEntityDefinitionRepo).findEntities(anyString(), anyString());

			// Mock dataset service to simulate that a dataset named "Lead" already exists
			// populateApiName will:
			// 1. Check "Lead" - finds dataset collision, enters while loop
			// 2. Calls populateApiNameWithCounter("Lead") -> "Lead__c"
			// 3. Checks "Lead__c" - no collision, exits loop
			doReturn(Optional.of(new com.syncari.core.model.insights.dataset.Dataset()))
					.when(mockDatasetService).findDatasetByName("Lead");
			doReturn(Optional.empty())
					.when(mockDatasetService).findDatasetByName("Lead__c");

			EntityDefinition sourceEntity = new EntityDefinition("Lead", "Lead");
			String newApiName = service.populateApiName(sourceEntity, syncariConnector);

			// Should append __c suffix to avoid collision with dataset name "Lead"
			assertEquals("Lead__c", newApiName);
		} finally {
			service.entityProxyRepo = originalEntityDefRepo;
			service.datasetService = originalDatasetService;
		}
	}

	// no more error for more than two api names
	@Test
	@Ignore
	public void populateApiName_Error(){
		Connector syncariConnector = connectorService.getSyncariConnector();
		EntityDefinitionCache mockEntityDefinitionRepo = mock(EntityDefinitionCache.class);
		var originalEntityDefRepo = service.entityProxyRepo;
		service.entityProxyRepo = mockEntityDefinitionRepo;

		try {
			doReturn(List.of(new EntityDefinition("Lead", "Lead")))
					.when(mockEntityDefinitionRepo).findEntities(syncariConnector.getId(), "lead");
			doReturn(List.of(new EntityDefinition("Lead", "Lead")))
					.when(mockEntityDefinitionRepo).findEntities(syncariConnector.getId(), "lead__c");
			EntityDefinition sourceEntity = new EntityDefinition("lead", "New Lead");

			service.populateApiName(sourceEntity, syncariConnector);
			fail();
		} catch (RuntimeException e){
			assertEquals("Tried creating Syncari entity with name lead__c, but it already exists", e.getMessage());
		}finally {
			service.entityProxyRepo = originalEntityDefRepo;
		}
	}

    @Test
	public void validateEntityAndAttributes() {
        EntityDefinition testEntity = new EntityDefinition("testEntity", "Test Entity")
                .setConnectorId(connector.getId()).setStatus(Status.ACTIVE);
        testEntity.setDraftStatus(DraftStatus.APPROVED);
        EntityData.SYNCARI_DEFINED_FIELDS.forEach(syncariField -> {
            AttributeDefinition attribute = new AttributeDefinition();
            attribute.setApiName(syncariField);
            testEntity.setAttributes(List.of(attribute));
            try {
                service.validateEntityAndAttributes(testEntity);
            } catch (SyncariValidationException e) {
                assertEquals("Attribute with api name " + syncariField + " in entity testEntity is a syncari defined field. Rename it to a different api name.", 
                    e.getMessage());
            }

            try {
                service.createAttributeInSynapse(connector.getId(), testEntity, attribute);
            } catch (SyncariValidationException e) {
                assertEquals("Attribute with api name " + syncariField + " in entity testEntity is a syncari defined field. Rename it to a different api name.", 
                    e.getMessage());
            }
        });
        
    }

    @Test
	public void initializeEndSystemSchema_duplicateAttributes() {
		DataServiceFactory orgFactory = service.factory;
		try {
			Connector localConnector = getConnector();
			EntitySchema testEntitySchema = new EntitySchema("testEntity", "Test Entity");
			testEntitySchema.addFields(List.of(new AttributeSchema("testField1", "string").setDisplayName("Test Field 1"),
					new AttributeSchema("testField1", "string").setDisplayName("Test Field 1")));

			doReturn(List.of(testEntitySchema)).when(metaService).describeAll(any());
			doReturn(metaService).when(factory).getSchemaService(any());
			service.factory = factory;

			service.initializeEndSystemSchema(localConnector);
			var testEntity = service.findEntity(localConnector.getId(), "testEntity");
			assertTrue(testEntity.isPresent());
			assertEquals(1, testEntity.get().getAttributes().size());
			assertEquals("testField1", testEntity.get().getAttributes().get(0).getApiName());
			// cleanup
			entityDefinitionCache.delete(testEntity.get());
			attributeProxyRepo.deleteAll(testEntity.get().getAttributes());
		} finally {
			service.factory = orgFactory;
		}

	}
	
    private Connector getConnector() {
        if(activatedConnector == null) {
            ConnectorMetadata metadata = connectorService.describe(Constants.TEST_SYNAPSE);
            activatedConnector = new Connector("testSynapse1", metadata.getId(), "http://someurl");
            activatedConnector.setMetadata(metadata);
            //activatedConnector.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
            Connector saved = connectorService.save(activatedConnector);
            connectorService.authenticated(saved.getId());
            connectorService.activate(saved.getId());
        }
        return activatedConnector;
    }

    @Test
	public void dynamodbSchemaRefresh(){

		ConnectorService mockConnectorService = mock(ConnectorService.class);
		MetadataService mockDataService = mock(MetadataService.class);
		DataServiceFactory mockDataServiceFactory = mock(DataServiceFactory.class);

		ConnectorMetadata metadata = connectorService.describe(Constants.DYNAMODB);
		var dynamoConnector = new Connector("dynamo", metadata.getId(), "");
		dynamoConnector.setId("dynamoConnector");
		dynamoConnector.setStatus(ConnectorStatus.ACTIVE);
		EntitySchema lead = new EntitySchema("Lead", "Lead"); // uppercase
		EntitySchema account = new EntitySchema("account", "account"); //lowercase

		when(mockConnectorService.find("dynamoConnector")).thenReturn(Optional.of(dynamoConnector));
		when(mockConnectorService.isSchemaEditable(dynamoConnector)).thenReturn(true);
		when(mockDataServiceFactory.getSchemaService(any())).thenReturn(mockDataService);
		when(mockDataService.describeAll(any())).thenReturn(List.of(lead, account));

		service.connectorService = mockConnectorService;
		service.factory =mockDataServiceFactory;

		// first time schema refresh creates new entities
		List<EntityDefinition> entities = service.refreshSynapseSchema(dynamoConnector.getId());
		assertEquals(2, entities.size());

		// second time schema refresh should still refresh even for dynamodb
		var refreshed = service.refreshSynapseSchema(dynamoConnector.getId());
		assertEquals(2, refreshed.size());

		var retrieved = service.getEntities(dynamoConnector.getId());
		assertEquals(2, retrieved.size());

		when(mockDataService.describeAll(any())).thenReturn(List.of(lead)); // change it to return single entity

		var refreshedAgain = service.refreshSynapseSchema(dynamoConnector.getId());
		assertFalse(refreshedAgain.isEmpty()); // account is deactivated and lead is skipped as it already exists

		retrieved = service.getEntities(dynamoConnector.getId());
		var acc = retrieved.stream().filter(e -> e.getApiName().equals("account")).findFirst();
		assertTrue(acc.isPresent());
		assertFalse(acc.get().isActive());

	}

	@Test
	public void updateDatastoreName() {

		Connector syncariConnector = connectorService.getSyncariConnector();

		// create new entity with attributes
		var e = new EntityDefinition("testEntity2", "TestEntity2")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));
		var a1 = new AttributeDefinition().setApiName("id").setDisplayName("Id").setDataType(StringType.VALUE).setIdField(true);
		a1.setId(ObjectId.get().toHexString());
		e.addField(a1);
		var a2 = new AttributeDefinition().setApiName("updated_at").setDisplayName("Update At").setDataType(DatetimeType.VALUE).setWatermarkField(true);
		a2.setId(ObjectId.get().toHexString());
		e.addField(a2);
		e.setDraftStatus(DraftStatus.NEW);
		service.save(e);
		service.approveDraftEntity(e);


		var e1 = new EntityDefinition("testEntity3", "TestEntity3")
				.setConnectorId(syncariConnector.getId()).setConnectorTypeId(syncariConnector.getMetadataId())
				.setTags(List.of(new Tag("testEntityTag", true, Taggable.entity, null)));

		e1.setDraftStatus(DraftStatus.NEW);
		e1.addField(a1);
		e1.addField(a2);
		service.save(e1);

		e1.setDataStoreName("testEntity2");
		try {
			service.updateDraftEntity(e1);
			fail();
		} catch (SyncariValidationException e2) {
			assertEquals("Entity with datastore name testEntity2 already exists.", e2.getMessage());
		}
	}

	@Test
	public void testDoSchemaRefreshForEntity_CaseInsensitiveDuplicateDetection() {
		Connector connector = getConnector();
		
		// Create an existing APPROVED entity with uppercase name
		EntityDefinition existingEntity = new EntityDefinition("OrderCase", "Order Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		existingEntity.setDraftStatus(DraftStatus.APPROVED);
		existingEntity = entityDefinitionCache.save(existingEntity);
		
		// Add an attribute to the existing entity
		AttributeDefinition existingAttr = new AttributeDefinition()
				.setApiName("amount")
				.setDisplayName("Amount")
				.setDataType(new DoubleType())
				.setEntityId(existingEntity.getId())
				.setStatus(Status.ACTIVE);
		existingAttr.setDraftStatus(DraftStatus.APPROVED);
		existingAttr = attributeProxyRepo.save(existingAttr);
		
		// Create entity map for the test (simulating existing entities in connector)
		Map<String, EntityDefinition> entityMap = new HashMap<>();
		// Note: entityMap uses lowercase keys - this is the actual behavior
		
		// Create incoming entity with lowercase name (case difference)
		EntityDefinition incomingEntity = new EntityDefinition("ordercase", "Order Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		
		// Add a new attribute to incoming entity
		AttributeDefinition newAttr = new AttributeDefinition()
				.setApiName("description")
				.setDisplayName("Description")
				.setDataType(new StringType());
		newAttr.setEntityId("temp");
		newAttr.setStatus(Status.ACTIVE);
		incomingEntity.setAttributes(List.of(newAttr));
		
		// Call doSchemaRefreshForEntity via reflection since it's private
		try {
			java.lang.reflect.Method method = SchemaService.class.getDeclaredMethod("doSchemaRefreshForEntity",
					Connector.class, EntityDefinition.class, Map.class);
			method.setAccessible(true);
			
			EntityDefinition result = (EntityDefinition) method.invoke(service, connector, incomingEntity, entityMap);
			
			// Verify the existing entity was reused (not a new one created)
			assertEquals(existingEntity.getId(), result.getId());
			assertEquals("OrderCase", result.getApiName()); // Original case preserved
			assertEquals(DraftStatus.APPROVED, result.getDraftStatus());
			
			// Verify new attribute was added to existing entity
			assertTrue(result.hasField("description"));
			assertTrue(result.hasField("amount"));
			
		} catch (Exception e) {
			fail("Failed to invoke doSchemaRefreshForEntity: " + e.getMessage());
		}
	}
	
	@Test
	public void testDoSchemaRefreshForEntity_IgnoresDraftEntities() {
		Connector connector = getConnector();
		
		// Create an existing entity with DRAFT status (should be ignored)
		EntityDefinition draftEntity = new EntityDefinition("OrderDraft", "Order Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		draftEntity.setDraftStatus(DraftStatus.NEW); // DRAFT status
		draftEntity = entityDefinitionCache.save(draftEntity);
		
		Map<String, EntityDefinition> entityMap = new HashMap<>();
		
		// Create incoming entity with same name
		EntityDefinition incomingEntity = new EntityDefinition("orderdraft", "Order Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		
		try {
			java.lang.reflect.Method method = SchemaService.class.getDeclaredMethod("doSchemaRefreshForEntity",
					Connector.class, EntityDefinition.class, Map.class);
			method.setAccessible(true);
			
			EntityDefinition result = (EntityDefinition) method.invoke(service, connector, incomingEntity, entityMap);
			
			// Verify a NEW entity was created (draft was ignored)
			assertNotEquals(draftEntity.getId(), result.getId());
			assertEquals("orderdraft", result.getApiName()); // New entity uses incoming case
			assertEquals(DraftStatus.APPROVED, result.getDraftStatus());
			
		} catch (Exception e) {
			fail("Failed to invoke doSchemaRefreshForEntity: " + e.getMessage());
		}
	}
	
	@Test
	public void testDoSchemaRefreshForEntity_AttributeMerging() {
		Connector connector = getConnector();
		
		// Create existing APPROVED entity with one attribute
		EntityDefinition existingEntity = new EntityDefinition("Product", "Product Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		existingEntity.setDraftStatus(DraftStatus.APPROVED);
		existingEntity = entityDefinitionCache.save(existingEntity);
		
		AttributeDefinition existingAttr = new AttributeDefinition()
				.setApiName("name")
				.setDisplayName("Product Name")
				.setDataType(new StringType())
				.setEntityId(existingEntity.getId())
				.setStatus(Status.ACTIVE);
		existingAttr.setDraftStatus(DraftStatus.APPROVED);
		existingAttr.setSyncariDefined(false);
		existingAttr = attributeProxyRepo.save(existingAttr);
		
		Map<String, EntityDefinition> entityMap = new HashMap<>();
		
		// Create incoming entity with multiple attributes (one new, one update)
		EntityDefinition incomingEntity = new EntityDefinition("product", "Product Entity");
		
		// New attribute to be added
		AttributeDefinition newAttr = new AttributeDefinition()
				.setApiName("price")
				.setDisplayName("Price")
				.setDataType(new DoubleType());
		newAttr.setStatus(Status.ACTIVE);
		
		// Existing attribute to be updated
		AttributeDefinition updateAttr = new AttributeDefinition()
				.setApiName("name")
				.setDisplayName("Updated Product Name")
				.setDataType(new StringType());
		updateAttr.setStatus(Status.ACTIVE);
		
		incomingEntity.setAttributes(List.of(newAttr, updateAttr));
		
		try {
			java.lang.reflect.Method method = SchemaService.class.getDeclaredMethod("doSchemaRefreshForEntity",
					Connector.class, EntityDefinition.class, Map.class);
			method.setAccessible(true);
			
			EntityDefinition result = (EntityDefinition) method.invoke(service, connector, incomingEntity, entityMap);
			
			// Verify existing entity was reused
			assertEquals(existingEntity.getId(), result.getId());
			assertEquals("Product", result.getApiName()); // Original case preserved
			
			// Verify both attributes are present
			assertTrue(result.hasField("name"));
			assertTrue(result.hasField("price"));
			
		} catch (Exception e) {
			fail("Failed to invoke doSchemaRefreshForEntity: " + e.getMessage());
		}
	}
	
	@Test
	public void testDoSchemaRefreshForEntity_SystemAttributeProtection() {
		Connector connector = getConnector();
		
		// Create existing APPROVED entity with system-defined attribute
		EntityDefinition existingEntity = new EntityDefinition("ContactTest", "Contact Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		existingEntity.setDraftStatus(DraftStatus.APPROVED);
		existingEntity = entityDefinitionCache.save(existingEntity);
		
		// System-defined attribute (should NOT be updated)
		AttributeDefinition systemAttr = new AttributeDefinition()
				.setApiName("id")
				.setDisplayName("System ID")
				.setDataType(new StringType())
				.setEntityId(existingEntity.getId())
				.setStatus(Status.ACTIVE);
		systemAttr.setDraftStatus(DraftStatus.APPROVED);
		systemAttr.setSyncariDefined(true); // System attribute
		systemAttr = attributeProxyRepo.save(systemAttr);
		
		Map<String, EntityDefinition> entityMap = new HashMap<>();
		
		// Create incoming entity trying to update system attribute
		EntityDefinition incomingEntity = new EntityDefinition("contacttest", "Contact Entity");
		
		AttributeDefinition attemptUpdateSystemAttr = new AttributeDefinition()
				.setApiName("id")
				.setDisplayName("Updated System ID") // Attempting to change display name
				.setDataType(new StringType());
		attemptUpdateSystemAttr.setStatus(Status.ACTIVE);
		
		incomingEntity.setAttributes(List.of(attemptUpdateSystemAttr));
		
		try {
			java.lang.reflect.Method method = SchemaService.class.getDeclaredMethod("doSchemaRefreshForEntity",
					Connector.class, EntityDefinition.class, Map.class);
			method.setAccessible(true);
			
			EntityDefinition result = (EntityDefinition) method.invoke(service, connector, incomingEntity, entityMap);
			
			// Verify existing entity was reused
			assertEquals(existingEntity.getId(), result.getId());
			
			// Verify system attribute was NOT updated (protection worked)
			AttributeDefinition resultAttr = result.getField("id").get();
			assertEquals("System ID", resultAttr.getDisplayName()); // Original name preserved
			assertTrue(resultAttr.isSyncariDefined());
			
		} catch (Exception e) {
			fail("Failed to invoke doSchemaRefreshForEntity: " + e.getMessage());
		}
	}
	
	@Test
	public void testDoSchemaRefreshForEntity_NoApprovedDuplicates_CreatesNewEntity() {
		Connector connector = getConnector();
		
		Map<String, EntityDefinition> entityMap = new HashMap<>();
		
		// Create incoming entity with no existing duplicates
		EntityDefinition incomingEntity = new EntityDefinition("UniqueEntity", "Unique Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		
		AttributeDefinition attr = new AttributeDefinition()
				.setApiName("field1")
				.setDisplayName("Field 1")
				.setDataType(new StringType());
		attr.setStatus(Status.ACTIVE);
		incomingEntity.setAttributes(List.of(attr));
		
		try {
			java.lang.reflect.Method method = SchemaService.class.getDeclaredMethod("doSchemaRefreshForEntity",
					Connector.class, EntityDefinition.class, Map.class);
			method.setAccessible(true);
			
			EntityDefinition result = (EntityDefinition) method.invoke(service, connector, incomingEntity, entityMap);
			
			// Verify new entity was created
			assertNotNull(result.getId());
			assertEquals("UniqueEntity", result.getApiName());
			assertEquals(DraftStatus.APPROVED, result.getDraftStatus());
			assertEquals(Status.ACTIVE, result.getStatus());
			assertEquals(connector.getId(), result.getConnectorId());
			
		} catch (Exception e) {
			fail("Failed to invoke doSchemaRefreshForEntity: " + e.getMessage());
		}
	}
	
	@Test
	public void testDoSchemaRefreshForEntity_DuplicateDetectionWithRepository() {
		Connector connector = getConnector();
		
		// Create one APPROVED entity
		EntityDefinition existingEntity = new EntityDefinition("CustomerEntity", "Customer Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		existingEntity.setDraftStatus(DraftStatus.APPROVED);
		existingEntity = entityDefinitionCache.save(existingEntity);
		
		// Add an attribute to the existing entity
		AttributeDefinition existingAttr = new AttributeDefinition()
				.setApiName("email")
				.setDisplayName("Email")
				.setDataType(new StringType())
				.setEntityId(existingEntity.getId())
				.setStatus(Status.ACTIVE);
		existingAttr.setDraftStatus(DraftStatus.APPROVED);
		existingAttr = attributeProxyRepo.save(existingAttr);
		
		Map<String, EntityDefinition> entityMap = new HashMap<>();
		// entityMap is empty - this simulates the case where the entity is not in the local map
		// but exists in the database with a different case
		
		// Create incoming entity with different case that should find the existing one
		EntityDefinition incomingEntity = new EntityDefinition("customerentity", "Customer Entity")
				.setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		
		// Add a new attribute to incoming entity
		AttributeDefinition newAttr = new AttributeDefinition()
				.setApiName("phone")
				.setDisplayName("Phone Number")
				.setDataType(new StringType());
		newAttr.setStatus(Status.ACTIVE);
		incomingEntity.setAttributes(List.of(newAttr));
		
		try {
			java.lang.reflect.Method method = SchemaService.class.getDeclaredMethod("doSchemaRefreshForEntity",
					Connector.class, EntityDefinition.class, Map.class);
			method.setAccessible(true);
			
			EntityDefinition result = (EntityDefinition) method.invoke(service, connector, incomingEntity, entityMap);
			
			// Verify the existing entity was reused (case-insensitive duplicate detection worked)
			assertEquals(existingEntity.getId(), result.getId());
			assertEquals("CustomerEntity", result.getApiName()); // Original case preserved
			assertEquals(DraftStatus.APPROVED, result.getDraftStatus());
			
			// Verify both attributes are present (merging worked)
			assertTrue(result.hasField("email"));
			assertTrue(result.hasField("phone"));
			
		} catch (Exception e) {
			fail("Failed to invoke doSchemaRefreshForEntity: " + e.getMessage());
		}
	}

	@Test
	public void testApproveDraftEntity_shouldRejectMultipleWatermarks_forNoWatermarkConnector() {
		// Test for SYN-20353: Multiple watermark fields should be rejected even for connectors with noWatermark capability
		ConnectorMetadata metadata = connectorService.describe(Constants.TEST_SYNAPSE);
		Connector testConnector = new Connector("testNoWatermarkConnector", metadata.getId(), "http://test.com");
		testConnector.setMetadata(metadata);
		testConnector = connectorService.save(testConnector);

		// Mock the connector service to return true for supportsNoWatermark
		ConnectorService mockConnectorService = mock(ConnectorService.class);
		when(mockConnectorService.find(testConnector.getId())).thenReturn(Optional.of(testConnector));
		when(mockConnectorService.supportsNoWatermark(testConnector.getId())).thenReturn(true);

		// Store original connector service
		ConnectorService originalConnectorService = service.connectorService;
		service.connectorService = mockConnectorService;

		try {
			// Create entity with multiple watermark fields
			EntityDefinition entity = new EntityDefinition("databricks_entity", "Databricks Entity")
					.setConnectorId(testConnector.getId())
					.setConnectorTypeId(testConnector.getMetadataId())
					.setStatus(Status.ACTIVE);

			AttributeDefinition watermark1 = new AttributeDefinition()
					.setApiName("created_at")
					.setDisplayName("Created At")
					.setDataType(DatetimeType.VALUE)
					.setWatermarkField(true)
					.setEntityId(entity.getId());
			watermark1.setId(ObjectId.get().toHexString());
			watermark1.setStatus(Status.ACTIVE);

			AttributeDefinition watermark2 = new AttributeDefinition()
					.setApiName("updated_at")
					.setDisplayName("Updated At")
					.setDataType(DatetimeType.VALUE)
					.setWatermarkField(true)
					.setEntityId(entity.getId());
			watermark2.setId(ObjectId.get().toHexString());
			watermark2.setStatus(Status.ACTIVE);

			AttributeDefinition idField = new AttributeDefinition()
					.setApiName("id")
					.setDisplayName("ID")
					.setDataType(StringType.VALUE)
					.setIdField(true)
					.setEntityId(entity.getId());
			idField.setId(ObjectId.get().toHexString());
			idField.setStatus(Status.ACTIVE);

			entity.setAttributes(List.of(watermark1, watermark2, idField));
			entity.setDraftStatus(DraftStatus.NEW);
			entity = entityDefinitionCache.save(entity);

			// Try to approve - should fail with validation exception
			service.approveDraftEntity(entity);
			fail("Expected SyncariValidationException for multiple watermarks");

		} catch (SyncariValidationException e) {
			assertEquals("The entity databricks_entity has multiple watermark fields defined", e.getMessage());
		} finally {
			// Restore original connector service
			service.connectorService = originalConnectorService;
		}
	}

	@Test
	public void testApproveDraftEntity_shouldRejectMultipleWatermarks_forRegularConnector() {
		// Test that multiple watermarks are rejected for regular connectors (without noWatermark capability)
		Connector testConnector = getConnector();

		// Mock the connector service to return false for supportsNoWatermark
		ConnectorService mockConnectorService = mock(ConnectorService.class);
		when(mockConnectorService.find(testConnector.getId())).thenReturn(Optional.of(testConnector));
		when(mockConnectorService.supportsNoWatermark(testConnector.getId())).thenReturn(false);

		// Store original connector service
		ConnectorService originalConnectorService = service.connectorService;
		service.connectorService = mockConnectorService;

		try {
			// Create entity with multiple watermark fields
			EntityDefinition entity = new EntityDefinition("regular_entity", "Regular Entity")
					.setConnectorId(testConnector.getId())
					.setConnectorTypeId(testConnector.getMetadataId())
					.setStatus(Status.ACTIVE);

			AttributeDefinition watermark1 = new AttributeDefinition()
					.setApiName("syncari_watermark_timestamp")
					.setDisplayName("Syncari Watermark")
					.setDataType(DatetimeType.VALUE)
					.setWatermarkField(true)
					.setEntityId(entity.getId());
			watermark1.setId(ObjectId.get().toHexString());
			watermark1.setStatus(Status.ACTIVE);

			AttributeDefinition watermark2 = new AttributeDefinition()
					.setApiName("last_modified_date")
					.setDisplayName("Last Modified Date")
					.setDataType(DatetimeType.VALUE)
					.setWatermarkField(true)
					.setEntityId(entity.getId());
			watermark2.setId(ObjectId.get().toHexString());
			watermark2.setStatus(Status.ACTIVE);

			AttributeDefinition idField = new AttributeDefinition()
					.setApiName("id")
					.setDisplayName("ID")
					.setDataType(StringType.VALUE)
					.setIdField(true)
					.setEntityId(entity.getId());
			idField.setId(ObjectId.get().toHexString());
			idField.setStatus(Status.ACTIVE);

			entity.setAttributes(List.of(watermark1, watermark2, idField));
			entity.setDraftStatus(DraftStatus.NEW);
			entity = entityDefinitionCache.save(entity);

			// Try to approve - should fail with validation exception
			service.approveDraftEntity(entity);
			fail("Expected SyncariValidationException for multiple watermarks");

		} catch (SyncariValidationException e) {
			assertEquals("The entity regular_entity has multiple watermark fields defined", e.getMessage());
		} finally {
			// Restore original connector service
			service.connectorService = originalConnectorService;
		}
	}

	@Test
	public void testApproveDraftEntity_shouldPassWithSingleWatermark_forNoWatermarkConnector() {
		// Test that single watermark is allowed for connectors with noWatermark capability
		ConnectorMetadata metadata = connectorService.describe(Constants.TEST_SYNAPSE);
		Connector testConnector = new Connector("testSingleWatermark", metadata.getId(), "http://test.com");
		testConnector.setMetadata(metadata);
		testConnector = connectorService.save(testConnector);

		// Mock the connector service to return true for supportsNoWatermark
		ConnectorService mockConnectorService = mock(ConnectorService.class);
		when(mockConnectorService.find(testConnector.getId())).thenReturn(Optional.of(testConnector));
		when(mockConnectorService.supportsNoWatermark(testConnector.getId())).thenReturn(true);

		// Store original connector service
		ConnectorService originalConnectorService = service.connectorService;
		service.connectorService = mockConnectorService;

		try {
			// Create entity with single watermark field
			EntityDefinition entity = new EntityDefinition("single_watermark_entity", "Single Watermark Entity")
					.setConnectorId(testConnector.getId())
					.setConnectorTypeId(testConnector.getMetadataId())
					.setStatus(Status.ACTIVE);

			AttributeDefinition watermark = new AttributeDefinition()
					.setApiName("updated_at")
					.setDisplayName("Updated At")
					.setDataType(DatetimeType.VALUE)
					.setWatermarkField(true)
					.setEntityId(entity.getId());
			watermark.setId(ObjectId.get().toHexString());
			watermark.setStatus(Status.ACTIVE);

			AttributeDefinition idField = new AttributeDefinition()
					.setApiName("id")
					.setDisplayName("ID")
					.setDataType(StringType.VALUE)
					.setIdField(true)
					.setEntityId(entity.getId());
			idField.setId(ObjectId.get().toHexString());
			idField.setStatus(Status.ACTIVE);

			entity.setAttributes(List.of(watermark, idField));
			entity.setDraftStatus(DraftStatus.NEW);
			entity = entityDefinitionCache.save(entity);

			// Should pass without exception
			service.approveDraftEntity(entity);

			EntityDefinition approved = service.getEntity(entity.getId());
			assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
			assertEquals(1, approved.getAttributes().stream().filter(a -> a.isWatermarkField()).count());

		} finally {
			// Restore original connector service
			service.connectorService = originalConnectorService;
		}
	}

	/*private Connector getMockConnector() {
		Connector connector = new Connector();
		if(activatedConnector == null) {
			ConnectorMetadata metadata = connectorService.describe(Constants.SALESFORCE);
			activatedConnector = new Connector("sfdc1", metadata.getId(),
					config.getSalesforceUrl());
			activatedConnector.setMetadata(metadata);
			activatedConnector.setAuthConfig(new AuthConfig(config.getUser(), config.getPassword(), config.getToken()));
			Connector saved = connectorService.save(activatedConnector);
			connectorService.authenticated(saved.getId());
			connectorService.activate(saved.getId());
		}
		return activatedConnector;
	}*/
}
