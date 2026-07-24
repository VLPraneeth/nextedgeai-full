package com.syncari.karibu.rest.response;

import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.apache.commons.lang3.StringUtils;

@Data
@ToString(callSuper = true)
public class ValidListResponse<T> extends BaseApiResponse {

    @JsonInclude(Include.NON_NULL)
    private String cursorToken;

    @JsonInclude(Include.NON_NULL)
    private List<T> result;

    public void addCursor(String cursor){
        if(StringUtils.isNotEmpty(cursor)) {
            this.cursorToken = cursor;
        }
    }

    public ValidListResponse addResultList(List<T> toAdd) {
        if (toAdd != null) {
            if (result == null) {
                result = new ArrayList<>();
            }
            result.addAll(toAdd);
        }
        return this;
    }

}
