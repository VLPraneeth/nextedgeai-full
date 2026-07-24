package com.syncari.api.rest.controllers.data.fragment;

import com.syncari.core.model.util.Scope;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class FragmentDTO {

    private String id;
    private String displayName;
    private Scope scope;
    private String description;
    private String ownerFirstName;
    private String ownerLastName;
    private String ownerEmail;
    private Set<String> tags = new HashSet<>();
    private FragmentGraphDTO fragment;
    private boolean shared;
    private String iconPath;
    private boolean hidden;
    private boolean sharedWithInstances;

}
