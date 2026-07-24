package com.syncari.core.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.repositories.customer.ComponentDependencyRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ComponentDependencyService {
	@Autowired
	private ComponentDependencyRepo repo;

	public List<String> findDependencies(String toId, ComponentType toComponent, ComponentType fromComponent) {
		List<String> result = new ArrayList<>();
		List<ComponentDependency> data = repo.findByToIdAndToComponent(toId, toComponent);
		for (ComponentDependency d : data) {
			if(d.getFromComponent() == fromComponent) result.add(d.getFromId());
		}
		return result;
	}

	public List<ComponentDependency> findDependenciesBy(String fromId, ComponentType fromComponent) {
		return repo.findByFromIdAndFromComponent(fromId, fromComponent);
	}

	public List<ComponentDependency> findDependenciesFor(String toId, ComponentType toComponent) {
		return repo.findByToIdAndToComponent(toId, toComponent);
	}

	public void addDependency(String fromId, ComponentType fromComponent, String toId,
			ComponentType toComponent) {
		if (StringUtils.isBlank(fromId))
			throw new RuntimeException("fromId is required to add dependency");
		if (fromComponent == null)
			throw new RuntimeException("fromComponent is required to add dependency");
		if (StringUtils.isBlank(toId))
			throw new RuntimeException("toId is required to add dependency");
		if (toComponent == null)
			throw new RuntimeException("toComponent is required to add dependency");
		try {
		    repo.save(new ComponentDependency(fromId, fromComponent, toId, toComponent));
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
	}

	public void deleteDependenciesBy(String fromId, ComponentType fromComponent){
		repo.deleteByFromIdAndFromComponent(fromId, fromComponent);
	}

	public void deleteDependenciesOn(String toId, ComponentType toComponent){
		repo.deleteByToIdAndToComponent(toId, toComponent);
	}

	public void deleteDependency(String fromId, ComponentType fromComponent, String toId, ComponentType toComponent){
		repo.deleteByFromIdAndFromComponentAndToIdAndToComponent(fromId, fromComponent, toId, toComponent);
	}

	public void updateDependenciesFor(String fromId, ComponentType fromComponent, List<ComponentDependency> updatedDependencies){
		// first delete existing dependencies
		deleteDependenciesBy(fromId, fromComponent);

		// save all existing dependencies
		updatedDependencies.forEach(dep -> {
			addDependency(dep.getFromId(), dep.getFromComponent(), dep.getToId(), dep.getToComponent());
		});
	}

	public void deleteById(String depId){
		repo.deleteById(depId);
	}
}
