package com.syncari.api.rest.controllers.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.Data;

@Data
public class UserRequest {
    boolean isAdmin;
    boolean isGhosted;
    boolean isGhostUser;
    boolean isSuperAdmin;
    private String email;
    private String firstName;
    private String lastName;
    private String timeZone;
    boolean isApiUser;
    private Map<String, Set<String>> userRoles = new HashMap<String, Set<String>>();
    boolean orgAdmin;
}