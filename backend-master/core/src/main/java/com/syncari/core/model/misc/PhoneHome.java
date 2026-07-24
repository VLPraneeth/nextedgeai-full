package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class PhoneHome {
    private String phoneHomeId;
    private String errorMessage;
    private Map<String, Object> browserInfo;

    // what page was the user on when it crashed
    private String url;

    // our "blackbox" zip file that spectrum uploaded to GCS
    private String blackboxUrl;

    private String originalStack;

    // Note: The errorStack, state and actions are base64 encoded strings
    // so we do not lose any raw information or have encoding issue from the browser
    private String errorStack;
    private String state;
    private String actions;

    public PhoneHome() {
    }
}
