package com.syncari.core.schema;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.util.Status;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class EntityDef {
	String id;
	String apiName;
	String displayName;
	String dataStoreName;
	String description;
	String subLabel;
	String iconPath;
	PipelineStatus pipelineStatus;
	EntityType type;
	Set<String> connectedTo = new HashSet<>();
	Set<String> tags = new HashSet<>();
	List<AttributeDef> fields = new ArrayList<>();
	EntityLocation location;
	Status status;
	DraftStatus draftStatus;
    String createdBy;
    String updatedBy;
    Date createdAt;
    Date updatedAt;
    boolean isReadonly;
    boolean isChild;
	boolean syncariSource;
	boolean runDFI;
	boolean runMerge;

	public EntityDef(String apiName, String displayName, String dataStoreName, String description, Set<String> tags){
		this.apiName = apiName;
		this.displayName = displayName;
		this.dataStoreName = dataStoreName;
		this.description = description;
		this.tags = tags;
	}

	public EntityDef(String id, String name) {
		this.id = id;
		this.apiName = name;
	}

	public EntityDef() {}
	public Optional<AttributeDef> getField(String apiName){
		return fields.stream().filter(f->apiName.equals(f.getApiName())).findFirst();
	}
	
	public Optional<AttributeDef> getFieldById(String id){
	    return fields.stream().filter(f->id.equals(f.getId())).findFirst();
	}

	public List<AttributeDef> getActiveFields(){
		return fields.stream().filter(f->f.getStatus()== Status.ACTIVE).collect(Collectors.toList());
	}
}