package com.syncari.karibu.rest.response;

import lombok.Data;

import java.util.Date;

@Data
public class TrialInstanceResponse extends InstanceResponse{

    private Date createdAt;
    private Date endDate;
}
