package com.syncari.core.quickstart.dedupe;

import com.syncari.core.quickstart.AbstractQuickStartConfig;
import com.syncari.core.quickstart.QuickStartConstants;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class DedupeQuickStartConfig extends AbstractQuickStartConfig {

    Map<String, Object> config;

    @Override
    public void validate() {
        validateCondition(StringUtils.isBlank(config.get("synapseId").toString()), "Synapse cannot be empty");
        validateCondition(StringUtils.isBlank(config.get("synapseEntityId").toString()), "Synapse entity cannot be empty");
        validateCondition(StringUtils.isBlank(config.get("winnerSelection").toString()), "Winner Selection cannot be empty");
        validateCondition(StringUtils.isBlank(config.get("mergePolicy").toString()), "Merge policy cannot be empty");
        validateCondition(StringUtils.isBlank(config.get("overridePolicy").toString()), "Override policy cannot be empty");
    }

    @Override
    public Map getInputs() {
        return null;
    }

    @Override
    public String getName() {
        return QuickStartConstants.DEDUPE;
    }
}
