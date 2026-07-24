package com.syncari.core.quickstart.v2;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class PipelineInstallConfig {

    String pipelineId;
    boolean newPipeline; // create a new pipeline for this incoming EP/FP
    Optional<String> syncariEntityId; // id of the destination pipeline user wants to merge this in

    InstallConfig entityPipelineInstallConfig;
    List<InstallConfig> fieldPipelineInstallConfigs = new ArrayList<>();
    Map<String, PipelineInstallOption> pipelineConfig = new HashMap<>();
}
