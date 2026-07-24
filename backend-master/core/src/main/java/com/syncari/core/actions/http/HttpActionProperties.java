package com.syncari.core.actions.http;

import com.syncari.connector.ConnectorType;
import com.syncari.core.actions.ActionProperties;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
public class HttpActionProperties implements ActionProperties {
    private String endPoint;
    private HttpMethod method;
    private Map<String, String> headers;
    private AuthenticationInfo authenticationInfo;
    private String body;
    private int batchSize;
    private boolean isBatch;
}