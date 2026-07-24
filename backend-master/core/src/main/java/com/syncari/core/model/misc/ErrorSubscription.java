package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import com.syncari.core.model.ErrorNotificationFrequency;

import lombok.Data;

@Data
public class ErrorSubscription {
    private String catalogId;
    private boolean active;
    private ErrorNotificationFrequency frequency;
    private List<String> channels = new ArrayList<>();
}
