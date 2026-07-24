package com.syncari.core.actions;

import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Tag;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class CustomActionDefinition extends ActionDefinition {
    private String apiName;
    private List<Tag> tags;
    private String author;
    private ZonedDateTime lastPublishedAt;
    private Date lastInstallTime;
    private boolean sharedWithMe = false;
    private String globalSharedItemId;

    @Override
    public ActionDefinition makeCopy() {
        return new CustomActionDefinition()
                .setTags(this.tags)
                .setAuthor(this.author)
                .setApiName(this.getApiName())
                .setLastPublishedAt(this.lastPublishedAt)
                .setLastInstallTime(this.lastInstallTime)
                .setSharedWithMe(this.sharedWithMe)
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
                .setConfiguration(this.getConfiguration().stream().map(c-> SerializationUtils.clone(c)).collect(Collectors.toList()))
                .setProperties(SerializationUtils.clone(this.getProperties()));
    }

    @Override
    public void copyValuesFrom(ActionDefinition model) {
        var customAction = (CustomActionDefinition) model;

        this.setTags(customAction.tags)
            .setAuthor(customAction.author)
            .setApiName(customAction.getApiName())
            .setLastPublishedAt(customAction.lastPublishedAt)
            .setLastInstallTime(customAction.lastInstallTime)
            .setSharedWithMe(customAction.sharedWithMe)
            .setDisplayName(customAction.getDisplayName())
            .setAvailableForDataTypes(customAction.getAvailableForDataTypes())
            .setDynamicConfig(customAction.isDynamicConfig())
            .setHidden(customAction.isHidden())
            .setEngineType(customAction.getEngineType())
            .setPositionalParams(customAction.getPositionalParams())
            .setScope(customAction.getScope())
            .setName(customAction.getName())
            .setOutputType(customAction.getOutputType())
            .setHelpPath(customAction.getHelpPath())
            .setHelpSummary(customAction.getHelpSummary())
            .setIconPath(customAction.getIconPath())
            .setDescription(customAction.getDescription())
            .setType(customAction.getType())
            .setConfiguration(customAction.getConfiguration().stream().map(c-> SerializationUtils.clone(c)).collect(Collectors.toList()))
            .setProperties(SerializationUtils.clone(customAction.getProperties()));
    }

    public boolean isGlobalAction(){
        return !StringUtils.isBlank(globalSharedItemId);
    }
}
