package com.syncari.core.model;

import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.OrganizationType;
import com.syncari.core.model.security.OAuthConfig;
import com.syncari.core.model.util.Status;

import lombok.Data;

@Data
public class Organization extends UUIDAuditModel {
	@NotNull(message = "Org name is required")
	private String name;
	private String logoLocation;
	private Status status;
	private List<Instance> instances = new ArrayList<>();
	private SSOAuthConfig ssoConfig;

    private OrganizationType orgType = OrganizationType.standard;
    // If partner org, the OAuth configurations, if any, to use instead of NextEdge AI defaults.
    private Map<String, OAuthConfig> oauthConfigs = new HashMap<String, OAuthConfig>();
	private String deletedBy;
	private Date deletedAt;
	private String maxNumberOfInstances;
	private String insightsProviderOrgId;

	public void addInstance(Instance instance) {
		instances.add(instance);
	}
	public Organization(String name) {
		this.name = name;
	}

	public Organization() {

	}

	public Optional<Instance> getInstance(String nextEdgeId) {
		return instances.stream().filter(i -> nextEdgeId.equals(i.getNextEdgeId())).findFirst();
	}

	public void removeInstance(String nextEdgeId) {
	    ListIterator<Instance> iter = instances.listIterator();
	    while(iter.hasNext()){
	        if(nextEdgeId.equals(iter.next().getNextEdgeId())){
	            iter.remove();
	        }
	    }
	}

	public Optional<Instance> getActiveInstance(String nextEdgeId) {
		return instances.stream().filter(i -> nextEdgeId.equals(i.getNextEdgeId()) && i.isActive()).findFirst();
	}

	public List<Instance> getActiveInstances() {
		return instances.stream().filter(i -> i.isActive()).collect(Collectors.toList());
	}

	public Set<String> getAllNextEdgeIds() {
	    return instances.stream().map(Instance::getNextEdgeId).collect(Collectors.toSet());
	}

	@Deprecated
	public Set<String> getAllSyncariIds() {
		return getAllNextEdgeIds();
	}
	
	public Optional<Instance> getInstanceByName(String instanceName) {
		return instances.stream().filter(i -> instanceName.equals(i.getName())).findFirst();
	}

	public boolean isActive(){
		return status == null || status== Status.ACTIVE;
	}

	public boolean isSSOEnabled(){
		return ssoConfig != null;
	}

    public boolean isPartner() {
        return orgType == OrganizationType.partner;
    }
	public boolean isTrial() {
		return orgType == OrganizationType.trial;
	}

}
