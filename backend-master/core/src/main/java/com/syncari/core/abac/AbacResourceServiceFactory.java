package com.syncari.core.abac;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.model.insights.dataset.Dataset;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AbacResourceServiceFactory {

  @Autowired
  AbacEntityResourceService entityResourceService;
  @Autowired
  AbacDatasetResourceService datasetResourceService;
  @Autowired
  AbacEntityDataResourceService entityDataResourceService;
  @Autowired
  AbacGlobalResourceService globalResourceService;

  public AbacResourceService getResourceService(AbacContext context, Object data) {
    if (context.getResourceType() == ResourceType.GLOBAL) {
      return globalResourceService;
    } else if (data instanceof EntityDefinition) {
      return entityResourceService;
    } else if (data instanceof Dataset) {
      return datasetResourceService;
    } else if (data instanceof EntityData) {
      return entityDataResourceService;
    } else {
      throw new RuntimeException("Unknown data type " + data.getClass());
    }
  }
}
