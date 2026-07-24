package com.syncari.core.schema;

import java.util.*;

import com.syncari.core.model.util.SyncDirection;

import lombok.Data;

@Data
public class Schema {
	Date lastRefreshedAt;
	List<EntityDef> entities = new ArrayList<EntityDef>();

	public Optional<EntityDef> findEntityByName(String apiName){
		return entities.stream().filter(e->apiName.equals(e.getApiName())).findAny();
	}

	public Optional<EntityDef> findEntityById(String id){
		return entities.stream().filter(e->id.equals(e.getId())).findAny();
	}

	public void addEntity(EntityDef entityDef) {
		this.entities.add(entityDef);
	}

	public List<Relationship> getConnections() {
		Map<String, Relationship> relationMap = new HashMap<String, Relationship>();
		entities.stream().forEach(e -> {
			e.connectedTo.stream().forEach(c -> {
				String key = e.getId() + ":" + c;
				String reverseKey = c + ":" + e.getId();
				if (relationMap.containsKey(reverseKey)) {
					Relationship relationship = relationMap.get(reverseKey);
					relationship.setDirection(SyncDirection.BIDI);
					relationMap.put(reverseKey, relationship);
				} else if (relationMap.containsKey(reverseKey)) {
					// do nothing
				} else {
					relationMap.put(key, new Relationship(e.getId()+c, e.getId(), c, SyncDirection.OUTBOUND));
				}
			});
		});
		return List.copyOf(relationMap.values());
	}
}
