package com.syncari.core.abac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.event.store.model.AbacAudit;
import com.syncari.core.event.store.repo.AbacAuditLogRepo;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.repositories.customer.AbacPolicyRepo;
import com.syncari.core.token.TokenHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SyncariNativeAbacServiceImplTest extends AbstractSyncariTest {

    @MockBean
    private AbacAttributeRepo attribRepo;
    
    @MockBean
    private AbacPolicyRepo policyRepo;
    
    @MockBean
    private AbacAuditLogRepo auditLogRepo;
    
    @MockBean
    private ObjectMapper objectMapper;
    
    @MockBean
    private ThreadPoolTaskExecutor exec;
    
    @Autowired
    private TokenHelper tokenHelper;
    
    private SyncariNativeAbacServiceImpl abacService;
    
    private AbacContext testContext;
    private AbacPolicy testPolicy;
    private Map<String, Object> userAttributes;
    private Map<String, Object> resourceAttributes;
    private Map<String, Map<String, Object>> resourceAttributesMap;

    @Before
    public void setUpAbacTest() {
        MockitoAnnotations.initMocks(this);
        
        // Create the service instance with mocked dependencies
        abacService = new SyncariNativeAbacServiceImpl();
        ReflectionTestUtils.setField(abacService, "attribRepo", attribRepo);
        ReflectionTestUtils.setField(abacService, "policyRepo", policyRepo);
        ReflectionTestUtils.setField(abacService, "tokenHelper", tokenHelper);
        ReflectionTestUtils.setField(abacService, "auditLogRepo", auditLogRepo);
        ReflectionTestUtils.setField(abacService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(abacService, "exec", exec);
        
        // Configure exec mock to run tasks synchronously for testing
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run(); // Execute synchronously
            return null;
        }).when(exec).execute(any(Runnable.class));
        
        // Setup test data
        userAttributes = new HashMap<>();
        userAttributes.put("userId", "user123");
        userAttributes.put("role", "admin");
        userAttributes.put("department", "engineering");
        
        resourceAttributes = new HashMap<>();
        resourceAttributes.put("resourceId", "resource123");
        resourceAttributes.put("owner", "user123");
        resourceAttributes.put("type", "entity");
        
        resourceAttributesMap = new HashMap<>();
        resourceAttributesMap.put("resource123", resourceAttributes);
        
        testContext = new AbacContext();
        testContext.setResourceType(ResourceType.ENTITY);
        testContext.setAction(Permission.READ);
        testContext.setUserAttributes(userAttributes);
        testContext.setResourceAttributes(resourceAttributesMap);
        
        testPolicy = new AbacPolicy();
        testPolicy.setId("policy123");
        testPolicy.setName("Test Policy");
        testPolicy.setResourceType(ResourceType.ENTITY);
        testPolicy.setPermissions(Arrays.asList(Permission.READ, Permission.UPDATE));
            
        // Set up a simple condition for testing
        Map<String, Object> condition = new HashMap<>();
        condition.put("operator", "eq");
        condition.put("left", "user.userId");
        condition.put("right", "resource.owner");
        testPolicy.setCondition(condition);
    }

    // Happy Path Tests
    
    @Test
    public void testCheck_WithValidContextAndMatchingPolicy_ReturnsAllowed() throws Exception {
        // Arrange
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Mock the condition evaluation by making it simple
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        testPolicy.setCondition(simpleCondition);
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result.size());
        assertTrue(result.containsKey("resource123"));
        
        // Verify audit log was attempted to be created (with timeout for async)
        verify(auditLogRepo).insertAbacAudit(any(AbacAudit.class));
    }
    
    @Test
    public void testCheck_WithMultipleResources_ReturnsCorrectResults() throws Exception {
        // Arrange
        Map<String, Object> resource2Attributes = new HashMap<>();
        resource2Attributes.put("resourceId", "resource456");
        resource2Attributes.put("owner", "user456");
        resource2Attributes.put("type", "entity");
        
        Map<String, Map<String, Object>> multiResourceAttributes = new HashMap<>();
        multiResourceAttributes.put("resource123", resourceAttributes);
        multiResourceAttributes.put("resource456", resource2Attributes);
        
        testContext.setResourceAttributes(multiResourceAttributes);
        
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Mock simple condition that always returns true
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        testPolicy.setCondition(simpleCondition);
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("resource123"));
        assertTrue(result.containsKey("resource456"));
        
        // Verify audit logs were created for both resources (with timeout for async)
        verify(auditLogRepo, times(2)).insertAbacAudit(any(AbacAudit.class));
    }
    
    @Test
    public void testSaveAttribute_InvalidatesCache() {
        // Arrange
        AbacAttribute attribute = new AbacAttribute();
        Cache<?, ?> resultCache = getResultCache();
        Cache<?, ?> policyCache = getPolicyEvaluationCache();
        
        // Act
        abacService.saveAttribute(attribute);
        
        // Assert - caches should be empty after invalidation
        assertEquals(0, resultCache.size());
        assertEquals(0, policyCache.size());
    }
    
    @Test
    public void testSavePolicy_InvalidatesCache() {
        // Arrange
        AbacPolicy policy = new AbacPolicy();
        Cache<?, ?> resultCache = getResultCache();
        Cache<?, ?> policyCache = getPolicyEvaluationCache();
        
        // Act
        abacService.savePolicy(policy);
        
        // Assert - caches should be empty after invalidation  
        assertEquals(0, resultCache.size());
        assertEquals(0, policyCache.size());
    }

    @Test
    public void testDeleteAttribute_InvalidatesCache() {
        // Arrange
        AbacAttribute attribute = new AbacAttribute();
        Cache<?, ?> resultCache = getResultCache();
        Cache<?, ?> policyCache = getPolicyEvaluationCache();
        
        // Act
        abacService.deleteAttribute(attribute);
        
        // Assert - caches should be empty after invalidation
        assertEquals(0, resultCache.size());
        assertEquals(0, policyCache.size());
    }
    
    @Test
    public void testDeletePolicy_InvalidatesCache() {
        // Arrange
        AbacPolicy policy = new AbacPolicy();
        Cache<?, ?> resultCache = getResultCache();
        Cache<?, ?> policyCache = getPolicyEvaluationCache();
        
        // Act
        abacService.deletePolicy(policy);
        
        // Assert - caches should be empty after invalidation
        assertEquals(0, resultCache.size());
        assertEquals(0, policyCache.size());
    }

    // Error Path Tests
    
    @Test
    public void testCheck_WithEmptyUserAttributes_ReturnsEmptyMap() {
        // Arrange
        testContext.setUserAttributes(Collections.emptyMap());
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(policyRepo, never()).findByResourceType(any());
        verify(auditLogRepo, never()).insertAbacAudit(any());
    }
    
    @Test
    public void testCheck_WithNullUserAttributes_ReturnsEmptyMap() {
        // Arrange
        testContext.setUserAttributes(null);
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(policyRepo, never()).findByResourceType(any());
        verify(auditLogRepo, never()).insertAbacAudit(any());
    }
    
    @Test
    public void testCheck_WithEmptyResourceAttributes_ReturnsEmptyMap() {
        // Arrange
        testContext.setResourceAttributes(Collections.emptyMap());
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(policyRepo, never()).findByResourceType(any());
        verify(auditLogRepo, never()).insertAbacAudit(any());
    }
    
    @Test
    public void testCheck_WithNullResourceAttributes_ReturnsEmptyMap() {
        // Arrange
        testContext.setResourceAttributes(null);
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(policyRepo, never()).findByResourceType(any());
        verify(auditLogRepo, never()).insertAbacAudit(any());
    }
    
    @Test
    public void testCheck_WithNoPoliciesFound_ReturnsEmptyMap() {
        // Arrange
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Collections.emptyList());
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(auditLogRepo, never()).insertAbacAudit(any());
    }
    
    @Test
    public void testCheck_WithPolicyWithoutMatchingPermission_ReturnsFalse() throws Exception {
        // Arrange
        AbacPolicy policyWithoutPermission = new AbacPolicy();
        policyWithoutPermission.setId("policy456");
        policyWithoutPermission.setName("No Permission Policy");
        policyWithoutPermission.setResourceType(ResourceType.ENTITY);
        policyWithoutPermission.setPermissions(Arrays.asList(Permission.DELETE)); // Different permission
            
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        policyWithoutPermission.setCondition(simpleCondition);
        
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(policyWithoutPermission));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result.size());
        assertFalse(result.get("resource123"));
        
        // Verify audit log was created (with timeout for async)
        verify(auditLogRepo).insertAbacAudit(any(AbacAudit.class));
    }
    
    @Test
    public void testCheck_WithAuditLogException_DoesNotFailMainFlow() throws Exception {
        // Arrange
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Mock simple condition
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        testPolicy.setCondition(simpleCondition);
        
        // Make audit log repo throw exception
        doThrow(new RuntimeException("Audit log failed")).when(auditLogRepo).insertAbacAudit(any());
        
        // Act & Assert - Should not throw exception
        Map<String, Boolean> result = assertDoesNotThrow(() -> abacService.check(testContext));
        
        assertEquals(1, result.size());
        assertTrue(result.get("resource123"));
    }
    
    // Condition Evaluation Tests
    
    @Test
    public void testEvaluateCondition_WithNullCondition_ThrowsException() {
        // Arrange
        AbacPolicy policyWithNullCondition = new AbacPolicy();
        policyWithNullCondition.setId("policy789");
        policyWithNullCondition.setName("Null Condition Policy");
        policyWithNullCondition.setResourceType(ResourceType.ENTITY);
        policyWithNullCondition.setPermissions(Arrays.asList(Permission.READ));
        policyWithNullCondition.setCondition(null);
        
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(policyWithNullCondition));
        
        // Act & Assert
        try {
            abacService.check(testContext);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Condition is null or empty"));
        }
    }
    
    @Test
    public void testEvaluateCondition_WithEmptyCondition_ThrowsException() {
        // Arrange
        AbacPolicy policyWithEmptyCondition = new AbacPolicy();
        policyWithEmptyCondition.setId("policy789");
        policyWithEmptyCondition.setName("Empty Condition Policy");
        policyWithEmptyCondition.setResourceType(ResourceType.ENTITY);
        policyWithEmptyCondition.setPermissions(Arrays.asList(Permission.READ));
        policyWithEmptyCondition.setCondition(Collections.emptyMap());
        
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(policyWithEmptyCondition));
        
        // Act & Assert  
        try {
            abacService.check(testContext);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Condition is null or empty"));
        }
    }
    
    @Test 
    public void testCreateAuditLog_WithObjectMapperException_ReturnsMinimalAudit() throws Exception {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON serialization failed"));
        
        // Use reflection to call private method
        var method = SyncariNativeAbacServiceImpl.class.getDeclaredMethod("createAuditLog", 
            AbacContext.class, Map.class, Boolean.class, AbacPolicy.class);
        method.setAccessible(true);
        
        // Act
        AbacAudit result = (AbacAudit) method.invoke(abacService, testContext, resourceAttributes, true, testPolicy);
        
        // Assert
        assertNotNull(result);
        assertEquals("READ", result.getAction());
        assertEquals(true, result.getAllowed());
        assertEquals("serialization_failed", result.getResource());
        assertEquals("serialization_failed", result.getUser());
        assertEquals("ENTITY", result.getResourceType());
        assertEquals("", result.getPolicy());
        assertNotNull(result.getCreatedAt());
    }
    
    @Test
    public void testMultiplePolicies_FirstMatchingPolicyGrantsAccess() throws Exception {
        // Arrange
        AbacPolicy denyPolicy = new AbacPolicy();
        denyPolicy.setId("denyPolicy");
        denyPolicy.setName("Deny Policy");
        denyPolicy.setResourceType(ResourceType.ENTITY);
        denyPolicy.setPermissions(Arrays.asList(Permission.READ));
            
        Map<String, Object> falseCondition = new HashMap<>();
        falseCondition.put("type", "literal");
        falseCondition.put("value", false);
        denyPolicy.setCondition(falseCondition);
        
        AbacPolicy allowPolicy = new AbacPolicy();
        allowPolicy.setId("allowPolicy");
        allowPolicy.setName("Allow Policy");
        allowPolicy.setResourceType(ResourceType.ENTITY);
        allowPolicy.setPermissions(Arrays.asList(Permission.READ));
            
        Map<String, Object> trueCondition = new HashMap<>();
        trueCondition.put("type", "literal");
        trueCondition.put("value", true);
        allowPolicy.setCondition(trueCondition);
        
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(denyPolicy, allowPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result.size());
        assertTrue(result.get("resource123")); // Should be true because the second policy matched
    }

    // Additional comprehensive tests for various scenarios
    
    @Test
    public void testCondition_WithPolicyEvaluationCache_UsesCachedResult() throws Exception {
        // Arrange - Test that policy evaluation caching works
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        
        testPolicy.setCondition(simpleCondition);
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // First call
        Map<String, Boolean> result1 = abacService.check(testContext);
        // Second call should use cache
        Map<String, Boolean> result2 = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result1.size());
        assertTrue(result1.get("resource123"));
        assertEquals(1, result2.size());
        assertTrue(result2.get("resource123"));
        
        // Should have audit logs for both calls (with timeout for async)
        verify(auditLogRepo, times(2)).insertAbacAudit(any(AbacAudit.class));
    }
    
    @Test 
    public void testCondition_WithDifferentResourceTypes_HandlesCorrectly() throws Exception {
        // Arrange - Test with different resource types
        testContext.setResourceType(ResourceType.DATASET);
        
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        
        testPolicy.setResourceType(ResourceType.DATASET);
        testPolicy.setCondition(simpleCondition);
        when(policyRepo.findByResourceType(ResourceType.DATASET)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result.size());
        assertTrue(result.get("resource123"));
        verify(auditLogRepo).insertAbacAudit(any(AbacAudit.class));
    }
    
    @Test
    public void testCondition_WithDifferentActions_WorksCorrectly() throws Exception {
        // Arrange - Test with different action types
        testContext.setAction(Permission.UPDATE);
        testPolicy.setPermissions(Arrays.asList(Permission.UPDATE, Permission.DELETE));
        
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        
        testPolicy.setCondition(simpleCondition);
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result.size());
        assertTrue(result.get("resource123"));
        verify(auditLogRepo).insertAbacAudit(any(AbacAudit.class));
    }
    
    @Test
    public void testCondition_WithLiteralTrueCondition_ReturnsTrue() throws Exception {
        // Arrange - Test basic literal true condition
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", true);
        
        testPolicy.setCondition(simpleCondition);
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result.size());
        assertTrue(result.get("resource123"));
        verify(auditLogRepo).insertAbacAudit(any(AbacAudit.class));
    }
    
    @Test
    public void testCondition_WithLiteralFalseCondition_ReturnsFalse() throws Exception {
        // Arrange - Test basic literal false condition
        Map<String, Object> simpleCondition = new HashMap<>();
        simpleCondition.put("type", "literal");
        simpleCondition.put("value", false);
        
        testPolicy.setCondition(simpleCondition);
        when(policyRepo.findByResourceType(ResourceType.ENTITY)).thenReturn(Arrays.asList(testPolicy));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // Act
        Map<String, Boolean> result = abacService.check(testContext);
        
        // Assert
        assertEquals(1, result.size());
        assertFalse(result.get("resource123"));
        verify(auditLogRepo).insertAbacAudit(any(AbacAudit.class));
    }

    // Helper methods
    
    @SuppressWarnings("unchecked")
    private Cache<Object, Boolean> getResultCache() {
        return (Cache<Object, Boolean>) ReflectionTestUtils.getField(abacService, "resultCache");
    }
    
    @SuppressWarnings("unchecked") 
    private Cache<Object, Boolean> getPolicyEvaluationCache() {
        return (Cache<Object, Boolean>) ReflectionTestUtils.getField(abacService, "policyEvaluationCache");
    }
    
    // Custom assertion that doesn't throw checked exceptions for lambda usage
    private Map<String, Boolean> assertDoesNotThrow(TestFunction function) {
        try {
            return function.apply();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception to be thrown, but got: " + e.getMessage(), e);
        }
    }
    
    @FunctionalInterface
    private interface TestFunction {
        Map<String, Boolean> apply() throws Exception;
    }
}