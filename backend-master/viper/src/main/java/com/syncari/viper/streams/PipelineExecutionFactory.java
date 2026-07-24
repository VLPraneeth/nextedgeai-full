package com.syncari.viper.streams;

import com.syncari.viper.streams.stages.ExecuteEntityPipeline;
import com.syncari.viper.streams.stages.ExecuteFieldPipeline;
import com.syncari.viper.streams.stages.SaveToSink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PipelineExecutionFactory implements StreamExecutionFactory {

    @Autowired
    ExecuteEntityPipeline executeEntityPipeline;

    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;

    @Autowired
    SaveToSink saveToSink;

    @Override
    public PipelineStages getPipelineStages() {
        return new PipelineStages(executeEntityPipeline, executeFieldPipeline, saveToSink);
    }
}
