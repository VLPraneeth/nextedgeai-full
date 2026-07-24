package com.syncari.core.model;

import java.util.HashSet;
import java.util.Set;

import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
@Accessors(chain = true)
public class UserRole extends UUIDAuditModel {
	
    private String userId;
	private Set<String> roleIds = new HashSet<>();
    
	public UserRole(String userId) {
		this.userId= userId;
	}

	public UserRole addRole(String roleId){
		roleIds.add(roleId);
		return this;
	}
	
	public UserRole removeRole(String roleId){
		roleIds.remove(roleId);
		return this;
	}
	
}
