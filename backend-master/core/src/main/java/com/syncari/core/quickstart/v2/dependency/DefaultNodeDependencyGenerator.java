package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.MappingNode;
import com.syncari.core.quickstart.v2.QuickStartContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultNodeDependencyGenerator implements DependencyService {
    @Override
    public void extract(QuickStartContext context) {
        // No op
    }

    @Override
    public MappingNode resolve(QuickStartContext context){
        return null;
    }
}
