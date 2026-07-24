package com.syncari.api.rest.controllers.data.studio;

import lombok.Data;

@Data
public class EntityVersions {
    String apiName;
    Entity draft;
    Entity published;
}
