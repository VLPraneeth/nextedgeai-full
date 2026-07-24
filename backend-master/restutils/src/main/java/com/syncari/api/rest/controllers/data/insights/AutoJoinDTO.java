package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;

import java.util.List;

@Data
public class AutoJoinDTO {
    List<DatasetFromDTO> existingDataSources;
    List<DatasetFromDTO> newDataSources;
}
