package com.syncari.core.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.annotation.Transient;

import com.syncari.core.model.misc.ResourcePrivilege;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Role extends UUIDAuditModel {
	@NotNull(message = "Role name is required")
	private String name;
	private String description;
	private boolean system;
	private boolean active;
	private Set<ResourcePrivilege> privileges = new HashSet<>();
	@Transient
    List<Tag> tags = new ArrayList<>();

	public Role(String name) {
		this.name = name;
	}

	public void setPrivileges(Set<ResourcePrivilege> privilegeIds) {
		if (privilegeIds == null)
			return;
		this.privileges = privilegeIds;
	}
}
