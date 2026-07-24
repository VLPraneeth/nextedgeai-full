package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ErrorNotificationPreference {
    private List<ErrorSubscription> subscriptions = new ArrayList<>();
    private List<ErrorChannelConfiguration> channelConfigurations = new ArrayList<>();
}
