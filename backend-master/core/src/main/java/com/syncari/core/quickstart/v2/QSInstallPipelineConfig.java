package com.syncari.core.quickstart.v2;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class QSInstallPipelineConfig extends QSInstallConfig {

    List<PipelineInstallConfig> pipelineConfigs = new ArrayList<>();
    PipelineInstallOption defaultInstallStrategy = PipelineInstallOption.REPLACE;
    boolean autoArrange = false;
}
