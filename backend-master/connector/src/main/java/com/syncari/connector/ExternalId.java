package com.syncari.connector;

import com.syncari.utils.KeyValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;

@Data
@AllArgsConstructor
@Getter
public class ExternalId {
    private String connectorId;
    private String entityDefinitionId;
    private String recordId;

    public static ExternalId fromMap(Map<String, Object> values){
        return new ExternalId(
                Objects.toString(values.get("connectorId"),null),
                Objects.toString(values.get("entityDefinitionId"),null),
                Objects.toString(values.get("recordId"),null));
    }
    public Map<String, Object> toMap(){
        return new KeyValue()
                .set("connectorId",connectorId)
                .set("entityDefinitionId",entityDefinitionId)
                .set("recordId",recordId);
    }
}
