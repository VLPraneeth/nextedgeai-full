package com.syncari.karibu.rest.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class QuickStartRunRequest {
    @NotEmpty(message = "installStrategy is empty. Please verify these request parameters")
    private String installStrategy;
    @NotEmpty(message = "autoArrange is empty. Please verify these request parameters")
    private String autoArrange;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Map<String, Object>> synapses;
}
