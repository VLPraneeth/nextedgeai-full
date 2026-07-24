package com.syncari.restutils.data;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class MappingGraphRestoreVersionRequestDTO {
    private boolean restoreAll;
    private List<String> fieldIds;
    private boolean restoreEntity;
}
