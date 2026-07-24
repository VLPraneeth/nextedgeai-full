package com.syncari.core.model.abac;

import java.util.List;
import java.util.Map;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AbacPolicy extends UUIDAuditModel {

  private String name;
  private String apiName;
  private ResourceType resourceType;
  private String resourceId;
  private Map<String, Object> condition;
  private List<Permission> permissions;
  private String externalId;

  public AbacPolicy copyFrom(AbacPolicy source) {
    if (source == null) {
      return this;
    }
    return this.setName(source.getName()).setResourceType(source.getResourceType())
        .setResourceId(source.getResourceId()).setCondition(source.getCondition()).setPermissions(
            source.getPermissions() != null ? source.getPermissions() : List.of());
  }
}
