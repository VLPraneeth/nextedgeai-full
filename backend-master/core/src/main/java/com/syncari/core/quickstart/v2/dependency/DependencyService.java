package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.MappingNode;
import com.syncari.core.quickstart.v2.QuickStartContext;

public interface DependencyService {

    public void extract(QuickStartContext context);

    public MappingNode resolve(QuickStartContext context);
    
    public default boolean postProcess(QuickStartContext context) {
      return false;
    }
}
