package com.syncari.core.model;

import lombok.Data;

import java.util.Date;

@Data
public class InstanceState {

    Instance instance;
    private long trialDaysLeft;
    private long numberOfRecordsLeft;
    private long numberOfRefDataLeft;
    private long numberofSynapses;
    private long numberofPipelines;
    private boolean isTrialExpired;
    private boolean isPublishLimitExpired;
    private boolean isRecordLimitExpired;
    private boolean isRefDataLimitExpired;
    private Date expiryDate;
}
