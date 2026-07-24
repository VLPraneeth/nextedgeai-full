package com.syncari.karibu.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExtractLoadRequest {

    @NotBlank(message = "Source entity ID is required")
    private String sourceEntityId;

    @NotBlank(message = "Destination connector ID is required")
    private String destinationConnectorId;

    // If absent, a new destination entity will be created
    private String destinationEntityId;

    // Name for the new destination entity (defaults to source entity name if not provided)
    private String destinationEntityName;

    // Syncari entity name - used to find or create Syncari entity (defaults to source entity name if not provided)
    private String syncariEntityName;

    // Pipeline name - used to find or create pipeline (defaults to source entity name if not provided)
    private String pipelineName;

    // If true, publishes the pipeline; if false, keeps it as draft
    private Boolean publish = false;

    // If true, starts resync immediately after creation/update
    private Boolean startResync = false;

    // If true, enables auto schema sync for future field additions
    private Boolean autoSchemaSync = false;
}
