package com.syncari.core.model;

import java.util.*;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncari.core.Features;
import com.syncari.core.model.misc.Audit;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.util.Status;

import lombok.Data;

@Data
public class Instance extends Audit {
	@NotNull(message = "Instance name is required")
	private String name;
	private String displayName;
	@NotNull(message = "Instance type is required")
	private InstanceType type;
	private String planId;
	private Status status;
	private List<Quota> quota = new ArrayList<>();
	private List<String> featureIds = new ArrayList<>();
	@NotNull(message = "NextEdge ID is required")
	private String nextEdgeId;
	/**
	 * Transitional persisted alias for installations created before the NextEdge ID migration.
	 * New code must use {@link #getNextEdgeId()}.
	 */
	@Deprecated
	private String syncariId;

	@JsonIgnore
	Map<String, Feature> features = Map.of();
	private Map<ResourceType, Resource> resources = new HashMap<>();
	private String deletedBy;
	private Date deletedAt;

	public Instance(String name, String displayName) {
		this.name = name;
		this.displayName = displayName;
		this.nextEdgeId = generateNextEdgeId();
		this.syncariId = this.nextEdgeId;
		this.type = InstanceType.production;
		this.setCreatedAt(new Date());
	}
	
	public String getDisplayName() {
	    return StringUtils.isBlank(displayName) ? name : displayName;
	}

	public void addResource(Resource resource) {
		resources.put(resource.getType(), resource);
	}

	public Optional<Resource> getResource(ResourceType type) {
		return Optional.ofNullable(resources.get(type));
	}
	
	public Optional<String> getResourceConfig(ResourceType type, String key) {
	    Resource resource = resources.get(type);
	    if(resource == null) return Optional.empty();
	    return Optional.ofNullable(resource.getConfiguration().get(key));
	}
	
	public String getDbName() {
	    Optional<Resource> dbName = getResource(ResourceType.DATABASE);
	    return Optional.ofNullable(dbName.get().getConfiguration().get("database")).get();
	}

	public Instance() {

	}

	public boolean isActive(){
		return status == null || status == Status.ACTIVE;
	}

	private static String generateNextEdgeId() {
		return RandomStringUtils.random(6, true, true).toUpperCase();
	}

	public String getNextEdgeId() {
		return StringUtils.isBlank(nextEdgeId) ? syncariId : nextEdgeId;
	}

	public void setNextEdgeId(String nextEdgeId) {
		this.nextEdgeId = nextEdgeId;
	}

	@Deprecated
	public String getSyncariId() {
		return getNextEdgeId();
	}

	@Deprecated
	public void setSyncariId(String legacyId) {
		this.syncariId = legacyId;
		if (StringUtils.isBlank(this.nextEdgeId)) {
			this.nextEdgeId = legacyId;
		}
	}

	public boolean isTrial(){
		return this.type == InstanceType.trial;
	}

	public boolean featureEnabled(Features feature) {
	    return getFeatures().containsKey(feature.name());
	}
}
