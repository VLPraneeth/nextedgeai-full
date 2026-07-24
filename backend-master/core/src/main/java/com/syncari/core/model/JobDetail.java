package com.syncari.core.model;

import com.syncari.connector.data.BatchJob;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;


@Data
@Accessors(chain = true)
public class JobDetail extends UUIDAuditModel {
    private BatchJob job;
}

