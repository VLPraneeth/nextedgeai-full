package com.syncari.api.rest.controllers.data.test;

import java.util.*;

import com.syncari.core.model.PipelineTest;
import com.syncari.core.model.TestResult;
import com.syncari.core.model.User;
import com.syncari.utils.DateUtil;

import org.parboiled.common.StringUtils;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PipelineTestRunResultDTO {
    private String id;
    private String displayName;
    private String description;
    private String syncariRecordId;
    private String externalRecordId;
    private String connectorName;
    private Set<String> tags = new HashSet<>();
    private PipelineTestData testData;
    private String ownerFirstName;
    private String ownerLastName;
    private String ownerEmail;
    //private PipelineTestStatus result;
    private String status;
    private String errorMsg;
    private List<PipelineTestNodeRunResultDTO> nodes = new ArrayList<>();
    private String entityId;

    public PipelineTestRunResultDTO() {}

    public PipelineTestRunResultDTO(PipelineTest test, List<PipelineTestNodeRunResultDTO> nodeResultDTOs, PipelineTestData pipelineTestData,
            TestResult result, User user) {
        this.id = result.getId().toString();
        this.displayName = StringUtils.isNotEmpty(test.getName()) ? test.getName() : 
            new DateUtil().format(test.getCreatedAt(), DateUtil.dateFormat1);
        this.description = test.getDescription();
        this.nodes = nodeResultDTOs;
        this.syncariRecordId = result.getSyncariRecordId();
        this.externalRecordId = result.getExternalRecordId();
        this.connectorName = result.getConnectorName();
        this.status = result.getStatus().name();
        this.errorMsg = result.getErrorMsg();
        this.ownerFirstName = user.getFirstName();
        this.ownerLastName = user.getLastName();
        this.ownerEmail = user.getEmail();
        this.testData = pipelineTestData;
        this.entityId = result.getEntityId();
    }
}
