package com.syncari.restutils.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@Accessors(chain = true)
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode
@NoArgsConstructor
public class MappingGraphVersionRequestWrapper {
    private MappingGraphVersionRequestDTO versionInfo;
}
