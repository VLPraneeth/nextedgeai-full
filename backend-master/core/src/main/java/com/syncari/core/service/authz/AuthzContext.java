package com.syncari.core.service.authz;

public class AuthzContext {
    private String user;
    private String roleId;
    private String privilege;
    private String resource;

    public AuthzContext(String user, String privilege, String resource) {
        this.user = user;
        this.privilege = privilege;
        this.resource = resource;
    }

    public AuthzContext(String user, String roleId, String privilege, String resource) {
        this(user, privilege, resource);
        this.roleId = roleId;
    }

    public String getUser() {
        return user;
    }

    public String getPrivilege() {
        return privilege;
    }

    public String getResource() {
        return resource;
    }

    public String getRoleId() {
        return roleId;
    }

}

