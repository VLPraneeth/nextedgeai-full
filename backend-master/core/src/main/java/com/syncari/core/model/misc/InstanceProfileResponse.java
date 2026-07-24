package com.syncari.core.model.misc;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InstanceProfileResponse {
    String syncariId;
    int numberOfRunningPipeline;
    int numberOfPausedPipeline;
    int numberOfErrorPipeline;
    int totalPipelines;
    long totalRecords;
    List<String> synapses = new ArrayList<>();
    List<String> errorSynapses = new ArrayList<>();
    long transactionsLastWeek;
}
