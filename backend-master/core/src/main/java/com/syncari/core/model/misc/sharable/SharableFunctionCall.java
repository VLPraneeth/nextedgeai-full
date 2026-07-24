package com.syncari.core.model.misc.sharable;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.ParameterValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class SharableFunctionCall implements Serializable {

    private String notes;
    private FunctionDefinition functionDefinition;
    private List<ParameterValue> params;
    private List<String> paramNames;
    private Map<String, Object> config = new HashMap<>();

    public Object getConfig(String key){
        return config.get(key);
    }
    public <T> Optional<T> getConfig(String key, Datatype<T> type){
        return  Optional.ofNullable(type.convert(config.get(key)));
    }

    public void validate(String graphName, String nodeName) {

    }
    
    public SharableFunctionCall copy() {
      SharableFunctionCall copy = new SharableFunctionCall();
      copy.notes = this.notes;
      copy.functionDefinition = this.functionDefinition;
      copy.params = this.params != null ? new ArrayList<>(this.params) : null;
      copy.paramNames = this.paramNames != null ? new ArrayList<>(this.paramNames) : null;
      copy.config = this.config != null ? new HashMap<>(this.config) : null;
      return copy;
   }
}
