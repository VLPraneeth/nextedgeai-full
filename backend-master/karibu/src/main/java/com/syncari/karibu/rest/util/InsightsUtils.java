package com.syncari.karibu.rest.util;

import com.syncari.api.rest.controllers.data.insights.DatasetDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetSampleDTO;
import com.syncari.karibu.rest.response.DatasetDataResponse;
import com.syncari.karibu.rest.response.DatasetResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class InsightsUtils {

    public DatasetDataResponse getDatasetDataResponse(DatasetSampleDTO sampleDTO){
        DatasetDataResponse response = new DatasetDataResponse();
        if (null != sampleDTO){
            response.setData(sampleDTO.getData());
            response.setColumns(sampleDTO.getColumns());
            response.setPageInfo(sampleDTO.getPageInfo());
        }
        return response;
    }

    public DatasetResponse getDatasetResponse(DatasetDTO dto){
        DatasetResponse response = new DatasetResponse();
        if (null != dto){
            response.setDatasetConfig(dto.getDatasetConfig());
            response.setDescription(dto.getDescription());
            if (null != dto.getCreatedAt()){
                response.setCreatedAt(Date.from(dto.getCreatedAt().toInstant()));
            }
            if (null != dto.getCreatedBy()){
                response.setCreatedBy(dto.getCreatedBy());
            }
            response.setDisplayName(dto.getDisplayName());
            response.setHidden(dto.isHidden());
            response.setDraftStatus(dto.getDraftStatus());
            response.setName(dto.getName());
            response.setId(dto.getId());
            response.setSeeded(dto.isSeeded());
            if (CollectionUtils.isNotEmpty(dto.getTags())){
                response.setTags(dto.getTags());
            }
            if (null != dto.getUpdatedAt()){
                response.setUpdatedAt(Date.from(dto.getUpdatedAt().toInstant()));
            }
            if (null != dto.getUpdatedBy()){
                response.setUpdatedBy(dto.getUpdatedBy());
            }
        }
        return response;
    }
}
