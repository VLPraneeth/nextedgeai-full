package com.syncari.core.model.misc;

import java.util.Objects;

public class ResourcePrivilege {
	private String resourceId;
	private String privilegeId;

	public ResourcePrivilege(String resourceId, String privilegeId) {
		this.resourceId = resourceId;
		this.privilegeId = privilegeId;
	}

	public String getResourceId() {
		return resourceId;
	}

	public String getPrivilegeId() {
		return privilegeId;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof ResourcePrivilege)) {
			return false;
		}

		ResourcePrivilege c = (ResourcePrivilege) o;
		if (resourceId == null && privilegeId == null && c.resourceId == null && c.privilegeId == null)
			return true;
		
		if (resourceId == null && c.resourceId == null && privilegeId != null && c.privilegeId != null)
			return privilegeId.equalsIgnoreCase(c.privilegeId);
		
		if (resourceId != null && c.resourceId != null && privilegeId == null && c.privilegeId == null)
			return resourceId.equalsIgnoreCase(c.resourceId);
		
		if ((resourceId == null && c.resourceId != null) || (resourceId != null && c.resourceId == null))
			return false;
		if ((privilegeId == null && c.privilegeId != null) || (privilegeId != null && c.privilegeId == null))
			return false;

		return resourceId.equalsIgnoreCase(c.resourceId) && privilegeId.equalsIgnoreCase(c.privilegeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(resourceId, privilegeId);
	}

}
