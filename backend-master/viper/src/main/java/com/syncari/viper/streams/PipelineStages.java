package com.syncari.viper.streams;

import com.syncari.viper.streams.stages.ExecuteEntityPipeline;
import com.syncari.viper.streams.stages.ExecuteFieldPipeline;
import com.syncari.viper.streams.stages.SaveToSink;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PipelineStages {
    final ExecuteEntityPipeline executeEntityPipeline;
    final ExecuteFieldPipeline executeFieldPipeline;
    final SaveToSink saveToSink;
}
