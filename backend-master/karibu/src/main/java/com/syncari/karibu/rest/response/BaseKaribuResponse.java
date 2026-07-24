package com.syncari.karibu.rest.response;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

@ToString
@Data
public abstract class BaseKaribuResponse implements KaribuResponse {

    private String id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String name;
    private String createdBy;
    private Date createdAt;
    private String updatedBy;
    private Date updatedAt;

}

