package com.syncari.core.model;

import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GroupDefinition extends NodeDefinition<GroupDefinition> {

    @Override
    public GroupDefinition makeCopy() {
    	return new GroupDefinition()
                .setDisplayName(this.getDisplayName())
                .setAvailableForDataTypes(this.getAvailableForDataTypes())
                .setDynamicConfig(this.isDynamicConfig())
                .setHidden(this.isHidden())
                .setEngineType(this.getEngineType())
                .setPositionalParams(this.getPositionalParams())
                .setScope(this.getScope())
                .setName(this.getName())
                .setOutputType(this.getOutputType())
                .setHelpPath(this.getHelpPath())
                .setHelpSummary(this.getHelpSummary())
                .setIconPath(this.getIconPath())
                .setDescription(this.getDescription())
                .setType(this.getType())
                .setConfiguration(this.getConfiguration().stream().map(c-> SerializationUtils.clone(c)).collect(Collectors.toList()));
    }

    @Override
    public void copyValuesFrom(GroupDefinition model) {
        this.setDisplayName(model.getDisplayName())
            .setAvailableForDataTypes(model.getAvailableForDataTypes())
            .setDynamicConfig(model.isDynamicConfig())
            .setHidden(model.isHidden())
            .setEngineType(model.getEngineType())
            .setPositionalParams(model.getPositionalParams())
            .setScope(model.getScope())
            .setName(model.getName())
            .setOutputType(model.getOutputType())
            .setHelpPath(model.getHelpPath())
            .setHelpSummary(model.getHelpSummary())
            .setIconPath(model.getIconPath())
            .setDescription(model.getDescription())
            .setType(model.getType())
            .setConfiguration(model.getConfiguration().stream().map(c-> SerializationUtils.clone(c)).collect(Collectors.toList()));
    }
}
