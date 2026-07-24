package com.syncari.connector.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class Pipeline {
    String apiName;
    String draftStatus;
    String instanceId;
}
