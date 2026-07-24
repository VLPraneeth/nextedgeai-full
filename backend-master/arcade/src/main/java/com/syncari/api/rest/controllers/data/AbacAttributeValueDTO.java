package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AbacAttributeValueDTO {
  String id;
  private String attributeId;
  private String attributeName;
  private String resourceId;
  private String resourceName;
  private String resourceTypeId;
  private String resourceTypeName;
  private Object value;
}
