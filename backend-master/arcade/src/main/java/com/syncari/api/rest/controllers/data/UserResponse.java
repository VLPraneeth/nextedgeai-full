package com.syncari.api.rest.controllers.data;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.util.Status;

import lombok.Data;

@Data
public class UserResponse {
    private String id;
    private String email;
    private Status status;
    private String orgId;
    private String orgName;
    private String orgLogo;
    private String orgType;
    private String currentInstanceName;
    private String currentInstanceNextEdgeId;
    private InstanceType currentInstanceType;
    private String firstName;
    private String lastName;
    private boolean ghosted;
    private boolean isGhostUser;
    private boolean isSuperAdmin;
    private boolean isSyncariDev;
    private String timeZone;
    private boolean isApiUser;
    private String clientId;
    private String clientSecret;
    private boolean passwordExpired;
    private Map<String, Set<String>> userRoles = new HashMap<String, Set<String>>();
    private Set<String> activeGhostAccessList = new HashSet<>();
    protected String createdBy;
    protected String updatedBy;
    protected Date createdAt;
    protected Date updatedAt;
    private List<String> privileges;
    private boolean orgAdmin;
    private long associatedInstancesCount;
    private String maxInstance;

    public boolean getIsGhostUser() {
        return isGhostUser;
    }
    
    public boolean getIsApiUser() {
        return isApiUser;
    }

    public boolean getIsSuperAdmin() {
        return isSuperAdmin;
    }

}
