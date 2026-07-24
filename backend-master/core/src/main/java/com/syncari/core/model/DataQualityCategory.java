package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
@Accessors(chain = true)
public class DataQualityCategory extends UUIDAuditModel {

  public static final String OTHER = "Other";
  public static final String COMPLETENESS = "Completeness";
  public static final String VALIDITY = "Validity";
  public static final String UNIQUENESS = "Uniqueness";
  public static final String CONFORMITY = "Conformity";

  @NotNull
  @NotNull(message = "Name is required")
  private String name;
  private boolean custom;
  private boolean seeded = false;

  public DataQualityCategory(String name) {
    this.name = name;
    this.custom = false;
    this.seeded = false;
  }

  public DataQualityCategory() {
    this.custom = false;
    this.custom = seeded;
  }
}
