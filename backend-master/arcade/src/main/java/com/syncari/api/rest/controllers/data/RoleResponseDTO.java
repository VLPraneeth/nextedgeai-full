package com.syncari.api.rest.controllers.data;

import java.util.List;
import java.util.Set;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RoleResponseDTO {

    String id;
    String name;
    String description;
    boolean active;
    boolean system;
    List<PrivilegeDTO> privileges;
    List<UserResponse> users;
    Set<String> tags;
}
