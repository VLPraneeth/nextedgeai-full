package com.syncari.restutils.data;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.utils.I18n;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class MappingNodeDTO implements Serializable {
    private String id;
    private String name;
    private String apiName;
    private String label;
    private String subLabel;
    private String iconPath;
    private String backgroundColor;
    private String groupId;
    private String originalId;
    private List<PortDTO> inputPorts;
    private List<PortDTO> outputPorts;

    private Map<String, Object> configuration=new HashMap<>();
    private MappingNodeType nodeType;

    private Map<String, Object> location=new HashMap<>();

    public <T> T getRequiredConfiguration(String configKey){
        return Optional.ofNullable((T)configuration.get(configKey)).orElseThrow(()->{
            String missingConfigMessage = String.format(I18n.i18n("missing_node_configuration"), configKey);
            if (nodeType == MappingNodeType.ATTRIBUTE_SOURCE || nodeType == MappingNodeType.ENTITY_SOURCE) {
                missingConfigMessage = String.format(I18n.i18n("missing_source_node_configuration"), name);
            } else if (nodeType == MappingNodeType.ATTRIBUTE_SINK || nodeType == MappingNodeType.ENTITY_SINK) {
                missingConfigMessage = String.format(I18n.i18n("missing_destination_node_configuration"), name);
            }

            return new SyncariValidationException(missingConfigMessage);
        });
    }
    public <T> Optional<T> getOptionalConfiguration(String configKey){
        return Optional.ofNullable((T)configuration.get(configKey));
    }

}


