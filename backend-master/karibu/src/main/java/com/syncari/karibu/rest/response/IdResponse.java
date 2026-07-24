package com.syncari.karibu.rest.response;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class IdResponse{

    private String id;

    public IdResponse(String id){
        this.id = id;
    }
}
