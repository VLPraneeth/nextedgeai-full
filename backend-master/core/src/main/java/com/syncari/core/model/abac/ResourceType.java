package com.syncari.core.model.abac;

import java.util.List;

public enum ResourceType {
  USER("User", true, List.of()),
  GLOBAL("Global", false, List.of(Permission.CREATE_ENTITY, Permission.CREATE_DATASET)),
  ENTITY("Entity", true, List.of(Permission.READ, Permission.CREATE_DRAFT, Permission.DELETE, Permission.APPROVE_DRAFT, Permission.DELETE_DRAFT, Permission.PURGE)),
  ENTITY_DATA("Entity Data", false, List.of(Permission.READ, Permission.DELETE, Permission.UPDATE)),
  DATASET("Dataset", true, List.of(Permission.READ, Permission.DELETE, Permission.UPDATE, Permission.EXECUTE));

  private final String displayName;
  private final boolean multiSelectSupport;
  private final List<Permission> permissions;

  ResourceType(String displayName, boolean multiSelectSupport, List<Permission> permissions) {
    this.displayName = displayName;
    this.multiSelectSupport = multiSelectSupport;
    this.permissions = permissions;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public boolean isMultiSelectSupport() {
    return multiSelectSupport;
  }
}
