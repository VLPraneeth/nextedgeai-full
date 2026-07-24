package com.syncari.core.pipeline;

import com.syncari.core.service.FeatureService;
import com.syncari.core.utils.RedisUtils;
import org.springframework.context.ApplicationEvent;

import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.utils.CustomerMongoUtils;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PipelinePublishedEvent extends ApplicationEvent {

    private MappingGraph graph;
    private MappingNode node;
    private CustomerMongoUtils mongoUtils;
    private RedisUtils redisUtils;
    private FeatureService featureService;
	public PipelinePublishedEvent(Object source) {
		super(source);
	}
    
}