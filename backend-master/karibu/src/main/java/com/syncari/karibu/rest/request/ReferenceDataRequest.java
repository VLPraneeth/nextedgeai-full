package com.syncari.karibu.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
public class ReferenceDataRequest {
    @NotEmpty(message = "Reference Data name is empty. Please verify these request parameters")
    private String name;
    @NotEmpty(message = "Reference Data headerColumns is empty. Please verify these request parameters")
    private List<String> headerColumns;
}
