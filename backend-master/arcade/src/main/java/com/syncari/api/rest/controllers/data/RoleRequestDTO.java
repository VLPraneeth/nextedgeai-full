package com.syncari.api.rest.controllers.data;

import java.util.List;
import java.util.Set;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RoleRequestDTO {
    String name;
    String description;
    boolean active;
    List<String> privileges;
    List<String> users;
    Set<String> tags;
}
