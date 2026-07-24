package com.syncari.api.rest.controllers.data.fragment;

import com.syncari.restutils.data.MappingNodeDTO;
import lombok.Data;

@Data
public class FragmentNodeDTO extends MappingNodeDTO {

    private String templateId;
}
