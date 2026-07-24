package com.syncari.core.quickstart.v2;

public enum PipelineInstallOption {
    // Install nodes from QS after the source node and first function/action node - applies only to source side
    AFTER_SOURCE,
    // Install nodes from QS before core node - applies only to source side
    BEFORE_CORE,
    // Install nodes from QS after core node - applies only to sink side
    AFTER_CORE,
    // Install nodes from QS before the source node and previous function/action node - applies only to sink side
    BEFORE_SINK,
    // Copy the path from QS and place it in canvas without making any connections -  applies to both source/sink side
    COPY,
    // (Default) Merge entire path from QS. Source side - AFTER_SOURCE and sink side - BEFORE_SINK
    MERGE,
    // Replace an entire path - applies to both source/sink side and core node
    REPLACE,
    // Use an existing core node configuration in destination pipeline - applies only to core node
    USE_EXISTING,
    // NONE
    NONE
}
