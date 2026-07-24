package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TSPermission {

    private TSPrincipalInput principal;
    // NO_ACCESS, READ_ONLY, MODIFY
    private String share_mode;
}
