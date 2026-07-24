package com.syncari.api.rest.controllers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.syncari.api.rest.controllers.data.AbacAttributeRequestDTO;
import com.syncari.api.rest.controllers.data.AbacAttributeResponseDTO;
import com.syncari.api.rest.controllers.data.AbacAttributeValueDTO;
import com.syncari.api.rest.controllers.data.AbacPolicyRequestDTO;
import com.syncari.api.rest.controllers.data.AbacPolicyResponseDTO;
import com.syncari.core.abac.AbacService;
import com.syncari.core.abac.AbacUserFriendlyViewExpressionVisitor;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.AbacPolicy;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.pipeline.DiffInfoExpressionVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.utils.TextUtil;

@Component
public class AbacDataTranformer {
  
  @Autowired
  private AbacService abacService;
  @Autowired
  private AbacAttributeRepo attributeRepo;
  
  public AbacAttributeResponseDTO toAbacAttributeResponseDTO(AbacAttribute attr) {
    var abac =  new AbacAttributeResponseDTO();
    abac.setDataType(attr.getDataType());
    abac.setId(attr.getId());
    abac.setName(attr.getDisplayName());
    abac.setApiName(attr.getApiName());
    abac.setPolicies(0);//TODO
    abac.setResourceId(attr.getResourceId());
    abac.setResourceName(abacService.getResource(attr.getResourceType(), attr.getResourceId()).map(r -> r.getDisplayName()).orElse(""));
    abac.setResourceTypeId(attr.getResourceType().name());
    abac.setResourceTypeName(attr.getResourceType().getDisplayName());
    abac.setMultiValued(attr.isMultiValued());
    abac.setAllowedValues(attr.getAllowedValues());
    return abac;
  }
  
  public AbacAttribute toAbacAttribute(AbacAttributeRequestDTO attr) {
    return toAbacAttribute(null, attr);
  }
  
  public AbacAttribute toAbacAttribute(String id, AbacAttributeRequestDTO attr) {
    var abac =  new AbacAttribute();
    abac.setId(id);
    abac.setAllowedValues(attr.getAllowedValues());
    abac.setDisplayName(attr.getName());
    abac.setApiName(TextUtil.createApiName(attr.getName()));
    abac.setDataType(attr.getDataType());
    abac.setMultiValued(attr.isMultiValued());
    abac.setResourceId(attr.getResourceId());
    abac.setResourceType(ResourceType.valueOf(attr.getResourceTypeId()));
    return abac;
  }
  
  public AbacPolicyResponseDTO toAbacPolicyResponseDTO(AbacPolicy ply) {
    var policy = new AbacPolicyResponseDTO();
    policy.setCondition(ply.getCondition());
    Map<String, Object> predicate = ply.getCondition();
    Expression filterExpression = new PredicateParser().fromMap(predicate);
    var evaluator = new AbacUserFriendlyViewExpressionVisitor(attributeRepo);
    filterExpression.accept(evaluator);
    policy.setUserFriendlyCondition(evaluator.getValue());
    policy.setId(ply.getId());
    policy.setName(ply.getName());
    policy.setPermissions(ply.getPermissions());
    policy.setResourceId(ply.getResourceId());
    policy.setResourceTypeId(ply.getResourceType().name());
    policy.setResourceTypeName(ply.getResourceType().getDisplayName());
    abacService.getResource(ply.getResourceType(), ply.getResourceId())
        .ifPresent(r -> policy.setResourceName(r.getDisplayName()));
    return policy;
  }
  
  public AbacPolicy toAbacPolicy(AbacPolicyRequestDTO attr) {
    return toAbacPolicy(null, attr);
  }
  
  public AbacPolicy toAbacPolicy(String id, AbacPolicyRequestDTO ply) {
    var policy =  new AbacPolicy();
    policy.setCondition(ply.getCondition());
    policy.setId(id);
    policy.setName(ply.getName());
    policy.setApiName(TextUtil.createApiName(ply.getName()));
    policy.setPermissions(ply.getPermissions());
    policy.setResourceId(ply.getResourceId());
    policy.setResourceType(ResourceType.valueOf(ply.getResourceTypeId()));
    return policy;
  }

  public List<AbacAttributeResponseDTO> toAbacAttributeListResponseDTO(List<AbacAttribute> abac) {
    if(CollectionUtils.isNotEmpty(abac)) {
      return abac.stream().map(a -> toAbacAttributeResponseDTO(a)).collect(Collectors.toList());
    }
    return List.of();
  }

  public List<AbacPolicyResponseDTO> toAbacPolicyListResponseDTO(List<AbacPolicy> abac) {
    if(CollectionUtils.isNotEmpty(abac)) {
      return abac.stream().map(a -> toAbacPolicyResponseDTO(a)).collect(Collectors.toList());
    }
    return List.of();
  }

  public List<AbacAttributeValueDTO> toAbacAttributeValueListResponseDTO(
      List<AbacAttributeValue> abac) {
    if(CollectionUtils.isNotEmpty(abac)) {
      return abac.stream().map(a -> toAbacAttributeValueResponseDTO(a)).collect(Collectors.toList());
    }
    return List.of();
  }


  public AbacAttributeValueDTO toAbacAttributeValueResponseDTO(AbacAttributeValue abac) {
    var val = new AbacAttributeValueDTO();
    val.setId(abac.getId());
    var attrOpt = abacService.getAttribute(abac.getAttributeId());
    val.setAttributeId(abac.getAttributeId());
    val.setResourceId(abac.getResourceId());
    if (attrOpt.isPresent()) {
      var attr = attrOpt.get();
      val.setAttributeName(attr.getDisplayName());
      val.setResourceTypeId(attr.getResourceType().name());
      val.setResourceTypeName(attr.getResourceType().getDisplayName());
      abacService.getResourceForValues(attr.getResourceType(), abac.getResourceId())
          .ifPresent(res -> val.setResourceName(res.getDisplayName()));
    }
    val.setValue(abac.getValue());
    return val;
  }

  public AbacAttributeValue toAbacAttributeValue(AbacAttributeValueDTO req) {
    var val =  new AbacAttributeValue();
    val.setId(req.getId());
    val.setAttributeId(req.getAttributeId());
    val.setResourceId(req.getResourceId());
    val.setValue(req.getValue());
    return val;
  }

}
