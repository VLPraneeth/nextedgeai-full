package com.syncari.core.model.misc.sharable;

import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import lombok.Data;

@Data
public class SharableNode {

    private String id;
    private Scope scope;
    private String name;
    private String apiName;
    private SharableNodeConfiguration configuration;
    private String mappingGraphId;
    private String groupId;

    public <T extends SharableNodeConfiguration> T getTypedConfiguration(){
        return (T)configuration;
    }

    public MappingNodeType getType() {
        return configuration.getNodeType();
    }
    
    public SharableNode copy() {
      SharableNode copy = new SharableNode();
      copy.id = this.id;
      copy.scope = this.scope;
      copy.name = this.name;
      copy.apiName = this.apiName;
      copy.configuration = this.configuration != null ? this.configuration.copy() : null;
      copy.mappingGraphId = this.mappingGraphId;
      copy.groupId = this.groupId;
      return copy;
  }
}
