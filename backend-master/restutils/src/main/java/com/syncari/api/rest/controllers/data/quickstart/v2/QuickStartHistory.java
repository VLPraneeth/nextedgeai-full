package com.syncari.api.rest.controllers.data.quickstart.v2;

import lombok.Data;

import java.util.List;

@Data
public class QuickStartHistory {

    String displayName;
    String name;
    List<QuickStartRunDTO> runs;
}
