package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.DELETE_ABAC;
import static com.syncari.core.security.Permissions.READ_ABAC;
import static com.syncari.core.security.Permissions.WRITE_ABAC;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.syncari.api.rest.controllers.data.AbacAttributeRequestDTO;
import com.syncari.api.rest.controllers.data.AbacAttributeResponseDTO;
import com.syncari.api.rest.controllers.data.AbacAttributeValueDTO;
import com.syncari.api.rest.controllers.data.AbacPolicyRequestDTO;
import com.syncari.api.rest.controllers.data.AbacPolicyResponseDTO;
import com.syncari.api.rest.controllers.data.ResourceTypeDTO;
import com.syncari.core.abac.AbacResource;
import com.syncari.core.abac.AbacService;
import com.syncari.core.abac.AbacServiceImpl;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.utils.KeyValue;

@RestController
@RequestMapping("/api/v1/abac")
public class AbacController {
  @Autowired
  private AbacService abacService;
  @Autowired
  private AbacDataTranformer dataTranformer;

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/policy")
  public List<AbacPolicyResponseDTO> listPolicies() {
    var abac = abacService.listPolicies();
    return dataTranformer.toAbacPolicyListResponseDTO(abac);
  }

  @Secured(WRITE_ABAC)
  @RequestMapping(method = RequestMethod.POST, value = "/policy")
  public AbacPolicyResponseDTO addPolicy(@RequestBody AbacPolicyRequestDTO req) {
    validateCondition(MapUtils.isEmpty(req.getCondition()) , i18n("abac_policy_condition_invalid"));
    validateCondition(StringUtils.isEmpty(req.getName()), i18n("abac_policy_name_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceId()), i18n("abac_policy_resource_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceTypeId()), i18n("abac_policy_resourceType_empty"));
    validateCondition(CollectionUtils.isEmpty(req.getPermissions()), i18n("abac_policy_permissions_empty"));
    var abac = abacService.savePolicy(dataTranformer.toAbacPolicy(req));
    return dataTranformer.toAbacPolicyResponseDTO(abac);
  }

  @Secured(WRITE_ABAC)
  @RequestMapping(method = RequestMethod.PUT, value = "/policy/{id}")
  public AbacPolicyResponseDTO editPolicy(@PathVariable String id,
      @RequestBody AbacPolicyRequestDTO req) {
    validateCondition(MapUtils.isEmpty(req.getCondition()) , i18n("abac_policy_condition_invalid"));
    validateCondition(StringUtils.isEmpty(req.getName()), i18n("abac_policy_name_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceId()), i18n("abac_policy_resource_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceTypeId()), i18n("abac_policy_resourceType_empty"));
    validateCondition(CollectionUtils.isEmpty(req.getPermissions()), i18n("abac_policy_permissions_empty"));
    var abac = abacService.savePolicy(dataTranformer.toAbacPolicy(id, req));
    return dataTranformer.toAbacPolicyResponseDTO(abac);
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/policy/{id}")
  public AbacPolicyResponseDTO getPolicy(@PathVariable String id) {
    var abac = abacService.getPolicy(id);
    validateCondition(abac.isEmpty(), i18n("abac_policy_not_found", id));
    return dataTranformer.toAbacPolicyResponseDTO(abac.get());
  }

  @Secured(DELETE_ABAC)
  @RequestMapping(method = RequestMethod.DELETE, value = "/policy/{id}")
  public void deletePolicy(@PathVariable String id) {
    abacService.deletePolicy(id);
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/attribute")
  public List<AbacAttributeResponseDTO> listAttributes() {
    var abac = abacService.listAttributes();
    return dataTranformer.toAbacAttributeListResponseDTO(abac);
  }

  @Secured(WRITE_ABAC)
  @RequestMapping(method = RequestMethod.POST, value = "/attribute")
  public AbacAttributeResponseDTO addAttribute(@RequestBody AbacAttributeRequestDTO req) {
    validateCondition(StringUtils.isEmpty(req.getName()), i18n("abac_attribute_name_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceId()), i18n("abac_policy_resource_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceTypeId()), i18n("abac_policy_resourceType_empty"));
    validateCondition(
        StringUtils.equals("enumeration", req.getDataType())
            && CollectionUtils.isEmpty(req.getAllowedValues()),
        i18n("abac_attribute_allowed_values_empty"));
    var abac = abacService.saveAttribute(dataTranformer.toAbacAttribute(req));
    return dataTranformer.toAbacAttributeResponseDTO(abac);
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/attribute/supportedDataTypes")
  public List<Map<String, String>> supportedDataTypes() {
    var supportedTypes = new TreeMap<>(Map.of(
          "boolean","Boolean",
          "integer","Integer",
          "double","Double",
          "date","Date",
          "datetime","Date Time",
          "text","Text"
  )).entrySet().stream().map(e -> Map.of("value", e.getKey(), "label", e.getValue())).collect(Collectors.toList());
    supportedTypes.add(Map.of("value", "enumeration", "label", "Enumeration"));
    return supportedTypes;
  }

  @Secured(WRITE_ABAC)
  @RequestMapping(method = RequestMethod.PUT, value = "/attribute/{id}")
  public AbacAttributeResponseDTO editAttribute(@PathVariable String id,
      @RequestBody AbacAttributeRequestDTO req) {
    validateCondition(StringUtils.isEmpty(req.getName()), i18n("abac_attribute_name_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceId()), i18n("abac_policy_resource_empty"));
    validateCondition(StringUtils.isEmpty(req.getResourceTypeId()), i18n("abac_policy_resourceType_empty"));
    validateCondition(
        StringUtils.equals("enumeration", req.getDataType())
            && CollectionUtils.isEmpty(req.getAllowedValues()),
        i18n("abac_attribute_allowed_values_empty"));
    var abac = abacService.saveAttribute(dataTranformer.toAbacAttribute(id, req));
    return dataTranformer.toAbacAttributeResponseDTO(abac);
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/attribute/{id}")
  public AbacAttributeResponseDTO getAttribute(@PathVariable String id) {
    var abac = abacService.getAttribute(id);
    validateCondition(abac.isEmpty(), i18n("abac_attr_not_found", id));
    return dataTranformer.toAbacAttributeResponseDTO(abac.get());
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/attribute/user")
  public AbacAttributeResponseDTO getUserAttribute() {
    var abac = abacService.getAttribute(AbacServiceImpl.ABAC_USER_RESOURCE.getId());
    validateCondition(abac.isEmpty(), i18n("abac_attr_not_found", "user"));
    return dataTranformer.toAbacAttributeResponseDTO(abac.get());
  }

  @Secured(DELETE_ABAC)
  @RequestMapping(method = RequestMethod.DELETE, value = "/attribute/{id}")
  public void deleteAttribute(@PathVariable String id) {
    abacService.deleteAttribute(id);
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/resource/{type}")
  public List<AbacResource> listResource(@PathVariable ResourceType type) {
    return abacService.listResources(type);
  }
  
 @Secured(READ_ABAC)
 @RequestMapping(method = RequestMethod.GET, value = "/resource/{type}/attribute_value")
 public List<AbacResource> listResourceForValues(@PathVariable ResourceType type) {
   return abacService.listResourcesForValues(type);
 }

 @Secured(READ_ABAC)
 @RequestMapping(method = RequestMethod.GET, value = "/resource_type")
 public List<ResourceTypeDTO> listResourceType(
     @RequestParam(name = "excludeUser", required = false) Boolean excludeUser) {
   var resourceTypes = Arrays.asList(ResourceType.values());
   if (BooleanUtils.isTrue(excludeUser)) {
     resourceTypes =
         resourceTypes.stream().filter(t -> t != ResourceType.USER).collect(Collectors.toList());
   }
   return resourceTypes.stream()
       .map(rt -> new ResourceTypeDTO(rt.name(), rt.getDisplayName(), rt.getPermissions(), rt.isMultiSelectSupport()))
       .collect(Collectors.toList());
 }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/attribute_value")
  public List<AbacAttributeValueDTO> listAttributeValues() {
    var abac = abacService.listAttributeValues();
    return dataTranformer.toAbacAttributeValueListResponseDTO(abac);
  }

  @Secured(WRITE_ABAC)
  @RequestMapping(method = RequestMethod.POST, value = "/attribute_value")
  public List<AbacAttributeValueDTO> addAttributeValue(
      @RequestBody List<AbacAttributeValueDTO> req) {
    List<AbacAttributeValueDTO> res = new ArrayList<>();
    if(CollectionUtils.isNotEmpty(req)) {
      req.forEach(r -> {
        validateCondition(StringUtils.isEmpty(r.getAttributeId()), i18n("abac_attribute_empty"));
        validateCondition(StringUtils.isEmpty(r.getResourceId()), i18n("abac_policy_resource_empty"));
      });
      req.forEach(r -> {
        var abac = abacService.saveAttributeValue(dataTranformer.toAbacAttributeValue(r));
        res.add(dataTranformer.toAbacAttributeValueResponseDTO(abac));
      });
    }
    return res;
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/attribute_value/{id}")
  public AbacAttributeValueDTO getAttributeValue(@PathVariable String id) {
    var abac = abacService.getAttributeValue(id);
    validateCondition(abac.isEmpty(), i18n("abac_attr_value_not_found", id));
    return dataTranformer.toAbacAttributeValueResponseDTO(abac.get());
  }

  @Secured(DELETE_ABAC)
  @RequestMapping(method = RequestMethod.DELETE, value = "/attribute_value/{id}")
  public void deleteAttributeValue(@PathVariable String id) {
    abacService.deleteAttributeValue(id);
  }
  
  @Secured(DELETE_ABAC)
  @RequestMapping(method = RequestMethod.PATCH, value = "/attribute_value")
  public void deleteAttributeValueBulk(@RequestBody List<String> ids) {
    abacService.deleteAttributeValues(ids);
  }

  @Secured(READ_ABAC)
  @RequestMapping(method = RequestMethod.GET, value = "/resource/{type}/{id}/attribute")
  public List<AbacAttributeResponseDTO> getAttributesOfResource(@PathVariable ResourceType type, @PathVariable String id) {
    var abac = abacService.listAttributes(type, id);
    return dataTranformer.toAbacAttributeListResponseDTO(abac);
  }
  
 @Secured(READ_ABAC)
 @RequestMapping(method = RequestMethod.GET, value = "/resource/{type}/{id}/token")
 public Map<String, List<KeyValue>> getAttributesTokens(@PathVariable ResourceType type, @PathVariable String id) {
   return abacService.listAttributeTokens(type, id);
 }
}
