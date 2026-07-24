package com.syncari.api.rest.controllers.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class FieldMappingResponse {

    boolean success;
    List<FieldMappingDTO> result = new ArrayList<>();
    List<FieldMappingError> error = new ArrayList<>();
    boolean newEntityDraft;
    boolean entityDraftUpdated;

    public void addError(String id, String errorMsg){
        error.add(new FieldMappingError(id, errorMsg));
    }

    public void addResult(FieldMappingDTO res){
        result.add(res);
    }
}

@Data
@Accessors(chain = true)
@AllArgsConstructor
class FieldMappingError{

    String id;
    String errorMessage;
}
