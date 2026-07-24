package com.syncari.api.rest.controllers.data.test;

import java.util.*;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SimulationTestRunDTO {
    private String id;
    private String runName;
    private List<String> testNames = new ArrayList<>();
}
