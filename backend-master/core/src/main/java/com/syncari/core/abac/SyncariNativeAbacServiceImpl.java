package com.syncari.core.abac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.syncari.core.event.store.model.AbacAudit;
import com.syncari.core.event.store.repo.AbacAuditLogRepo;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.Instance;
import com.syncari.core.model.User;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.repositories.customer.AbacPolicyRepo;
import com.syncari.core.token.TokenHelper;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncariNativeAbacServiceImpl {

  @Autowired
  AbacAttributeRepo attribRepo;
  @Autowired
  AbacPolicyRepo policyRepo;
  @Autowired
  TokenHelper tokenHelper;
  @Autowired
  AbacAuditLogRepo auditLogRepo;
  @Autowired
  ObjectMapper objectMapper;
  @Autowired
  ThreadPoolTaskExecutor exec;

  private final Cache<CacheKey, AbacResult> resultCache;
  private final Cache<PolicyEvaluationCacheKey, Boolean> policyEvaluationCache;

  public SyncariNativeAbacServiceImpl() {
    this.resultCache =
        CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10000).build();
    this.policyEvaluationCache =
        CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10000).build();
  }

  public AbacAttribute saveAttribute(AbacAttribute attr) {
    invalidateCache();
    return attr;
  }

  public void deleteAttribute(AbacAttribute attr) {
    invalidateCache();
  }

  public AbacPolicy savePolicy(AbacPolicy policy) {
    invalidateCache();
    return policy;
  }

  public void deletePolicy(AbacPolicy policy) {
    invalidateCache();
  }

  private void invalidateCache() {
    resultCache.invalidateAll();
    policyEvaluationCache.invalidateAll();
    log.debug("ABAC caches invalidated");
  }

  public Map<String, Boolean> check(AbacContext context) {
    log.debug("ABAC check started for resourceType: {}, action: {}", 
        context.getResourceType(), context.getAction());
    
    if (MapUtils.isEmpty(context.getUserAttributes())
        || MapUtils.isEmpty(context.getResourceAttributes())) {
      log.debug("ABAC check returning empty - missing user or resource attributes");
      return Map.of();
    }
  
    List<AbacPolicy> policies = policyRepo.findByResourceType(context.getResourceType());
    if (policies.isEmpty()) {
      log.debug("ABAC check returning empty - no policies found for resourceType: {}", 
          context.getResourceType());
      return Map.of();
    }
  
    log.debug("Found {} policies for resourceType: {}", policies.size(), context.getResourceType());
    Map<String, Boolean> results = new HashMap<>();
  
    for (var resEntry : context.getResourceAttributes().entrySet()) {
      String resourceId = resEntry.getKey();
      Map<String, Object> resourceAttributes = resEntry.getValue();
  
      log.debug("Evaluating resource: {}", resourceId);
      
      CacheKey cacheKey = new CacheKey(context.getResourceType(), context.getAction(),
          context.getUserAttributes(), resourceAttributes);
      AbacResult cachedResult = resultCache.getIfPresent(cacheKey);
  
      Boolean result;
      AbacPolicy appliedPolicy = null;
      if (cachedResult != null) {
        result = cachedResult.allowed;
        appliedPolicy = cachedResult.policy;
        log.debug("ABAC cache hit for resource {}, result: {}, policy: {}", resourceId, result,
                  appliedPolicy != null ? appliedPolicy.getId() : "none");
      } else {
        log.debug("ABAC cache miss for resource {}", resourceId);
        PolicyEvaluationResult evaluationResult = evaluatePolicyWithDetails(context, resourceAttributes, policies);
        result = evaluationResult.allowed;
        appliedPolicy = evaluationResult.policy;
        log.debug("Policy evaluation result for resource {}: {}, policy: {}", resourceId, result, 
                  appliedPolicy != null ? appliedPolicy.getId() : "none");
        resultCache.put(cacheKey, new AbacResult(result, appliedPolicy));
      }
      results.put(resourceId, result);
      // Create audit log asynchronously to avoid blocking main flow
      saveAuditLogAsync(context, resourceAttributes, result, appliedPolicy, resourceId);
    }
  
    log.debug("ABAC check completed, returning {} results", results.size());
    return results;
  }

  /**
   * Saves an audit log asynchronously with proper Syncari context
   */
  private void saveAuditLogAsync(AbacContext context, Map<String, Object> resourceAttributes, Boolean result, 
                                AbacPolicy appliedPolicy, String resourceId) {
    try {
      // Capture current context before async call
      var org = SyncariContext.getOrganziation();
      var ins = SyncariContext.getInstance();
      var user = SyncariContext.getUser();
      
      // Execute async with proper context
      exec.execute(() -> {
        SyncariContext.runWithContext(org, ins, user, () -> {
          try {
            AbacAudit audit = createAuditLog(context, resourceAttributes, result, appliedPolicy);
            auditLogRepo.insertAbacAudit(audit);
            log.debug("ABAC audit log saved for resource: {}, action: {}, result: {}, policy: {}", 
                      resourceId, context.getAction(), result, appliedPolicy != null ? appliedPolicy.getId() : "none");
          } catch (Exception e) {
            log.error("Error saving ABAC audit log for resource {} action {}: {}", 
                      resourceId, context.getAction(), e.getMessage(), e);
          }
        });
      });
    } catch (Exception e) {
      log.error("Error submitting async ABAC audit log task for resource {} action {}: {}", 
                resourceId, context.getAction(), e.getMessage(), e);
      // Don't fail the main flow if audit logging setup fails
    }
  }

  /**
   * Creates an audit log entry for ABAC authorization check
   */
  private AbacAudit createAuditLog(AbacContext context, Map<String, Object> resourceAttributes, Boolean result, AbacPolicy grantingPolicy) {
    try {
      String policyInfo = "";
      if (result && grantingPolicy != null) {
        policyInfo = grantingPolicy.getId() + ":" + grantingPolicy.getName();
      }
      
      return new AbacAudit()
          .setAction(String.valueOf(context.getAction()))
          .setAllowed(result)
          .setResource(objectMapper.writeValueAsString(resourceAttributes))
          .setUser(objectMapper.writeValueAsString(context.getUserAttributes()))
          .setCreatedAt(Instant.now())
          .setResourceType(String.valueOf(context.getResourceType()))
          .setPolicy(policyInfo);
    } catch (Exception e) {
      log.error("Error creating ABAC audit log: {}", e.getMessage(), e);
      // Return a minimal audit log if JSON serialization fails
      return new AbacAudit()
          .setAction(String.valueOf(context.getAction()))
          .setAllowed(result)
          .setResource("serialization_failed")
          .setUser("serialization_failed")
          .setCreatedAt(Instant.now())
          .setResourceType(String.valueOf(context.getResourceType()))
          .setPolicy("");
    }
  }

  /**
   * Evaluates policies and returns both the result and which policy granted access
   */
  private PolicyEvaluationResult evaluatePolicyWithDetails(AbacContext context, Map<String, Object> resource,
      List<AbacPolicy> policies) {
    log.debug("evaluatePolicyWithDetails called for resource: {}, action: {}, policies count: {}", 
        resource, context.getAction(), policies.size());
    
    for (AbacPolicy policy : policies) {
      log.debug("Evaluating policy: {}, permissions: {}", policy.getId(), policy.getPermissions());
      
      boolean hasPermission = policy.getPermissions().contains(context.getAction());
      log.debug("Policy {} has permission for action {}: {}", policy.getId(), context.getAction(), hasPermission);
      
      if (hasPermission) {
        PolicyEvaluationCacheKey cacheKey = new PolicyEvaluationCacheKey(
            context.getResourceType(), 
            context.getAction(), 
            context.getUserAttributes(), 
            resource, 
            policy.getId()
        );
        
        Boolean cachedResult = policyEvaluationCache.getIfPresent(cacheKey);
        
        boolean conditionResult;
        if (cachedResult != null) {
          log.debug("Policy evaluation cache hit for policy {}, result: {}", policy.getId(), cachedResult);
          conditionResult = cachedResult;
        } else {
          log.debug("Policy evaluation cache miss for policy {}", policy.getId());
          conditionResult = evaluateCondition(context, policy.getCondition(), resource);
          log.debug("Policy {} condition evaluation result: {}", policy.getId(), conditionResult);
          policyEvaluationCache.put(cacheKey, conditionResult);
        }
        
        if (conditionResult) {
          return new PolicyEvaluationResult(true, policy);
        }
      }
    }
    
    return new PolicyEvaluationResult(false, null);
  }

  private boolean evaluatePolicy(AbacContext context, Map<String, Object> resource,
      List<AbacPolicy> policies) {
    log.debug("evaluatePolicy called for resource: {}, action: {}, policies count: {}", 
        resource, context.getAction(), policies.size());
    
    return policies.stream().anyMatch(policy -> {
      log.debug("Evaluating policy: {}, permissions: {}", policy.getId(), policy.getPermissions());
      
      boolean hasPermission = policy.getPermissions().contains(context.getAction());
      log.debug("Policy {} has permission for action {}: {}", policy.getId(), context.getAction(), hasPermission);
      
      if (hasPermission) {
        PolicyEvaluationCacheKey cacheKey = new PolicyEvaluationCacheKey(
            context.getResourceType(), 
            context.getAction(), 
            context.getUserAttributes(), 
            resource, 
            policy.getId()
        );
        
        Boolean cachedResult = policyEvaluationCache.getIfPresent(cacheKey);
        
        if (cachedResult != null) {
          log.debug("Policy evaluation cache hit for policy {}, result: {}", policy.getId(), cachedResult);
          return cachedResult;
        } else {
          log.debug("Policy evaluation cache miss for policy {}", policy.getId());
          boolean conditionResult = evaluateCondition(context, policy.getCondition(), resource);
          log.debug("Policy {} condition evaluation result: {}", policy.getId(), conditionResult);
          policyEvaluationCache.put(cacheKey, conditionResult);
          return conditionResult;
        }
      }
      return false;
    });
  }


  private boolean evaluateCondition(AbacContext context, Map<String, Object> condition,
      Map<String, Object> resource) {
    log.debug("evaluateCondition called with condition: {}, resource: {}", condition, resource);
    
    if (condition == null || condition.isEmpty()) {
      log.debug("Condition is null or empty - throwing RuntimeException");
      throw new RuntimeException("Condition is null or empty");
    }

    try {
      log.debug("Parsing condition into expression");
      Expression expr = new PredicateParser().fromMap(condition);
      
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("user", context.getUserAttributes());
      attributes.put("resource", resource);
      log.debug("Created attributes map: {}", attributes);

      log.debug("Creating SyncariAbacEvaluationVisitor");
      SyncariAbacEvaluationVisitor visitor =
          new SyncariAbacEvaluationVisitor(attributes, attribRepo, tokenHelper);
      
      log.debug("Accepting expression with visitor");
      expr.accept(new DynamicDispatchVisitor(visitor));
      
      Boolean result = (Boolean) visitor.getValue();
      log.debug("Condition evaluation completed with result: {}", result);
      return result;
    } catch (Exception e) {
      log.error("Error evaluating policy condition: " + condition, e);
      throw new RuntimeException("Error evaluating policy condition", e);
    }
  }

  @EqualsAndHashCode
  private static class CacheKey {
    private final ResourceType resourceType;
    private final Permission action;
    private final Map<String, Object> userAttributes;
    private final Map<String, Object> resourceAttributes;

    public CacheKey(ResourceType resourceType, Permission action,
        Map<String, Object> userAttributes, Map<String, Object> resourceAttributes) {
      this.resourceType = resourceType;
      this.action = action;
      this.userAttributes = userAttributes;
      this.resourceAttributes = resourceAttributes;
    }
  }

  @EqualsAndHashCode
  private static class PolicyEvaluationCacheKey {
    private final ResourceType resourceType;
    private final Permission action;
    private final Map<String, Object> userAttributes;
    private final Map<String, Object> resourceAttributes;
    private final String policyId;

    public PolicyEvaluationCacheKey(ResourceType resourceType, Permission action,
        Map<String, Object> userAttributes, Map<String, Object> resourceAttributes, String policyId) {
        this.resourceType = resourceType;
        this.action = action;
        this.userAttributes = userAttributes;
        this.resourceAttributes = resourceAttributes;
        this.policyId = policyId;
    }
  }

  /**
   * Result of policy evaluation including which policy granted access
   */
  private static class PolicyEvaluationResult {
    final boolean allowed;
    final AbacPolicy policy;

    public PolicyEvaluationResult(boolean allowed, AbacPolicy policy) {
      this.allowed = allowed;
      this.policy = policy;
    }
  }

  /**
   * Cached result containing both authorization decision and granting policy
   */
  private static class AbacResult {
    final boolean allowed;
    final AbacPolicy policy;

    public AbacResult(boolean allowed, AbacPolicy policy) {
      this.allowed = allowed;
      this.policy = policy;
    }
  }
}