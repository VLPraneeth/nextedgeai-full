package com.syncari.core.service;

import com.syncari.core.datatype.StringType;
import com.syncari.core.model.MappingNode;
import com.syncari.core.pipeline.NodeInfoContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultNodeInfoService implements NodeInfoService {

    @Override
    public String inferNodeOutputDatatype(NodeInfoContext context){
        MappingNode node = context.getCurrentNode();
        return node.getConfiguration().getOutputPorts().stream().findFirst().map(o -> o.getDatatype()).orElse(StringType.VALUE).getName();
    }
}
