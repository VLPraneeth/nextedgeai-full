package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TSTableResp {

    private String id;
    private String name;
    private String type;
    private String created;
    private String modified;
}
