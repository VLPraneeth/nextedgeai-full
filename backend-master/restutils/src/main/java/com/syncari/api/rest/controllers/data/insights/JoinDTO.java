package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.JoinType;
import lombok.Data;

@Data
public class JoinDTO {
    private DatasetFieldDTO field1;
    private DatasetFieldDTO field2;
    private JoinType joinType;
}
