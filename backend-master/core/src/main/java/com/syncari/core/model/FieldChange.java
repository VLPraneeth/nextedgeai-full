package com.syncari.core.model;

import com.syncari.core.model.misc.ExternalValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class FieldChange {
    private String fieldId;
    //Key is external Attribute Id & Value is external value
    private Map<String, ExternalValue> incomingExternalValues = new HashMap<>();
    // authoritative value
    private ExternalValue authoritativeSource;
    private Map<String, ExternalValue> outgoingExternalValues = new HashMap<>();
    private String apiName;
    private String displayName;
    private String dataType;
    private Object oldValue;
    private Object newValue;
    @EqualsAndHashCode.Exclude
    private long timestamp;
    @EqualsAndHashCode.Exclude
    private String srcId;

    public FieldChange addIncomingExternalValue(String externalAttributeId, ExternalValue value) {
        synchronized (this) {
            incomingExternalValues.put(externalAttributeId, value);
        }
        return this;
    }

    public FieldChange addOutgoingExternalValue(String externalAttributeId, ExternalValue value) {
        synchronized (this) {
            outgoingExternalValues.put(externalAttributeId, value);
        }
        return this;
    }

    @Deprecated(forRemoval = true)
    public boolean hasChanges(FieldChange other){
        return Objects.equals(incomingExternalValues,other.incomingExternalValues) && Objects.equals(newValue , other.newValue)
                && Objects.equals(apiName, other.apiName);
    }
}
