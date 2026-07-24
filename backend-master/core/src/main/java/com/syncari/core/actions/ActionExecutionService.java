package com.syncari.core.actions;

import com.syncari.core.model.ActionResult;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.pipeline.GraphContext;

public interface ActionExecutionService {
    ActionResult execute(GenericActionConfig actionConfig, GraphContext context);
}
