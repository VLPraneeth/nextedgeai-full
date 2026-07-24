package com.syncari.core.model.insights.provider.ts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class TSUserGrpResponse {
    private String id;
    private String name;
}
