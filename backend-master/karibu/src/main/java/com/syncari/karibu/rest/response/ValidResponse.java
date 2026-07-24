package com.syncari.karibu.rest.response;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class ValidResponse<T> extends BaseApiResponse {

    @JsonInclude(Include.NON_NULL)
    private Object result;

    public ValidResponse addResult(T toAdd) {
        result = toAdd;
        return this;
    }
}
