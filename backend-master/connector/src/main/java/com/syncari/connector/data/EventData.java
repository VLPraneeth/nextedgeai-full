package com.syncari.connector.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventData {
    private EntityData data;
    private Operation operation;
    private String eventId;
    private String connectorId;

    public EventData() {
    }
}
