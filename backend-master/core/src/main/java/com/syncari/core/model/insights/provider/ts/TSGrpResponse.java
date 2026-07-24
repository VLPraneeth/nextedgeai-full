package com.syncari.core.model.insights.provider.ts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class TSGrpResponse {

    private String id;
    private String name;
    private boolean complete_detail;
    private boolean deleted;
    private String generation_number;
    private boolean hidden;
    private String author_id;
    private String display_name;
    private String visibility;
    private boolean system_group;
    private List<TSUserGrpResponse> users;
    private List<String> privileges;
}
