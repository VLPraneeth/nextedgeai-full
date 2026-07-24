package com.syncari.connector.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@ToString
public class CloudFunctionInfo {
    private String cloudFunctionEndpoint;
    private String custSynapseIdentifier;
    private String deployerCredentialsKey;
    private String executorCredentialsKey;
    private Date lastModified;
    private String host;
    private String syncariId;
    
    public CloudFunctionInfo() {
    }
}
