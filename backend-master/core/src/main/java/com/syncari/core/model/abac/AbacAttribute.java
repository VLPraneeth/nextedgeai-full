package com.syncari.core.model.abac;

import java.util.List;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)    
public class AbacAttribute extends UUIDAuditModel {

  private String apiName;
  private String displayName;
  private ResourceType resourceType;
  private String resourceId;
  private String dataType;
  private List<String> allowedValues;
  private boolean multiValued;
  private String externalId;
  
  public AbacAttribute copyFrom(AbacAttribute source) {
    if (source == null) {
        return this;
    }
    this.displayName = source.displayName;
    this.resourceType = source.resourceType;
    this.resourceId = source.resourceId;
    this.dataType = source.dataType;
    this.allowedValues = source.allowedValues;
    this.multiValued = source.multiValued;
    return this;
  }
}
