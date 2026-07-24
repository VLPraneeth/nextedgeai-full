package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class PipelineSettings implements Serializable {

    public static final String NODE_LOGGING_HELP_URL = "https://support.syncari.com/hc/en-us/articles/29412405721492-Troubleshooting-Pipeline";
    
    public PipelineSettings() {
    }

    private boolean continuousPipeline;
    private boolean nodeLoggingEnabled;
    private boolean simpleLoops;
    private boolean realtimePipeline;
    private boolean dataQuality;
    private String realtimeEndpointSuffix;
    private String realtimeEndpointBase;
    private String realtimeIpWhitelist;

    public PipelineSettings clone() {
        return new PipelineSettings(continuousPipeline, nodeLoggingEnabled, simpleLoops, realtimePipeline, dataQuality, realtimeEndpointSuffix, realtimeEndpointBase, realtimeIpWhitelist);
    }
}
