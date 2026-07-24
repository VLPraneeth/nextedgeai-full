package com.syncari.api.rest.controllers.data.insights;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardConfigDTO {

    String name;
    String displayName;
    String description;
}
