package com.syncari.core.model.insights.provider;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class InsightsProviderGroup {

    private String name;
    private String display_name;
    private String visibility="NON_SHARABLE";
    private String type="LOCAL_GROUP";
    List<String> privileges;
    List<String> user_identifiers;

}
