package com.syncari.core.model.abac;

import com.syncari.core.model.UUIDAuditModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)   
@NoArgsConstructor
@AllArgsConstructor
public class AbacAttributeValue extends UUIDAuditModel {

  private String attributeId;
  private String resourceId;
  private Object value;
  
  public AbacAttributeValue copyFrom(AbacAttributeValue source) {
    if (source == null) {
        return this;
    }
    this.attributeId = source.attributeId;
    this.resourceId = source.resourceId;
    this.value = source.value;
    return this;
  }
}
