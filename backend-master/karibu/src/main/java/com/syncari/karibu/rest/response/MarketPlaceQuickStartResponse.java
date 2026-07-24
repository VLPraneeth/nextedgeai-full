package com.syncari.karibu.rest.response;

import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.quickstart.v2.QSAuthoringSeed;
import com.syncari.core.quickstart.v2.QuickStart;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString(callSuper=true)
public class MarketPlaceQuickStartResponse extends BaseKaribuResponse {

    private String displayName;
    private String description;
    private List<String> tags;
    private String postInstallationInstruction;
    private String status;
    private String iconPath;
    private List<String> requiredSynapses;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        QuickStart quickStart = (QuickStart) object;
        MarketPlaceQuickStartResponse response = new MarketPlaceQuickStartResponse();

        response.setCreatedBy(quickStart.getCreatedBy());
        response.setCreatedAt(quickStart.getCreatedAt());
        response.setUpdatedBy(quickStart.getUpdatedBy());
        response.setUpdatedAt(quickStart.getUpdatedAt());
        response.setId(quickStart.getId());
        response.setDisplayName(quickStart.getDisplayName());
        response.setDescription(quickStart.getDescription());
        List<String> stringTags = new ArrayList<>();
        if (quickStart.getTags() != null) {
            quickStart.getTags().forEach(tag -> {
                stringTags.add(tag.getName());
            });
        }
        response.setTags(stringTags);
        response.setPostInstallationInstruction(quickStart.getPostInstallationInstruction());
        response.setStatus(quickStart.getDraftStatus().name());
        response.setIconPath(String.format(QSAuthoringSeed.ICON_PATH_URL, quickStart.getId()));
        response.setRequiredSynapses(quickStart.getRequiredSynapses());

        return response;
    }
}
