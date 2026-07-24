package com.syncari.viper.streams;

import com.syncari.viper.streams.stages.ExecuteEntityPipeline;
import com.syncari.viper.streams.stages.ExecuteFieldPipeline;
import com.syncari.viper.streams.stages.SaveToSink;

public interface StreamExecutionFactory {

    PipelineStages getPipelineStages();
}
