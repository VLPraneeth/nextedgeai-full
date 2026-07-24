package com.syncari.api.rest.controllers.data;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AbacAttributeResponseDTO {
  String id;
  String name;
  String apiName;
  String resourceTypeId;
  String resourceTypeName;
  String resourceId;
  String resourceName;
  String dataType;
  Integer policies;
  boolean multiValued;
  private List<String> allowedValues;
}
