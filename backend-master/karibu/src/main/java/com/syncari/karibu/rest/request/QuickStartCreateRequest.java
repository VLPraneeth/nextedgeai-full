package com.syncari.karibu.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Data
@AllArgsConstructor
public class QuickStartCreateRequest {

    private String displayName;
    private String description;
    private String postInstallationInstruction;
    private List<String> tags = new ArrayList<>();
    private MultipartFile icon;
    private List<String> shareWithInstances = new ArrayList<>();
    private boolean shareWithOrg;
    private String publishToQuickStartLibrary;
    private List<Map<String, Object>> entities;

}
