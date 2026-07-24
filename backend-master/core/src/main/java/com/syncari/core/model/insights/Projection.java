package com.syncari.core.model.insights;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Optional;

@Data
@Accessors(chain = true)
@ToString
public class Projection {
    private String aliasName;
    private QueryFunction function;
    private String dataType;
}
