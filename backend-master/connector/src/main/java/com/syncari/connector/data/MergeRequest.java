package com.syncari.connector.data;

import java.util.*;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MergeRequest {
    String entityName;
    EntityData winner;
    private List<EntityData> losers = new ArrayList<EntityData>();
    private ConnectorInfo connector;
    private EntitySchema entitySchema;
    private Map<String , Object> destParams = new HashMap<>();

    public MergeRequest(ConnectorInfo connector, EntitySchema entitySchema) {
        this.connector = connector;
        this.entitySchema = entitySchema;
        this.entityName = entitySchema.apiName;
    }

    public void addLoser(EntityData loser) {
        this.losers.add(loser);
    }
    
    public Optional<EntityData> findLoser(String id) {
        return losers.stream().filter(l -> l.getId().equals(id)).findFirst();
    }

}
