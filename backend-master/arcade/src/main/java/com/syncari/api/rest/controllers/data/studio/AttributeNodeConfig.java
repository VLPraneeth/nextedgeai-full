package com.syncari.api.rest.controllers.data.studio;

import static com.syncari.utils.I18n.i18n;
import java.util.*;
import java.util.stream.Collectors;


import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.connector.Constants;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSinkNodeConfig;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.restutils.data.PortDTO;
import com.syncari.utils.KeyValue;

public class AttributeNodeConfig {

    List<KeyValue> attributeConfigs = new ArrayList<>();
  
    EntityDefinition entityDef;
  
    boolean isSource;
  
    Connector connector;
  
    AttributeDefinition attribute;

    public AttributeNodeConfig(EntityDefinition entityDef, AttributeDefinition attribute, boolean isSource, Connector connector) {
        this.entityDef = entityDef;
        this.isSource = isSource;
        this.connector = connector;
        this.attribute = attribute;
    }

    public List<KeyValue> getNodeConfiguration() {
        var sortedAttributes = new ArrayList<>(entityDef. getActiveAttributes());
        Collections.sort(sortedAttributes, Comparator.comparing(AttributeDefinition::getDisplayName));

        var nodeTypeConfig = new KeyValue("datatype", "string")
            .set("name", "nodeType")
            .set("value", isSource ? MappingNodeType.ATTRIBUTE_SOURCE.name() : MappingNodeType.ATTRIBUTE_SINK.name())
            .set("implicit", true)
            .set("mapping", List.of(new KeyValue("graphKey", "nodeType")));
        var defaultNodeSubLabelConfig = new KeyValue("datatype", "string")
            .set("name", "defaultSubLabel")
            .set("value",  connector.getName()+" "+entityDef.getApiName())
            .set("implicit", true)
            .set("mapping", List.of(new KeyValue("graphKey", "subLabel")));
        var nodeLabelConfig = new KeyValue("datatype", "string")
            .set("name", "label")
            .set("value", (isSource ? "Sync from " : "Sync to ") + "{attribute}")
            .set("implicit", true)
            .set("mapping", List.of(new KeyValue("graphKey", "label")));
        var attributeKeyMapping = List.of(
            new KeyValue("graphKey", "configuration.attributeDefinition").set("configKey", "value"),
            new KeyValue("graphKey", "inputPorts").set("configKey", "inputPorts"),
            new KeyValue("graphKey", "outputPorts").set("configKey", "outputPorts"),
            new KeyValue("graphKey", "label").set("configKey", "nodeLabel"),
            new KeyValue("graphKey", "subLabel").set("configKey", "subLabel"));
        var attributeConfig = new KeyValue("datatype", "picklist")
            .set("name", "attribute")
            .set("label", "Field")
            .set("mapping", attributeKeyMapping)
            .set("implicit", false)
            .set("values", sortedAttributes.stream().map(a -> new KeyValue()
                .set("value", a.getId())
                .set("label", String.format("%s (%s)", a.getDisplayName(), a.getApiName()))
                .set("nodeLabel", (isSource ? "Sync from " : "Sync to ") + a.getDisplayName())
                .set("subLabel", connector.getName()+" "+entityDef.getApiName())
                .set("inputPorts", isSource ? Collections.emptyList() : sinkInputPorts(a))
                .set("outputPorts", isSource ? sourceOutputPorts(a) : Collections.emptyList())
            ).collect(Collectors.toList()));

        var entityConfig = new KeyValue().set("name", "entityName")
            .set("datatype", "string")
            .set("label", "Entity")
            .set("defaultValue", entityDef.getDisplayName())
            .set("uneditable", true)
            .set("implicit", false);

        var entityDefinitionIdConfig = new KeyValue("datatype", "string")
            .set("name", "entityDefinitionId")
            .set("value", entityDef.getId())
            .set("implicit", true)
            .set("mapping", List.of(new KeyValue("graphKey", "configuration.entityDefinitionId")));

        List<KeyValue> attributeConfigs = new ArrayList<>(List.of(nodeLabelConfig, defaultNodeSubLabelConfig, nodeTypeConfig, entityConfig, attributeConfig, entityDefinitionIdConfig));

        attributeConfigs.add(new KeyValue().set("name", "datatype")
            .set("datatype", "string")
            .set("label", "Data Type")
            .set("name", "dataType")
            .set("defaultValue", attribute != null ? attribute.getDataType().getName() : null)
            .set("uneditable", true)
            .set("implicit", false));

        attributeConfigs.add(new KeyValue().set("name", "isMultiValued")
            .set("datatype", "boolean")
            .set("label", "Multi Value")
            .set("name", "multiValues")
            .set("defaultValue", attribute != null ? attribute.isMultiValueField() : null)
            .set("uneditable", true)
            .set("implicit", false));

        attributeConfigs.add(new KeyValue().set("name", "isRequired")
            .set("datatype", "boolean")
            .set("label", "Required")
            .set("name", "required")
            .set("defaultValue", attribute != null ? !attribute.isNillable() : null)
            .set("uneditable", true)
            .set("implicit", false));

        if(!isSource){
            var defaultValueConfig = new KeyValue("datatype", "string")
            .set("name", "defaultValue")
            .set("label", "Default Value")
            .set("mapping", new KeyValue("graphKey", "configuration.defaultValue").set("configKey", "defaultValue"))
            .set("implicit", false);
            attributeConfigs.add(defaultValueConfig);

            var alwaysUseDefaultValueOnEmpty = new KeyValue("datatype", "boolean")
                .set("name", "alwaysUseDefaultOnEmpty")
                .set("label", i18n("default_value_label"))
                .set("mapping", new KeyValue("graphKey", "configuration.alwaysUseDefaultOnEmpty").set("configKey", "alwaysUseDefaultOnEmpty"))
                .set("implicit", false)
                .set("hideTokenPicker", true);
            
            var rejectEmpty = new KeyValue("datatype", "picklist")
                .set("name", "rejectEmpty")
                .set("label", i18n("reject_empty_value"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration.rejectEmpty").set("configKey", "value")))
                .set("values",
                    List.of(
                        new KeyValue().set("value", Constants.REJECT_EMPTY_ENUM.NEVER.name()).set("label",i18n("rm_never")),
                        new KeyValue().set("value", Constants.REJECT_EMPTY_ENUM.ALWAYS.name()).set("label",i18n("rm_always")),
                        new KeyValue().set("value", Constants.REJECT_EMPTY_ENUM.ON_CREATE.name()).set("label",i18n("rm_oncreate")),
                        new KeyValue().set("value", Constants.REJECT_EMPTY_ENUM.ON_UPDATE.name()).set("label",i18n("rm_onupdate"))
                    ))
                .set("implicit", false);
            attributeConfigs.add(rejectEmpty);
            attributeConfigs.add(alwaysUseDefaultValueOnEmpty);
        }
        return attributeConfigs;
    }

    public KeyValue getNode() {
        return new KeyValue()
            .set("name", entityDef.getApiName())
            .set("label", entityDef.getDisplayName())
            .set("iconPath", ConnectorMetadataDTO.getIconURIForDTO(connector.getMetadata()))
            .set("type", isSource ? "source" : "sink")
            .set("connectorId", entityDef.getConnectorId())
            .set("connectorName", connector.getName())
            .set("isCoreNode", false)
            .set("dynamicConfig", true)
            // We're ignoring graph validation error when getting the dynamic config for source and sink nodes
            // since the node itself is self suffient to build the configuration
            .set("dynamicConfigParams", KeyValue.of("ignoreValidationError", true))
            .set("entityDefinitionId", entityDef.getId())
            .set("id", entityDef.getId()+"_"+(isSource ? "source" : "sink"))
            .set("configuration", getNodeConfiguration());
    }

    private List<PortDTO> sourceOutputPorts(AttributeDefinition attributeDefinition) {
        return new AttributeSourceNodeConfig().setAttributeDefinition(attributeDefinition).getOutputPorts().stream()
            .map(p -> PortDTO.fromOutputPort(p)).collect(Collectors.toList());
    }

    private List<PortDTO> sinkInputPorts(AttributeDefinition attributeDefinition) {
        return new AttributeSinkNodeConfig().setAttributeDefinition(attributeDefinition).getInputPorts().stream()
            .map(p -> PortDTO.fromInputPort(p)).collect(Collectors.toList());
    }
}
