package com.syncari.core.repositories.customer;

import java.util.List;

import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.repositories.SyncariRepo;

public interface ComponentDependencyRepo extends SyncariRepo<ComponentDependency> {
	
	List<ComponentDependency> findByToIdAndToComponent(String toId, ComponentType toComponent);

	List<ComponentDependency> deleteByFromIdAndFromComponent(String fromId, ComponentType fromComponent);

	List<ComponentDependency> deleteByToIdAndToComponent(String toId, ComponentType toComponent);

	List<ComponentDependency> deleteByFromIdAndFromComponentAndToIdAndToComponent(String fromId,
																				  ComponentType fromComponent,
																				  String toId,
																				  ComponentType toComponent);

	List<ComponentDependency> findByFromIdAndFromComponent(String fromId, ComponentType fromComponent);
	
}
