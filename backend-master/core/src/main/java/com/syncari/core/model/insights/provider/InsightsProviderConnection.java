package com.syncari.core.model.insights.provider;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class InsightsProviderConnection {
    private String connection_identifier;
    private String name;
    private String data_warehouse_type="POSTGRES";
    private Map<String, Object> data_warehouse_config;
    private boolean include_details;
    private boolean validate;
}
