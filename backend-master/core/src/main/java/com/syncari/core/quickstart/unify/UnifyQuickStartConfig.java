package com.syncari.core.quickstart.unify;

import com.syncari.core.quickstart.AbstractQuickStartConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class UnifyQuickStartConfig extends AbstractQuickStartConfig {

    private String syncariEntityId;
    List<SynapseUnificationConfig> synapseUnificationConfigs = new ArrayList<>();

    @Override
    public void validate() {
        validateCondition(StringUtils.isBlank(syncariEntityId), "Syncari Entity cannot be empty");
        validateCondition(synapseUnificationConfigs.isEmpty(),  "Unify quickstart should have atleast one synapse selected");
    }

    @Override
    public Map<String, Object> getInputs() {
        return Map.of("syncariEntity", syncariEntityId,
                "synapseUnificationConfigs", synapseUnificationConfigs);
    }

    public UnifyQuickStartConfig addSynapseUnificationConfig(String synapseId, String entityId, List<String> attributeIds){
        synapseUnificationConfigs.add(new SynapseUnificationConfig(synapseId, entityId, attributeIds));
        return this;
    }
}

@Data
@AllArgsConstructor
class SynapseUnificationConfig{
    String synapseId;
    String entityId;
    List<String> attributeIds;
}
