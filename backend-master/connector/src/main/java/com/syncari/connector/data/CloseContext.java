package com.syncari.connector.data;

import com.syncari.connector.ConnectorInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CloseContext {
    ConnectorInfo connectorInfo;
    WatermarkInfo watermarkInfo;
    Pipeline pipeline;
    String entityName;
}
