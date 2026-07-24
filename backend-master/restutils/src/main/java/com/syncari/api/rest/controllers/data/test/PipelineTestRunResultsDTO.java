package com.syncari.api.rest.controllers.data.test;

import java.util.*;

import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.PipelineTestWebhook;
import com.syncari.utils.DateUtil;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.*;
import lombok.experimental.*;

@Data
@Accessors(chain = true)
public class PipelineTestRunResultsDTO {
    private String id;
    private String runName;
    private String status;
    private String description;
    String startTime;
    String endTime;
    long limit;
    Map<String, List<String>> recordIds;
    String createdAt;
    String updatedAt;
    String errorMsg;
    private List<PipelineTestRunResultDTO> resultDetails;
    Map<String, PipelineTestWebhook> webhook;

    public PipelineTestRunResultsDTO() {}

    public PipelineTestRunResultsDTO(PipelineTest pipelineTest) {
        DateUtil dateUtil = new DateUtil();
        String testName = StringUtils.isNotEmpty(pipelineTest.getName()) ? pipelineTest.getName() : 
            dateUtil.format(pipelineTest.getCreatedAt(), DateUtil.dateFormat1);
        id = pipelineTest.getId();
        runName = testName;
        description = pipelineTest.getDescription();
        startTime = (pipelineTest.getStartTime() != null) ? 
            dateUtil.format(new Date(pipelineTest.getStartTime().toEpochMilli()), DateUtil.dateFormat1) : "";
        endTime = (pipelineTest.getEndTime() != null) ? 
            dateUtil.format(new Date(pipelineTest.getEndTime().toEpochMilli()), DateUtil.dateFormat1) : "";
        limit = pipelineTest.getLimit();
        recordIds = pipelineTest.getRecordIds();
        status = pipelineTest.getStatus().name();
        createdAt = dateUtil.format(pipelineTest.getCreatedAt(), DateUtil.dateFormat1);
        updatedAt = dateUtil.format(pipelineTest.getUpdatedAt(), DateUtil.dateFormat1);
        errorMsg = pipelineTest.getErrorMsg();
        webhook = pipelineTest.getWebhook();
    }

}
