package com.syncari.core.model;

import java.util.List;
import java.util.Map;

import com.syncari.core.model.util.Type;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;


@Data
@Accessors(chain=true)
public abstract class AbstractActionConfig implements NodeConfiguration {

    protected String name;

    protected Type type;

    @Transient
    ActionDefinition actionDefinition;

    @Override
    public String getApiName() {
        return name;
    }

    @Override
    public List<OutputPort> getOutputPorts() {
        return List.of(OutputPort.any());
    }

    @Override
    public List<InputPort> getInputPorts() {
        return List.of(InputPort.any());
    }

    @Override
    public Map<String, Object> getConfigMap() {
        return Map.of("name",name);
    }


}
