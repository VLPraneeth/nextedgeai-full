package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Deprecated
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorNotificationUserInfo extends UUIDAuditModel {
    private String key;
    private String userId;
}
