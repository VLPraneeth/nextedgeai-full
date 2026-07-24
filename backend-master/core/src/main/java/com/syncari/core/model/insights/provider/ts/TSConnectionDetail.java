package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
@Data
@Accessors(chain = true)
public class TSConnectionDetail {

    private String name;
    private String type;
    private String id;
    private String created;
    private String modified;
    private List<TSTableResp> tables;
}
