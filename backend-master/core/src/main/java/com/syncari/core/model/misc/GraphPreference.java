package com.syncari.core.model.misc;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class GraphPreference {
  private Map<String, UserNodePreference> nodes = new HashMap<>();
  private Map<String, UserEdgePreference> edges = new HashMap<>();
  private String instanceId;
}
