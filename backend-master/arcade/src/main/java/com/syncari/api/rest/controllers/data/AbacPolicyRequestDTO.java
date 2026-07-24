package com.syncari.api.rest.controllers.data;

import java.util.List;
import java.util.Map;
import com.syncari.core.model.abac.Permission;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AbacPolicyRequestDTO {
  String name;
  String resourceId;
  private Map<String, Object> condition;
  List<Permission> permissions;
  String resourceTypeId;
}
