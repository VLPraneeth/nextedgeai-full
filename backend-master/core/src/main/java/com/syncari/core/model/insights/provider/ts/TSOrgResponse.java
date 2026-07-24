package com.syncari.core.model.insights.provider.ts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class TSOrgResponse {

    private String id;
    private String name;
    private String status;
    private String description;
    private String visibility;
}
