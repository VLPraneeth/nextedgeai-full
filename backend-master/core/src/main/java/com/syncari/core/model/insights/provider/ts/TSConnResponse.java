package com.syncari.core.model.insights.provider.ts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class TSConnResponse {

    String id;
    String name;
    private String description;
    String data_warehouse_type;
    TSConnectionDetail details;
}
