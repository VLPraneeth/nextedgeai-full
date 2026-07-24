package com.syncari.core.quickstart;

import java.util.Map;

public interface QuickStartConfig {

    /**
     * Validates the input of QuickStart
     */
    public void validate();

    /**
     *
     * @return all inputs for the quickstart
     */
    public Map getInputs();

    /**
     *
     * @return - apiName of the quickstart
     */
    public String getName();

    /**
     *
     * @return - apiName of the quickstart
     */
    public String getDisplayName();

}
