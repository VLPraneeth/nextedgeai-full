package com.syncari.api.rest.controllers.data;

import java.util.List;
import com.syncari.core.model.abac.Permission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTypeDTO {
  private String name;
  private String displayName;
  private List<Permission> permissions;
  private boolean multiSelectSupport;
}
