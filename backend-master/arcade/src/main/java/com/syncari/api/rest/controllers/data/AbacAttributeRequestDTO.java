package com.syncari.api.rest.controllers.data;

import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AbacAttributeRequestDTO {
  String name;
  String resourceTypeId;
  String resourceId;
  String dataType;
  boolean multiValued;
  private List<String> allowedValues;
}
