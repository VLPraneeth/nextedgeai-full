package com.syncari.core.model.insights;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DashboardVariableMapping {
  String apiName;
  List<String> mappedApiNames;
}
