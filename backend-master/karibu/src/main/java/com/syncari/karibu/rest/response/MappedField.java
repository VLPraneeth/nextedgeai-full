package com.syncari.karibu.rest.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappedField {
    private String sourceFieldId;
    private String sourceFieldName;
    private String sourceFieldApiName;
    private String syncariFieldId;
    private String syncariFieldName;
    private String syncariFieldApiName;
    private String destinationFieldId;
    private String destinationFieldName;
    private String destinationFieldApiName;
    private boolean newlyCreated;
}
