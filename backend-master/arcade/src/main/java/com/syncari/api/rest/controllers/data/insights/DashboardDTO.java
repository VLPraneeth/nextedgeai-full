package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.draft.DraftStatus;
import lombok.Data;
import lombok.experimental.Accessors;
import org.joda.time.DateTime;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
@Accessors(chain = true)
public class DashboardDTO {

    String id;
    String name;
    String displayName;
    String description;
    DraftStatus draftStatus;
    List<DatacardDTO> dataCards = new ArrayList<>();
    Set<String> tags = new LinkedHashSet<>();
    DashboardDTO draft;
    String parentId;
    boolean seeded;
    String createdBy;
    String updatedBy;
    ZonedDateTime createdAt;
    ZonedDateTime updatedAt;

}
