package com.syncari.karibu.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
public class FieldRequest {

    // fieldId is required for update but invalid on a create request
    private String fieldId;
    private String displayName;
    // apiName is required for create but invalid on an update request
    private String apiName;
    private String datastoreName;
    private String description;
    private String dataType;
    private String referenceTo;
    private String referenceTargetField;
    private Integer length;
    private Boolean multiValueField;
    private Boolean required;
    private Boolean unique;
    private List<String> picklistValues;
    Set<String> tags = new HashSet<>();

    public FieldRequest() {
    }
}
