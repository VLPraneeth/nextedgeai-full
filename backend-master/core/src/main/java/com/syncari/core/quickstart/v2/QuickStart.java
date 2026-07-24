package com.syncari.core.quickstart.v2;

import com.syncari.core.model.Tag;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.share.SharedItemObject;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Accessors(chain = true)

public class QuickStart extends DraftableModel<QuickStart> implements SharedItemObject {

    private String displayName;
    private String description;
    @Transient
    private List<Tag> tags;
    private String postInstallationInstruction;
    private String iconPath;
    private ZonedDateTime snapshotedAt;
    private ZonedDateTime lastPublishedAt;
    private List<QSConfig> configuration;
    // TODO: Convert this to connectorMetadata
    private List<String> requiredSynapses;
    private String authoringOrg;

    @Override
    public QuickStart makeCopy() {
        return new QuickStart().setDisplayName(displayName)
                .setDescription(description)
                .setTags(tags)
                .setIconPath(iconPath)
                .setPostInstallationInstruction(postInstallationInstruction)
                .setSnapshotedAt(snapshotedAt)
                .setConfiguration(configuration);
    }

    @Override
    public void copyValuesFrom(QuickStart model) {
        this.setDisplayName(model.getDisplayName())
            .setDescription(model.getDescription())
            .setTags(model.getTags())
            .setIconPath(model.getIconPath())
            .setPostInstallationInstruction(model.getPostInstallationInstruction())
            .setSnapshotedAt(model.getSnapshotedAt())
            .setConfiguration(model.getConfiguration())
            .setRequiredSynapses(model.getRequiredSynapses());
    }
}
