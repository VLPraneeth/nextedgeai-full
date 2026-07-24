package com.syncari.core.abac;

import java.util.Map;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;

@Data
@Wither
@AllArgsConstructor
@NoArgsConstructor
public class AbacContext {
  public static String METHOD_NAME="methodName";
  private ResourceType resourceType;
  private Map<String, Object> userAttributes;
  private String resource;
  private Map<String,Map<String, Object>> resourceAttributes;
  private Permission action;
  private boolean throwException = false;
  private String throwExceptionMessage = "";
}
