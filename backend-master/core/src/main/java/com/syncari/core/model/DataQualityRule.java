
package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
@Accessors(chain = true)
public class DataQualityRule extends UUIDAuditModel {

  @NotNull
  @NotNull(message = "Name is required")
  private String name;

  @NotNull
  @NotNull(message = "Scope is required")
  private List<String> scope;

  @NotNull
  @NotNull(message = "Scope Type is required")
  private String scopeType;

  @NotNull
  @NotNull(message = "Category is required")
  private String category;

  @NotNull
  @NotNull(message = "Policy is required")
  private String policy;

  @NotNull
  private Boolean isDeleted;

  private Integer passed;
  private Integer failed;

  private Map<String, Object> ruleConfig;

  @NotNull
  private String mappingGraphId;

  @NotNull
  private String entityId;

  private String originalId;

  public DataQualityRule() {
    this.passed = 0;
    this.failed = 0;
  }
}
