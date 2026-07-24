package com.syncari.api.rest.controllers.data.test;

import com.syncari.core.model.misc.PipelineTestStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

@Data
@Accessors(chain = true)
public class PipelineTestDTO {

    private String id;
    private String displayName;
    private String description;
    private Set<String> tags = new HashSet<>();
    private PipelineTestData testData;
    private String ownerFirstName;
    private String ownerLastName;
    private String ownerEmail;
    private PipelineTestStatus result;
}
