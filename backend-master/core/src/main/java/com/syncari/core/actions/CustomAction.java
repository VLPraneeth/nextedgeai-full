package com.syncari.core.actions;

import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.ActionResult;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.pipeline.GraphContext;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface CustomAction extends Action {
    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context);

    // action design time validation
    public void validate(ActionDefinition actionDefinition);

    // design time test
    public ActionTestResult test(ActionDefinition definition, Map<String, Object> context);

    public boolean resolve(ActionDefinition actionDefinition);
}
