package com.syncari.core.abac;

import com.syncari.core.model.abac.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AbacResource {
  ResourceType type;
  String id;
  String displayName;
}
