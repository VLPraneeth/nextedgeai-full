package com.syncari.api.rest.controllers.data;

import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ConnectoryEntityNodeDTO {
    private String id;
    private String name;
    private String iconPath;
    private String backgroundColor;
    private List<KeyValue> configuration;
    private KeyValue renderer;
    private boolean isCoreNode = false;
}
