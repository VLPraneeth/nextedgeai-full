package com.syncari.core.quickstart.v2;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class InstallConfig {

    String id;

    // For pipeline install, the map will hold the install option for each source, sink and core
    Map<String, PipelineInstallOption> config = new HashMap<>();
    boolean autoArrange = false;

    public InstallConfig getDefaultPipelineConfig(String pipelineId){
        this.id = pipelineId;
        this.config.put("source", PipelineInstallOption.AFTER_SOURCE);
        this.config.put("sink", PipelineInstallOption.BEFORE_SINK);
        this.config.put("core", PipelineInstallOption.REPLACE);

        return this;
    }
}
