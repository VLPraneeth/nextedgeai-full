package com.syncari.karibu.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.*;

@Data
@AllArgsConstructor
public class InstanceRequest {
    @NotEmpty(message = "Instance subscriptionName is empty. Please verify these request parameters")
    private String subscriptionName;
    @NotEmpty(message = "Instance name is empty. Please verify these request parameters")
    @Size(max = 30, message = "Length of Instance name is more than 30 characters. Please reduce the length to process the request")
    private String name;
    @NotEmpty(message = "Instance displayName is empty. Please verify these request parameters")
    @Size(max = 30, message = "Length of Instance displayName is more than 30 characters. Please reduce the length to process the request")
    private String displayName;
    @Pattern(regexp = "default|trial", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Invalid plan name. plan must one of default | trial")
    @NotEmpty(message = "Instance planName is empty. Please verify these request parameters")
    private String planName;
    @NotEmpty(message = "Instance type is empty. Please verify these request parameters")
    @Pattern(regexp = "production|sandbox|trial|demo|internal", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Invalid type. type must one of production | sandbox | trial | demo | internal")
    private String type;
}
