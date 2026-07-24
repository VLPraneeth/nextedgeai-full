package com.syncari.core.abac;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.core.model.User;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.AbacException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.repositories.customer.AbacAttributeValueRepo;
import com.syncari.core.repositories.customer.AbacPolicyRepo;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.EmailService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.UserService;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AbacServiceImpl implements AbacService {
  public static AbacResource ABAC_USER_RESOURCE = new AbacResource(ResourceType.USER, "user", "User");
  public static AbacResource ABAC_ENTITY_RESOURCE = new AbacResource(ResourceType.ENTITY, "entity", "All Entities");
  public static AbacResource ABAC_DATASET_RESOURCE = new AbacResource(ResourceType.DATASET, "dataset", "All Datasets");
  public static AbacResource ABAC_GLOBAL_RESOURCE = new AbacResource(ResourceType.GLOBAL, "global", "Global");
  private static String TOKEN_GROUP = "Resource";
  private static String FORMAT_RESOURCE_VALUE = "{{resource.values.%s}}";
  
  @Autowired
  SchemaService schemaService;
  @Autowired
  AbacAttributeRepo attribRepo;
  @Autowired
  AbacPolicyRepo policyRepo;
  @Autowired
  AbacAttributeValueRepo valueRepo;
  @Autowired
  UserService userService;
  @Autowired
  DatasetService datasetService;
  @Autowired
  SyncariNativeAbacServiceImpl syncariNativeAbacService;
  @Autowired
  @Qualifier("taskExecutor")
  ThreadPoolTaskExecutor exec;
  @Autowired
  AbacResourceServiceFactory resourceServiceFactory;
  @Autowired
  FeatureService featureService;
  @Autowired
  @Qualifier("defaultEmailService")
  EmailService emailService;
  @Autowired
  AppConfig appConfig;

  public List<AbacResource> listResources() {
    List<AbacResource> list = new ArrayList<>();
    for (var type : ResourceType.values()) {
      list.addAll(listResources(type));
    }
    return list;
  }
  
  public List<AbacResource> listResourcesForValues(ResourceType type) {
    if (type == ResourceType.USER) {
      var users = new ArrayList<AbacResource>();
      userService.getAllUsersFromInstance().forEach(user -> {
        users.add(new AbacResource(ResourceType.USER, user.getId(), user.getEmail()));
      });
      return users;
    } else if(type == ResourceType.GLOBAL) {
      return List.of(new AbacResource(ResourceType.GLOBAL, "__global__", "Global"));
    } else if (type == ResourceType.ENTITY) {
      var entities = new ArrayList<AbacResource>();
      entities.add(new AbacResource(ResourceType.ENTITY, "__all__", "All Entities"));
      entities.addAll(schemaService.getSyncariEntitiesWithoutAbac().stream()
          .map(e -> new AbacResource(ResourceType.ENTITY, e.getId(), e.getDisplayName()))
          .collect(Collectors.toList()));
      return entities;
    } else if (type == ResourceType.DATASET) {
      var datasets = new ArrayList<AbacResource>();
      datasets.add(new AbacResource(ResourceType.DATASET, "__all__", "All Datasets"));
      datasets.addAll(datasetService.getAllActiveDatasets().stream()
          .map(e -> new AbacResource(ResourceType.DATASET, e.getId(), e.getDisplayName()))
          .collect(Collectors.toList()));
      return datasets;
    } else {
      return listResources(type);
    }
  }
  
  public Optional<AbacResource> getResourceForValues(ResourceType type, String resoureId) {
    if (type == ResourceType.USER) {
      return userService.findUserById(resoureId).map(user -> {
        return new AbacResource(ResourceType.USER, user.getId(), user.getEmail());
      });
    } else if (type == ResourceType.GLOBAL) {
      return Optional.of(ABAC_GLOBAL_RESOURCE);
    } else if (type == ResourceType.ENTITY) {
      return schemaService.findEntity(resoureId)
          .map(e -> new AbacResource(ResourceType.ENTITY, e.getId(), e.getDisplayName()));
    } else if (type == ResourceType.DATASET) {
      return datasetService.findDataset(resoureId)
          .map(e -> new AbacResource(ResourceType.DATASET, e.getId(), e.getDisplayName()));
    } else {
      return getResource(type, resoureId);
    }
  }

  public List<AbacResource> listResources(ResourceType type) {
    if( type == null) {
      return List.of();
    }
    switch (type) {
      case USER:
        return List.of(ABAC_USER_RESOURCE);
      case GLOBAL:
        return List.of(ABAC_GLOBAL_RESOURCE);
      case ENTITY:
        return List.of(ABAC_ENTITY_RESOURCE);
      case ENTITY_DATA:
        return schemaService.getSyncariEntitiesWithoutAbac().stream()
            .map(e -> new AbacResource(ResourceType.ENTITY_DATA, e.getId(), e.getDisplayName()))
            .collect(Collectors.toList());
      case DATASET:
        return List.of(ABAC_DATASET_RESOURCE);
      default:
        return List.of();
    }
  }

  public List<AbacAttribute> listAttributes() {
    return attribRepo.findAll();
  }
  
  public List<AbacAttribute> listAttributes(ResourceType type, String id) {
    if (type == ResourceType.USER || type == ResourceType.ENTITY || type == ResourceType.DATASET || type == ResourceType.GLOBAL) {
      return attribRepo.findByResourceType(type);
    } else {
      return attribRepo.findByResourceTypeAndResourceId(type, id);
    }
  }
  
  public Map<String, List<KeyValue>> listAttributeTokens(ResourceType type, String id) {
    switch (type) {
      case USER:
        List<KeyValue> userTokens = new ArrayList<KeyValue>();
        List.of("id", "email", "firstName", "lastName").forEach(p -> {
          userTokens.add(new KeyValue("value", ABAC_USER_RESOURCE.getId())
              .set("label", p)
              .set("shortLabel", p)
              .set("token", String.format(FORMAT_RESOURCE_VALUE, p))
              .set("datatype", StringType.NAME)
              .set("group", TOKEN_GROUP));
        });
        return Map.of(TOKEN_GROUP, userTokens);
      case ENTITY:
        var entityToken = new KeyValue("value", ABAC_ENTITY_RESOURCE.getId())
        .set("label", ABAC_ENTITY_RESOURCE.getDisplayName())
        .set("shortLabel", ABAC_ENTITY_RESOURCE.getDisplayName())
        .set("token", "{{resource.apiName}}")
        .set("datatype", StringType.NAME)
        .set("group", TOKEN_GROUP);
        return Map.of(TOKEN_GROUP, List.of(entityToken));
      case ENTITY_DATA:
        var attributes =  schemaService.getAttributesByEntityId(id);
        List<KeyValue> entityDataTokens = new ArrayList<KeyValue>();
        attributes.forEach(attr -> {
          entityDataTokens.add(new KeyValue("value", attr.getId())
              .set("label", attr.getDisplayName())
              .set("shortLabel", attr.getDisplayName())
              .set("token", String.format(FORMAT_RESOURCE_VALUE, attr.getApiName()))
              .set("datatype", attr.getDataType().getName())
              .set("group", TOKEN_GROUP));
        });
        return Map.of(TOKEN_GROUP, entityDataTokens);
      case DATASET:
        return Map.of();
      default:
        return Map.of();
    }
  }

  public Optional<AbacAttribute> getAttribute(String id) {
    return attribRepo.findById(id);
  }

  public AbacAttribute saveAttribute(AbacAttribute attr) {
    var potentialDuplicate = attribRepo.findByResourceTypeAndResourceIdAndApiName(
        attr.getResourceType(), attr.getResourceId(), attr.getApiName());
    if (attr.getId() != null) {
      var attrDbOpt = attribRepo.findById(attr.getId());
      if (attrDbOpt.isPresent()) {
        var attrDb = attrDbOpt.get();
        if(potentialDuplicate.isPresent() && !potentialDuplicate.get().getId().equals(attr.getId())) {
          throw new SyncariValidationException(i18n("abac_attr_duplicate", attr.getApiName()));
        }
        attrDb.copyFrom(attr);
        attrDb = syncariNativeAbacService.saveAttribute(attrDb);
        return attribRepo.save(attrDb);
      } else {
        throw new SyncariValidationException(i18n("abac_attr_not_found", attr.getId()));
      }
    } else {
      if(potentialDuplicate.isPresent()) {
        throw new SyncariValidationException(i18n("abac_attr_duplicate", attr.getApiName()));
      }
      attr = syncariNativeAbacService.saveAttribute(attr);
      return attribRepo.save(attr);
    }
  }

  public void deleteAttribute(String id) {
    attribRepo.findById(id).ifPresent(a -> {
      if (valueRepo.countByAttributeId(id) > 0) {
        throw new SyncariValidationException(i18n("abac_attr_values_defined", a.getDisplayName()));
      }
      for (var policy : policyRepo.findAll()) {
        Map<String, Object> predicate = policy.getCondition();
        Expression filterExpression = new PredicateParser().fromMap(predicate);
        var evaluator = new AttributeListExpressionVisitor();
        filterExpression.accept(evaluator);
        if (evaluator.getValue().contains(id)) {
          throw new SyncariValidationException(
              i18n("abac_attr_in_use_policy", a.getDisplayName(), policy.getName()));
        }
      }
      syncariNativeAbacService.deleteAttribute(a);
      attribRepo.deleteById(id);
    });
  }
  
  public List<AbacPolicy> listPolicies() {
    return policyRepo.findAll();
  }

  public Optional<AbacPolicy> getPolicy(String id) {
    return policyRepo.findById(id);
  }

  public AbacPolicy savePolicy(AbacPolicy policy) {
    var potentialDuplicate = policyRepo.findByApiName(policy.getApiName());
    if (policy.getId() != null) {
      var policyDbOpt = policyRepo.findById(policy.getId());
      if (policyDbOpt.isPresent()) {
        var policyDb = policyDbOpt.get();
        if(potentialDuplicate.isPresent() && !potentialDuplicate.get().getId().equals(policy.getId())) {
          throw new SyncariValidationException(i18n("abac_policy_duplicate", policy.getApiName()));
        }
        policyDb.copyFrom(policy);
        policyDb = syncariNativeAbacService.savePolicy(policyDb);
        return policyRepo.save(policyDb);
      } else {
        throw new SyncariValidationException(i18n("abac_policy_not_found", policy.getId()));
      }
    } else {
      if(potentialDuplicate.isPresent()) {
        throw new SyncariValidationException(i18n("abac_policy_duplicate", policy.getApiName()));
      }
      policy = syncariNativeAbacService.savePolicy(policy);
      return policyRepo.save(policy);
    }
  }

  public void deletePolicy(String id) {
    policyRepo.findById(id).ifPresent(p -> {
      syncariNativeAbacService.deletePolicy(p);
      policyRepo.deleteById(id);
    });
  }
  
  public List<AbacAttributeValue> listAttributeValues() {
    return valueRepo.findAll();
  }

  public Optional<AbacAttributeValue> getAttributeValue(String id) {
    return valueRepo.findById(id);
  }
  
  public List<AbacAttributeValue> saveAttributeValues(List<AbacAttributeValue> attribs) {
    List<AbacAttributeValue> values = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(attribs)) {
      attribs.forEach(attr -> {
        values.add(saveAttributeValue(attr));
      });
    }
    return values;
  }

  public AbacAttributeValue saveAttributeValue(AbacAttributeValue attrVal) {
    var attr = attribRepo.findById(attrVal.getAttributeId());
    if(attr.isPresent()) {
      var attrValDbOpt = valueRepo.findByResourceIdAndAttributeId(attrVal.getResourceId(), attrVal.getAttributeId());
      if("enumeration".equals(attr.get().getDataType())) {
        List<Object> values = List.of();
        if(attrVal.getValue() instanceof List<?>) {
          values = (List<Object>) attrVal.getValue();
        } else {
          values = List.of(attrVal.getValue());
        }
        for(var val: values) {
          validateCondition(!attr.get().getAllowedValues().contains(val), i18n("abac_attribute_invalid_value"));
        }
      }
      if (attrValDbOpt.isPresent()) {
        var attrValDb = attrValDbOpt.get();
        attrValDb.copyFrom(attrVal);
        return valueRepo.save(attrValDb);
      } else {
        return valueRepo.save(attrVal);
      }
    }
    return attrVal;
  }

  public void deleteAttributeValue(String id) {
    valueRepo.deleteById(id);
  }
  
  public Optional<AbacResource> getResource(ResourceType type, String resourceId) {
    if(type == null) {
      return Optional.empty();
    }
    switch (type) {
      case USER:
        return Optional.of(ABAC_USER_RESOURCE);
      case GLOBAL:
        return Optional.of(ABAC_GLOBAL_RESOURCE);
      case ENTITY:
        return Optional.of(ABAC_ENTITY_RESOURCE);
      case ENTITY_DATA:
        return schemaService.getSyncariEntityById(resourceId)
            .map(e -> new AbacResource(ResourceType.ENTITY_DATA, e.getId(), e.getDisplayName()));
      case DATASET:
        return Optional.of(ABAC_DATASET_RESOURCE);
      default:
        return Optional.empty();
    }
  }
  public void deleteAttributeValues(List<String> ids) {
    if(CollectionUtils.isNotEmpty(ids)) {
      ids.forEach(id -> deleteAttributeValue(id));
    }
  }
  
  public Map<String, Boolean> check(AbacContext context) {
    if (!isAbacCheckRequired(context)) {
      return Map.of();
    }
    try {
      return syncariNativeAbacService.check(context);
    }catch (Exception e) {
      //Fallback to RBAC
      //Return empty map so that data filtering wont happen.
      return Map.of();
    }
  }
  
  public Object check(AbacContext context, Object data) {
    if (data == null) {
      return data;
    }
    if (!isAbacCheckRequired(context)) {
      return data;
    }

    if (data instanceof Optional<?>) {
      Optional<?> optional = (Optional<?>) data;
      if (optional.isEmpty()) {
        return optional;
      } else {
        var rs = resourceServiceFactory.getResourceService(context, optional.get());
        var filteredData =  Optional.ofNullable(rs.checkSingle(context, optional.get()));
        if(context.isThrowException() && filteredData.isEmpty()) {
          throw new AbacException(context.getThrowExceptionMessage());
        }
        return filteredData;

      }
    } else if (data instanceof Iterable<?>) {
      Iterator<Object> list = ((Iterable<Object>) data).iterator();
      if (!list.hasNext()) {
        return data;
      } else {
        var rs = resourceServiceFactory.getResourceService(context, list.next());
        return rs.checkList(context, (Iterable<Object>) data);
      }
    } else if (data instanceof Page<?>) {
      Page<Object> page = ((Page<Object>) data);
      if (CollectionUtils.isEmpty(page.getRecords())) {
        return data;
      } else {
        var rs = resourceServiceFactory.getResourceService(context, page.getRecords().get(0));
        var ret = rs.checkList(context, page.getRecords());
        page.setRecords(ret);
        return page;
      }
    }
    var rs = resourceServiceFactory.getResourceService(context, data);
    var filteredData = rs.checkSingle(context, data);
    if(context.isThrowException() &&  data != null && filteredData == null) {
      throw new AbacException(context.getThrowExceptionMessage());
    }
    return filteredData;
  }
  
  private boolean isAbacCheckRequired(AbacContext context) {
    if (!featureService.isEnabled(Features.ABAC, false)) return false;
    User user = SyncariContext.getUser();
    if (user == null) return false;
    if (valueRepo.countByResourceId(user.getId()) <= 0) return false;
    if (policyRepo.countByResourceType(context.getResourceType()) <= 0) return false;
    return isUserAttributesInUse(context);
  }

  public boolean isUserAttributesInUse(AbacContext context) {
    User user = SyncariContext.getUser();
    if (user == null) return false;
    List<String> userAttrIds = valueRepo.findByResourceId(user.getId()).stream()
        .map(v -> v.getAttributeId()).collect(Collectors.toList());
    for (var policy : policyRepo.findByResourceType(context.getResourceType())) {
      Map<String, Object> predicate = policy.getCondition();
      Expression filterExpression = new PredicateParser().fromMap(predicate);
      var evaluator = new AttributeListExpressionVisitor();
      filterExpression.accept(evaluator);
      if (CollectionUtils.containsAny(evaluator.getValue(), userAttrIds)) return true;
    }
    return false;
  }
}
