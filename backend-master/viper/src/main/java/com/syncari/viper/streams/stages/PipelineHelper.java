package com.syncari.viper.streams.stages;

import org.apache.commons.lang3.StringUtils;

public final class PipelineHelper {

    public static final String INCOMING_CHANGE_FIELD="incoming_change";

    public static String toApiName(String value) {
        return StringUtils.isBlank(value) ? value : value.replaceAll("[^a-zA-Z0-9_]+", "_");
    }

}
