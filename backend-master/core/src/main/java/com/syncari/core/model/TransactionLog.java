package com.syncari.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.syncari.connector.Operation;
import com.syncari.core.model.misc.Destination;
import com.syncari.core.model.misc.Source;
import com.syncari.core.pipeline.NodeError;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TransactionLog extends UUIDAuditModel {
    private String syncariId;
    private String entityName;
    private String entityId;
    private Map<String, FieldChange> changes = new HashMap<>();
    private Map<String, Object> additionalInfo = new HashMap<>();
    private String batchId;
    private long occurredAt;
    private boolean isNew;
    private Operation operation;
    private List<Source> sources = new ArrayList<>();
    private List<Destination> destinations = new ArrayList<>();
    private List<NodeError> errors = new ArrayList<>();
    private String notes;
    private String sourceTransactionId;

    public TransactionLog() {
        this.occurredAt = System.currentTimeMillis();
    }

    /**
     * @deprecated
     * @param connectorId
     * @param externalId
     * @param lastModified
     * @return
     */
    public TransactionLog addSource(String connectorId, String externalId, long lastModified) {
        sources.add(new Source().setConnectorId(connectorId).setExternalId(externalId).setLastModified(lastModified));
        return this;
    }

    public TransactionLog addSource(String connectorId, String connectorName, String externalEntityDefinitionId, String externalId, long lastModified) {
        sources.add(new Source().setConnectorId(connectorId).setEntityDefinitionId(externalEntityDefinitionId).setExternalId(externalId).setLastModified(lastModified).setConnectorName(connectorName));
        return this;
    }

/*    public TransactionLog addSource(String connectorId, String connectorName, String externalId, long lastModified) {
        sources.add(new Source().setConnectorId(connectorId).setExternalId(externalId).setLastModified(lastModified).setConnectorName(connectorName));
        return this;
    }*/

    public boolean hasChangeFor(String attributeId){
        return changes.containsKey(attributeId);
    }
    
    public boolean hasChanges(){
        return !changes.isEmpty();
    }

    public boolean hasData(){
        return !changes.isEmpty() || !errors.isEmpty();
    }

    public boolean hasSameChange(FieldChange otherChange){
        return changes.containsKey(otherChange.getFieldId()) &&
         getChange(otherChange.getFieldId()).map(change-> change.hasChanges(otherChange)).orElse(false);
    }

    public TransactionLog addChange(FieldChange change) {
        changes.put(change.getFieldId(), change);
        return this;
    }

    public boolean isMerge(){
        return Operation.merge ==operation;
    }

    public MergeOperation getMergeOperation(){
        return (MergeOperation) additionalInfo.get("mergeDetails");
    }

    public Optional<FieldChange> getChange(String fieldId) {
        return Optional.ofNullable(changes.get(fieldId));
    }
    
    public MergeOperation getMergeSkipOperation(){
      return (MergeOperation) additionalInfo.get("mergeSkipDetails");
    }

}
