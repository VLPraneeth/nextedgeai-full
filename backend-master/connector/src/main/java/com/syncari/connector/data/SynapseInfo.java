package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SynapseInfo {
    String name;
    String category;
    String disabledMessage;
    ConnectorType type = ConnectorType.Synapse;
    List<Capability> capabilities = new ArrayList<>();
    UIMetadata metadata = new UIMetadata();
    List<AuthMetadata> supportedAuthTypes = new ArrayList<>();
    List<AuthField> configuredFields = new ArrayList<>();
    Map<String, String> oauthInfo = new HashMap<>();
    Integer apiMaxCrudSize;
}
