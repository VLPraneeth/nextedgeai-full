package com.syncari.core.utils;


import lombok.Data;

@Data
public class CustomSynapseDraftIssue {
    private String issue_text;
    private CustomSynapseDraftIssueSeverity issue_severity;
    private String issue_confidence;
    private int line_number;
}