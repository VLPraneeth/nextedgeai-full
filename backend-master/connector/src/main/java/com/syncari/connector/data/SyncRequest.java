package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.utils.Storage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class SyncRequest {
    @Wither
    WatermarkInfo watermark;
    private EntityData masterRecord;
    //connector id to list of entity data map
    @Wither
	private Map<String, List<EntityData>> data = new HashMap<String, List<EntityData>>();
	@Wither
	private ConnectorInfo connector;
	@Wither
	private EntitySchema entitySchema;
	private List<BatchJob> batchJobs = List.of();
	@Wither
	private EntitySchema entitySchemaWithMappedFields;
	private boolean excludeDeleted = false;
	private boolean batchMode = false;
	private Map<String , Object> sourceParams = new HashMap<>();
	private Map<String , Object> destParams = new HashMap<>();
	private Map<String , Object> additionalParams = new HashMap<>();
    private Pipeline pipeline;

	private Storage storage;
	// This is mostly for integration tests. The page sizes are specific to synapses
	int pageSize = 0;
	public SyncRequest(){
	}
	public SyncRequest Builder(ConnectorInfo connector, EntitySchema entitySchema) {
		this.connector = connector;
		this.entitySchema = entitySchema;
		return this;
	}

    public SyncRequest Builder(ConnectorInfo connector, EntitySchema entitySchema, Pipeline pipeline) {
        this.connector = connector;
        this.entitySchema = entitySchema;
        this.pipeline = pipeline;
        return this;
    }

	public SyncRequest setWatermark(WatermarkInfo watermark) {
		this.watermark = watermark;
		return this;
	}
	
	public String getEntityName() {
        return entitySchema.getApiName();
    }

    public SyncRequest addData(String connectorId, EntityData entityData) {
        List<EntityData> entities = data.getOrDefault(connectorId, new ArrayList<>());
        entities.add(entityData);
        data.put(connectorId, entities);
        return this;
    }

    public boolean hasPendingJobs() {
        return batchJobs.stream().anyMatch(b -> b.isPending() || b.isError());
    }

    public List<String> getIds() {
        List<EntityData> entityList = getData().get(getConnector().getId());
        if (entityList == null) return new ArrayList<>();
        return entityList.stream().map(e -> e.getId()).collect(Collectors.toList());
    }

    public SyncRequest addEntityData(EntityData data) {
        this.data.putIfAbsent(connector.getId(), new ArrayList<>());
        final List<EntityData> records = this.data.get(connector.getId());
        records.add(data);
        return this;
    }

    public String getIdsAsString() {
        return String.join(", ", getIds().stream().map(i -> String.format("'%s'", i)).collect(Collectors.toList()));
    }

    public List<String> getSyncariIds() {
        List<EntityData> entityList = getData().get(getConnector().getId());
        if (entityList == null) return new ArrayList<>();
        return entityList.stream().map(e -> e.getSyncariEntityId()).collect(Collectors.toList());
    }

	public SyncRequest copy() {
		return new SyncRequest().withWatermark(watermark.copy()).withConnector(connector).withData(data).withEntitySchema(entitySchema)
			.withEntitySchemaWithMappedFields(entitySchemaWithMappedFields);
	}

	public void clearData() {
		data = new HashMap<String, List<EntityData>>();
	}

    public Object getSourceParam(String key) {
        return sourceParams.get(key);
    }

    public <T> T geTypedSourceParam(String key) {
        return (T) sourceParams.get(key);
    }

    public Object getDestParam(String key) {
        return destParams.get(key);
    }

    public <T> T getTypedDestParam(String key) {
        return (T) destParams.get(key);
    }
}
