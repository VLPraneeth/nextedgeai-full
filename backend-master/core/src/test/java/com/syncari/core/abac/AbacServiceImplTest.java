package com.syncari.core.abac;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.argThat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.User;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.repositories.customer.AbacAttributeValueRepo;
import com.syncari.core.repositories.customer.AbacPolicyRepo;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import com.syncari.utils.KeyValue;

public class AbacServiceImplTest {

  private AbacServiceImpl abacService;

  @Mock
  private AbacAttributeRepo attribRepo;

  @Mock
  private AbacAttributeValueRepo valueRepo;

  @Mock
  private AbacPolicyRepo policyRepo;

  @Mock
  private SyncariNativeAbacServiceImpl syncariNativeAbacService;

  @Mock
  private UserService userService;
  @Mock
  private SchemaService schemaService;
  @Mock
  private DatasetService datasetService;
  @Mock
  private AbacResourceServiceFactory resourceServiceFactory;
  @Mock
  private FeatureService featureService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    abacService = new AbacServiceImpl();
    abacService.attribRepo = attribRepo;
    abacService.valueRepo = valueRepo;
    abacService.policyRepo = policyRepo;
    abacService.syncariNativeAbacService = syncariNativeAbacService;
    abacService.userService = userService;
    abacService.schemaService = schemaService;
    abacService.datasetService = datasetService;
    abacService.resourceServiceFactory = resourceServiceFactory;
    abacService.featureService = featureService;
    abacService = spy(abacService);
    doReturn(true).when(abacService).isUserAttributesInUse(any());
    doReturn(true).when(featureService).isEnabled(Features.ABAC, false);
  }

  @Test
  public void testListResources_ReturnsCombinedResultsFromAllTypes() {
    AbacResource r1 = new AbacResource();
    r1.setDisplayName("res1");
    AbacResource r2 = new AbacResource();
    r2.setDisplayName("res2");

    for (ResourceType type : ResourceType.values()) {
      List<AbacResource> mockList = type == ResourceType.ENTITY ? List.of(r1) : List.of(r2);
      doReturn(mockList).when(abacService).listResources(type);
    }

    List<AbacResource> result = abacService.listResources();

    assertNotNull(result);
    assertFalse(result.isEmpty());

    // Expected total is number of enum values, since each returns 1 item
    assertEquals(ResourceType.values().length, result.size());

    for (ResourceType type : ResourceType.values()) {
      verify(abacService).listResources(type);
    }
  }

  @Test
  public void testListResources_EmptyListsReturned() {
    for (ResourceType type : ResourceType.values()) {
      doReturn(Collections.emptyList()).when(abacService).listResources(type);
    }

    List<AbacResource> result = abacService.listResources();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test(expected = RuntimeException.class)
  public void testListResources_ThrowsExceptionFromOneType() {
    doReturn(Collections.emptyList()).when(abacService).listResources(any(ResourceType.class));
    doThrow(new RuntimeException("test error")).when(abacService).listResources(ResourceType.USER); // example

    abacService.listResources(); // should throw
  }

  @Test
  public void testListResourcesForValues_USER() {
    User user = mock(User.class);
    when(user.getId()).thenReturn("u1");
    when(user.getEmail()).thenReturn("user@example.com");

    when(userService.getAllUsersFromInstance()).thenReturn(List.of(user));

    List<AbacResource> result = abacService.listResourcesForValues(ResourceType.USER);

    assertEquals(1, result.size());
    assertEquals("u1", result.get(0).getId());
    assertEquals("user@example.com", result.get(0).getDisplayName());
    assertEquals(ResourceType.USER, result.get(0).getType());
  }

  @Test
  public void testListResourcesForValues_GLOBAL() {
    List<AbacResource> result = abacService.listResourcesForValues(ResourceType.GLOBAL);

    assertEquals(1, result.size());
    assertEquals("__global__", result.get(0).getId());
    assertEquals("Global", result.get(0).getDisplayName());
    assertEquals(ResourceType.GLOBAL, result.get(0).getType());
  }

  @Test
  public void testListResourcesForValues_ENTITY() {
    EntityDefinition entity = mock(EntityDefinition.class);
    when(entity.getId()).thenReturn("e1");
    when(entity.getDisplayName()).thenReturn("Entity1");

    when(schemaService.getSyncariEntitiesWithoutAbac()).thenReturn(List.of(entity));

    List<AbacResource> result = abacService.listResourcesForValues(ResourceType.ENTITY);

    assertEquals(2, result.size());
    assertEquals("__all__", result.get(0).getId());
    assertEquals("All Entities", result.get(0).getDisplayName());

    assertEquals("e1", result.get(1).getId());
    assertEquals("Entity1", result.get(1).getDisplayName());
    assertEquals(ResourceType.ENTITY, result.get(1).getType());
  }

  @Test
  public void testListResourcesForValues_DATASET() {
    Dataset dataset = mock(Dataset.class);
    when(dataset.getId()).thenReturn("d1");
    when(dataset.getDisplayName()).thenReturn("Dataset1");

    when(datasetService.getAllActiveDatasets()).thenReturn(List.of(dataset));

    List<AbacResource> result = abacService.listResourcesForValues(ResourceType.DATASET);

    assertEquals(2, result.size());
    assertEquals("__all__", result.get(0).getId());
    assertEquals("All Datasets", result.get(0).getDisplayName());

    assertEquals("d1", result.get(1).getId());
    assertEquals("Dataset1", result.get(1).getDisplayName());
    assertEquals(ResourceType.DATASET, result.get(1).getType());
  }

  @Test
  public void testListResourcesForValues_OtherType() {
    // Assume CONNECTOR is a ResourceType that triggers default case
    ResourceType type = ResourceType.ENTITY_DATA;
    List<AbacResource> mockedResult = List.of(new AbacResource(type, "c1", "Connector1"));

    doReturn(mockedResult).when(abacService).listResources(type);

    List<AbacResource> result = abacService.listResourcesForValues(type);

    assertEquals(1, result.size());
    assertEquals("c1", result.get(0).getId());
    assertEquals("Connector1", result.get(0).getDisplayName());
    assertEquals(ResourceType.ENTITY_DATA, result.get(0).getType());
  }

  @Test
  public void testGetResourceForValues_USER_found() {
    String userId = "u123";
    User user = mock(User.class);
    when(user.getId()).thenReturn(userId);
    when(user.getEmail()).thenReturn("user@example.com");

    when(userService.findUserById(userId)).thenReturn(Optional.of(user));

    Optional<AbacResource> result = abacService.getResourceForValues(ResourceType.USER, userId);
    assertTrue(result.isPresent());
    assertEquals(userId, result.get().getId());
    assertEquals("user@example.com", result.get().getDisplayName());
    assertEquals(ResourceType.USER, result.get().getType());
  }

  @Test
  public void testGetResourceForValues_USER_notFound() {
    String userId = "u404";
    when(userService.findUserById(userId)).thenReturn(Optional.empty());

    Optional<AbacResource> result = abacService.getResourceForValues(ResourceType.USER, userId);
    assertFalse(result.isPresent());
  }

  @Test
  public void testGetResourceForValues_GLOBAL() {
    Optional<AbacResource> result =
        abacService.getResourceForValues(ResourceType.GLOBAL, "__global__");
    assertTrue(result.isPresent());
    assertEquals(AbacServiceImpl.ABAC_GLOBAL_RESOURCE, result.get()); // assuming it's public/static
  }

  @Test
  public void testGetResourceForValues_ENTITY_found() {
    String entityId = "e1";
    EntityDefinition entity = mock(EntityDefinition.class);
    when(entity.getId()).thenReturn(entityId);
    when(entity.getDisplayName()).thenReturn("Entity One");

    when(schemaService.findEntity(entityId)).thenReturn(Optional.of(entity));

    Optional<AbacResource> result = abacService.getResourceForValues(ResourceType.ENTITY, entityId);
    assertTrue(result.isPresent());
    assertEquals(entityId, result.get().getId());
    assertEquals("Entity One", result.get().getDisplayName());
    assertEquals(ResourceType.ENTITY, result.get().getType());
  }

  @Test
  public void testGetResourceForValues_ENTITY_notFound() {
    when(schemaService.findEntity("e404")).thenReturn(Optional.empty());

    Optional<AbacResource> result = abacService.getResourceForValues(ResourceType.ENTITY, "e404");
    assertFalse(result.isPresent());
  }

  @Test
  public void testGetResourceForValues_DATASET_found() {
    String datasetId = "d1";
    Dataset dataset = mock(Dataset.class);
    when(dataset.getId()).thenReturn(datasetId);
    when(dataset.getDisplayName()).thenReturn("Dataset One");

    when(datasetService.findDataset(datasetId)).thenReturn(Optional.of(dataset));

    Optional<AbacResource> result =
        abacService.getResourceForValues(ResourceType.DATASET, datasetId);
    assertTrue(result.isPresent());
    assertEquals(datasetId, result.get().getId());
    assertEquals("Dataset One", result.get().getDisplayName());
    assertEquals(ResourceType.DATASET, result.get().getType());
  }

  @Test
  public void testGetResourceForValues_DATASET_notFound() {
    when(datasetService.findDataset("d404")).thenReturn(Optional.empty());

    Optional<AbacResource> result = abacService.getResourceForValues(ResourceType.DATASET, "d404");
    assertFalse(result.isPresent());
  }

  @Test
  public void testGetResourceForValues_OtherType() {
    String connectorId = "c1";
    AbacResource mockResource =
        new AbacResource(ResourceType.ENTITY_DATA, connectorId, "Connector1");

    doReturn(Optional.of(mockResource)).when(abacService).getResource(ResourceType.ENTITY_DATA,
        connectorId);

    Optional<AbacResource> result =
        abacService.getResourceForValues(ResourceType.ENTITY_DATA, connectorId);

    assertTrue(result.isPresent());
    assertEquals(mockResource, result.get());
  }

  @Test
  public void testListResources_USER() {
    List<AbacResource> result = abacService.listResources(ResourceType.USER);

    assertEquals(1, result.size());
    assertEquals(AbacServiceImpl.ABAC_USER_RESOURCE, result.get(0));
  }

  @Test
  public void testListResources_GLOBAL() {
    List<AbacResource> result = abacService.listResources(ResourceType.GLOBAL);

    assertEquals(1, result.size());
    assertEquals(AbacServiceImpl.ABAC_GLOBAL_RESOURCE, result.get(0));
  }

  @Test
  public void testListResources_ENTITY() {
    List<AbacResource> result = abacService.listResources(ResourceType.ENTITY);

    assertEquals(1, result.size());
    assertEquals(AbacServiceImpl.ABAC_ENTITY_RESOURCE, result.get(0));
  }

  @Test
  public void testListResources_ENTITY_DATA() {
    EntityDefinition e1 = mock(EntityDefinition.class);
    EntityDefinition e2 = mock(EntityDefinition.class);
    when(e1.getId()).thenReturn("e1");
    when(e1.getDisplayName()).thenReturn("Entity One");
    when(e2.getId()).thenReturn("e2");
    when(e2.getDisplayName()).thenReturn("Entity Two");

    when(schemaService.getSyncariEntitiesWithoutAbac()).thenReturn(List.of(e1, e2));

    List<AbacResource> result = abacService.listResources(ResourceType.ENTITY_DATA);

    assertEquals(2, result.size());
    assertEquals("e1", result.get(0).getId());
    assertEquals("Entity One", result.get(0).getDisplayName());
    assertEquals(ResourceType.ENTITY_DATA, result.get(0).getType());

    assertEquals("e2", result.get(1).getId());
    assertEquals("Entity Two", result.get(1).getDisplayName());
    assertEquals(ResourceType.ENTITY_DATA, result.get(1).getType());
  }

  @Test
  public void testListResources_DATASET() {
    List<AbacResource> result = abacService.listResources(ResourceType.DATASET);

    assertEquals(1, result.size());
    assertEquals(AbacServiceImpl.ABAC_DATASET_RESOURCE, result.get(0));
  }

  @Test
  public void testListResources_UnknownType() {
    // Create a dummy unknown enum constant via a custom type if necessary, otherwise use null
    List<AbacResource> result = abacService.listResources(null);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testListAttributes_success() {
    AbacAttribute attr1 = new AbacAttribute().setApiName("department").setDisplayName("Department")
        .setResourceType(ResourceType.USER).setResourceId("user-1").setDataType("string")
        .setAllowedValues(Arrays.asList("Engineering", "Sales")).setMultiValued(false);

    AbacAttribute attr2 = new AbacAttribute().setApiName("location").setDisplayName("Location")
        .setResourceType(ResourceType.ENTITY).setResourceId("entity-1").setDataType("string")
        .setAllowedValues(Arrays.asList("NY", "SF")).setMultiValued(true);

    when(attribRepo.findAll()).thenReturn(Arrays.asList(attr1, attr2));

    List<AbacAttribute> result = abacService.listAttributes();

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("department", result.get(0).getApiName());
    assertEquals("location", result.get(1).getApiName());
    assertTrue(result.get(1).isMultiValued());
  }

  @Test
  public void testListAttributes_emptyList() {
    when(attribRepo.findAll()).thenReturn(Collections.emptyList());

    List<AbacAttribute> result = abacService.listAttributes();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test(expected = RuntimeException.class)
  public void testListAttributes_throwsException() {
    when(attribRepo.findAll()).thenThrow(new RuntimeException("DB error"));

    abacService.listAttributes(); // should throw RuntimeException
  }

  @Test
  public void testListAttributes_ByResourceTypeOnly() {
    AbacAttribute attr1 = new AbacAttribute().setApiName("team").setResourceType(ResourceType.USER);
    AbacAttribute attr2 = new AbacAttribute().setApiName("role").setResourceType(ResourceType.USER);

    when(attribRepo.findByResourceType(ResourceType.USER)).thenReturn(Arrays.asList(attr1, attr2));

    List<AbacAttribute> result = abacService.listAttributes(ResourceType.USER, "any-id");

    assertNotNull(result);
    assertEquals(2, result.size());
    verify(attribRepo, times(1)).findByResourceType(ResourceType.USER);
    verify(attribRepo, never()).findByResourceTypeAndResourceId(any(), any());
  }

  @Test
  public void testListAttributes_ByResourceTypeAndId() {
    AbacAttribute attr = new AbacAttribute().setApiName("entityType")
        .setResourceType(ResourceType.ENTITY_DATA).setResourceId("ent-123");

    when(attribRepo.findByResourceTypeAndResourceId(ResourceType.ENTITY_DATA, "ent-123"))
        .thenReturn(List.of(attr));

    List<AbacAttribute> result = abacService.listAttributes(ResourceType.ENTITY_DATA, "ent-123");

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("entityType", result.get(0).getApiName());
    verify(attribRepo, times(1)).findByResourceTypeAndResourceId(ResourceType.ENTITY_DATA,
        "ent-123");
    verify(attribRepo, never()).findByResourceType(any());
  }

  @Test
  public void testListAttributes_EmptyList() {
    when(attribRepo.findByResourceType(ResourceType.USER)).thenReturn(Collections.emptyList());

    List<AbacAttribute> result = abacService.listAttributes(ResourceType.USER, "ignored");

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test(expected = RuntimeException.class)
  public void testListAttributes_Exception() {
    when(attribRepo.findByResourceType(ResourceType.USER))
        .thenThrow(new RuntimeException("DB failure"));

    abacService.listAttributes(ResourceType.USER, "ignored");
  }

  @Test
  public void testListAttributeTokens_User() {
    Map<String, List<KeyValue>> result = abacService.listAttributeTokens(ResourceType.USER, null);

    assertTrue(result.containsKey("Resource"));
    List<KeyValue> tokens = result.get("Resource");
    assertEquals(4, tokens.size());

    List<String> expectedLabels = Arrays.asList("id", "email", "firstName", "lastName");
    for (int i = 0; i < tokens.size(); i++) {
      KeyValue token = tokens.get(i);
      assertEquals(expectedLabels.get(i), token.get("label"));
      assertEquals(expectedLabels.get(i), token.get("shortLabel"));
      assertEquals("{{resource.values." + expectedLabels.get(i) + "}}", token.get("token"));
    }
  }

  @Test
  public void testListAttributeTokens_Entity() {
    Map<String, List<KeyValue>> result = abacService.listAttributeTokens(ResourceType.ENTITY, null);

    assertTrue(result.containsKey("Resource"));
    List<KeyValue> tokens = result.get("Resource");
    assertEquals(1, tokens.size());

    KeyValue token = tokens.get(0);
    assertEquals("All Entities", token.get("label"));
    assertEquals("{{resource.apiName}}", token.get("token"));
  }

  @Test
  public void testListAttributeTokens_EntityData() {
    AttributeDefinition attr1 = new AttributeDefinition().setApiName("city").setDisplayName("City")
        .setDataType(StringType.VALUE);
    attr1.setId("id1");
    AttributeDefinition attr2 = new AttributeDefinition().setApiName("state")
        .setDisplayName("State").setDataType(StringType.VALUE);
    attr2.setId("id2");

    when(schemaService.getAttributesByEntityId("ent1")).thenReturn(Arrays.asList(attr1, attr2));

    Map<String, List<KeyValue>> result =
        abacService.listAttributeTokens(ResourceType.ENTITY_DATA, "ent1");

    assertTrue(result.containsKey("Resource"));
    List<KeyValue> tokens = result.get("Resource");
    assertEquals(2, tokens.size());

    assertEquals("City", tokens.get(0).get("label"));
    assertEquals("{{resource.values.city}}", tokens.get(0).get("token"));
    assertEquals("State", tokens.get(1).get("label"));
    assertEquals("{{resource.values.state}}", tokens.get(1).get("token"));
  }

  @Test
  public void testListAttributeTokens_Dataset() {
    Map<String, List<KeyValue>> result =
        abacService.listAttributeTokens(ResourceType.DATASET, "ds1");

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testGetAttribute_Found() {
    String attributeId = "attr1";
    AbacAttribute attr = new AbacAttribute().setDisplayName("Test Attribute");
    attr.setId(attributeId);
    when(attribRepo.findById(attributeId)).thenReturn(Optional.of(attr));

    Optional<AbacAttribute> result = abacService.getAttribute(attributeId);

    assertTrue(result.isPresent());
    assertEquals("Test Attribute", result.get().getDisplayName());
    verify(attribRepo).findById(attributeId);
  }

  @Test
  public void testGetAttribute_NotFound() {
    String attributeId = "attr2";
    when(attribRepo.findById(attributeId)).thenReturn(Optional.empty());

    Optional<AbacAttribute> result = abacService.getAttribute(attributeId);

    assertFalse(result.isPresent());
    verify(attribRepo).findById(attributeId);
  }

  @Test
  public void testSaveAttribute_NewAttribute_NoDuplicate() {
    AbacAttribute attr = new AbacAttribute().setResourceType(ResourceType.USER)
        .setResourceId("res1").setApiName("api1");

    when(attribRepo.findByResourceTypeAndResourceIdAndApiName(attr.getResourceType(),
        attr.getResourceId(), attr.getApiName())).thenReturn(Optional.empty());
    when(syncariNativeAbacService.saveAttribute(any(AbacAttribute.class))).thenAnswer(i -> i.getArgument(0));
    when(attribRepo.save(any(AbacAttribute.class))).thenAnswer(i -> i.getArgument(0));

    AbacAttribute saved = abacService.saveAttribute(attr);

    assertNotNull(saved);
    verify(attribRepo).findByResourceTypeAndResourceIdAndApiName(attr.getResourceType(),
        attr.getResourceId(), attr.getApiName());
    verify(syncariNativeAbacService).saveAttribute(attr);
    verify(attribRepo).save(attr);
  }

  @Test(expected = SyncariValidationException.class)
  public void testSaveAttribute_NewAttribute_Duplicate() {
    AbacAttribute attr = new AbacAttribute().setResourceType(ResourceType.USER)
        .setResourceId("res1").setApiName("api1");

    AbacAttribute duplicate = new AbacAttribute();
    duplicate.setId("dupId");

    when(attribRepo.findByResourceTypeAndResourceIdAndApiName(attr.getResourceType(),
        attr.getResourceId(), attr.getApiName())).thenReturn(Optional.of(duplicate));

    abacService.saveAttribute(attr);
  }

  @Test
  public void testSaveAttribute_ExistingAttribute_NoDuplicate() {
    AbacAttribute attr = new AbacAttribute().setResourceType(ResourceType.USER)
        .setResourceId("res1").setApiName("api1");
    attr.setId("id1");

    AbacAttribute existingAttr = new AbacAttribute().setApiName("api1");
    existingAttr.setId("id1");

    when(attribRepo.findByResourceTypeAndResourceIdAndApiName(attr.getResourceType(),
        attr.getResourceId(), attr.getApiName())).thenReturn(Optional.of(existingAttr));
    when(attribRepo.findById(attr.getId())).thenReturn(Optional.of(existingAttr));
    when(syncariNativeAbacService.saveAttribute(any(AbacAttribute.class))).thenAnswer(i -> i.getArgument(0));
    when(attribRepo.save(any(AbacAttribute.class))).thenAnswer(i -> i.getArgument(0));

    AbacAttribute saved = abacService.saveAttribute(attr);

    assertNotNull(saved);
    verify(attribRepo).findByResourceTypeAndResourceIdAndApiName(attr.getResourceType(),
        attr.getResourceId(), attr.getApiName());
    verify(attribRepo).findById(attr.getId());
    verify(syncariNativeAbacService).saveAttribute(existingAttr);
    verify(attribRepo).save(existingAttr);
  }

  @Test(expected = SyncariValidationException.class)
  public void testSaveAttribute_ExistingAttribute_DuplicateDifferentId() {
    AbacAttribute attr = new AbacAttribute().setResourceType(ResourceType.USER)
        .setResourceId("res1").setApiName("api1");
    attr.setId("id1");

    AbacAttribute duplicate = new AbacAttribute().setApiName("api1");
    duplicate.setId("id2"); // different id from attr.getId()

    AbacAttribute existingAttr = new AbacAttribute().setApiName("api1");
    existingAttr.setId("id1");

    when(attribRepo.findByResourceTypeAndResourceIdAndApiName(attr.getResourceType(),
        attr.getResourceId(), attr.getApiName())).thenReturn(Optional.of(duplicate));
    when(attribRepo.findById(attr.getId())).thenReturn(Optional.of(existingAttr));

    abacService.saveAttribute(attr);
  }

  @Test(expected = SyncariValidationException.class)
  public void testSaveAttribute_ExistingAttribute_NotFoundById() {
    AbacAttribute attr = new AbacAttribute().setResourceType(ResourceType.USER)
        .setResourceId("res1").setApiName("api1");
    attr.setId("id1");

    when(attribRepo.findByResourceTypeAndResourceIdAndApiName(attr.getResourceType(),
        attr.getResourceId(), attr.getApiName())).thenReturn(Optional.empty());
    when(attribRepo.findById(attr.getId())).thenReturn(Optional.empty());

    abacService.saveAttribute(attr);
  }

  @Test
  public void testDeleteAttribute_AttributeNotFound_DoesNothing() {
    when(attribRepo.findById("attr1")).thenReturn(Optional.empty());

    abacService.deleteAttribute("attr1");

    verify(valueRepo, never()).countByAttributeId(anyString());
    verify(policyRepo, never()).findAll();
    verify(syncariNativeAbacService, never()).deleteAttribute(any());
    verify(attribRepo, never()).deleteById(anyString());
  }

  @Test
  public void testDeleteAttribute_AttributeHasValues_ThrowsException() {
    AbacAttribute attr = new AbacAttribute().setDisplayName("Attribute1");
    attr.setId("attr1");
    when(attribRepo.findById("attr1")).thenReturn(Optional.of(attr));
    when(valueRepo.countByAttributeId("attr1")).thenReturn(3L);

    try {
      abacService.deleteAttribute("attr1");
      fail("Expected SyncariValidationException was not thrown");
    } catch (SyncariValidationException e) {
    }

    verify(policyRepo, never()).findAll();
    verify(syncariNativeAbacService, never()).deleteAttribute(any());
    verify(attribRepo, never()).deleteById(anyString());
  }

  @Test
  public void testDeleteAttribute_AttributeUsedInPolicy_ThrowsException() {
    AbacAttribute attr = new AbacAttribute().setDisplayName("Attribute1");
    attr.setId("attr1");
    when(attribRepo.findById("attr1")).thenReturn(Optional.of(attr));
    when(valueRepo.countByAttributeId("attr1")).thenReturn(0L);

    Map<String, Object> predicate = Map.of("==", List.of("attr1", "someValue"));
    AbacPolicy policy = mock(AbacPolicy.class);
    when(policy.getCondition()).thenReturn(predicate);
    when(policy.getName()).thenReturn("PolicyA");
    when(policyRepo.findAll()).thenReturn(List.of(policy));

    try {
      abacService.deleteAttribute("attr1");
      fail("Expected SyncariValidationException was not thrown");
    } catch (SyncariValidationException e) {
    }

    verify(syncariNativeAbacService, never()).deleteAttribute(any());
    verify(attribRepo, never()).deleteById(anyString());
  }

  @Test
  public void testDeleteAttribute_AttributeSafeToDelete() {
    // Mock the attribute
    AbacAttribute attr = new AbacAttribute().setDisplayName("Attribute1");
    attr.setId("attr1");
    when(attribRepo.findById("attr1")).thenReturn(Optional.of(attr));

    // No values present
    when(valueRepo.countByAttributeId("attr1")).thenReturn(0L);

    // Mock a policy with empty condition map

    AbacPolicy mockPolicy = mock(AbacPolicy.class);
    when(mockPolicy.getCondition()).thenReturn(getCondition());
    when(mockPolicy.getName()).thenReturn("MockPolicy");

    // Return a list with this mock policy
    when(policyRepo.findAll()).thenReturn(List.of(mockPolicy));

    // Proceed with mocks for syncariNativeAbacService and attribRepo
    doNothing().when(syncariNativeAbacService).deleteAttribute(attr);
    doNothing().when(attribRepo).deleteById("attr1");

    // Call the method under test
    abacService.deleteAttribute("attr1");

    // Verifications
    verify(syncariNativeAbacService).deleteAttribute(attr);
    verify(attribRepo).deleteById("attr1");
  }

  private Map<String, Object> getCondition() {
    Map<String, Object> left = new HashMap<>();
    left.put("label", "All Entities.Dept");
    left.put("value", "682aaf8dab103b676cfbbcf4");
    left.put("datatype", "multivaluetext");
    left.put("type", "variable");

    Map<String, Object> right = new HashMap<>();
    right.put("type", "literal");

    Map<String, Object> predicate = new HashMap<>();
    predicate.put("left", left);
    predicate.put("operator", "not_empty");
    predicate.put("right", right);
    predicate.put("predicateId", "682aafea0c18db050d215d17");
    predicate.put("name", "condition");

    List<Map<String, Object>> predicates = new ArrayList<>();
    predicates.add(predicate);

    Map<String, Object> root = new HashMap<>();
    root.put("predicates", predicates);
    root.put("groupPredicateId", "682aafea0c18db050d215d18");
    root.put("operator", "AND");

    return root;
  }

  @Test
  public void testListPolicies_ShouldReturnAllPolicies() {
    AbacPolicy policy1 = new AbacPolicy();
    AbacPolicy policy2 = new AbacPolicy();
    List<AbacPolicy> mockPolicies = Arrays.asList(policy1, policy2);

    when(policyRepo.findAll()).thenReturn(mockPolicies);

    List<AbacPolicy> result = abacService.listPolicies();

    assertEquals(2, result.size());
    assertSame(policy1, result.get(0));
    assertSame(policy2, result.get(1));
    verify(policyRepo, times(1)).findAll();
  }

  @Test
  public void testGetPolicy_WhenPolicyExists_ShouldReturnPolicy() {
    String policyId = "policy123";
    AbacPolicy mockPolicy = new AbacPolicy();
    mockPolicy.setId(policyId);

    when(policyRepo.findById(policyId)).thenReturn(Optional.of(mockPolicy));

    Optional<AbacPolicy> result = abacService.getPolicy(policyId);

    assertTrue(result.isPresent());
    assertEquals(policyId, result.get().getId());
    verify(policyRepo, times(1)).findById(policyId);
  }

  @Test
  public void testGetPolicy_WhenPolicyDoesNotExist_ShouldReturnEmptyOptional() {
    String policyId = "nonexistent";

    when(policyRepo.findById(policyId)).thenReturn(Optional.empty());

    Optional<AbacPolicy> result = abacService.getPolicy(policyId);

    assertFalse(result.isPresent());
    verify(policyRepo, times(1)).findById(policyId);
  }
  
  @Test
  public void testSavePolicy_NewPolicy_ShouldSaveSuccessfully() {
      AbacPolicy newPolicy = new AbacPolicy(); // no ID
      AbacPolicy savedPolicy = new AbacPolicy();
      savedPolicy.setId("new-id");

      when(syncariNativeAbacService.savePolicy(newPolicy)).thenReturn(newPolicy);
      when(policyRepo.save(newPolicy)).thenReturn(savedPolicy);

      AbacPolicy result = abacService.savePolicy(newPolicy);

      assertEquals("new-id", result.getId());
      verify(syncariNativeAbacService).savePolicy(newPolicy);
      verify(policyRepo).save(newPolicy);
  }

  @Test
  public void testSavePolicy_ExistingPolicy_ShouldUpdateSuccessfully() {
      AbacPolicy inputPolicy = new AbacPolicy();
      inputPolicy.setId("existing-id");

      AbacPolicy dbPolicy = mock(AbacPolicy.class);
      AbacPolicy updatedPolicy = new AbacPolicy();
      updatedPolicy.setId("existing-id");

      when(policyRepo.findById("existing-id")).thenReturn(Optional.of(dbPolicy));
      when(dbPolicy.copyFrom(inputPolicy)).thenReturn(dbPolicy);
      when(syncariNativeAbacService.savePolicy(dbPolicy)).thenReturn(dbPolicy);
      when(policyRepo.save(dbPolicy)).thenReturn(updatedPolicy);

      AbacPolicy result = abacService.savePolicy(inputPolicy);

      assertEquals("existing-id", result.getId());
      verify(policyRepo).findById("existing-id");
      verify(dbPolicy).copyFrom(inputPolicy);
      verify(syncariNativeAbacService).savePolicy(dbPolicy);
      verify(policyRepo).save(dbPolicy);
  }

  @Test(expected = SyncariValidationException.class)
  public void testSavePolicy_ExistingPolicyNotFound_ShouldThrowException() {
      AbacPolicy inputPolicy = new AbacPolicy();
      inputPolicy.setId("missing-id");

      when(policyRepo.findById("missing-id")).thenReturn(Optional.empty());

      abacService.savePolicy(inputPolicy);
  }
  
  @Test
  public void testDeletePolicy_ShouldCallDeleteById() {
      String policyId = "policy-123";
      AbacPolicy dbPolicy = mock(AbacPolicy.class);
      when(policyRepo.findById(policyId)).thenReturn(Optional.of(dbPolicy));
      abacService.deletePolicy(policyId);
      verify(policyRepo).findById(policyId);
      verify(policyRepo).deleteById(policyId);
  }

  @Test
  public void testListAttributeValues_ShouldReturnAllValues() {
      AbacAttributeValue value1 = new AbacAttributeValue();
      AbacAttributeValue value2 = new AbacAttributeValue();
      List<AbacAttributeValue> mockValues = List.of(value1, value2);

      when(valueRepo.findAll()).thenReturn(mockValues);

      List<AbacAttributeValue> result = abacService.listAttributeValues();

      assertEquals(2, result.size());
      assertSame(value1, result.get(0));
      assertSame(value2, result.get(1));
      verify(valueRepo).findAll();
  }

  @Test
  public void testGetAttributeValue_Found() {
      String attrValueId = "val-1";
      AbacAttributeValue value = new AbacAttributeValue();

      when(valueRepo.findById(attrValueId)).thenReturn(Optional.of(value));

      Optional<AbacAttributeValue> result = abacService.getAttributeValue(attrValueId);

      assertTrue(result.isPresent());
      assertSame(value, result.get());
      verify(valueRepo).findById(attrValueId);
  }

  @Test
  public void testGetAttributeValue_NotFound() {
      String attrValueId = "missing";

      when(valueRepo.findById(attrValueId)).thenReturn(Optional.empty());

      Optional<AbacAttributeValue> result = abacService.getAttributeValue(attrValueId);

      assertFalse(result.isPresent());
      verify(valueRepo).findById(attrValueId);
  }
  
  @Test
  public void testSaveAttributeValue_NewValue() {
      AbacAttributeValue newVal = new AbacAttributeValue();
      newVal.setResourceId("res1");
      newVal.setAttributeId("attr1");
      newVal.setValue("someValue"); // Optional, unless dataType is enumeration

      // ✅ Mock attribute existence
      AbacAttribute mockAttr = new AbacAttribute();
      mockAttr.setId("attr1");
      mockAttr.setDataType("string"); // Or "enumeration" with allowed values
      when(attribRepo.findById("attr1")).thenReturn(Optional.of(mockAttr));

      // ✅ Mock value not present in DB
      when(valueRepo.findByResourceIdAndAttributeId("res1", "attr1"))
          .thenReturn(Optional.empty());

      // ✅ Mock save
      when(valueRepo.save(newVal)).thenReturn(newVal);

      // Act
      AbacAttributeValue saved = abacService.saveAttributeValue(newVal);

      // Assert
      assertSame(newVal, saved);
      verify(valueRepo).save(newVal);
  }

  @Test
  public void testSaveAttributeValue_UpdateExisting() {
      AbacAttributeValue existing = new AbacAttributeValue();
      existing.setId("id1");
      existing.setResourceId("res1");
      existing.setAttributeId("attr1");

      AbacAttributeValue incoming = new AbacAttributeValue();
      incoming.setResourceId("res1");
      incoming.setAttributeId("attr1");
      incoming.setValue("newValue");

      // ✅ Mock attribute metadata
      AbacAttribute attr = new AbacAttribute();
      attr.setId("attr1");
      attr.setDataType("string"); // or "enumeration" with allowedValues
      when(attribRepo.findById("attr1")).thenReturn(Optional.of(attr));

      // ✅ Mock existing value
      when(valueRepo.findByResourceIdAndAttributeId("res1", "attr1"))
          .thenReturn(Optional.of(existing));

      // ✅ Mock updated save
      when(valueRepo.save(existing)).thenReturn(existing);

      // Act
      AbacAttributeValue result = abacService.saveAttributeValue(incoming);

      // Assert
      assertSame(existing, result);
      verify(valueRepo).save(existing);
  }

  @Test
  public void testSaveAttributeValues_MultipleValues() {
      AbacAttributeValue attr1 = new AbacAttributeValue();
      attr1.setResourceId("r1");
      attr1.setAttributeId("a1");
      attr1.setValue("val1");

      AbacAttributeValue attr2 = new AbacAttributeValue();
      attr2.setResourceId("r2");
      attr2.setAttributeId("a2");
      attr2.setValue("val2");

      AbacAttribute abacAttr1 = new AbacAttribute();
      abacAttr1.setId("a1");
      abacAttr1.setDataType("string"); // Or "enumeration" with allowed values

      AbacAttribute abacAttr2 = new AbacAttribute();
      abacAttr2.setId("a2");
      abacAttr2.setDataType("string");

      // ✅ Mock attribute existence
      when(attribRepo.findById("a1")).thenReturn(Optional.of(abacAttr1));
      when(attribRepo.findById("a2")).thenReturn(Optional.of(abacAttr2));

      // ✅ No existing values in DB
      when(valueRepo.findByResourceIdAndAttributeId(anyString(), anyString())).thenReturn(Optional.empty());

      // ✅ Save returns input
      when(valueRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      List<AbacAttributeValue> result = abacService.saveAttributeValues(List.of(attr1, attr2));

      // Assert
      assertEquals(2, result.size());
      verify(valueRepo).save(argThat(val -> "a1".equals(val.getAttributeId()) && "r1".equals(val.getResourceId())));
      verify(valueRepo).save(argThat(val -> "a2".equals(val.getAttributeId()) && "r2".equals(val.getResourceId())));
  }

  @Test
  public void testSaveAttributeValues_EmptyList() {
      List<AbacAttributeValue> result = abacService.saveAttributeValues(Collections.emptyList());
      assertTrue(result.isEmpty());
      verify(valueRepo, never()).save(any());
  }

  @Test
  public void testDeleteAttributeValue() {
      String id = "id1";
      abacService.deleteAttributeValue(id);
      verify(valueRepo).deleteById(id);
  }
  
  @Test
  public void testGetResource_User() {
      Optional<AbacResource> result = abacService.getResource(ResourceType.USER, null);
      assertTrue(result.isPresent());
      assertEquals(ResourceType.USER, result.get().getType());
  }

  @Test
  public void testGetResource_EntityData_Found() {
      EntityDefinition entity = new EntityDefinition().setDisplayName("Test Entity");entity.setId("123");
      when(schemaService.getSyncariEntityById("123")).thenReturn(Optional.of(entity));

      Optional<AbacResource> result = abacService.getResource(ResourceType.ENTITY_DATA, "123");

      assertTrue(result.isPresent());
      assertEquals("123", result.get().getId());
      assertEquals("Test Entity", result.get().getDisplayName());
  }

  @Test
  public void testGetResource_EntityData_NotFound() {
      when(schemaService.getSyncariEntityById("notFound")).thenReturn(Optional.empty());

      Optional<AbacResource> result = abacService.getResource(ResourceType.ENTITY_DATA, "notFound");

      assertFalse(result.isPresent());
  }

  @Test
  public void testGetResource_InvalidType() {
      Optional<AbacResource> result = abacService.getResource(null, "irrelevant");
      assertFalse(result.isPresent());
  }

  @Test
  public void testDeleteAttributeValues_Multiple() {
      AbacServiceImpl spyService = spy(abacService);

      List<String> ids = Arrays.asList("id1", "id2");
      spyService.deleteAttributeValues(ids);

      verify(spyService).deleteAttributeValue("id1");
      verify(spyService).deleteAttributeValue("id2");
  }

  @Test
  public void testDeleteAttributeValues_EmptyList() {
      AbacServiceImpl spyService = spy(abacService);

      spyService.deleteAttributeValues(Collections.emptyList());

      verify(spyService, never()).deleteAttributeValue(anyString());
  }

  @Test
  public void testDeleteAttributeValues_NullList() {
      AbacServiceImpl spyService = spy(abacService);

      spyService.deleteAttributeValues(null);

      verify(spyService, never()).deleteAttributeValue(anyString());
  }
  
  @Test
  public void testCheck_ContextNotActivated_ReturnsEmptyMap() {
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(0L);

      Map<String, Boolean> result = abacService.check(context);
      assertTrue(result.isEmpty());
  }

  @Test
  public void testCheck_ContextActivated_DelegatesToSyncariNativeAbacService() {
      User mockUser = new User();
      mockUser.setId("test-user-id");
      SyncariContext.push();
      SyncariContext.setUser(mockUser);
      
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(1L);
      when(valueRepo.countByResourceId("test-user-id")).thenReturn(1L);

      Map<String, Boolean> mockedResult = Map.of("read", true);
      when(syncariNativeAbacService.check(context)).thenReturn(mockedResult);

      Map<String, Boolean> result = abacService.check(context);
      assertEquals(mockedResult, result);
      SyncariContext.restore();
  }
  
  @Test
  public void testCheck_WithNullData_ReturnsNull() {
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(1L);

      assertNull(abacService.check(context, null));
  }

  @Test
  public void testCheck_WithEmptyOptional_ReturnsSameOptional() {
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(1L);

      Optional<?> input = Optional.empty();
      Object result = abacService.check(context, input);

      assertSame(input, result);
  }

  @Test
  public void testCheck_WithOptionalValue_ReturnsFilteredOptional() {
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(1L);
      when(context.isThrowException()).thenReturn(false);

      Object entity = new Object();
      AbacResourceService rs = mock(AbacResourceService.class);
      when(resourceServiceFactory.getResourceService(context, entity)).thenReturn(rs);
      when(rs.checkSingle(context, entity)).thenReturn(entity);

      Optional<?> input = Optional.of(entity);
      Optional<?> result = (Optional<?>) abacService.check(context, input);

      assertTrue(result.isPresent());
      assertEquals(entity, result.get());
  }

  @Test
  public void testCheck_WithIterableValue() {
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(1L);

      List<Object> input = List.of("item1", "item2");
      AbacResourceService rs = mock(AbacResourceService.class);
      when(resourceServiceFactory.getResourceService(context, "item1")).thenReturn(rs);
      when(rs.checkList(context, input)).thenReturn(input);

      Object result = abacService.check(context, input);
      assertSame(input, result);
  }

  @Test
  public void testCheck_WithPageValue() {
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(1L);

      // Create a Page object with non-null records
      Page<Object> page = new Page<>();
      List<Object> records = new ArrayList<>();
      records.add("r1");
      records.add("r2");
      page.setRecords(records); // <-- This prevents NullPointerException

      AbacResourceService rs = mock(AbacResourceService.class);
      when(resourceServiceFactory.getResourceService(context, "r1")).thenReturn(rs);
      when(rs.checkList(context, records)).thenReturn(records);

      Object result = abacService.check(context, page);
      assertTrue(result instanceof Page);
      assertEquals(records, ((Page<?>) result).getRecords());
  }

  @Test
  public void testCheck_DefaultSingleObject() {
      AbacContext context = mock(AbacContext.class);
      when(context.getResourceType()).thenReturn(ResourceType.USER);
      when(policyRepo.countByResourceType(ResourceType.USER)).thenReturn(1L);
      when(context.isThrowException()).thenReturn(false);

      Object input = "someObject";
      AbacResourceService rs = mock(AbacResourceService.class);
      when(resourceServiceFactory.getResourceService(context, input)).thenReturn(rs);
      when(rs.checkSingle(context, input)).thenReturn(input);

      Object result = abacService.check(context, input);
      assertSame(input, result);
  }

}
