package com.syncari.api.rest.controllers.data;

import java.util.List;
import java.util.Map;
import com.syncari.core.model.abac.Permission;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AbacPolicyResponseDTO {
  String id;
  String name;
  String resourceId;
  String resourceName;
  Map<String, Object> condition;
  List<Permission> permissions;
  Integer users;
  String accessReport;
  String resourceTypeId;
  String resourceTypeName;
  String userFriendlyCondition;
}
