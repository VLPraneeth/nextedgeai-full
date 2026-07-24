package com.syncari.core.quickstart.v2.dependency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.v2.QuickStartContext;

@Component
public class GroupNodeDependencyGenerator implements DependencyService {

	@Autowired
    SharableGraphTransformer sharableGraphTransformer;

    @Override
    public void extract(QuickStartContext context) {
        //No OP
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
    	return sharableGraphTransformer.toMappingNode(context.getCurrentNode(), context.getCurrentPipeline());
    }

}
