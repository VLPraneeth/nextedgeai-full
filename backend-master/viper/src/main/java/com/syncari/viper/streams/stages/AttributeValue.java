package com.syncari.viper.streams.stages;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.MappingNode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AttributeValue {
    final Object value;
    final AttributeDefinition attribute;
    final String connectorId;
    final MappingNode node;
}
