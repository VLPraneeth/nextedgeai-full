package com.syncari.core.sync;

import com.syncari.core.DataSourceRequest;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.PipelineTest;
import com.syncari.core.pipeline.GraphContext;

public interface DataSource {

    CurrentBatch fetch(DataSourceRequest request);

    CurrentBatch fetchSource(DataSourceRequest request);

    CurrentBatch fetchSourceById(DataSourceRequest request);

    CurrentBatch fetchSourceFromTestInput(EntityDefinition syncariEntity, PipelineTest test);

    void closeSource(GraphContext graphContext);
    
}
