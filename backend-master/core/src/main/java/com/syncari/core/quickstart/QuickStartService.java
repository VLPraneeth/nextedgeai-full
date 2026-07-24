package com.syncari.core.quickstart;

import com.syncari.core.model.QuickStartRun;
import com.syncari.utils.KeyValue;

import java.util.List;
import java.util.Map;

public interface QuickStartService<T extends QuickStartConfig> {

    /**
     * Executes a specified QuickStart
     * @param quickStartRun
     */
    public void execute(QuickStartRun quickStartRun);

    /**
     * Validates a specified QuickStart
     * @param config
     */
    public void validate(T config);

    /**
     *
     * @return Seeded metadata for a given QuickStart
     */
    public QuickStartMetadata getMetadata();

    /**
     * Return the data on demand for metadata driven configuration
     * @param configName
     * @param input
     * @return
     */
    public List<KeyValue> getData(String configName, String configType, Map<String, Object> input);

    /**
     * Return the updated inputs for a given step of a quickstart
     * @param stepNumber
     * @param input
     * @return
     */
    public QuickStartMetadata getDynamicStepsUpdate(Integer stepNumber, Map<String, Object> input);

    /**
     * Return the updated inputs for a given step of a quickstart
     * @param config
     * @return runName of quick start
     */
    public String getRunDetail(T config);
}
