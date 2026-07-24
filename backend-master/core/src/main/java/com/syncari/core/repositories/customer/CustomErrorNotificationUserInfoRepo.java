package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.ErrorNotificationUserInfo;

public interface CustomErrorNotificationUserInfoRepo {
    Optional<ErrorNotificationUserInfo> findLatestNotifForUserByKey(String key, String userId);
}
